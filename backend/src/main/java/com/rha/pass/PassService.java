package com.rha.pass;

import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import com.rha.common.paging.LikePattern;
import com.rha.common.paging.PageInfo;
import com.rha.common.validation.RequestValidator;
import com.rha.member.AdminGuard;
import com.rha.member.Member;
import com.rha.member.MemberRepository;
import com.rha.member.MemberRole;
import com.rha.member.MemberStatus;
import com.rha.passproduct.PassProduct;
import com.rha.payment.PassPayment;
import com.rha.payment.PassPaymentRepository;
import com.rha.reservation.ReservationRepository;
import com.rha.passproduct.PassProductRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
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
import com.rha.activity.ActivityAction;
import com.rha.activity.ActivityChange;
import com.rha.activity.ActivityLogRecorder;
import com.rha.activity.ActivityTime;

/**
 * 이용권 발급·조회·수정과 일시정지, 날짜에 따른 상태 전환.
 *
 * <p>상태는 조회·수정할 때와 주기적으로 {@link #refreshStatuses}가 최신으로 맞춘다.
 */
@Service
public class PassService {

    private static final ZoneId CENTER_ZONE = ZoneId.of("Asia/Seoul");
    private static final List<PassStatus> NOT_EXPIRED = List.of(PassStatus.UPCOMING, PassStatus.ACTIVE,
            PassStatus.PAUSED);

    /** 목록 조회 조건. */
    public record Filter(Long memberId, String keyword, Long instructorId, Long passProductId, PassStatus status,
            PaymentStatus paymentStatus, Instant createdFrom, Instant createdTo) {
    }

    private final PassRepository passRepository;
    private final PassPauseRepository pauseRepository;
    private final PassProductRepository productRepository;
    private final PassPaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final MemberRepository memberRepository;
    private final AdminGuard adminGuard;
    private final RequestValidator requestValidator;
    private final ActivityLogRecorder activityLogRecorder;

    public PassService(PassRepository passRepository, PassPauseRepository pauseRepository,
            PassProductRepository productRepository, PassPaymentRepository paymentRepository,
            ReservationRepository reservationRepository, MemberRepository memberRepository, AdminGuard adminGuard, RequestValidator requestValidator,
            ActivityLogRecorder activityLogRecorder) {
        this.activityLogRecorder = activityLogRecorder;
        this.passRepository = passRepository;
        this.pauseRepository = pauseRepository;
        this.productRepository = productRepository;
        this.paymentRepository = paymentRepository;
        this.reservationRepository = reservationRepository;
        this.memberRepository = memberRepository;
        this.adminGuard = adminGuard;
        this.requestValidator = requestValidator;
    }

    // ===== 상태 전환 =====

    /** 날짜가 바뀐 만큼 일시정지와 이용권의 상태를 전환한다. 여러 번 호출해도 결과는 같다. */
    @Transactional
    public void refreshStatuses(LocalDate today) {
        Instant now = Instant.now();
        pauseRepository.activate(today);
        pauseRepository.end(today);
        List<Pass> expiring = passRepository.findExpiringForUpdate(today);
        for (Pass pass : expiring) {
            PassStatus beforeStatus = pass.getStatus();
            pass.changeStatus(PassStatus.EXPIRED, now);
            activityLogRecorder.record(ActivityAction.PASS_EXPIRED, null, pass.getMemberId(), "PASS", pass.getId(),
                    "이용권 #" + pass.getId() + " 만료 (만료일 " + pass.getEndDate() + " 경과)",
                    ActivityChange.of("status", beforeStatus, PassStatus.EXPIRED));
        }
        passRepository.flush();
        passRepository.pause(today, now);
        passRepository.activate(today, now);
    }

    private LocalDate today() {
        return LocalDate.now(CENTER_ZONE);
    }

    /** 날짜와 진행 중인 정지로 이용권의 상태를 정한다. */
    private PassStatus statusOf(Pass pass, LocalDate today) {
        if (pass.getEndDate().isBefore(today)) {
            return PassStatus.EXPIRED;
        }
        if (pauseRepository.existsByPassIdAndStatus(pass.getId(), PassPauseStatus.ACTIVE)) {
            return PassStatus.PAUSED;
        }
        return pass.getStartDate().isAfter(today) ? PassStatus.UPCOMING : PassStatus.ACTIVE;
    }

    // ===== 조회 =====

