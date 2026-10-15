package com.rha.lesson;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 시각은 문자열로 받아 서비스에서 해석한다. 권한 확인(403) 전에 형식 오류(400)가 나지 않게 하기 위함이다. */
public record LessonCreateRequest(
        @NotNull @Size(min = 1, max = 100) String title,
        @NotNull Long lessonTypeId,
        @NotNull Long instructorId,
        @NotNull Long roomId,
        @NotNull String startAt,
        @NotNull String endAt) {
}
