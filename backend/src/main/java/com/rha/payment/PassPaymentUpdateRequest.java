package com.rha.payment;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 전달한 필드만 정정한다. 메모는 null을 보내면 삭제를 뜻하므로 전달 여부를 따로 기록한다.
 * 수단과 날짜는 문자열로 받아 서비스에서 해석한다.
 */
public class PassPaymentUpdateRequest {

    @Pattern(regexp = "CARD|CASH|TRANSFER|OTHER", message = "결제 수단이 올바르지 않습니다.")
    private String method;

    @Min(1)
    private Long amount;

    private String paidAt;

    @Size(max = 200)
    private String memo;

    @JsonIgnore
    private boolean memoSpecified;

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public String getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(String paidAt) {
        this.paidAt = paidAt;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
        this.memoSpecified = true;
    }

    public boolean isMemoSpecified() {
        return memoSpecified;
    }

    public boolean hasAnyField() {
        return method != null || amount != null || paidAt != null || memoSpecified;
    }
}
