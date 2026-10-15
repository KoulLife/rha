package com.rha.lesson;

import com.rha.absence.AbsenceRepository;
import com.rha.businesshour.BusinessHour;
import com.rha.businesshour.BusinessHourRepository;
import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import com.rha.common.paging.PageInfo;
import com.rha.common.validation.RequestValidator;
import com.rha.instructor.InstructorAvailabilityRepository;
import com.rha.lessontype.LessonType;
import com.rha.member.AdminGuard;
import com.rha.lessontype.LessonTypeRepository;
import com.rha.member.Member;
import com.rha.member.MemberPermission;
import com.rha.member.MemberPermissionRepository;
import com.rha.member.MemberRepository;
import com.rha.member.MemberRole;
import com.rha.room.Room;
import com.rha.room.RoomRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
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
 * 수업 개설·수정·공개 상태 변경·취소와 조회.
 *
 * <p>영업시간은 센터 현지 시각(Asia/Seoul) 기준이다. 예약이 있는 수업의 시간 변경(LESSON_HAS_RESERVATIONS)과
 * 취소 시 예약·이용권·알림 처리는 해당 기능이 구현될 때 추가한다.
 */
@Service
public class LessonService {

    static final ZoneId CENTER_ZONE = ZoneId.of("Asia/Seoul");

    private static final String CREATE_PREFIX = "LESSON_CREATE:";
    private static final String UPDATE_PREFIX = "LESSON_UPDATE:";
    /** 새 수업은 아직 id가 없어 충돌 검사에서 제외할 수업이 없다. */
    private static final Long NO_LESSON = -1L;

    private final LessonRepository lessonRepository;
    private final LessonTypeRepository lessonTypeRepository;
    private final RoomRepository roomRepository;
    private final MemberRepository memberRepository;
    private final MemberPermissionRepository permissionRepository;
    private final BusinessHourRepository businessHourRepository;
    private final InstructorAvailabilityRepository availabilityRepository;
    private final AbsenceRepository absenceRepository;
    private final AdminGuard adminGuard;
    private final RequestValidator requestValidator;

    public LessonService(LessonRepository lessonRepository, LessonTypeRepository lessonTypeRepository,
            RoomRepository roomRepository, MemberRepository memberRepository,
            MemberPermissionRepository permissionRepository, BusinessHourRepository businessHourRepository,
            InstructorAvailabilityRepository availabilityRepository, AbsenceRepository absenceRepository,
            AdminGuard adminGuard,
            RequestValidator requestValidator) {
        this.lessonRepository = lessonRepository;
        this.lessonTypeRepository = lessonTypeRepository;
        this.roomRepository = roomRepository;
        this.memberRepository = memberRepository;
        this.permissionRepository = permissionRepository;
        this.businessHourRepository = businessHourRepository;
        this.availabilityRepository = availabilityRepository;
        this.absenceRepository = absenceRepository;
        this.adminGuard = adminGuard;
        this.requestValidator = requestValidator;
    }

    // ===== 조회 =====

    /** 호출자의 역할에 따라 보이는 수업이 다르다. 시작 시각 순(같으면 id 오름차순)이며 {@code page}는 1부터 시작한다. */
    @Transactional(readOnly = true)
    public LessonPageResponse getLessons(Long callerId, Instant from, Instant to, Long lessonTypeId,
            Long instructorId, Long roomId, LessonStatus status, Sort.Direction order, int page, int size) {
        Member caller = findCaller(callerId);
        if (from != null && to != null && !to.isAfter(from)) {
            throw new ApiException(ErrorCode.INVALID_INPUT);
        }

        Specification<Lesson> spec = visibleTo(caller).and((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("startAt"), to));
            }
            if (lessonTypeId != null) {
                predicates.add(cb.equal(root.get("lessonTypeId"), lessonTypeId));
            }
            if (instructorId != null) {
                predicates.add(cb.equal(root.get("instructorId"), instructorId));
            }
            if (roomId != null) {
                predicates.add(cb.equal(root.get("roomId"), roomId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        });

        PageRequest pageable = PageRequest.of(page - 1, size,
                Sort.by(new Sort.Order(order, "startAt"), Sort.Order.asc("id")));
        Page<Lesson> result = lessonRepository.findAll(spec, pageable);
        return new LessonPageResponse(toResponses(result.getContent()), PageInfo.of(result));
    }

