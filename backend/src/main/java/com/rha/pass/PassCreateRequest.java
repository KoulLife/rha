package com.rha.pass;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 날짜는 문자열로 받아 서비스에서 해석한다. 관리자 확인(403) 전에 형식 오류(400)가 나지 않게 하기 위함이다. */
public record PassCreateRequest(
        @NotNull Long passProductId,
        @NotNull Long memberId,
        @NotNull Long instructorId,
        @NotNull String startDate,
        @Min(0) Long price) {
}
