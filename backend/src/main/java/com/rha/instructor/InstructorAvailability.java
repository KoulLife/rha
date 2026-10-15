package com.rha.instructor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;

/** 강사의 요일별 수업 가능 시간대 하나. 현지 시각(Asia/Seoul)이다. */
@Entity
@Table(name = "instructor_availabilities")
public class InstructorAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long instructorId;

    @Column(nullable = false)
    private String dayOfWeek;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    protected InstructorAvailability() {
    }

    public static InstructorAvailability of(Long instructorId, String dayOfWeek, LocalTime startTime,
            LocalTime endTime) {
        InstructorAvailability slot = new InstructorAvailability();
        slot.instructorId = instructorId;
        slot.dayOfWeek = dayOfWeek;
        slot.startTime = startTime;
        slot.endTime = endTime;
        return slot;
    }

    public String getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }
}
