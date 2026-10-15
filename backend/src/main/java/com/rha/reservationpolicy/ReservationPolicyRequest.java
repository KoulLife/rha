package com.rha.reservationpolicy;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReservationPolicyRequest(
        @NotNull @Min(0) @Max(720) Integer cancelDeadlineHours,
        @NotNull Boolean allowUnpaidReservation) {
}
