package com.rha.reservationpolicy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 단일 행(id = 1)인 예약 정책. */
@Entity
@Table(name = "reservation_policies")
public class ReservationPolicy {

    static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Column(nullable = false)
    private int cancelDeadlineHours;

    @Column(nullable = false)
    private boolean allowUnpaidReservation;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ReservationPolicy() {
    }

    public void replace(int cancelDeadlineHours, boolean allowUnpaidReservation, Instant now) {
        this.cancelDeadlineHours = cancelDeadlineHours;
        this.allowUnpaidReservation = allowUnpaidReservation;
        this.updatedAt = now;
    }

    public int getCancelDeadlineHours() {
        return cancelDeadlineHours;
    }

    public boolean isAllowUnpaidReservation() {
        return allowUnpaidReservation;
    }
}
