package com.rha.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/** 센터에서 오프라인으로 받은 결제의 기록. 날짜는 센터 현지 날짜이다. */
@Entity
@Table(name = "pass_payments")
public class PassPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long passId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private LocalDate paidAt;

    private String memo;

    @Column(nullable = false)
    private boolean refunded;

    private LocalDate refundedAt;

    private String refundMemo;

    @Column(nullable = false)
    private Long recordedBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected PassPayment() {
    }

    public static PassPayment record(Long passId, PaymentMethod method, long amount, LocalDate paidAt, String memo,
            Long recordedBy, Instant now) {
        PassPayment payment = new PassPayment();
        payment.passId = passId;
        payment.method = method;
        payment.amount = amount;
        payment.paidAt = paidAt;
        payment.memo = memo;
        payment.recordedBy = recordedBy;
        payment.createdAt = now;
        payment.updatedAt = now;
        return payment;
    }

    /** null인 필드는 바꾸지 않는다. 메모는 null이 삭제를 뜻하므로 {@code changeMemo}로 변경 여부를 따로 받는다. */
    public void correct(PaymentMethod method, Long amount, LocalDate paidAt, boolean changeMemo, String memo,
            Instant now) {
        if (method != null) {
            this.method = method;
        }
        if (amount != null) {
            this.amount = amount;
        }
        if (paidAt != null) {
            this.paidAt = paidAt;
        }
        if (changeMemo) {
            this.memo = memo;
        }
        this.updatedAt = now;
    }

    public void markRefunded(LocalDate refundedAt, String refundMemo, Instant now) {
        this.refunded = true;
        this.refundedAt = refundedAt;
        this.refundMemo = refundMemo;
        this.updatedAt = now;
    }

    /** 환불 체크를 해제하면 환불일과 메모도 지운다. */
    public void clearRefund(Instant now) {
        this.refunded = false;
        this.refundedAt = null;
        this.refundMemo = null;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getPassId() {
        return passId;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public long getAmount() {
        return amount;
    }

    public LocalDate getPaidAt() {
        return paidAt;
    }

    public String getMemo() {
        return memo;
    }

    public boolean isRefunded() {
        return refunded;
    }

    public LocalDate getRefundedAt() {
        return refundedAt;
    }

    public String getRefundMemo() {
        return refundMemo;
    }

    public Long getRecordedBy() {
        return recordedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
