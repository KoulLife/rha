package com.rha.activity;

import com.rha.member.MemberRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 활동 이력 한 건. 추가만 하고 수정하지 않는다. */
@Entity
@Table(name = "activity_logs")
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String action;

    private Long actorId;

    @Enumerated(EnumType.STRING)
    private MemberRole actorRole;

    private Long subjectMemberId;

    private String targetType;

    private Long targetId;

    @Column(nullable = false)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<ActivityChange> changes;

    protected ActivityLog() {
    }

    static ActivityLog of(ActivityAction action, Long actorId, MemberRole actorRole, Long subjectMemberId,
            String targetType, Long targetId, String summary, List<ActivityChange> changes, Instant now) {
        ActivityLog log = new ActivityLog();
        log.occurredAt = now;
        log.category = action.category().name();
        log.action = action.name();
        log.actorId = actorId;
        log.actorRole = actorRole;
        log.subjectMemberId = subjectMemberId;
        log.targetType = targetType;
        log.targetId = targetId;
        log.summary = summary;
        log.changes = changes;
        return log;
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getCategory() {
        return category;
    }

    public String getAction() {
        return action;
    }

    public Long getActorId() {
        return actorId;
    }

    public MemberRole getActorRole() {
        return actorRole;
    }

    public Long getSubjectMemberId() {
        return subjectMemberId;
    }

    public String getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getSummary() {
        return summary;
    }

    public List<ActivityChange> getChanges() {
        return changes;
    }
}
