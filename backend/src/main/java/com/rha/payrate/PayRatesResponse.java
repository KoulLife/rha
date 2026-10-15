package com.rha.payrate;

import java.util.List;

public record PayRatesResponse(InstructorSummary instructor, List<PayRateResponse> payRates) {

    public record InstructorSummary(Long id, String name) {
    }

    public record LessonTypeSummary(Long id, String code, String name) {
    }

    public record PayRateResponse(LessonTypeSummary lessonType, long unitPrice) {
    }
}
