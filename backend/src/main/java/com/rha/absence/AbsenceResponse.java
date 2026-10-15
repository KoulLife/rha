package com.rha.absence;

import com.rha.instructor.InstructorSummary;
import java.time.Instant;

public record AbsenceResponse(Long id, InstructorSummary instructor, Instant startAt, Instant endAt, String reason,
        AbsenceStatus status, String rejectReason, InstructorSummary processedBy, Instant processedAt,
        Instant createdAt) {

    /** {@code processedBy}는 처리 전이면 null이다. 신청한 강사와 처리자는 모두 id와 이름만 담는다. */
    static AbsenceResponse of(Absence absence, InstructorSummary instructor, InstructorSummary processedBy) {
        return new AbsenceResponse(absence.getId(), instructor, absence.getStartAt(), absence.getEndAt(),
                absence.getReason(), absence.getStatus(), absence.getRejectReason(), processedBy,
                absence.getProcessedAt(), absence.getCreatedAt());
    }
}
