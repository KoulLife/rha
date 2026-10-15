package com.rha.settlement;

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

/** 강사 한 명의 기간(센터 현지 날짜) 수당 정산. */
@Entity
@Table(name = "settlements")
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long instructorId;

    @Column(nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementStatus status;

    @Column(nullable = false)
    private long totalAmount;

    @Column(nullable = false)
    private int settledLessonCount;

    @Column(nullable = false)
    private int excludedLessonCount;

    private Instant reviewRequestedAt;

    private Instant confirmedAt;

    private LocalDate paidAt;

    private String paymentMemo;

    private Instant paymentRecordedAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected Settlement() {
    }

    public static Settlement calculated(Long instructorId, LocalDate periodStart, LocalDate periodEnd,
            long totalAmount, int settledLessonCount, int excludedLessonCount, Instant now) {
        Settlement settlement = new Settlement();
        settlement.instructorId = instructorId;
        settlement.periodStart = periodStart;
        settlement.periodEnd = periodEnd;
        settlement.status = SettlementStatus.CALCULATED;
        settlement.totalAmount = totalAmount;
        settlement.settledLessonCount = settledLessonCount;
        settlement.excludedLessonCount = excludedLessonCount;
        settlement.createdAt = now;
        return settlement;
    }

    public void requestReview(Instant now) {
        this.status = SettlementStatus.REVIEW_REQUESTED;
        this.reviewRequestedAt = now;
    }

    public void confirm(Instant now) {
        this.status = SettlementStatus.CONFIRMED;
        this.confirmedAt = now;
    }

    /** 외부 지급을 기록한다. 실제 지급은 시스템 밖에서 이뤄진다. */
    public void recordPayment(LocalDate paidAt, String memo, Instant now) {
        this.status = SettlementStatus.PAID;
        this.paidAt = paidAt;
        this.paymentMemo = memo;
        this.paymentRecordedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getInstructorId() {
        return instructorId;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public SettlementStatus getStatus() {
        return status;
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    public int getSettledLessonCount() {
        return settledLessonCount;
    }

    public int getExcludedLessonCount() {
        return excludedLessonCount;
    }

    public Instant getReviewRequestedAt() {
        return reviewRequestedAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public LocalDate getPaidAt() {
        return paidAt;
    }

    public String getPaymentMemo() {
        return paymentMemo;
    }

    public Instant getPaymentRecordedAt() {
        return paymentRecordedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
