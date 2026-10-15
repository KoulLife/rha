package com.rha.common.error;

import java.util.List;

public class ApiException extends RuntimeException {

    /** 응답의 {@code invalidParams} 항목. 필드(또는 대상 ID)와 사유를 담는다. */
    public record Detail(String field, String reason) {
    }

    private final ErrorCode errorCode;
    private final List<Detail> details;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, List.of());
    }

    public ApiException(ErrorCode errorCode, List<Detail> details) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
        this.details = details;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public List<Detail> details() {
        return details;
    }
}
