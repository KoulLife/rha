package com.rha.lessontype;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/** 전달한 필드만 수정한다. 최소 1개 필드가 필요하며, 정의되지 않은 필드(code 등)는 무시된다. */
public record LessonTypeUpdateRequest(
        @Pattern(regexp = "(?s)\\s*\\S(?:.{0,48}\\S)?\\s*", message = "공백을 제외하고 1~50자여야 합니다.") String name,
        @Min(1) @Max(100) Integer capacity,
        Boolean active) {

    public boolean hasAnyField() {
        return name != null || capacity != null || active != null;
    }
}