    /** 볼 수 없는 수업은 존재 여부를 숨기기 위해 없는 수업처럼 404이다. */
    @Transactional(readOnly = true)
    public LessonResponse getLesson(Long callerId, Long lessonId) {
        Member caller = findCaller(callerId);
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!canSee(caller, lesson)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        return toResponse(lesson);
    }

    // ===== 쓰기 =====

    @Transactional
    public LessonResponse createLesson(Long callerId, LessonCreateRequest request) {
        LessonType type = request.lessonTypeId() == null ? null
                : lessonTypeRepository.findById(request.lessonTypeId()).orElse(null);
        authorize(callerId, CREATE_PREFIX, type == null ? null : type.getCode());
        requestValidator.validate(request);

        Instant start = parseInstant(request.startAt());
        Instant end = parseInstant(request.endAt());
        Instant now = Instant.now();
        if (!end.isAfter(start) || start.isBefore(now)) {
            throw new ApiException(ErrorCode.INVALID_LESSON_TIME);
        }
        if (type == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        // 룸 → 강사 순으로 잠가 같은 룸·강사에 동시에 들어오는 수업이 서로의 충돌 검사를 지나치지 못하게 한다.
        Room room = roomRepository.findActiveForUpdate(request.roomId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!type.isActive()) {
            throw new ApiException(ErrorCode.LESSON_TYPE_INACTIVE);
        }
        Member instructor = lockInstructor(request.instructorId());

        checkAssignable(instructor, room.getId(), start, end, NO_LESSON);

        Lesson lesson = lessonRepository.save(Lesson.create(request.title(), type.getId(), type.getCapacity(),
                instructor.getId(), room.getId(), start, end, now));
        return LessonResponse.of(lesson, type, instructor, room);
    }

    /**
     * 확인 순서: 권한(403) → 입력(400) → 수업·룸(404) → 수업 상태(409) → 시간·강사 값(400) → 충돌(409).
     * 시간, 강사, 룸 중 바뀐 값이 있을 때만 개설과 같은 검증을 다시 한다.
     */
    @Transactional
    public LessonResponse updateLesson(Long callerId, Long lessonId, LessonUpdateRequest request) {
        Lesson lesson = lessonRepository.findById(lessonId).orElse(null);
        authorize(callerId, UPDATE_PREFIX, lesson == null ? null : typeCodeOf(lesson));
        requestValidator.validate(request);
        if (!request.hasAnyField()) {
            throw new ApiException(ErrorCode.INVALID_INPUT);
        }
        Instant requestedStart = request.startAt() == null ? null : parseInstant(request.startAt());
        Instant requestedEnd = request.endAt() == null ? null : parseInstant(request.endAt());

        if (lesson == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        Room newRoom = request.roomId() == null ? null : roomRepository.findActiveForUpdate(request.roomId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        Instant now = Instant.now();
        requireModifiable(lesson, now);

        Instant start = requestedStart != null ? requestedStart : lesson.getStartAt();
        Instant end = requestedEnd != null ? requestedEnd : lesson.getEndAt();
        Long roomId = request.roomId() != null ? request.roomId() : lesson.getRoomId();
        Long instructorId = request.instructorId() != null ? request.instructorId() : lesson.getInstructorId();
        boolean timeChanged = !start.equals(lesson.getStartAt()) || !end.equals(lesson.getEndAt());
        boolean recheck = timeChanged || !roomId.equals(lesson.getRoomId())
                || !instructorId.equals(lesson.getInstructorId());

        if (timeChanged) {
            if (!end.isAfter(start) || start.isBefore(now)) {
                throw new ApiException(ErrorCode.INVALID_LESSON_TIME);
            }
            if (lesson.getStatus() == LessonStatus.SCHEDULED && lesson.getPublishAt() != null
                    && !start.isAfter(lesson.getPublishAt())) {
                throw new ApiException(ErrorCode.INVALID_PUBLISH_AT);
            }
        }
        if (recheck) {
            Room room = newRoom != null ? newRoom : roomRepository.findActiveForUpdate(roomId)
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
            Member instructor = lockInstructor(instructorId);
            checkAssignable(instructor, room.getId(), start, end, lessonId);
        }

        String title = request.title() != null ? request.title() : lesson.getTitle();
        lesson.reschedule(title, instructorId, roomId, start, end, now);
        return toResponse(lesson);
    }

    /**
     * 확인 순서: 권한(403) → 입력(400) → 수업(404) → 수업 상태(409: 취소, 시작, 상태 전이)
     * → 공개 예약 시각 값(400).
     */
    @Transactional
    public LessonResponse changeLessonStatus(Long callerId, Long lessonId, LessonStatusChangeRequest request) {
        Lesson lesson = lessonRepository.findById(lessonId).orElse(null);
        authorize(callerId, UPDATE_PREFIX, lesson == null ? null : typeCodeOf(lesson));

        LessonStatus target = requestedStatus(request.status());
        boolean scheduled = target == LessonStatus.SCHEDULED;
        if (scheduled != (request.publishAt() != null)) {
            throw new ApiException(ErrorCode.INVALID_PUBLISH_AT);
        }
        Instant publishAt = scheduled ? parseInstant(request.publishAt()) : null;

        if (lesson == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        Instant now = Instant.now();
        requireModifiable(lesson, now);
        if (!isAllowedTransition(lesson.getStatus(), target)) {
            throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        if (scheduled && (!publishAt.isAfter(now) || !publishAt.isBefore(lesson.getStartAt()))) {
            throw new ApiException(ErrorCode.INVALID_PUBLISH_AT);
        }

        lesson.changeStatus(target, publishAt, now);
        return toResponse(lesson);
    }

    /** 확인 순서: 권한(403) → 입력(400) → 수업(404) → 이미 취소(409) → 이미 시작(409). */
    @Transactional
    public LessonResponse cancelLesson(Long callerId, Long lessonId, LessonCancelRequest request) {
        Lesson lesson = lessonRepository.findById(lessonId).orElse(null);
        authorize(callerId, UPDATE_PREFIX, lesson == null ? null : typeCodeOf(lesson));
        LessonCancelRequest body = request != null ? request : new LessonCancelRequest(null);
        requestValidator.validate(body);

        if (lesson == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        Instant now = Instant.now();
        requireModifiable(lesson, now);

        lesson.cancel(body.reason(), now);
        return toResponse(lesson);
    }

    /**
     * 강사의 시작 전 수업을 모두 다른 강사에게 넘긴다. 하나라도 배정할 수 없으면 아무것도 바꾸지 않고
     * 실패한 수업 ID와 사유를 담아 거절한다. 확인 순서: 관리자(403) → 입력(400) → 강사들(404)
     * → 새 강사 자격(400) → 수업별 검증(409).
     */
    @Transactional
    public LessonReassignmentResponse reassignLessons(Long callerId, Long fromInstructorId,
            LessonReassignmentRequest request) {
        adminGuard.requireAdmin(callerId);
        requestValidator.validate(request);
        Instant now = Instant.now();
        Instant from = request.from() == null ? now : parseInstant(request.from());

        Member fromInstructor = memberRepository.findById(fromInstructorId)
                .filter(member -> member.isRole(MemberRole.INSTRUCTOR))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        Member toInstructor = memberRepository.findById(request.toInstructorId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!toInstructor.isRole(MemberRole.INSTRUCTOR) || !toInstructor.isActive()
                || toInstructor.getId().equals(fromInstructor.getId())) {
            throw new ApiException(ErrorCode.INVALID_INSTRUCTOR);
        }
        lockInstructor(toInstructor.getId());

        // 이미 시작한 수업은 from이 과거여도 넘기지 않는다.
        List<Lesson> lessons = lessonRepository.findUpcomingOf(fromInstructorId, from.isAfter(now) ? from : now);
        List<ApiException.Detail> failures = new ArrayList<>();
        for (Lesson lesson : lessons) {
            try {
                checkAssignable(toInstructor, lesson.getRoomId(), lesson.getStartAt(), lesson.getEndAt(),
                        lesson.getId());
            } catch (ApiException e) {
                failures.add(new ApiException.Detail(String.valueOf(lesson.getId()), e.errorCode().name()));
            }
        }
        if (!failures.isEmpty()) {
            throw new ApiException(ErrorCode.REASSIGNMENT_FAILED, failures);
        }

        lessons.forEach(lesson -> lesson.reschedule(lesson.getTitle(), toInstructor.getId(), lesson.getRoomId(),
                lesson.getStartAt(), lesson.getEndAt(), now));
        return new LessonReassignmentResponse(lessons.size());
    }

    /** 공개 예약 시각이 지난 수업을 공개하고 공개한 수업 수를 반환한다. */
    @Transactional
    public int publishDueLessons(Instant now) {
        return lessonRepository.publishDue(now);
    }

    // ===== 권한 =====

    /**
     * 관리자이거나, 해당 수업 유형의 {@code prefix} 권한을 가진 활성 강사만 통과한다.
     * 유형을 알 수 없으면(없는 수업·유형) 그 접두사의 권한을 하나라도 가진 강사만 통과시켜 존재 여부를 숨긴다.
     */
    private Member authorize(Long callerId, String prefix, String typeCode) {
        Member caller = findCaller(callerId);
        if (!caller.isActive()) {
            throw new ApiException(ErrorCode.PERMISSION_DENIED);
        }
        if (caller.isRole(MemberRole.ADMIN)) {
            return caller;
        }
        if (!caller.isRole(MemberRole.INSTRUCTOR)) {
            throw new ApiException(ErrorCode.PERMISSION_DENIED);
        }
        List<String> codes = permissionCodesOf(callerId);
        boolean allowed = typeCode != null ? codes.contains(prefix + typeCode)
                : codes.stream().anyMatch(code -> code.startsWith(prefix));
        if (!allowed) {
            throw new ApiException(ErrorCode.PERMISSION_DENIED);
        }
        return caller;
    }

    private Member findCaller(Long callerId) {
        return memberRepository.findById(callerId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }

    private List<String> permissionCodesOf(Long memberId) {
        return permissionRepository.findByMemberIdInOrderByPermissionCode(List.of(memberId)).stream()
                .map(MemberPermission::getPermissionCode).toList();
    }

    private String typeCodeOf(Lesson lesson) {
        return lessonTypeRepository.findById(lesson.getLessonTypeId()).map(LessonType::getCode).orElse(null);
    }

    // ===== 노출 범위 =====

    /** 관리자는 모든 수업, 강사는 공개 + 담당 + 권한 있는 유형의 수업, 그 외는 공개된 수업만 본다. */
    private Specification<Lesson> visibleTo(Member caller) {
        if (caller.isActive() && caller.isRole(MemberRole.ADMIN)) {
            return (root, query, cb) -> cb.conjunction();
        }
        if (caller.isActive() && caller.isRole(MemberRole.INSTRUCTOR)) {
            Set<Long> typeIds = permittedTypeIds(caller.getId());
            return (root, query, cb) -> {
                List<Predicate> any = new ArrayList<>();
                any.add(cb.equal(root.get("status"), LessonStatus.PUBLISHED));
                any.add(cb.equal(root.get("instructorId"), caller.getId()));
                if (!typeIds.isEmpty()) {
                    any.add(root.get("lessonTypeId").in(typeIds));
                }
                return cb.or(any.toArray(new Predicate[0]));
            };
        }
        return (root, query, cb) -> cb.equal(root.get("status"), LessonStatus.PUBLISHED);
    }

    private boolean canSee(Member caller, Lesson lesson) {
        if (caller.isActive() && caller.isRole(MemberRole.ADMIN)) {
            return true;
        }
        if (lesson.getStatus() == LessonStatus.PUBLISHED) {
            return true;
        }
        if (caller.isActive() && caller.isRole(MemberRole.INSTRUCTOR)) {
            return lesson.getInstructorId().equals(caller.getId())
                    || permittedTypeIds(caller.getId()).contains(lesson.getLessonTypeId());
        }
        return false;
    }

    /** 개설 또는 수정 권한이 있는 수업 유형의 id. */
    private Set<Long> permittedTypeIds(Long memberId) {
        Set<String> typeCodes = permissionCodesOf(memberId).stream()
                .filter(code -> code.startsWith(CREATE_PREFIX) || code.startsWith(UPDATE_PREFIX))
                .map(code -> code.substring(code.indexOf(':') + 1))
                .collect(Collectors.toSet());
        if (typeCodes.isEmpty()) {
            return Set.of();
        }
        return lessonTypeRepository.findByCodeIn(typeCodes).stream().map(LessonType::getId)
                .collect(Collectors.toSet());
    }

    // ===== 검증 =====

    /** 취소되었거나 이미 시작한 수업은 수정·상태 변경·취소할 수 없다. 취소가 먼저 확인된다. */
    private void requireModifiable(Lesson lesson, Instant now) {
        if (lesson.getStatus() == LessonStatus.CANCELED) {
            throw new ApiException(ErrorCode.LESSON_CANCELED);
        }
        if (lesson.hasStarted(now)) {
            throw new ApiException(ErrorCode.LESSON_ALREADY_STARTED);
        }
    }

    /** 담당 강사는 활성 상태의 강사여야 한다. 시간 충돌 검사를 위해 행을 잠근다. */
    private Member lockInstructor(Long instructorId) {
        return memberRepository.findByIdForUpdate(instructorId)
                .filter(member -> member.isRole(MemberRole.INSTRUCTOR) && member.isActive())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_INSTRUCTOR));
    }

    /** 영업시간 → 퇴사일 → 강사 시간 충돌 → 룸 시간 충돌 순으로 확인한다. {@code excludeId}는 충돌에서 제외할 수업이다. */
    private void checkAssignable(Member instructor, Long roomId, Instant start, Instant end, Long excludeId) {
        checkBusinessHours(start, end);
        checkAvailability(instructor.getId(), start, end);
        if (absenceRepository.existsApprovedOverlap(instructor.getId(), start, end)) {
            throw new ApiException(ErrorCode.INSTRUCTOR_ABSENT);
        }
        LocalDate resignationDate = instructor.getResignationDate();
        if (resignationDate != null && !start.atZone(CENTER_ZONE).toLocalDate().isBefore(resignationDate)) {
            throw new ApiException(ErrorCode.INSTRUCTOR_RESIGNING);
        }
        if (lessonRepository.existsInstructorConflict(instructor.getId(), start, end, excludeId)) {
            throw new ApiException(ErrorCode.INSTRUCTOR_TIME_CONFLICT);
        }
        if (lessonRepository.existsRoomConflict(roomId, start, end, excludeId)) {
            throw new ApiException(ErrorCode.ROOM_TIME_CONFLICT);
        }
    }

    /** 수업 시간이 강사의 그 요일 가능 시간대 하나 안에 모두 들어와야 한다. 설정이 없으면 배정할 수 없다. */
    private void checkAvailability(Long instructorId, Instant start, Instant end) {
        ZonedDateTime localStart = start.atZone(CENTER_ZONE);
        String day = localStart.getDayOfWeek().name().substring(0, 3);
        if (!availabilityRepository.existsCovering(instructorId, day, localStart.toLocalTime(),
                end.atZone(CENTER_ZONE).toLocalTime())) {
            throw new ApiException(ErrorCode.INSTRUCTOR_UNAVAILABLE);
        }
    }

    /** 시작과 종료가 현지 시각으로 같은 날이어야 하고, 그 요일의 영업시간 안이어야 한다. */
    private void checkBusinessHours(Instant start, Instant end) {
        ZonedDateTime localStart = start.atZone(CENTER_ZONE);
        ZonedDateTime localEnd = end.atZone(CENTER_ZONE);
        if (!localStart.toLocalDate().equals(localEnd.toLocalDate())) {
            throw new ApiException(ErrorCode.OUTSIDE_BUSINESS_HOURS);
        }
        String day = localStart.getDayOfWeek().name().substring(0, 3);
        BusinessHour hour = businessHourRepository.findById(day)
                .orElseThrow(() -> new ApiException(ErrorCode.OUTSIDE_BUSINESS_HOURS));
        if (hour.isClosed() || localStart.toLocalTime().isBefore(hour.getOpenTime())
                || localEnd.toLocalTime().isAfter(hour.getCloseTime())) {
            throw new ApiException(ErrorCode.OUTSIDE_BUSINESS_HOURS);
        }
    }

    /** CREATED나 CANCELED로는 바꿀 수 없다. */
    private static LessonStatus requestedStatus(String value) {
        if (value != null) {
            for (LessonStatus status : List.of(LessonStatus.PUBLISHED, LessonStatus.UNPUBLISHED,
                    LessonStatus.SCHEDULED)) {
                if (status.name().equals(value)) {
                    return status;
                }
            }
        }
        throw new ApiException(ErrorCode.INVALID_INPUT);
    }

    private static boolean isAllowedTransition(LessonStatus from, LessonStatus to) {
        return switch (from) {
            case CREATED -> to == LessonStatus.PUBLISHED || to == LessonStatus.SCHEDULED;
            case SCHEDULED -> true;
            case PUBLISHED -> to == LessonStatus.UNPUBLISHED;
            case UNPUBLISHED -> to == LessonStatus.PUBLISHED || to == LessonStatus.SCHEDULED;
            case CANCELED -> false;
        };
    }

    private static Instant parseInstant(String value) {
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            throw new ApiException(ErrorCode.INVALID_INPUT);
        }
    }

    // ===== 응답 =====

    private LessonResponse toResponse(Lesson lesson) {
        return toResponses(List.of(lesson)).get(0);
    }

    /** 목록의 유형, 강사, 룸을 한 번에 조회해 응답으로 바꾼다. 삭제된 룸의 수업도 룸 이름을 보여 준다. */
    private List<LessonResponse> toResponses(List<Lesson> lessons) {
        Map<Long, LessonType> types = byId(lessonTypeRepository.findAllById(
                lessons.stream().map(Lesson::getLessonTypeId).distinct().toList()), LessonType::getId);
        Map<Long, Member> instructors = byId(memberRepository.findAllById(
                lessons.stream().map(Lesson::getInstructorId).distinct().toList()), Member::getId);
        Map<Long, Room> rooms = byId(roomRepository.findAllById(
                lessons.stream().map(Lesson::getRoomId).distinct().toList()), Room::getId);
        return lessons.stream()
                .map(lesson -> LessonResponse.of(lesson, types.get(lesson.getLessonTypeId()),
                        instructors.get(lesson.getInstructorId()), rooms.get(lesson.getRoomId())))
                .toList();
    }

    private static <T> Map<Long, T> byId(List<T> entities, Function<T, Long> id) {
        return entities.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
