package com.rha.activity;

/** 활동 이력을 묶어 보는 카테고리. 선언 순서가 카탈로그 응답 순서이다. */
public enum ActivityCategory {
    AUTH("인증"),
    MEMBER("회원"),
    PERMISSION("권한"),
    LESSON("수업"),
    RESERVATION("예약"),
    ATTENDANCE("출결"),
    PASS("이용권"),
    PAYMENT("결제"),
    PASS_PRODUCT("이용권 상품"),
    LESSON_TYPE("수업 유형"),
    ROOM("룸"),
    BUSINESS_HOUR("영업시간"),
    POLICY("예약 정책"),
    PAY_RATE("수당 단가"),
    SETTLEMENT("정산"),
    INSTRUCTOR("강사"),
    ABSENCE("부재");

    private final String displayName;

    ActivityCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
