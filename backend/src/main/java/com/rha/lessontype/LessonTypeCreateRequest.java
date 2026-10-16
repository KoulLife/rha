package com.rha.lessontype;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record LessonTypeCreateRequest(
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,29}$",
                message = "대문자, 숫자, 밑줄만 허용하며 2~30자, 대문자로 시작해야 합니다.") String code,
        @NotNull @Pattern(regexp = "(?s)\\s*\\S(?:.{0,48}\\S)?\\s*", message = "공백을 제외하고 1~50자여야 합니다.") String name,
        @NotNull @Min(1) @Max(100) Integer capacity) {
}
