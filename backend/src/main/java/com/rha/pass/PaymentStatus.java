package com.rha.pass;

public enum PaymentStatus {
    UNPAID,
    PARTIAL,
    PAID;

    /** 결제 합계가 0이고 금액이 0보다 크면 미결제, 합계가 금액보다 작으면 일부 결제, 그 외는 완납(금액 0 포함)이다. */
    public static PaymentStatus of(long price, long paidAmount) {
        if (paidAmount == 0 && price > 0) {
            return UNPAID;
        }
        return paidAmount < price ? PARTIAL : PAID;
    }
}
