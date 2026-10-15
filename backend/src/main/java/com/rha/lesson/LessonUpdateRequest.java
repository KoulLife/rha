package com.rha.lesson;

import jakarta.validation.constraints.Size;

/** 전달한 필드만 수정한다. 수업 유형은 바꿀 수 없어 필드가 없다. */
public record LessonUpdateRequest(
        @Size(min = 1, max = 100) String title,
        Long instructorId,
        Long roomId,
        String startAt,
        String endAt) {

    public boolean hasAnyField() {
        return title != null || instructorId != null || roomId != null || startAt != null || endAt != null;
    }
}
