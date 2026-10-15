package com.rha.passproduct;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PassProductCreateRequest(
        @NotNull @Size(min = 1, max = 100) String name,
        @NotNull @Min(0) Long price,
        @NotNull @Min(1) @Max(1000) Integer totalCount,
        @NotNull @Min(1) @Max(3650) Integer validityDays,
        @Size(max = 500) String description) {
}
