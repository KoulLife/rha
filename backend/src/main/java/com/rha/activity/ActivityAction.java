package com.rha.activity;

import static com.rha.activity.ActivityCategory.*;

/** 기록하는 행위 코드. 선언 순서가 카탈로그 응답 순서이며, 코드와 이름은 openapi.yaml의 목록과 같아야 한다. */
public enum ActivityAction {
    SIGN_UP(AUTH, "회원가입"),
    LOGIN(AUTH, "로그인"),
    LOGIN_FAILED(AUTH, "로그인 실패"),
    LOGOUT(AUTH, "로그아웃"),
    PASSWORD_CHANGED(AUTH, "비밀번호 변경"),
    PASSWORD_RESET(AUTH, "비밀번호 재설정"),
    ACCOUNT_WITHDRAWN(AUTH, "회원탈퇴"),

    MEMBER_CREATED(MEMBER, "강사/관리자 등록"),
    MEMBER_PROFILE_UPDATED(MEMBER, "내 정보 수정"),
    MEMBER_UPDATED(MEMBER, "회원 정보 수정"),
    MEMBER_STATUS_CHANGED(MEMBER, "회원 상태 변경"),

    PERMISSIONS_UPDATED(PERMISSION, "권한 설정"),

    LESSON_CREATED(LESSON, "수업 개설"),
    LESSON_UPDATED(LESSON, "수업 수정"),
    LESSON_STATUS_CHANGED(LESSON, "수업 공개 상태 변경"),
    LESSON_CANCELED(LESSON, "수업 취소"),
    LESSON_PUBLISHED(LESSON, "수업 자동 공개"),

    RESERVATION_CREATED(RESERVATION, "예약 생성"),
    RESERVATION_CANCELED(RESERVATION, "예약 취소"),

    ATTENDANCE_RECORDED(ATTENDANCE, "출결 기록"),

    PASS_ISSUED(PASS, "이용권 발급"),
    PASS_UPDATED(PASS, "이용권 수정"),
    PASS_PAUSED(PASS, "일시정지 등록"),
    PASS_PAUSE_TERMINATED(PASS, "일시정지 해제"),
    PASS_EXPIRED(PASS, "이용권 만료"),

    PAYMENT_RECORDED(PAYMENT, "결제 기록"),
    PAYMENT_UPDATED(PAYMENT, "결제 정정"),
    PAYMENT_DELETED(PAYMENT, "결제 삭제"),
    PAYMENT_REFUND_CHECKED(PAYMENT, "환불 체크"),
    PAYMENT_REFUND_UNCHECKED(PAYMENT, "환불 체크 해제"),

    PASS_PRODUCT_CREATED(PASS_PRODUCT, "등록"),
    PASS_PRODUCT_UPDATED(PASS_PRODUCT, "수정"),
    PASS_PRODUCT_DELETED(PASS_PRODUCT, "삭제"),

    LESSON_TYPE_CREATED(LESSON_TYPE, "등록"),
    LESSON_TYPE_UPDATED(LESSON_TYPE, "수정"),

    ROOM_CREATED(ROOM, "등록"),
    ROOM_UPDATED(ROOM, "수정"),
    ROOM_DELETED(ROOM, "삭제"),

    BUSINESS_HOURS_UPDATED(BUSINESS_HOUR, "영업시간 설정"),

    RESERVATION_POLICY_UPDATED(POLICY, "예약 정책 설정"),

    PAY_RATES_UPDATED(PAY_RATE, "수당 단가 설정"),

    SETTLEMENT_CREATED(SETTLEMENT, "정산 계산"),
    SETTLEMENT_DISCARDED(SETTLEMENT, "정산 폐기"),
    SETTLEMENT_REVIEW_REQUESTED(SETTLEMENT, "검토 요청"),
    SETTLEMENT_CONFIRMED(SETTLEMENT, "정산 확정"),
    SETTLEMENT_PAID(SETTLEMENT, "지급 기록"),

    AVAILABILITY_UPDATED(INSTRUCTOR, "수업 가능시간 설정"),
    RESIGNATION_REGISTERED(INSTRUCTOR, "퇴사일 등록"),
    RESIGNATION_CANCELED(INSTRUCTOR, "퇴사일 등록 취소"),
    RESIGNATION_COMPLETED(INSTRUCTOR, "퇴사 처리"),
    LESSONS_REASSIGNED(INSTRUCTOR, "수업 인계"),

    ABSENCE_REQUESTED(ABSENCE, "부재 신청"),
    ABSENCE_APPROVED(ABSENCE, "부재 승인"),
    ABSENCE_REJECTED(ABSENCE, "부재 반려"),
    ABSENCE_CANCELED(ABSENCE, "부재 취소");

    private final ActivityCategory category;
    private final String displayName;

    ActivityAction(ActivityCategory category, String displayName) {
        this.category = category;
        this.displayName = displayName;
    }

    public ActivityCategory category() {
        return category;
    }

    public String displayName() {
        return displayName;
    }
}
