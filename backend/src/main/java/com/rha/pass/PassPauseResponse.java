package com.rha.pass;

import java.time.Instant;
import java.time.LocalDate;

public record PassPauseResponse(Long id, LocalDate startDate, LocalDate endDate, int days, PassPauseStatus status,
        String reason, Instant terminatedAt, Instant createdAt) {

    static PassPauseResponse from(PassPause pause) {
        return new PassPauseResponse(pause.getId(), pause.getStartDate(), pause.getEndDate(), pause.days(),
                pause.getStatus(), pause.getReason(), pause.getTerminatedAt(), pause.getCreatedAt());
    }
}
