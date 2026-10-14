package com.rha.businesshour;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BusinessHoursRequest(@NotNull List<@NotNull @Valid BusinessHourRequest> businessHours) {
}
