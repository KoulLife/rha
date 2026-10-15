package com.rha.instructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 요일과 시각은 문자열로 받는다. 관리자 확인(403) 전에 형식 오류(400)가 나지 않게 하기 위함이다. */
public record AvailabilityRequest(
        @NotNull @Size(max = 70) List<@NotNull @Valid Slot> slots) {

    public record Slot(
            @NotNull @Pattern(regexp = "MON|TUE|WED|THU|FRI|SAT|SUN", message = "요일 형식이 올바르지 않습니다.")
            String dayOfWeek,
            @NotNull @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$", message = "HH:mm 형식이어야 합니다.")
            String startTime,
            @NotNull @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$", message = "HH:mm 형식이어야 합니다.")
            String endTime) {
    }
}
