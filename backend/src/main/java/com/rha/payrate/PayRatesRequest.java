package com.rha.payrate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record PayRatesRequest(@NotNull List<@NotNull @Valid PayRateInput> payRates) {

    public record PayRateInput(@NotNull Long lessonTypeId, @NotNull @Min(0) Long unitPrice) {
    }
}
