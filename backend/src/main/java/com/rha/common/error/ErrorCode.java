package com.rha.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 필드 형식이 올바르지 않습니다."),
    INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다."),
    SAME_AS_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "새 비밀번호가 현재 비밀번호와 같습니다."),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "인증 코드가 일치하지 않거나 만료되었습니다."),
    INVALID_VERIFICATION_TOKEN(HttpStatus.BAD_REQUEST, "이메일 인증 토큰이 유효하지 않습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "인증이 필요하거나 인증 정보가 유효하지 않습니다."),
    ACCOUNT_DISABLED(HttpStatus.UNAUTHORIZED, "로그인할 수 없는 계정입니다."),
    INVALID_RESET_TOKEN(HttpStatus.BAD_REQUEST, "비밀번호 재설정 토큰이 유효하지 않거나 만료되었습니다."),
    FORBIDDEN_ROLE(HttpStatus.FORBIDDEN, "강사와 관리자는 직접 탈퇴할 수 없습니다."),
    PERMISSION_DENIED(HttpStatus.FORBIDDEN, "해당 작업을 할 수 있는 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "지원하지 않는 응답 형식입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 본문 형식입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh Token이 유효하지 않습니다."),
    INVALID_PERMISSION(HttpStatus.BAD_REQUEST, "권한 코드가 목록에 없거나 중복되었습니다."),
    PERMISSION_TARGET_INVALID(HttpStatus.BAD_REQUEST, "권한은 강사에게만 부여할 수 있습니다."),
    INVALID_BUSINESS_HOURS(HttpStatus.BAD_REQUEST, "영업시간 설정이 올바르지 않습니다."),
    INVALID_LESSON_TIME(HttpStatus.BAD_REQUEST, "수업 시간이 올바르지 않습니다."),
    INVALID_ABSENCE_PERIOD(HttpStatus.BAD_REQUEST, "부재 기간이 올바르지 않습니다."),
    INVALID_START_DATE(HttpStatus.BAD_REQUEST, "이용권 시작일이 올바르지 않습니다."),
    INVALID_MEMBER(HttpStatus.BAD_REQUEST, "이용권을 발급할 수 없는 회원입니다."),
    INVALID_REMAINING_COUNT(HttpStatus.BAD_REQUEST, "잔여 횟수가 올바르지 않습니다."),
    INVALID_END_DATE(HttpStatus.BAD_REQUEST, "만료일이 시작일보다 빠릅니다."),
    INVALID_PAUSE_PERIOD(HttpStatus.BAD_REQUEST, "일시정지 기간이 올바르지 않습니다."),
    INVALID_PAYMENT_DATE(HttpStatus.BAD_REQUEST, "결제일 또는 환불일이 올바르지 않습니다."),
    INVALID_REFUND_AMOUNT(HttpStatus.BAD_REQUEST, "환불 금액이 환불할 수 있는 금액을 넘거나 올바르지 않습니다."),
    INVALID_PAY_RATE(HttpStatus.BAD_REQUEST, "수당 단가 목록이 올바르지 않습니다."),
    INVALID_PERIOD(HttpStatus.BAD_REQUEST, "정산 기간이 올바르지 않습니다."),
    INVALID_AVAILABILITY(HttpStatus.BAD_REQUEST, "수업 가능시간 설정이 올바르지 않습니다."),
    INVALID_RESIGNATION_DATE(HttpStatus.BAD_REQUEST, "퇴사일은 오늘보다 과거일 수 없습니다."),
    INVALID_PUBLISH_AT(HttpStatus.BAD_REQUEST, "공개 예약 시각이 올바르지 않습니다."),
    INVALID_INSTRUCTOR(HttpStatus.BAD_REQUEST, "담당 강사로 지정할 수 없는 회원입니다."),
    LESSON_TYPE_INACTIVE(HttpStatus.BAD_REQUEST, "비활성 수업 유형으로는 수업을 개설할 수 없습니다."),
    ROOM_NAME_DUPLICATED(HttpStatus.CONFLICT, "이미 있는 룸 이름입니다."),
    LESSON_TYPE_CODE_DUPLICATED(HttpStatus.CONFLICT, "이미 있는 수업 유형 코드입니다."),
    ROOM_IN_USE(HttpStatus.CONFLICT, "시작 전 수업이 있는 룸은 삭제할 수 없습니다."),
    OUTSIDE_BUSINESS_HOURS(HttpStatus.CONFLICT, "영업시간 밖이거나 휴무일입니다."),
    INSTRUCTOR_UNAVAILABLE(HttpStatus.CONFLICT, "강사의 수업 가능시간 밖입니다."),
    INSTRUCTOR_ABSENT(HttpStatus.CONFLICT, "강사의 승인된 부재 기간과 겹칩니다."),
    LESSON_NOT_RESERVABLE(HttpStatus.CONFLICT, "예약할 수 없는 수업입니다."),
    LESSON_FULL(HttpStatus.CONFLICT, "수업 정원이 가득 찼습니다."),
    ALREADY_RESERVED(HttpStatus.CONFLICT, "이미 예약한 수업입니다."),
    NO_AVAILABLE_PASS(HttpStatus.CONFLICT, "예약에 사용할 수 있는 이용권이 없습니다."),
    PASS_UNPAID(HttpStatus.CONFLICT, "완납한 이용권만 예약에 사용할 수 있습니다."),
    CANCELLATION_DEADLINE_PASSED(HttpStatus.CONFLICT, "예약 취소 가능 시간이 지났습니다."),
    INVALID_RESERVATION_STATUS(HttpStatus.CONFLICT, "현재 예약 상태에서는 할 수 없는 작업입니다."),
    LESSON_NOT_STARTED(HttpStatus.CONFLICT, "수업이 시작되기 전에는 출결을 기록할 수 없습니다."),
    LESSON_HAS_RESERVATIONS(HttpStatus.CONFLICT, "예약이 있는 수업은 시작·종료 시각을 바꿀 수 없습니다."),
    ABSENCE_HAS_RESERVATIONS(HttpStatus.CONFLICT, "부재 기간에 예약이 있는 수업이 있어 승인할 수 없습니다."),
    PAUSE_HAS_RESERVATIONS(HttpStatus.CONFLICT, "정지 기간에 속한 예약이 있어 먼저 취소해야 합니다."),
    MEMBER_HAS_UPCOMING_RESERVATIONS(HttpStatus.CONFLICT, "시작 전 예약이 있는 회원입니다."),
    PASS_PRODUCT_IN_USE(HttpStatus.CONFLICT, "발급된 이용권이 있는 상품은 삭제할 수 없습니다."),
    PASS_EXPIRED(HttpStatus.CONFLICT, "이미 만료된 이용권입니다."),
    PASS_REFUNDED(HttpStatus.CONFLICT, "환불된 이용권입니다."),
    SETTLEMENT_PERIOD_OVERLAP(HttpStatus.CONFLICT, "같은 강사의 다른 정산 기간과 겹칩니다."),
    UNRECORDED_ATTENDANCE_EXISTS(HttpStatus.CONFLICT, "출결이 기록되지 않은 예약이 있는 수업이 있습니다."),
    PAY_RATE_NOT_SET(HttpStatus.CONFLICT, "수당 단가가 설정되지 않은 수업 유형이 있습니다."),
    INVALID_SETTLEMENT_STATUS(HttpStatus.CONFLICT, "현재 정산 상태에서는 할 수 없는 작업입니다."),
    LESSON_SETTLED(HttpStatus.CONFLICT, "강사 정산에 포함된 수업은 출결을 정정할 수 없습니다."),
    PAYMENT_EXCEEDS_PRICE(HttpStatus.CONFLICT, "결제 합계가 이용권 금액을 넘을 수 없습니다."),
    PAYMENT_REFUNDED(HttpStatus.CONFLICT, "환불로 체크된 결제 기록은 정정하거나 삭제할 수 없습니다."),
    PAUSE_PERIOD_OVERLAP(HttpStatus.CONFLICT, "다른 일시정지 기간과 겹칩니다."),
    INVALID_PAUSE_STATUS(HttpStatus.CONFLICT, "이미 끝났거나 취소된 일시정지입니다."),
    ABSENCE_PERIOD_OVERLAP(HttpStatus.CONFLICT, "승인 대기 또는 승인된 부재와 기간이 겹칩니다."),
    INVALID_ABSENCE_STATUS(HttpStatus.CONFLICT, "현재 부재 상태에서는 할 수 없는 작업입니다."),
    RESIGNATION_NOT_REGISTERED(HttpStatus.CONFLICT, "등록된 퇴사일이 없습니다."),
    RESIGNATION_DATE_NOT_REACHED(HttpStatus.CONFLICT, "퇴사일이 아직 오지 않았습니다."),
    REASSIGNMENT_FAILED(HttpStatus.CONFLICT, "새 강사에게 배정할 수 없는 수업이 있습니다."),
    INSTRUCTOR_RESIGNING(HttpStatus.CONFLICT, "퇴사일 이후의 수업은 배정할 수 없습니다."),
    INSTRUCTOR_TIME_CONFLICT(HttpStatus.CONFLICT, "담당 강사의 다른 수업과 시간이 겹칩니다."),
    ROOM_TIME_CONFLICT(HttpStatus.CONFLICT, "같은 룸의 다른 수업과 시간이 겹칩니다."),
    INSTRUCTOR_HAS_UPCOMING_LESSONS(HttpStatus.CONFLICT, "시작 전 담당 수업이 있는 강사입니다."),
    LESSON_CANCELED(HttpStatus.CONFLICT, "이미 취소된 수업입니다."),
    LESSON_ALREADY_STARTED(HttpStatus.CONFLICT, "이미 시작한 수업입니다."),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "허용되지 않는 공개 상태 변경입니다."),
    CANNOT_CHANGE_OWN_ROLE(HttpStatus.FORBIDDEN, "본인의 역할은 변경할 수 없습니다."),
    CANNOT_CHANGE_OWN_STATUS(HttpStatus.FORBIDDEN, "본인 계정의 상태는 변경할 수 없습니다."),
    MEMBER_WITHDRAWN(HttpStatus.CONFLICT, "탈퇴한 회원은 ACTIVE로만 되돌릴 수 있습니다."),
    MEMBER_RESIGNED(HttpStatus.CONFLICT, "퇴사한 강사는 ACTIVE로만 되돌릴 수 있습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
