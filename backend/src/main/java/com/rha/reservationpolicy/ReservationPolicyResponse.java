package com.rha.reservationpolicy;

public record ReservationPolicyResponse(int cancelDeadlineHours, boolean allowUnpaidReservation) {

    static ReservationPolicyResponse from(ReservationPolicy policy) {
        return new ReservationPolicyResponse(policy.getCancelDeadlineHours(), policy.isAllowUnpaidReservation());
    }
}
