package com.rha.reservation;

import com.rha.lesson.Lesson;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lesson_id", nullable = false)
    private Long lessonId;

    /** 수업 시작 시각으로 정렬·필터하기 위한 읽기 전용 연관이다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", insertable = false, updatable = false)
    private Lesson lesson;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Long passId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    private Instant attendanceConfirmedAt;

    @Enumerated(EnumType.STRING)
    private CanceledBy canceledBy;

    private Instant canceledAt;

    private String cancelReason;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Reservation() {
    }

    public static Reservation reserve(Long lessonId, Long memberId, Long passId, Instant now) {
        Reservation reservation = new Reservation();
        reservation.lessonId = lessonId;
        reservation.memberId = memberId;
        reservation.passId = passId;
        reservation.status = ReservationStatus.RESERVED;
        reservation.createdAt = now;
        reservation.updatedAt = now;
        return reservation;
    }

    public void cancel(CanceledBy by, String reason, Instant now) {
        this.status = ReservationStatus.CANCELED;
        this.canceledBy = by;
        this.cancelReason = reason;
        this.canceledAt = now;
        this.updatedAt = now;
    }

    /** 출석 또는 노쇼를 기록한다. 이미 기록된 출결도 같은 방식으로 정정한다. */
    public void confirmAttendance(ReservationStatus status, Instant now) {
        this.status = status;
        this.attendanceConfirmedAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getLessonId() {
        return lessonId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getPassId() {
        return passId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getAttendanceConfirmedAt() {
        return attendanceConfirmedAt;
    }

    public CanceledBy getCanceledBy() {
        return canceledBy;
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
