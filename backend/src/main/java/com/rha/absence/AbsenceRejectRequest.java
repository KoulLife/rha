package com.rha.absence;

import jakarta.validation.constraints.Size;

public record AbsenceRejectRequest(@Size(max = 200) String reason) {
}
