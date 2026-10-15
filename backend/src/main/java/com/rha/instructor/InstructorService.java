package com.rha.instructor;

import com.rha.absence.Absence;
import com.rha.absence.AbsenceRepository;
import com.rha.auth.RefreshTokenRepository;
import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import com.rha.common.paging.PageInfo;
import com.rha.common.validation.RequestValidator;
import com.rha.lesson.LessonRepository;
import com.rha.lesson.LessonStatus;
import com.rha.member.AdminGuard;
import com.rha.member.Member;
import com.rha.member.MemberPermissionRepository;
import com.rha.member.MemberRepository;
import com.rha.member.MemberResponse;
import com.rha.member.MemberRole;
import com.rha.member.MemberStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.rha.activity.ActivityAction;
import com.rha.activity.ActivityChange;
import com.rha.activity.ActivityLogRecorder;
import com.rha.activity.Josa;
import com.rha.activity.ActivityTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import com.rha.absence.AbsenceStatus;

/** 강사의 수업 가능시간, 퇴사, 강사 목록. 수업 인계는 수업 검증을 재사용하므로 {@code LessonService}에 있다. */
@Service
public class InstructorService {

    private static final ZoneId CENTER_ZONE = ZoneId.of("Asia/Seoul");
    private static final List<String> DAYS = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

    private final MemberRepository memberRepository;
    private final InstructorAvailabilityRepository availabilityRepository;
    private final LessonRepository lessonRepository;
    private final AbsenceRepository absenceRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberPermissionRepository permissionRepository;
    private final AdminGuard adminGuard;
    private final RequestValidator requestValidator;
    private final ActivityLogRecorder activityLogRecorder;

    public InstructorService(MemberRepository memberRepository,
            InstructorAvailabilityRepository availabilityRepository, LessonRepository lessonRepository,
            AbsenceRepository absenceRepository, RefreshTokenRepository refreshTokenRepository, MemberPermissionRepository permissionRepository,
            AdminGuard adminGuard, RequestValidator requestValidator, ActivityLogRecorder activityLogRecorder) {
        this.activityLogRecorder = activityLogRecorder;
        this.memberRepository = memberRepository;
        this.availabilityRepository = availabilityRepository;
        this.lessonRepository = lessonRepository;
        this.absenceRepository = absenceRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.permissionRepository = permissionRepository;
        this.adminGuard = adminGuard;
        this.requestValidator = requestValidator;
    }

    // ===== 강사 목록 =====

