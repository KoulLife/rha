package com.rha.lesson;

import com.rha.auth.AuthenticatedMember;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 쓰기 API의 본문 검증은 권한을 확인한 뒤에 하므로 `@Valid`를 붙이지 않는다. */
@RestController
@RequestMapping("/lessons")
public class LessonController {

    private final LessonService lessonService;

    public LessonController(LessonService lessonService) {
        this.lessonService = lessonService;
    }

    @GetMapping
    public LessonPageResponse getLessons(@AuthenticationPrincipal AuthenticatedMember me,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Long lessonTypeId,
            @RequestParam(required = false) Long instructorId,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) LessonStatus status,
            @RequestParam(defaultValue = "ASC") Sort.Direction order,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return lessonService.getLessons(me.id(), from, to, lessonTypeId, instructorId, roomId, status, order,
                page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LessonResponse createLesson(@AuthenticationPrincipal AuthenticatedMember me,
            @RequestBody LessonCreateRequest request) {
        return lessonService.createLesson(me.id(), request);
    }

    @GetMapping("/{lessonId}")
    public LessonResponse getLesson(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long lessonId) {
        return lessonService.getLesson(me.id(), lessonId);
    }

    @PatchMapping("/{lessonId}")
    public LessonResponse updateLesson(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long lessonId, @RequestBody LessonUpdateRequest request) {
        return lessonService.updateLesson(me.id(), lessonId, request);
    }

    @PutMapping("/{lessonId}/status")
    public LessonResponse changeLessonStatus(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long lessonId, @RequestBody LessonStatusChangeRequest request) {
        return lessonService.changeLessonStatus(me.id(), lessonId, request);
    }

    @PostMapping("/{lessonId}/cancellations")
    public LessonResponse cancelLesson(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long lessonId, @RequestBody(required = false) LessonCancelRequest request) {
        return lessonService.cancelLesson(me.id(), lessonId, request);
    }
}
