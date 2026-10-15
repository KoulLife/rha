package com.rha.payrate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

/** 강사별, 수업 유형별 수업당 단가. 출석 인원과 무관하다. */
@Entity
@Table(name = "pay_rates")
@IdClass(PayRate.Key.class)
public class PayRate {

    public record Key(Long instructorId, Long lessonTypeId) implements Serializable {
    }

    @Id
    private Long instructorId;

    @Id
    private Long lessonTypeId;

    @Column(nullable = false)
    private long unitPrice;

    @Column(nullable = false)
    private Instant updatedAt;

    protected PayRate() {
    }

    public static PayRate of(Long instructorId, Long lessonTypeId, long unitPrice, Instant now) {
        PayRate rate = new PayRate();
        rate.instructorId = instructorId;
        rate.lessonTypeId = lessonTypeId;
        rate.unitPrice = unitPrice;
        rate.updatedAt = now;
        return rate;
    }

    public Long getInstructorId() {
        return instructorId;
    }

    public Long getLessonTypeId() {
        return lessonTypeId;
    }

    public long getUnitPrice() {
        return unitPrice;
    }
}