    /** 활동 중인 강사의 id와 이름만 이름 오름차순(같으면 id 오름차순)으로 반환한다. {@code page}는 1부터 시작한다. */
    @Transactional(readOnly = true)
    public InstructorPageResponse getInstructors(int page, int size) {
        PageRequest pageable = PageRequest.of(page - 1, size,
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
        Page<Member> result = memberRepository.findByRoleAndStatus(MemberRole.INSTRUCTOR, MemberStatus.ACTIVE,
                pageable);
        List<InstructorSummary> content = result.getContent().stream()
                .map(member -> new InstructorSummary(member.getId(), member.getName())).toList();
        return new InstructorPageResponse(content, PageInfo.of(result));
    }

    // ===== 수업 가능시간 =====

    /** 관리자 또는 본인만 볼 수 있고, 그 외에는 존재 여부를 숨기기 위해 404이다. */
    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailability(Long callerId, Long memberId) {
        Member caller = memberRepository.findById(callerId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        boolean admin = caller.isActive() && caller.isRole(MemberRole.ADMIN);
        if (!admin && !callerId.equals(memberId)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        Member instructor = findInstructor(memberId);
        return toResponse(instructor, availabilityRepository.findByInstructorId(memberId));
    }

    /** 요일별 시간대 목록을 전달한 목록으로 통째로 교체한다. */
    @Transactional
    public AvailabilityResponse updateAvailability(Long callerId, Long memberId, AvailabilityRequest request) {
        adminGuard.requireAdmin(callerId);
        requestValidator.validate(request);

        List<InstructorAvailability> slots = new ArrayList<>();
        for (AvailabilityRequest.Slot slot : request.slots()) {
            LocalTime start = LocalTime.parse(slot.startTime());
            LocalTime end = LocalTime.parse(slot.endTime());
            if (!start.isBefore(end)) {
                throw new ApiException(ErrorCode.INVALID_AVAILABILITY);
            }
            slots.add(InstructorAvailability.of(memberId, slot.dayOfWeek(), start, end));
        }
        requireNoOverlap(slots);

        Member instructor = findInstructor(memberId);
        Map<String, String> before = describeByDay(availabilityRepository.findByInstructorId(memberId));
        availabilityRepository.deleteAllOf(memberId);
        List<InstructorAvailability> saved = availabilityRepository.saveAll(slots);

        Map<String, String> after = describeByDay(saved);
        List<ActivityChange> changes = new ArrayList<>();
        for (String day : DAYS) {
            ActivityChange change = ActivityChange.ifChanged(day, before.get(day), after.get(day));
            if (change != null) {
                changes.add(change);
            }
        }
        activityLogRecorder.record(ActivityAction.AVAILABILITY_UPDATED, callerId, memberId, "MEMBER", memberId,
                "강사 " + Josa.eulReul("'" + instructor.getName() + "'") + " 수업 가능시간을 설정했다.",
                changes.toArray(new ActivityChange[0]));
        return toResponse(instructor, saved);
    }

    /** 요일별 "09:00~12:00, 14:00~18:00" 표기. 시간대가 없는 요일은 "없음"이다. */
    private static Map<String, String> describeByDay(List<InstructorAvailability> slots) {
        Map<String, String> result = new HashMap<>();
        for (String day : DAYS) {
            String text = slots.stream().filter(slot -> slot.getDayOfWeek().equals(day))
                    .sorted(Comparator.comparing(InstructorAvailability::getStartTime))
                    .map(slot -> slot.getStartTime() + "~" + slot.getEndTime())
                    .collect(Collectors.joining(", "));
            result.put(day, text.isEmpty() ? "없음" : text);
        }
        return result;
    }

    /** 같은 요일 안에서 시간대끼리 겹치면 거절한다. 끝과 시작이 맞닿는 것은 겹침이 아니다. */
    private static void requireNoOverlap(List<InstructorAvailability> slots) {
        for (int i = 0; i < slots.size(); i++) {
            for (int j = i + 1; j < slots.size(); j++) {
                InstructorAvailability a = slots.get(i);
                InstructorAvailability b = slots.get(j);
                if (a.getDayOfWeek().equals(b.getDayOfWeek()) && a.getStartTime().isBefore(b.getEndTime())
                        && b.getStartTime().isBefore(a.getEndTime())) {
                    throw new ApiException(ErrorCode.INVALID_AVAILABILITY);
                }
            }
        }
    }

    private AvailabilityResponse toResponse(Member instructor, List<InstructorAvailability> slots) {
        List<AvailabilityResponse.SlotResponse> sorted = slots.stream()
                .sorted(Comparator.comparing((InstructorAvailability s) -> DAYS.indexOf(s.getDayOfWeek()))
                        .thenComparing(InstructorAvailability::getStartTime))
                .map(AvailabilityResponse.SlotResponse::from).toList();
        return new AvailabilityResponse(new InstructorSummary(instructor.getId(), instructor.getName()), sorted);
    }

    // ===== 퇴사 =====

    /** 퇴사일을 등록하거나 바꾼다. 퇴사 처리는 {@link #completeResignation}으로 따로 한다. */
    @Transactional
    public MemberResponse registerResignation(Long callerId, Long memberId, ResignationRequest request) {
        adminGuard.requireAdmin(callerId);
        requestValidator.validate(request);
        LocalDate date = parseDate(request.resignationDate());

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (member.isRole(MemberRole.INSTRUCTOR) && member.getStatus() == MemberStatus.RESIGNED) {
            throw new ApiException(ErrorCode.MEMBER_RESIGNED);
        }
        if (!member.isRole(MemberRole.INSTRUCTOR) || !member.isActive()) {
            throw new ApiException(ErrorCode.INVALID_INSTRUCTOR);
        }
        if (date.isBefore(LocalDate.now(CENTER_ZONE))) {
            throw new ApiException(ErrorCode.INVALID_RESIGNATION_DATE);
        }
        LocalDate beforeDate = member.getResignationDate();
        member.registerResignation(date, Instant.now());
        activityLogRecorder.record(ActivityAction.RESIGNATION_REGISTERED, callerId, memberId, "MEMBER", memberId,
                "강사 " + Josa.eulReul("'" + member.getName() + "'") + " 퇴사일로 " + date + "을 등록했다.",
                ActivityChange.ifChanged("resignationDate", beforeDate, date));
        return MemberResponse.from(member, permissionsOf(memberId));
    }

    @Transactional
    public void cancelResignation(Long callerId, Long memberId) {
        adminGuard.requireAdmin(callerId);
        Member member = findInstructor(memberId);
        requireNotResigned(member);
        if (member.getResignationDate() == null) {
            throw new ApiException(ErrorCode.RESIGNATION_NOT_REGISTERED);
        }
        LocalDate beforeDate = member.getResignationDate();
        member.cancelResignation(Instant.now());
        activityLogRecorder.record(ActivityAction.RESIGNATION_CANCELED, callerId, memberId, "MEMBER", memberId,
                "강사 " + Josa.eulReul("'" + member.getName() + "'") + " 퇴사일 등록을 취소했다.",
                ActivityChange.of("resignationDate", beforeDate, null));
    }

    /**
     * 퇴사일이 등록되고 도래한 강사를 퇴사 처리한다. 로그인이 막히고 Refresh Token과 권한이 회수되며,
     * 승인 대기 중인 부재와 아직 시작하지 않은 승인된 부재는 취소된다.
     */
    @Transactional
    public MemberResponse completeResignation(Long callerId, Long memberId) {
        adminGuard.requireAdmin(callerId);
        Member member = findInstructor(memberId);
        requireNotResigned(member);
        LocalDate resignationDate = member.getResignationDate();
        if (resignationDate == null) {
            throw new ApiException(ErrorCode.RESIGNATION_NOT_REGISTERED);
        }
        if (resignationDate.isAfter(LocalDate.now(CENTER_ZONE))) {
            throw new ApiException(ErrorCode.RESIGNATION_DATE_NOT_REACHED);
        }
        Instant now = Instant.now();
        if (lessonRepository.existsByInstructorIdAndStatusNotAndStartAtAfter(memberId, LessonStatus.CANCELED,
                now)) {
            throw new ApiException(ErrorCode.INSTRUCTOR_HAS_UPCOMING_LESSONS);
        }

        MemberStatus beforeStatus = member.getStatus();
        member.resign(now);
        refreshTokenRepository.revokeAllOf(memberId, now);
        permissionRepository.deleteAllOf(memberId);
        List<Absence> canceledAbsences = absenceRepository.findCancelableOnResignation(memberId, now);
        for (Absence absence : canceledAbsences) {
            AbsenceStatus beforeAbsenceStatus = absence.getStatus();
            absence.cancel(callerId, now);
            activityLogRecorder.record(ActivityAction.ABSENCE_CANCELED, callerId, memberId, "ABSENCE",
                    absence.getId(), "퇴사 처리로 부재(" + ActivityTime.period(absence.getStartAt(),
                            absence.getEndAt()) + ")를 자동 취소했다.",
                    ActivityChange.of("status", beforeAbsenceStatus, AbsenceStatus.CANCELED));
        }
        activityLogRecorder.record(ActivityAction.RESIGNATION_COMPLETED, callerId, memberId, "MEMBER", memberId,
                "강사 " + Josa.eulReul("'" + member.getName() + "'") + " 퇴사 처리했다."
                        + (canceledAbsences.isEmpty() ? "" : " 부재 " + canceledAbsences.size() + "건이 자동 취소되었다."),
                ActivityChange.of("status", beforeStatus, member.getStatus()));
        return MemberResponse.from(member, List.of());
    }

    // ===== 공통 =====

    private Member findInstructor(Long memberId) {
        return memberRepository.findById(memberId)
                .filter(member -> member.isRole(MemberRole.INSTRUCTOR))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private static void requireNotResigned(Member member) {
        if (member.getStatus() == MemberStatus.RESIGNED) {
            throw new ApiException(ErrorCode.MEMBER_RESIGNED);
        }
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new ApiException(ErrorCode.INVALID_INPUT);
        }
    }

    private List<String> permissionsOf(Long memberId) {
        return permissionRepository.findByMemberIdInOrderByPermissionCode(List.of(memberId)).stream()
                .map(p -> p.getPermissionCode()).toList();
    }
}
