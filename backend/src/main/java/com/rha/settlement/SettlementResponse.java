package com.rha.settlement;

import com.rha.lesson.LessonStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 정산. 상세 조회와 계산 응답에는 수업별 내역({@code items})이 담기고 목록에는 null이라 생략된다. */
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public record SettlementResponse(Long id, IdName instructor, LocalDate periodStart, LocalDate periodEnd,
        SettlementStatus status, long totalAmount, int settledLessonCount, int excludedLessonCount,
        Instant createdAt, Instant reviewRequestedAt, Instant confirmedAt, Payment payment,
        List<Item> items) {

    public record IdName(Long id, String name) {
    }

    public record Payment(LocalDate paidAt, String memo, Instant recordedAt) {
    }

    public record LessonType(Long id, String code, String name) {
    }

    public record LessonSummary(Long id, String title, LessonType lessonType, IdName instructor, IdName room,
            Instant startAt, Instant endAt, LessonStatus status) {
    }

    public record Item(LessonSummary lesson, int attendedCount, int noShowCount, Long unitPrice, long amount,
            boolean settled, String excludedReason) {
    }
}
