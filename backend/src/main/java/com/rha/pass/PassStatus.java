package com.rha.pass;

public enum PassStatus {
    UPCOMING,
    ACTIVE,
    PAUSED,
    EXPIRED,
    /** 환불됨. 날짜에 따른 자동 상태 전환 대상이 아니며 다른 상태로 돌아가지 않는다. */
    REFUNDED
}
