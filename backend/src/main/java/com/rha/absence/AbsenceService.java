package com.rha.absence;

import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import com.rha.common.paging.PageInfo;
import com.rha.common.persistence.RowLock;
import com.rha.common.validation.RequestValidator;
import com.rha.instructor.InstructorSummary;
import com.rha.lesson.Lesson;
import com.rha.lesson.LessonRepository;
import com.rha.reservation.ReservationRepository;
import com.rha.member.AdminGuard;
import com.rha.member.Member;
import com.rha.member.MemberRepository;
import com.rha.member.MemberRole;
import com.rha.notification.NotificationPublisher;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
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
import com.rha.activity.Josa;
import com.rha.activity.ActivityTime;
import com.rha.lesson.LessonStatus;

/**
 * 강사 부재 신청·조회·승인·반려·취소.
 *
 * <p>신청은 관리자에게, 승인·반려는 신청한 강사에게 알림이 간다.
 */
@Service
public class AbsenceService {

    static final String AUTO_CANCEL_REASON = "강사 부재 승인으로 자동 취소";

    private final AbsenceRepository absenceRepository;
    private final MemberRepository memberRepository;
    private final LessonRepository lessonRepository;
    private final ReservationRepository reservationRepository;
    private final NotificationPublisher notificationPublisher;
    private final AdminGuard adminGuard;
    private final RequestValidator requestValidator;
    private final ActivityLogRecorder activityLogRecorder;
    private final RowLock rowLock;

    public AbsenceService(AbsenceRepository absenceRepository, MemberRepository memberRepository,
            LessonRepository lessonRepository, ReservationRepository reservationRepository,
            NotificationPublisher notificationPublisher, AdminGuard adminGuard,
            RequestValidator requestValidator, ActivityLogRecorder activityLogRecorder, RowLock rowLock) {
        this.activityLogRecorder = activityLogRecorder;
        this.rowLock = rowLock;
        this.absenceRepository = absenceRepository;
        this.memberRepository = memberRepository;
        this.lessonRepository = lessonRepository;
        this.reservationRepository = reservationRepository;
        this.notificationPublisher = notificationPublisher;
        this.adminGuard = adminGuard;
        this.requestValidator = requestValidator;
    }

    // ===== 조회 =====

