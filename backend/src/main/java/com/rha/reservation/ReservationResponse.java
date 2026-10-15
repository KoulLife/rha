package com.rha.reservation;

import com.rha.lesson.LessonStatus;
import java.time.Instant;
import java.time.LocalDate;

public record ReservationResponse(Long id, LessonSummary lesson, PersonSummary member, PassSummary pass,
        ReservationStatus status, Instant attendanceConfirmedAt, CanceledBy canceledBy, Instant canceledAt,
        String cancelReason, Instant createdAt) {

    public record IdName(Long id, String name) {
    }

    public record LessonTypeSummary(Long id, String code, String name) {
    }

    public record LessonSummary(Long id, String title, LessonTypeSummary lessonType, IdName instructor,
            IdName room, Instant startAt, Instant endAt, LessonStatus status) {
    }

    public record PersonSummary(Long id, String name) {
    }

    public record PassSummary(Long id, IdName passProduct, LocalDate endDate) {
    }
}
