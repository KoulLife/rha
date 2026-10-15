package com.rha.reservation;

import jakarta.validation.constraints.Size;

public record ReservationCancelRequest(@Size(max = 200) String reason) {
}