    @Transactional
    public PassPageResponse getPasses(Long callerId, Filter filter, String sortBy, Sort.Direction order, int page,
            int size) {
        adminGuard.requireAdmin(callerId);
        if (filter.createdFrom() != null && filter.createdTo() != null
                && !filter.createdTo().isAfter(filter.createdFrom())) {
            throw new ApiException(ErrorCode.INVALID_INPUT);
        }
        refreshStatuses(today());

        PageRequest pageable = PageRequest.of(page - 1, size, Sort.by(new Sort.Order(order, sortBy),
                new Sort.Order(order, "id")));
        Page<Pass> result = passRepository.findAll(specificationOf(filter), pageable);
        return new PassPageResponse(toResponses(result.getContent()), PageInfo.of(result));
    }

    @Transactional
    public PassResponse getPass(Long callerId, Long passId) {
        adminGuard.requireAdmin(callerId);
        refreshStatuses(today());
        return toResponse(find(passId));
    }

    /** 로그인한 회원 본인의 이용권을 만료일 오름차순(같으면 id 오름차순)으로 조회한다. 일반 회원이 아니면 빈 목록이다. */
    @Transactional
    public PassPageResponse getMyPasses(Long callerId, PassStatus status, int page, int size) {
        Member caller = memberRepository.findById(callerId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        PageRequest pageable = PageRequest.of(page - 1, size,
                Sort.by(Sort.Order.asc("endDate"), Sort.Order.asc("id")));
        if (!caller.isRole(MemberRole.MEMBER)) {
            return new PassPageResponse(List.of(), PageInfo.of(Page.empty(pageable)));
        }
        refreshStatuses(today());

        Specification<Pass> spec = (root, query, cb) -> status == null
                ? cb.equal(root.get("memberId"), callerId)
                : cb.and(cb.equal(root.get("memberId"), callerId), cb.equal(root.get("status"), status));
        Page<Pass> result = passRepository.findAll(spec, pageable);
        return new PassPageResponse(toResponses(result.getContent()), PageInfo.of(result));
    }

    private Specification<Pass> specificationOf(Filter filter) {
        String pattern = LikePattern.contains(filter.keyword());
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.memberId() != null) {
                predicates.add(cb.equal(root.get("memberId"), filter.memberId()));
            }
            if (pattern != null) {
                Subquery<Long> members = query.subquery(Long.class);
                Root<Member> member = members.from(Member.class);
                members.select(member.get("id")).where(cb.like(cb.lower(member.get("name")), pattern, '\\'));
                predicates.add(root.get("memberId").in(members));
            }
            if (filter.instructorId() != null) {
                predicates.add(cb.equal(root.get("instructorId"), filter.instructorId()));
            }
            if (filter.passProductId() != null) {
                predicates.add(cb.equal(root.get("passProductId"), filter.passProductId()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.paymentStatus() != null) {
                // 결제 합계(환불로 체크된 기록 포함)와 이용권 금액을 비교한다.
                Subquery<Long> sum = query.subquery(Long.class);
                Root<PassPayment> payment = sum.from(PassPayment.class);
                sum.select(cb.sum(payment.<Long>get("amount")))
                        .where(cb.equal(payment.get("passId"), root.get("id")));
                var paid = cb.coalesce(sum, 0L);
                var price = root.<Long>get("price");
                switch (filter.paymentStatus()) {
                    case UNPAID -> predicates.add(cb.and(cb.equal(paid, 0L), cb.greaterThan(price, 0L)));
                    case PARTIAL -> predicates.add(cb.and(cb.greaterThan(paid, 0L), cb.lessThan(paid, price)));
                    case PAID -> predicates.add(cb.greaterThanOrEqualTo(paid, price));
                }
            }
            if (filter.createdFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), filter.createdFrom()));
            }
            if (filter.createdTo() != null) {
                predicates.add(cb.lessThan(root.<Instant>get("createdAt"), filter.createdTo()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // ===== 발급·수정 =====

    /** 확인 순서: 관리자(403) → 형식(400) → 상품·회원·강사(404) → 값(400) → 퇴사 예정 강사(409). */
    @Transactional
    public PassResponse createPass(Long callerId, PassCreateRequest request) {
        adminGuard.requireAdmin(callerId);
        requestValidator.validate(request);
        LocalDate start = parseDate(request.startDate());

        PassProduct product = productRepository.findById(request.passProductId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        Member member = memberRepository.findById(request.memberId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        Member instructor = memberRepository.findById(request.instructorId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        LocalDate today = today();
        LocalDate end = start.plusDays(product.getValidityDays() - 1L);
        if (end.isBefore(today)) {
            throw new ApiException(ErrorCode.INVALID_START_DATE);
        }
        if (!member.isRole(MemberRole.MEMBER) || member.getStatus() != MemberStatus.ACTIVE) {
            throw new ApiException(ErrorCode.INVALID_MEMBER);
        }
        requireAssignableInstructor(instructor);

        long price = request.price() != null ? request.price() : product.getPrice();
        PassStatus status = start.isAfter(today) ? PassStatus.UPCOMING : PassStatus.ACTIVE;
        Pass pass = passRepository.save(Pass.issue(product.getId(), member.getId(), instructor.getId(), price,
                product.getTotalCount(), start, end, status, Instant.now()));
        activityLogRecorder.record(ActivityAction.PASS_ISSUED, callerId, member.getId(), "PASS", pass.getId(),
                "회원 '" + member.getName() + "'에게 이용권 '" + product.getName() + "' 발급 (이용권 #"
                        + pass.getId() + ", " + start + " ~ " + end + ")");
        return PassResponse.of(pass, product, member, instructor, 0);
    }

    /**
     * 확인 순서: 관리자(403) → 형식(400) → 이용권·강사(404) → 값(400: 잔여 횟수, 만료일, 강사 자격)
     * → 퇴사 예정 강사(409).
     */
    @Transactional
    public PassResponse updatePass(Long callerId, Long passId, PassUpdateRequest request) {
        adminGuard.requireAdmin(callerId);
        if (!request.hasAnyField()) {
            throw new ApiException(ErrorCode.INVALID_INPUT);
        }
        LocalDate endDate = request.endDate() == null ? null : parseDate(request.endDate());

        refreshStatuses(today());
        Pass pass = passRepository.findByIdForUpdate(passId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        Member instructor = request.instructorId() == null ? null : memberRepository
                .findById(request.instructorId()).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        if (request.remainingCount() != null
                && (request.remainingCount() < 0 || request.remainingCount() > pass.getTotalCount())) {
            throw new ApiException(ErrorCode.INVALID_REMAINING_COUNT);
        }
        if (endDate != null && endDate.isBefore(pass.getStartDate())) {
            throw new ApiException(ErrorCode.INVALID_END_DATE);
        }
        boolean instructorChanged = instructor != null && !instructor.getId().equals(pass.getInstructorId());
        if (instructorChanged) {
            requireAssignableInstructor(instructor);
        }

        Instant now = Instant.now();
        int beforeRemaining = pass.getRemainingCount();
        Long beforeInstructorId = pass.getInstructorId();
        LocalDate beforeEndDate = pass.getEndDate();
        PassStatus beforeStatus = pass.getStatus();
        if (request.remainingCount() != null) {
            pass.changeRemainingCount(request.remainingCount(), now);
        }
        if (instructorChanged) {
            pass.changeInstructor(instructor.getId(), now);
        }
        if (endDate != null) {
            pass.changeEndDate(endDate, now);
            pass.changeStatus(statusOf(pass, today()), now);
        }
        activityLogRecorder.record(ActivityAction.PASS_UPDATED, callerId, pass.getMemberId(), "PASS", pass.getId(),
                "이용권 #" + pass.getId() + " 수정",
                ActivityChange.ifChanged("remainingCount", beforeRemaining, pass.getRemainingCount()),
                ActivityChange.ifChanged("instructorId", beforeInstructorId, pass.getInstructorId()),
                ActivityChange.ifChanged("endDate", beforeEndDate, pass.getEndDate()),
                ActivityChange.ifChanged("status", beforeStatus, pass.getStatus()));
        return toResponse(pass);
    }

    /** 담당 강사는 활성 상태의 강사여야 하고, 퇴사일이 등록된 강사는 지정할 수 없다. */
    private static void requireAssignableInstructor(Member instructor) {
        if (!instructor.isRole(MemberRole.INSTRUCTOR) || instructor.getStatus() != MemberStatus.ACTIVE) {
            throw new ApiException(ErrorCode.INVALID_INSTRUCTOR);
        }
        if (instructor.getResignationDate() != null) {
            throw new ApiException(ErrorCode.INSTRUCTOR_RESIGNING);
        }
    }

    // ===== 일시정지 =====

    @Transactional
    public PassPausePageResponse getPassPauses(Long callerId, Long passId, int page, int size) {
        adminGuard.requireAdmin(callerId);
        refreshStatuses(today());
        find(passId);

        PageRequest pageable = PageRequest.of(page - 1, size,
                Sort.by(Sort.Order.desc("startDate"), Sort.Order.desc("id")));
        Page<PassPause> result = pauseRepository.findByPassId(passId, pageable);
        List<PassPauseResponse> content = result.getContent().stream().map(PassPauseResponse::from).toList();
        return new PassPausePageResponse(content, PageInfo.of(result));
    }

    /**
     * 확인 순서: 관리자(403) → 형식(400) → 이용권(404) → 만료(409) → 기간(400) → 겹침(409).
     * 정지 일수만큼 만료일을 즉시 연장한다.
     */
    @Transactional
    public PassPauseResponse createPassPause(Long callerId, Long passId, PassPauseCreateRequest request) {
        adminGuard.requireAdmin(callerId);
        requestValidator.validate(request);
        LocalDate start = parseDate(request.startDate());
        LocalDate end = parseDate(request.endDate());

        LocalDate today = today();
        refreshStatuses(today);
        Pass pass = passRepository.findByIdForUpdate(passId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (pass.getStatus() == PassStatus.EXPIRED) {
            throw new ApiException(ErrorCode.PASS_EXPIRED);
        }
        if (start.isBefore(today) || end.isBefore(start) || start.isBefore(pass.getStartDate())
                || start.isAfter(pass.getEndDate())) {
            throw new ApiException(ErrorCode.INVALID_PAUSE_PERIOD);
        }
        if (pauseRepository.existsOverlap(passId, start, end)) {
            throw new ApiException(ErrorCode.PAUSE_PERIOD_OVERLAP);
        }
        // 정지 기간(현지 날짜 start~end)에 시작하는 수업을 이 이용권으로 예약한 것이 있으면 먼저 취소해야 한다.
        if (reservationRepository.existsReservedOnPassBetween(passId,
                start.atStartOfDay(CENTER_ZONE).toInstant(), end.plusDays(1).atStartOfDay(CENTER_ZONE).toInstant())) {
            throw new ApiException(ErrorCode.PAUSE_HAS_RESERVATIONS);
        }

        Instant now = Instant.now();
        boolean startsToday = start.equals(today);
        LocalDate beforeEndDate = pass.getEndDate();
        PassPause pause = pauseRepository.save(PassPause.register(passId, start, end,
                startsToday ? PassPauseStatus.ACTIVE : PassPauseStatus.SCHEDULED, request.reason(), callerId, now));
        pass.changeEndDate(pass.getEndDate().plusDays(pause.plannedDays()), now);
        if (startsToday) {
            pass.changeStatus(PassStatus.PAUSED, now);
        }
        activityLogRecorder.record(ActivityAction.PASS_PAUSED, callerId, pass.getMemberId(), "PASS_PAUSE",
                pause.getId(), "이용권 #" + passId + "의 일시정지(" + start + " ~ " + end + ") 등록 ("
                        + pause.plannedDays() + "일, 만료일 연장)",
                ActivityChange.ifChanged("endDate", beforeEndDate, pass.getEndDate()));
        return PassPauseResponse.from(pause);
    }

    /**
     * 정지를 조기에 끝낸다. 시작 전이거나 시작일 당일이면 취소하고 연장한 만료일을 모두 되돌리며,
     * 진행 중이면 오늘부터 해제해 사용하지 않은 일수만큼 만료일을 줄인다.
     */
    @Transactional
    public PassPauseResponse terminatePassPause(Long callerId, Long passId, Long pauseId) {
        adminGuard.requireAdmin(callerId);
        LocalDate today = today();
        refreshStatuses(today);
        Pass pass = passRepository.findByIdForUpdate(passId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        PassPause pause = pauseRepository.findByIdAndPassId(pauseId, passId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (pause.getStatus() == PassPauseStatus.ENDED || pause.getStatus() == PassPauseStatus.CANCELED) {
            throw new ApiException(ErrorCode.INVALID_PAUSE_STATUS);
        }

        Instant now = Instant.now();
        LocalDate beforeEndDate = pass.getEndDate();
        if (pause.getStatus() == PassPauseStatus.SCHEDULED || !pause.getStartDate().isBefore(today)) {
            pass.changeEndDate(shortened(pass, pause.plannedDays()), now);
            pause.cancel(now);
        } else {
            LocalDate newEnd = today.minusDays(1);
            long unused = ChronoUnit.DAYS.between(newEnd, pause.getEndDate());
            pass.changeEndDate(shortened(pass, unused), now);
            pause.endEarly(newEnd, now);
        }
        pauseRepository.saveAndFlush(pause);
        pass.changeStatus(statusOf(pass, today), now);
        activityLogRecorder.record(ActivityAction.PASS_PAUSE_TERMINATED, callerId, pass.getMemberId(), "PASS_PAUSE",
                pause.getId(), "이용권 #" + passId + "의 일시정지(" + pause.getStartDate() + " ~ "
                        + pause.getEndDate() + ") 해제",
                ActivityChange.ifChanged("endDate", beforeEndDate, pass.getEndDate()));
        return PassPauseResponse.from(pause);
    }

    // ===== 상품 수정 반영 =====

    /**
     * 상품의 총 횟수·유효 기간 변경을 만료되지 않은(UPCOMING, ACTIVE, PAUSED) 발급 이용권에 반영하고
     * 반영한 이용권 수를 반환한다. {@code countDelta}, {@code validityDays}가 null이면 그 값은 바뀌지 않은 것이다.
     */
    @Transactional
    public int applyProductChange(Long passProductId, Integer countDelta, Integer validityDays) {
        if (countDelta == null && validityDays == null) {
            return 0;
        }
        LocalDate today = today();
        refreshStatuses(today);
        Instant now = Instant.now();
        List<Pass> passes = passRepository.findByPassProductIdAndStatusInForUpdate(passProductId, NOT_EXPIRED);
        Map<Long, Integer> extendedDays = validityDays == null ? Map.of() : extendedDaysOf(passes);
        for (Pass pass : passes) {
            if (countDelta != null) {
                pass.applyCountDelta(countDelta, now);
            }
            if (validityDays != null) {
                pass.changeEndDate(pass.getStartDate()
                        .plusDays(validityDays - 1L + extendedDays.getOrDefault(pass.getId(), 0)), now);
                pass.changeStatus(statusOf(pass, today), now);
            }
        }
        return passes.size();
    }

    /** 정지로 연장된 일수. 취소된 정지는 제외하고, 조기 해제한 정지는 실제로 정지한 일수만 센다. */
    private Map<Long, Integer> extendedDaysOf(List<Pass> passes) {
        if (passes.isEmpty()) {
            return Map.of();
        }
        return pauseRepository.findByPassIdInAndStatusNot(passes.stream().map(Pass::getId).toList(),
                PassPauseStatus.CANCELED).stream()
                .collect(Collectors.groupingBy(PassPause::getPassId, Collectors.summingInt(PassPause::days)));
    }

    /** 정지 해제로 만료일을 줄인다. 시작일보다 앞서지 않는다. */
    private static LocalDate shortened(Pass pass, long days) {
        LocalDate end = pass.getEndDate().minusDays(days);
        return end.isBefore(pass.getStartDate()) ? pass.getStartDate() : end;
    }

    // ===== 공통 =====

    private Pass find(Long passId) {
        return passRepository.findById(passId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new ApiException(ErrorCode.INVALID_INPUT);
        }
    }

    private PassResponse toResponse(Pass pass) {
        return toResponses(List.of(pass)).get(0);
    }

    /** 목록의 상품, 회원, 강사를 한 번에 조회해 응답으로 바꾼다. */
    private List<PassResponse> toResponses(List<Pass> passes) {
        Set<Long> memberIds = new HashSet<>();
        Set<Long> productIds = new HashSet<>();
        for (Pass pass : passes) {
            memberIds.add(pass.getMemberId());
            memberIds.add(pass.getInstructorId());
            productIds.add(pass.getPassProductId());
        }
        Map<Long, Member> members = memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));
        Map<Long, PassProduct> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(PassProduct::getId, Function.identity()));
        Map<Long, Long> paid = new java.util.HashMap<>();
        if (!passes.isEmpty()) {
            for (Object[] row : paymentRepository.sumsByPassIds(
                    passes.stream().map(Pass::getId).toList())) {
                paid.put((Long) row[0], ((Number) row[1]).longValue());
            }
        }
        return passes.stream().map(pass -> PassResponse.of(pass, products.get(pass.getPassProductId()),
                members.get(pass.getMemberId()), members.get(pass.getInstructorId()),
                paid.getOrDefault(pass.getId(), 0L))).toList();
    }
}
