package com.rha.businesshour;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;

/** 요일별 영업시간. 항상 7행이며 현지 시각(Asia/Seoul)이다. */
@Entity
@Table(name = "business_hours")
public class BusinessHour {

    @Id
    @Column(name = "day_of_week")
    private String dayOfWeek;

    @Column(nullable = false)
    private boolean closed;

    private LocalTime openTime;

    private LocalTime closeTime;

    protected BusinessHour() {
    }

    /** 휴무이면 시각을 비운다. */
    public void change(boolean closed, LocalTime openTime, LocalTime closeTime) {
        this.closed = closed;
        this.openTime = closed ? null : openTime;
        this.closeTime = closed ? null : closeTime;
    }

    public String getDayOfWeek() {
        return dayOfWeek;
    }

    public boolean isClosed() {
        return closed;
    }

    public LocalTime getOpenTime() {
        return openTime;
    }

    public LocalTime getCloseTime() {
        return closeTime;
    }
}
