package com.rha.absence;

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
@Table(name = "absences")
public class Absence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long instructorId;

    @Column(nullable = false)
    private Instant startAt;

    @Column(nullable = false)
    private Instant endAt;

    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AbsenceStatus status;

    private String rejectReason;

    private Long processedBy;

    private Instant processedAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected Absence() {
    }

    public static Absence request(Long instructorId, Instant startAt, Instant endAt, String reason, Instant now) {
        Absence absence = new Absence();
        absence.instructorId = instructorId;
        absence.startAt = startAt;
        absence.endAt = endAt;
        absence.reason = reason;
        absence.status = AbsenceStatus.REQUESTED;
        absence.createdAt = now;
        return absence;
    }

    public void approve(Long processedBy, Instant now) {
        process(AbsenceStatus.APPROVED, processedBy, now);
    }

    public void reject(Long processedBy, String rejectReason, Instant now) {
        process(AbsenceStatus.REJECTED, processedBy, now);
        this.rejectReason = rejectReason;
    }

    /** 신청한 강사 본인의 철회이든 관리자의 취소이든 처리한 사람을 기록한다. */
    public void cancel(Long processedBy, Instant now) {
        process(AbsenceStatus.CANCELED, processedBy, now);
    }

    private void process(AbsenceStatus status, Long processedBy, Instant now) {
        this.status = status;
        this.processedBy = processedBy;
        this.processedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getInstructorId() {
        return instructorId;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public String getReason() {
        return reason;
    }

    public AbsenceStatus getStatus() {
        return status;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public Long getProcessedBy() {
        return processedBy;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
