package com.rha.reservation;

import jakarta.validation.constraints.NotNull;

public record ReservationCreateRequest(@NotNull Long lessonId) {
}
