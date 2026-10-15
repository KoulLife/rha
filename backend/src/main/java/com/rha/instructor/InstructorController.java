package com.rha.instructor;

import com.rha.auth.AuthenticatedMember;
import com.rha.lesson.LessonReassignmentRequest;
import com.rha.lesson.LessonReassignmentResponse;
import com.rha.lesson.LessonService;
import com.rha.member.MemberResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 쓰기 API의 본문 검증은 관리자 여부를 확인한 뒤에 하므로 `@Valid`를 붙이지 않는다. */
@RestController
public class InstructorController {

    private final InstructorService instructorService;
    private final LessonService lessonService;

    public InstructorController(InstructorService instructorService, LessonService lessonService) {
        this.instructorService = instructorService;
        this.lessonService = lessonService;
    }

    @GetMapping("/instructors")
    public InstructorPageResponse getInstructors(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return instructorService.getInstructors(page, size);
    }

    @GetMapping("/members/{memberId}/availability")
    public AvailabilityResponse getInstructorAvailability(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long memberId) {
        return instructorService.getAvailability(me.id(), memberId);
    }

    @PutMapping("/members/{memberId}/availability")
    public AvailabilityResponse updateInstructorAvailability(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long memberId, @RequestBody AvailabilityRequest request) {
        return instructorService.updateAvailability(me.id(), memberId, request);
    }

    @PutMapping("/members/{memberId}/resignation")
    public MemberResponse registerResignation(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long memberId, @RequestBody ResignationRequest request) {
        return instructorService.registerResignation(me.id(), memberId, request);
    }

    @DeleteMapping("/members/{memberId}/resignation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelResignation(@AuthenticationPrincipal AuthenticatedMember me, @PathVariable Long memberId) {
        instructorService.cancelResignation(me.id(), memberId);
    }

    @PostMapping("/members/{memberId}/resignation/completions")
    public MemberResponse completeResignation(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long memberId) {
        return instructorService.completeResignation(me.id(), memberId);
    }

    @PostMapping("/members/{memberId}/lesson-reassignments")
    public LessonReassignmentResponse reassignLessons(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long memberId, @RequestBody LessonReassignmentRequest request) {
        return lessonService.reassignLessons(me.id(), memberId, request);
    }
}