    /** 관리자는 모두, 강사는 본인 것만, 그 외는 빈 목록이다. 시작 시각 내림차순(같으면 id 내림차순)이다. */
    @Transactional(readOnly = true)
    public AbsencePageResponse getAbsences(Long callerId, Long instructorId, AbsenceStatus status, Instant from,
            Instant to, int page, int size) {
        Member caller = findCaller(callerId);
        if (from != null && to != null && !to.isAfter(from)) {
            throw new ApiException(ErrorCode.INVALID_INPUT);
        }
        PageRequest pageable = PageRequest.of(page - 1, size,
                Sort.by(Sort.Order.desc("startAt"), Sort.Order.desc("id")));

        boolean admin = caller.isActive() && caller.isRole(MemberRole.ADMIN);
        boolean instructor = caller.isActive() && caller.isRole(MemberRole.INSTRUCTOR);
        if (!admin && !instructor) {
            return new AbsencePageResponse(List.of(), PageInfo.of(Page.empty(pageable)));
        }
        Long targetInstructorId = admin ? instructorId : caller.getId();

        Specification<Absence> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (targetInstructorId != null) {
                predicates.add(cb.equal(root.get("instructorId"), targetInstructorId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("endAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("startAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<Absence> result = absenceRepository.findAll(spec, pageable);
        return new AbsencePageResponse(toResponses(result.getContent()), PageInfo.of(result));
    }

    /** 관리자 또는 신청한 강사 본인만 볼 수 있고, 그 외는 존재 여부를 숨기기 위해 404이다. */
    @Transactional(readOnly = true)
    public AbsenceResponse getAbsence(Long callerId, Long absenceId) {
        Member caller = findCaller(callerId);
        Absence absence = find(absenceId);
        if (!isAdmin(caller) && !absence.getInstructorId().equals(callerId)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        return toResponses(List.of(absence)).get(0);
    }

    // ===== 신청 =====

    /** 확인 순서: 강사(403) → 입력(400, INVALID_ABSENCE_PERIOD 포함) → 기간 겹침(409). */
    @Transactional
    public AbsenceResponse createAbsence(Long callerId, AbsenceCreateRequest request) {
        Member caller = findCaller(callerId);
        if (!caller.isActive() || !caller.isRole(MemberRole.INSTRUCTOR)) {
            throw new ApiException(ErrorCode.PERMISSION_DENIED);
        }
        requestValidator.validate(request);
        Instant start = parseInstant(request.startAt());
        Instant end = parseInstant(request.endAt());
        Instant now = Instant.now();
        if (!end.isAfter(start) || start.isBefore(now)) {
            throw new ApiException(ErrorCode.INVALID_ABSENCE_PERIOD);
        }
        // 같은 강사의 신청이 동시에 들어와 서로의 겹침 검사를 지나치지 못하도록 강사 행을 잠근다.
        rowLock.lock(caller);
        if (absenceRepository.existsActiveOverlap(callerId, start, end)) {
            throw new ApiException(ErrorCode.ABSENCE_PERIOD_OVERLAP);
        }
        Absence absence = absenceRepository.save(
                Absence.request(callerId, start, end, request.reason(), now));
        notificationPublisher.absenceRequested(absence, caller.getName());
        activityLogRecorder.record(ActivityAction.ABSENCE_REQUESTED, callerId, callerId, "ABSENCE", absence.getId(),
                "부재(" + ActivityTime.period(start, end) + ")를 신청했다.");
        return toResponses(List.of(absence)).get(0);
    }

    // ===== 처리 =====

    /**
     * 승인 대기 부재를 승인하고, 기간과 겹치는 그 강사의 시작 전 수업을 자동 취소한다.
     * 확인 순서: 관리자(403) → 부재(404) → 상태(409).
     */
    @Transactional
    public AbsenceResponse approveAbsence(Long callerId, Long absenceId) {
        adminGuard.requireAdmin(callerId);
        Absence absence = find(absenceId);
        requireRequested(absence);
        // 강사 → 부재 → 수업 순으로 잠근다. 강사를 잠가야 그 강사의 수업 개설과 겹치지 않고, 부재를 잠근 뒤 다시 읽어야
        // 동시에 들어온 다른 처리와 중복되지 않는다.
        memberRepository.findByIdForUpdate(absence.getInstructorId());
        rowLock.lock(absence);
        requireRequested(absence);

        Instant now = Instant.now();
        List<Lesson> overlapping = lessonRepository.findUpcomingOverlappingForUpdate(absence.getInstructorId(),
                absence.getStartAt(), absence.getEndAt(), now);
        if (!overlapping.isEmpty()) {
            List<ApiException.Detail> withReservations = reservationRepository
                    .lessonIdsWithReserved(overlapping.stream().map(Lesson::getId).toList()).stream()
                    .sorted()
                    .map(id -> new ApiException.Detail(String.valueOf(id), ErrorCode.ABSENCE_HAS_RESERVATIONS.name()))
                    .toList();
            if (!withReservations.isEmpty()) {
                throw new ApiException(ErrorCode.ABSENCE_HAS_RESERVATIONS, withReservations);
            }
        }
        absence.approve(callerId, now);
        for (Lesson lesson : overlapping) {
            LessonStatus beforeLessonStatus = lesson.getStatus();
            lesson.cancel(AUTO_CANCEL_REASON, now);
            activityLogRecorder.record(ActivityAction.LESSON_CANCELED, callerId, null, "LESSON", lesson.getId(),
                    "부재 승인으로 " + Josa.eulReul("수업 '" + lesson.getTitle() + "'") + " 자동 취소했다.",
                    ActivityChange.of("status", beforeLessonStatus, LessonStatus.CANCELED),
                    ActivityChange.of("reason", null, AUTO_CANCEL_REASON));
        }
        notificationPublisher.absenceApproved(absence);
        activityLogRecorder.record(ActivityAction.ABSENCE_APPROVED, callerId, absence.getInstructorId(), "ABSENCE",
                absence.getId(), "부재(" + ActivityTime.period(absence.getStartAt(), absence.getEndAt())
                        + ")를 승인했다." + (overlapping.isEmpty() ? "" : " 겹치는 수업 " + overlapping.size()
                        + "건이 자동 취소되었다."));
        return toResponses(List.of(absence)).get(0);
    }

    /** 확인 순서: 관리자(403) → 입력(400) → 부재(404) → 상태(409). 본문은 생략할 수 있다. */
    @Transactional
    public AbsenceResponse rejectAbsence(Long callerId, Long absenceId, AbsenceRejectRequest request) {
        adminGuard.requireAdmin(callerId);
        AbsenceRejectRequest body = request != null ? request : new AbsenceRejectRequest(null);
        requestValidator.validate(body);
        Absence absence = rowLock.lock(find(absenceId));
        requireRequested(absence);

        absence.reject(callerId, body.reason(), Instant.now());
        notificationPublisher.absenceRejected(absence);
        activityLogRecorder.record(ActivityAction.ABSENCE_REJECTED, callerId, absence.getInstructorId(), "ABSENCE",
                absence.getId(), "부재(" + ActivityTime.period(absence.getStartAt(), absence.getEndAt())
                        + ")를 반려했다." + (body.reason() == null || body.reason().isBlank() ? ""
                        : " 사유: " + body.reason()));
        return toResponses(List.of(absence)).get(0);
    }

    /**
     * 확인 순서: 부재 없음·무관한 호출자(404) → 강사가 승인된 부재 취소(403) → 상태(409).
     * 신청한 강사는 승인 전에만, 관리자는 승인 대기와 승인된 부재를 취소할 수 있다.
     */
    @Transactional
    public AbsenceResponse cancelAbsence(Long callerId, Long absenceId) {
        Member caller = findCaller(callerId);
        Absence absence = rowLock.lock(find(absenceId));
        boolean admin = isAdmin(caller);
        if (!admin && !absence.getInstructorId().equals(callerId)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        if (!admin && absence.getStatus() == AbsenceStatus.APPROVED) {
            throw new ApiException(ErrorCode.PERMISSION_DENIED);
        }
        boolean cancelable = absence.getStatus() == AbsenceStatus.REQUESTED
                || (admin && absence.getStatus() == AbsenceStatus.APPROVED);
        if (!cancelable) {
            throw new ApiException(ErrorCode.INVALID_ABSENCE_STATUS);
        }
        AbsenceStatus beforeStatus = absence.getStatus();
        absence.cancel(callerId, Instant.now());
        activityLogRecorder.record(ActivityAction.ABSENCE_CANCELED, callerId, absence.getInstructorId(), "ABSENCE",
                absence.getId(), "부재(" + ActivityTime.period(absence.getStartAt(), absence.getEndAt())
                        + ")를 취소했다.",
                ActivityChange.of("status", beforeStatus, AbsenceStatus.CANCELED));
        return toResponses(List.of(absence)).get(0);
    }

    // ===== 공통 =====

    private Member findCaller(Long callerId) {
        return memberRepository.findById(callerId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }

    private Absence find(Long absenceId) {
        return absenceRepository.findById(absenceId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private static boolean isAdmin(Member member) {
        return member.isActive() && member.isRole(MemberRole.ADMIN);
    }

    private static void requireRequested(Absence absence) {
        if (absence.getStatus() != AbsenceStatus.REQUESTED) {
            throw new ApiException(ErrorCode.INVALID_ABSENCE_STATUS);
        }
    }

    private static Instant parseInstant(String value) {
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            throw new ApiException(ErrorCode.INVALID_INPUT);
        }
    }

    /** 신청한 강사와 처리자 이름을 한 번에 조회해 응답으로 바꾼다. */
    private List<AbsenceResponse> toResponses(List<Absence> absences) {
        Set<Long> memberIds = new HashSet<>();
        for (Absence absence : absences) {
            memberIds.add(absence.getInstructorId());
            if (absence.getProcessedBy() != null) {
                memberIds.add(absence.getProcessedBy());
            }
        }
        Map<Long, Member> members = memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));
        return absences.stream().map(absence -> AbsenceResponse.of(absence,
                summary(members.get(absence.getInstructorId())),
                absence.getProcessedBy() == null ? null : summary(members.get(absence.getProcessedBy()))))
                .toList();
    }

    private static InstructorSummary summary(Member member) {
        return new InstructorSummary(member.getId(), member.getName());
    }
}
