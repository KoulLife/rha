package com.rha.businesshour;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 형식만 검증한다. 요일 구성과 시각 관계는 서비스에서 INVALID_BUSINESS_HOURS로 처리한다. */
public record BusinessHourRequest(
        @NotNull @Pattern(regexp = "^(MON|TUE|WED|THU|FRI|SAT|SUN)$", message = "정의되지 않은 요일입니다.")
        String dayOfWeek,
        @NotNull Boolean closed,
        @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$", message = "HH:mm 형식이어야 합니다.") String openTime,
        @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$", message = "HH:mm 형식이어야 합니다.") String closeTime) {
}
