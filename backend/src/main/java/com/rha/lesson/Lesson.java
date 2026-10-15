package com.rha.lesson;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "lessons")
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private Long lessonTypeId;

    @Column(nullable = false)
    private Long instructorId;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private Instant startAt;

    @Column(nullable = false)
    private Instant endAt;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LessonStatus status;

    private Instant publishAt;

    private Instant canceledAt;

    private String cancelReason;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Lesson() {
    }

    /** 개설된 수업은 비공개(CREATED)이며 정원은 개설 시점의 수업 유형 정원이다. */
    public static Lesson create(String title, Long lessonTypeId, int capacity, Long instructorId, Long roomId,
            Instant startAt, Instant endAt, Instant now) {
        Lesson lesson = new Lesson();
        lesson.title = title;
        lesson.lessonTypeId = lessonTypeId;
        lesson.capacity = capacity;
        lesson.instructorId = instructorId;
        lesson.roomId = roomId;
        lesson.startAt = startAt;
        lesson.endAt = endAt;
        lesson.status = LessonStatus.CREATED;
        lesson.createdAt = now;
        lesson.updatedAt = now;
        return lesson;
    }

    /** 수업 유형은 바꿀 수 없다. */
    public void reschedule(String title, Long instructorId, Long roomId, Instant startAt, Instant endAt,
            Instant now) {
        this.title = title;
        this.instructorId = instructorId;
        this.roomId = roomId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.updatedAt = now;
    }

    /** 공개 예약 시각은 SCHEDULED일 때만 값을 가진다. */
    public void changeStatus(LessonStatus status, Instant publishAt, Instant now) {
        this.status = status;
        this.publishAt = status == LessonStatus.SCHEDULED ? publishAt : null;
        this.updatedAt = now;
    }

    public void cancel(String reason, Instant now) {
        this.status = LessonStatus.CANCELED;
        this.publishAt = null;
        this.canceledAt = now;
        this.cancelReason = reason;
        this.updatedAt = now;
    }

    public boolean hasStarted(Instant now) {
        return !startAt.isAfter(now);
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Long getLessonTypeId() {
        return lessonTypeId;
    }

    public Long getInstructorId() {
        return instructorId;
    }

    public Long getRoomId() {
        return roomId;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public int getCapacity() {
        return capacity;
    }

    public LessonStatus getStatus() {
        return status;
    }

    public Instant getPublishAt() {
        return publishAt;
    }

    public Instant getCanceledAt() {
        return canceledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
