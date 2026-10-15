package com.rha.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 정산에 포함된 수업 한 건. 한 수업은 하나의 정산에만 포함되며 이 행이 있으면 출결을 정정할 수 없다. */
@Entity
@Table(name = "settlement_items")
public class SettlementItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long settlementId;

    @Column(nullable = false)
    private Long lessonId;

    @Column(nullable = false)
    private int attendedCount;

    @Column(nullable = false)
    private int noShowCount;

    private Long unitPrice;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private boolean settled;

    private String excludedReason;

    protected SettlementItem() {
    }

    /** 진행된 수업. 계산 시점의 단가로 금액을 정한다. */
    public static SettlementItem settled(Long settlementId, Long lessonId, int attendedCount, int noShowCount,
            long unitPrice) {
        SettlementItem item = new SettlementItem();
        item.settlementId = settlementId;
        item.lessonId = lessonId;
        item.attendedCount = attendedCount;
        item.noShowCount = noShowCount;
        item.unitPrice = unitPrice;
        item.amount = unitPrice;
        item.settled = true;
        return item;
    }

    /** 취소되지 않은 예약이 없어 진행되지 않은 수업. 금액은 0이다. */
    public static SettlementItem excluded(Long settlementId, Long lessonId) {
        SettlementItem item = new SettlementItem();
        item.settlementId = settlementId;
        item.lessonId = lessonId;
        item.amount = 0;
        item.settled = false;
        item.excludedReason = "NO_RESERVATION";
        return item;
    }

    public Long getSettlementId() {
        return settlementId;
    }

    public Long getLessonId() {
        return lessonId;
    }

    public int getAttendedCount() {
        return attendedCount;
    }

    public int getNoShowCount() {
        return noShowCount;
    }

    public Long getUnitPrice() {
        return unitPrice;
    }

    public long getAmount() {
        return amount;
    }

    public boolean isSettled() {
        return settled;
    }

    public String getExcludedReason() {
        return excludedReason;
    }
}
