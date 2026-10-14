package com.rha.lessontype;

import com.rha.auth.AuthenticatedMember;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 쓰기 API의 본문 검증은 관리자 여부를 확인한 뒤에 하므로 `@Valid`를 붙이지 않는다. */
@RestController
@RequestMapping("/lesson-types")
public class LessonTypeController {

    private final LessonTypeService lessonTypeService;

    public LessonTypeController(LessonTypeService lessonTypeService) {
        this.lessonTypeService = lessonTypeService;
    }

    @GetMapping
    public LessonTypePageResponse getLessonTypes(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Boolean active) {
        return lessonTypeService.getLessonTypes(active, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LessonTypeResponse createLessonType(@AuthenticationPrincipal AuthenticatedMember me,
            @RequestBody LessonTypeCreateRequest request) {
        return lessonTypeService.createLessonType(me.id(), request);
    }

    @PatchMapping("/{lessonTypeId}")
    public LessonTypeResponse updateLessonType(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long lessonTypeId, @RequestBody LessonTypeUpdateRequest request) {
        return lessonTypeService.updateLessonType(me.id(), lessonTypeId, request);
    }
}
