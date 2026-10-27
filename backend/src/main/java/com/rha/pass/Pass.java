package com.rha.pass;

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

/** 회원에게 발급된 이용권. 날짜는 센터 현지 날짜(Asia/Seoul)이다. */
@Entity
@Table(name = "passes")
public class Pass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long passProductId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Long instructorId;

    @Column(nullable = false)
    private long price;

    @Column(nullable = false)
    private int totalCount;

    @Column(nullable = false)
    private int remainingCount;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PassStatus status;

    @Column(nullable = false)
    private long refundedAmount;

    private LocalDate refundedAt;

    private String refundMemo;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Pass() {
    }

    public static Pass issue(Long passProductId, Long memberId, Long instructorId, long price, int totalCount,
            LocalDate startDate, LocalDate endDate, PassStatus status, Instant now) {
        Pass pass = new Pass();
        pass.passProductId = passProductId;
        pass.memberId = memberId;
        pass.instructorId = instructorId;
        pass.price = price;
        pass.totalCount = totalCount;
        pass.remainingCount = totalCount;
        pass.startDate = startDate;
        pass.endDate = endDate;
        pass.status = status;
        pass.createdAt = now;
        pass.updatedAt = now;
        return pass;
    }

    public void changeEndDate(LocalDate endDate, Instant now) {
        this.endDate = endDate;
        this.updatedAt = now;
    }

    public void changeRemainingCount(int remainingCount, Instant now) {
        this.remainingCount = remainingCount;
        this.updatedAt = now;
    }

    public void changeInstructor(Long instructorId, Instant now) {
        this.instructorId = instructorId;
        this.updatedAt = now;
    }

    /** 상품의 총 횟수가 바뀐 만큼 총 횟수와 잔여 횟수를 함께 증감한다. 총 횟수는 1, 잔여 횟수는 0 아래로 내려가지 않는다. */
    public void applyCountDelta(int delta, Instant now) {
        this.totalCount = Math.max(1, this.totalCount + delta);
        this.remainingCount = Math.min(this.totalCount, Math.max(0, this.remainingCount + delta));
        this.updatedAt = now;
    }

    /** 예약하면 잔여 횟수를 1회 차감한다. */
    public void consumeOne(Instant now) {
        this.remainingCount = this.remainingCount - 1;
        this.updatedAt = now;
    }

    /** 예약을 취소하면 1회 복구한다. 만료된 이용권도 횟수만 늘고 총 횟수를 넘지 않는다. */
    public void restoreOne(Instant now) {
        this.remainingCount = Math.min(this.totalCount, this.remainingCount + 1);
        this.updatedAt = now;
    }

    public void changeStatus(PassStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    /** 환불 처리한다. 되돌릴 수 없으며, 이후 날짜에 따른 자동 상태 전환 대상에서 빠진다. */
    public void refund(long amount, LocalDate refundedAt, String memo, Instant now) {
        this.status = PassStatus.REFUNDED;
        this.refundedAmount = amount;
        this.refundedAt = refundedAt;
        this.refundMemo = memo;
        this.updatedAt = now;
    }

    public boolean isRefunded() {
        return status == PassStatus.REFUNDED;
    }

    public Long getId() {
        return id;
    }

    public Long getPassProductId() {
        return passProductId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getInstructorId() {
        return instructorId;
    }

    public long getPrice() {
        return price;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getRemainingCount() {
        return remainingCount;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public PassStatus getStatus() {
        return status;
    }

    public long getRefundedAmount() {
        return refundedAmount;
    }

    public LocalDate getRefundedAt() {
        return refundedAt;
    }

    public String getRefundMemo() {
        return refundMemo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
