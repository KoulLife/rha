package com.rha.reservation;

import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import com.rha.common.paging.PageInfo;
import com.rha.common.validation.RequestValidator;
import com.rha.lesson.Lesson;
import com.rha.lesson.LessonRepository;
import com.rha.lesson.LessonStatus;
import com.rha.lessontype.LessonType;
import com.rha.lessontype.LessonTypeRepository;
import com.rha.member.Member;
import com.rha.member.MemberPermissionRepository;
import com.rha.member.MemberRepository;
import com.rha.member.MemberRole;
import com.rha.pass.Pass;
import com.rha.pass.PassRepository;
import com.rha.passproduct.PassProduct;
import com.rha.passproduct.PassProductRepository;
import com.rha.payment.PassPaymentRepository;
import com.rha.reservationpolicy.ReservationPolicy;
import com.rha.reservationpolicy.ReservationPolicyRepository;
import com.rha.room.Room;
import com.rha.room.RoomRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수업 예약, 취소, 출결 기록과 조회. 예약하면 서버가 이용권을 골라 횟수를 1회 차감하고 취소하면 복구한다.
 * 강사 정산 포함 여부에 따른 출결 정정 제한(LESSON_SETTLED)과 관리자 취소 알림은 정산·알림 기능이 구현될 때 추가한다.
 */
@Service
public class ReservationService {

    private static final ZoneId CENTER_ZONE = ZoneId.of("Asia/Seoul");
    private static final String MEMBER_READ = "MEMBER_READ";

    /** 목록 조회 조건. */
    public record Filter(Long memberId, Long lessonId, ReservationStatus status, Instant lessonFrom,
            Instant lessonTo) {
    }

    private final ReservationRepository reservationRepository;
    private final LessonRepository lessonRepository;
    private final LessonTypeRepository lessonTypeRepository;
    private final RoomRepository roomRepository;
    private final MemberRepository memberRepository;
    private final MemberPermissionRepository permissionRepository;
    private final PassRepository passRepository;
    private final PassProductRepository productRepository;
    private final PassPaymentRepository paymentRepository;
    private final ReservationPolicyRepository policyRepository;
    private final RequestValidator requestValidator;

    public ReservationService(ReservationRepository reservationRepository, LessonRepository lessonRepository,
            LessonTypeRepository lessonTypeRepository, RoomRepository roomRepository,
            MemberRepository memberRepository, MemberPermissionRepository permissionRepository,
            PassRepository passRepository, PassProductRepository productRepository,
            PassPaymentRepository paymentRepository, ReservationPolicyRepository policyRepository,
            RequestValidator requestValidator) {
        this.reservationRepository = reservationRepository;
        this.lessonRepository = lessonRepository;
        this.lessonTypeRepository = lessonTypeRepository;
        this.roomRepository = roomRepository;
        this.memberRepository = memberRepository;
        this.permissionRepository = permissionRepository;
        this.passRepository = passRepository;
        this.productRepository = productRepository;
        this.paymentRepository = paymentRepository;
        this.policyRepository = policyRepository;
        this.requestValidator = requestValidator;
    }

    // ===== 예약 =====

