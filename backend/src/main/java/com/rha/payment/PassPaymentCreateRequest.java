package com.rha.payment;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 수단과 날짜는 문자열로 받아 서비스에서 해석한다. 관리자 확인(403) 전에 형식 오류(400)가 나지 않게 하기 위함이다. */
public record PassPaymentCreateRequest(
        @NotNull @Pattern(regexp = "CARD|CASH|TRANSFER|OTHER", message = "결제 수단이 올바르지 않습니다.")
        String method,
        @NotNull @Min(1) Long amount,
        @NotNull String paidAt,
        @Size(max = 200) String memo) {
}
