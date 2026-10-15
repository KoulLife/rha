package com.rha.settlement;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 지급일은 문자열로 받아 서비스에서 해석한다. 관리자 확인(403) 전에 형식 오류(400)가 나지 않게 하기 위함이다. */
public record SettlementPaymentRequest(@NotNull String paidAt, @Size(max = 200) String memo) {
}