    /**
     * 확인 순서: 일반 회원(403) → 입력(400) → 수업(404) → 예약 가능 여부 → 중복 → 정원 → 이용권(409).
     * 수업, 회원, 이용권 행을 잠가 정원과 횟수가 동시 요청으로 어긋나지 않게 한다.
     */
    @Transactional
    public ReservationResponse createReservation(Long callerId, ReservationCreateRequest request) {
        Member caller = findCaller(callerId);
        if (!caller.isActive() || !caller.isRole(MemberRole.MEMBER)) {
            throw new ApiException(ErrorCode.PERMISSION_DENIED);
        }
        requestValidator.validate(request);

        Lesson lesson = lessonRepository.findByIdForUpdate(request.lessonId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        Instant now = Instant.now();
        if (lesson.getStatus() != LessonStatus.PUBLISHED || lesson.hasStarted(now)) {
            throw new ApiException(ErrorCode.LESSON_NOT_RESERVABLE);
        }
        memberRepository.findByIdForUpdate(callerId);
        if (reservationRepository.existsByLessonIdAndMemberIdAndStatusNot(lesson.getId(), callerId,
                ReservationStatus.CANCELED)) {
            throw new ApiException(ErrorCode.ALREADY_RESERVED);
        }
        if (reservationRepository.countByLessonIdAndStatusNot(lesson.getId(), ReservationStatus.CANCELED)
                >= lesson.getCapacity()) {
            throw new ApiException(ErrorCode.LESSON_FULL);
        }

        Pass pass = choosePass(callerId, lesson);
        pass.consumeOne(now);
        Reservation reservation = reservationRepository.save(
                Reservation.reserve(lesson.getId(), callerId, pass.getId(), now));
        return toResponses(List.of(reservation)).get(0);
    }

    /**
     * 수업일(현지 날짜)이 기간 안에 있고 일시정지에 속하지 않으며 잔여 횟수가 있는 이용권 중
     * 만료일이 가장 이른 것(같으면 먼저 발급된 것)을 고른다. 미수 예약이 불허이면 완납한 이용권만 대상이다.
     */
    private Pass choosePass(Long memberId, Lesson lesson) {
        LocalDate lessonDate = lesson.getStartAt().atZone(CENTER_ZONE).toLocalDate();
        List<Pass> candidates = passRepository.findReservableCandidates(memberId, lessonDate);
        if (candidates.isEmpty()) {
            throw new ApiException(ErrorCode.NO_AVAILABLE_PASS);
        }
        boolean allowUnpaid = policy().isAllowUnpaidReservation();
        boolean skippedUnpaid = false;
        for (Pass candidate : candidates) {
            if (!allowUnpaid && paymentRepository.sumByPassId(candidate.getId()) < candidate.getPrice()) {
                skippedUnpaid = true;
                continue;
            }
            Pass locked = passRepository.findByIdForUpdate(candidate.getId()).orElse(null);
            if (locked != null && locked.getRemainingCount() > 0) {
                return locked;
            }
        }
        throw new ApiException(skippedUnpaid ? ErrorCode.PASS_UNPAID : ErrorCode.NO_AVAILABLE_PASS);
    }

    /**
     * 확인 순서: 입력(400) → 없거나 볼 수 없는 예약(404) → 본인·관리자 아님(403)
     * → 상태 → 이미 시작 → 마감(409). 관리자는 마감과 무관하게 취소할 수 있다.
     */
    @Transactional
    public ReservationResponse cancelReservation(Long callerId, Long reservationId,
            ReservationCancelRequest request) {
        ReservationCancelRequest body = request != null ? request : new ReservationCancelRequest(null);
        requestValidator.validate(body);
        Member caller = findCaller(callerId);
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        Lesson lesson = lessonRepository.findById(reservation.getLessonId()).orElseThrow();
        if (!canView(caller, reservation, lesson)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        boolean admin = isAdmin(caller);
        if (!admin && !reservation.getMemberId().equals(callerId)) {
            throw new ApiException(ErrorCode.PERMISSION_DENIED);
        }
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new ApiException(ErrorCode.INVALID_RESERVATION_STATUS);
        }
        Instant now = Instant.now();
        if (lesson.hasStarted(now)) {
            throw new ApiException(ErrorCode.LESSON_ALREADY_STARTED);
        }
        if (!admin) {
            Instant deadline = lesson.getStartAt().minus(policy().getCancelDeadlineHours(), ChronoUnit.HOURS);
            if (now.isAfter(deadline)) {
                throw new ApiException(ErrorCode.CANCELLATION_DEADLINE_PASSED);
            }
        }

        reservation.cancel(admin ? CanceledBy.ADMIN : CanceledBy.MEMBER, body.reason(), now);
        passRepository.findByIdForUpdate(reservation.getPassId()).ifPresent(pass -> pass.restoreOne(now));
        return toResponses(List.of(reservation)).get(0);
    }

    /**
     * 출석 또는 노쇼를 기록하거나 정정한다. 이용권 횟수에는 영향이 없다.
     * 확인 순서: 관리자·담당 강사(403) → 입력(400) → 예약(404) → 상태 → 시작 전(409).
     */
    @Transactional
    public ReservationResponse confirmAttendance(Long callerId, Long reservationId, AttendanceRequest request) {
        Member caller = findCaller(callerId);
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        Lesson lesson = reservation == null ? null : lessonRepository.findById(reservation.getLessonId()).orElse(null);
        // 담당 강사가 아니면 수업이 없어도 존재 여부를 숨기기 위해 403이다.
        boolean allowed = isAdmin(caller) || (caller.isActive() && caller.isRole(MemberRole.INSTRUCTOR)
                && lesson != null && lesson.getInstructorId().equals(callerId));
        if (!allowed) {
            throw new ApiException(ErrorCode.PERMISSION_DENIED);
        }
        ReservationStatus target = attendanceStatus(request.status());
        if (reservation == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        if (reservation.getStatus() == ReservationStatus.CANCELED) {
            throw new ApiException(ErrorCode.INVALID_RESERVATION_STATUS);
        }
        Instant now = Instant.now();
        if (!lesson.hasStarted(now)) {
            throw new ApiException(ErrorCode.LESSON_NOT_STARTED);
        }
        reservation.confirmAttendance(target, now);
        return toResponses(List.of(reservation)).get(0);
    }

    private static ReservationStatus attendanceStatus(String value) {
        if ("ATTENDED".equals(value)) {
            return ReservationStatus.ATTENDED;
        }
        if ("NO_SHOW".equals(value)) {
            return ReservationStatus.NO_SHOW;
        }
        throw new ApiException(ErrorCode.INVALID_INPUT);
    }

    // ===== 조회 =====

    /** 관리자 또는 MEMBER_READ 권한을 가진 강사만 조회한다. 수업 시작 시각 순(같으면 id)이다. */
    @Transactional(readOnly = true)
    public ReservationPageResponse getReservations(Long callerId, Filter filter, Sort.Direction order, int page,
            int size) {
        Member caller = findCaller(callerId);
        boolean allowed = isAdmin(caller) || (caller.isActive() && caller.isRole(MemberRole.INSTRUCTOR)
                && permissionRepository.existsByMemberIdAndPermissionCode(callerId, MEMBER_READ));
        if (!allowed) {
            throw new ApiException(ErrorCode.PERMISSION_DENIED);
        }
        requireValidRange(filter);
        return page(filter, order, page, size);
    }

    /** 로그인한 회원 본인의 예약만 조회한다. 일반 회원이 아니면 빈 목록이다. */
    @Transactional(readOnly = true)
    public ReservationPageResponse getMyReservations(Long callerId, ReservationStatus status, Instant lessonFrom,
            Instant lessonTo, Sort.Direction order, int page, int size) {
        Member caller = findCaller(callerId);
        Filter filter = new Filter(callerId, null, status, lessonFrom, lessonTo);
        requireValidRange(filter);
        if (!caller.isRole(MemberRole.MEMBER)) {
            PageRequest pageable = PageRequest.of(page - 1, size);
            return new ReservationPageResponse(List.of(), PageInfo.of(Page.empty(pageable)));
        }
        return page(filter, order, page, size);
    }

    /** 관리자 또는 해당 수업의 담당 강사만 조회한다. 예약 시각 오름차순(같으면 id 오름차순)이다. */
    @Transactional(readOnly = true)
    public ReservationPageResponse getLessonReservations(Long callerId, Long lessonId, ReservationStatus status,
            int page, int size) {
        Member caller = findCaller(callerId);
        Lesson lesson = lessonRepository.findById(lessonId).orElse(null);
        boolean allowed = isAdmin(caller) || (caller.isActive() && caller.isRole(MemberRole.INSTRUCTOR)
                && lesson != null && lesson.getInstructorId().equals(callerId));
        if (!allowed) {
            throw new ApiException(ErrorCode.PERMISSION_DENIED);
        }
        if (lesson == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        PageRequest pageable = PageRequest.of(page - 1, size,
                Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")));
        Specification<Reservation> spec = (root, query, cb) -> status == null
                ? cb.equal(root.get("lessonId"), lessonId)
                : cb.and(cb.equal(root.get("lessonId"), lessonId), cb.equal(root.get("status"), status));
        Page<Reservation> result = reservationRepository.findAll(spec, pageable);
        return new ReservationPageResponse(toResponses(result.getContent()), PageInfo.of(result));
    }

    /** 예약한 본인, 관리자, 해당 수업의 담당 강사, MEMBER_READ 권한을 가진 강사만 볼 수 있다. */
    @Transactional(readOnly = true)
    public ReservationResponse getReservation(Long callerId, Long reservationId) {
        Member caller = findCaller(callerId);
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        Lesson lesson = lessonRepository.findById(reservation.getLessonId()).orElseThrow();
        if (!canView(caller, reservation, lesson)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        return toResponses(List.of(reservation)).get(0);
    }

    private ReservationPageResponse page(Filter filter, Sort.Direction order, int page, int size) {
        PageRequest pageable = PageRequest.of(page - 1, size,
                Sort.by(new Sort.Order(order, "lesson.startAt"), new Sort.Order(order, "id")));
        Specification<Reservation> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.memberId() != null) {
                predicates.add(cb.equal(root.get("memberId"), filter.memberId()));
            }
            if (filter.lessonId() != null) {
                predicates.add(cb.equal(root.get("lessonId"), filter.lessonId()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.lessonFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("lesson").<Instant>get("startAt"),
                        filter.lessonFrom()));
            }
            if (filter.lessonTo() != null) {
                predicates.add(cb.lessThan(root.get("lesson").<Instant>get("startAt"), filter.lessonTo()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<Reservation> result = reservationRepository.findAll(spec, pageable);
        return new ReservationPageResponse(toResponses(result.getContent()), PageInfo.of(result));
    }

    private static void requireValidRange(Filter filter) {
        if (filter.lessonFrom() != null && filter.lessonTo() != null
                && !filter.lessonTo().isAfter(filter.lessonFrom())) {
            throw new ApiException(ErrorCode.INVALID_INPUT);
        }
    }

    // ===== 공통 =====

    private Member findCaller(Long callerId) {
        return memberRepository.findById(callerId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }

    private static boolean isAdmin(Member member) {
        return member.isActive() && member.isRole(MemberRole.ADMIN);
    }

    private boolean canView(Member caller, Reservation reservation, Lesson lesson) {
        if (isAdmin(caller) || reservation.getMemberId().equals(caller.getId())) {
            return true;
        }
        if (caller.isActive() && caller.isRole(MemberRole.INSTRUCTOR)) {
            return lesson.getInstructorId().equals(caller.getId())
                    || permissionRepository.existsByMemberIdAndPermissionCode(caller.getId(), MEMBER_READ);
        }
        return false;
    }

    private ReservationPolicy policy() {
        return policyRepository.findById((short) 1).orElseThrow();
    }

    /** 수업, 유형, 강사, 룸, 회원, 이용권, 상품을 한 번에 조회해 응답으로 바꾼다. */
    private List<ReservationResponse> toResponses(List<Reservation> reservations) {
        Set<Long> lessonIds = reservations.stream().map(Reservation::getLessonId).collect(Collectors.toSet());
        Set<Long> passIds = reservations.stream().map(Reservation::getPassId).collect(Collectors.toSet());
        Map<Long, Lesson> lessons = lessonRepository.findAllById(lessonIds).stream()
                .collect(Collectors.toMap(Lesson::getId, Function.identity()));
        Map<Long, Pass> passes = passRepository.findAllById(passIds).stream()
                .collect(Collectors.toMap(Pass::getId, Function.identity()));

        Set<Long> memberIds = new HashSet<>();
        Set<Long> typeIds = new HashSet<>();
        Set<Long> roomIds = new HashSet<>();
        Set<Long> productIds = new HashSet<>();
        reservations.forEach(r -> memberIds.add(r.getMemberId()));
        lessons.values().forEach(l -> {
            memberIds.add(l.getInstructorId());
            typeIds.add(l.getLessonTypeId());
            roomIds.add(l.getRoomId());
        });
        passes.values().forEach(p -> productIds.add(p.getPassProductId()));

        Map<Long, Member> members = memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));
        Map<Long, LessonType> types = lessonTypeRepository.findAllById(typeIds).stream()
                .collect(Collectors.toMap(LessonType::getId, Function.identity()));
        Map<Long, Room> rooms = roomRepository.findAllById(roomIds).stream()
                .collect(Collectors.toMap(Room::getId, Function.identity()));
        Map<Long, PassProduct> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(PassProduct::getId, Function.identity()));

        return reservations.stream().map(r -> {
            Lesson lesson = lessons.get(r.getLessonId());
            LessonType type = types.get(lesson.getLessonTypeId());
            Member instructor = members.get(lesson.getInstructorId());
            Room room = rooms.get(lesson.getRoomId());
            Member member = members.get(r.getMemberId());
            Pass pass = passes.get(r.getPassId());
            PassProduct product = products.get(pass.getPassProductId());
            return new ReservationResponse(r.getId(),
                    new ReservationResponse.LessonSummary(lesson.getId(), lesson.getTitle(),
                            new ReservationResponse.LessonTypeSummary(type.getId(), type.getCode(), type.getName()),
                            new ReservationResponse.IdName(instructor.getId(), instructor.getName()),
                            new ReservationResponse.IdName(room.getId(), room.getName()), lesson.getStartAt(),
                            lesson.getEndAt(), lesson.getStatus()),
                    new ReservationResponse.PersonSummary(member.getId(), member.getName()),
                    new ReservationResponse.PassSummary(pass.getId(),
                            new ReservationResponse.IdName(product.getId(), product.getName()), pass.getEndDate()),
                    r.getStatus(), r.getAttendanceConfirmedAt(), r.getCanceledBy(), r.getCanceledAt(),
                    r.getCancelReason(), r.getCreatedAt());
        }).toList();
    }
}
