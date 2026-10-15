package com.rha.pass;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "pass_pauses")
public class PassPause {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long passId;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PassPauseStatus status;

    private String reason;

    private Instant terminatedAt;

    @Column(nullable = false)
    private Long createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    protected PassPause() {
    }

    public static PassPause register(Long passId, LocalDate startDate, LocalDate endDate, PassPauseStatus status,
            String reason, Long createdBy, Instant now) {
        PassPause pause = new PassPause();
        pause.passId = passId;
        pause.startDate = startDate;
        pause.endDate = endDate;
        pause.status = status;
        pause.reason = reason;
        pause.createdBy = createdBy;
        pause.createdAt = now;
        return pause;
    }

    /** 시작 전이거나 시작일 당일에 해제한 정지. 기간은 그대로 두고 취소로 표시한다. */
    public void cancel(Instant now) {
        this.status = PassPauseStatus.CANCELED;
        this.terminatedAt = now;
    }

    /** 진행 중인 정지를 조기 해제한다. 종료일이 해제 전날로 바뀐다. */
    public void endEarly(LocalDate newEndDate, Instant now) {
        this.status = PassPauseStatus.ENDED;
        this.endDate = newEndDate;
        this.terminatedAt = now;
    }

    /** 정지 일수. 취소된 정지는 0이다. */
    public int days() {
        return status == PassPauseStatus.CANCELED ? 0 : (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    /** 등록 당시 정해진 일수. 만료일 연장·복구 계산에 쓴다. */
    public int plannedDays() {
        return (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    public Long getId() {
        return id;
    }

    public Long getPassId() {
        return passId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public PassPauseStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public Instant getTerminatedAt() {
        return terminatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
