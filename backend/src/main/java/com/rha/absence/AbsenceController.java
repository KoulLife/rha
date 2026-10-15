package com.rha.absence;

import com.rha.auth.AuthenticatedMember;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 쓰기 API의 본문 검증은 권한을 확인한 뒤에 하므로 `@Valid`를 붙이지 않는다. */
@RestController
@RequestMapping("/absences")
public class AbsenceController {

    private final AbsenceService absenceService;

    public AbsenceController(AbsenceService absenceService) {
        this.absenceService = absenceService;
    }

    @GetMapping
    public AbsencePageResponse getAbsences(@AuthenticationPrincipal AuthenticatedMember me,
            @RequestParam(required = false) Long instructorId,
            @RequestParam(required = false) AbsenceStatus status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return absenceService.getAbsences(me.id(), instructorId, status, from, to, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AbsenceResponse createAbsence(@AuthenticationPrincipal AuthenticatedMember me,
            @RequestBody AbsenceCreateRequest request) {
        return absenceService.createAbsence(me.id(), request);
    }

    @GetMapping("/{absenceId}")
    public AbsenceResponse getAbsence(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long absenceId) {
        return absenceService.getAbsence(me.id(), absenceId);
    }

    @PostMapping("/{absenceId}/approvals")
    public AbsenceResponse approveAbsence(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long absenceId) {
        return absenceService.approveAbsence(me.id(), absenceId);
    }

    @PostMapping("/{absenceId}/rejections")
    public AbsenceResponse rejectAbsence(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long absenceId, @RequestBody(required = false) AbsenceRejectRequest request) {
        return absenceService.rejectAbsence(me.id(), absenceId, request);
    }

    @PostMapping("/{absenceId}/cancellations")
    public AbsenceResponse cancelAbsence(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long absenceId) {
        return absenceService.cancelAbsence(me.id(), absenceId);
    }
}
