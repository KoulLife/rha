package com.rha.activity;

import com.rha.member.MemberRole;
import java.time.Instant;
import java.util.List;

public record ActivityLogResponse(Long id, Instant occurredAt, String category, String action, String actionName,
        Actor actor, MemberSummary subjectMember, String targetType, Long targetId, String summary,
        List<ActivityChange> changes) {

    public record Actor(Long id, String name, MemberRole role) {
    }

    public record MemberSummary(Long id, String name) {
    }
}
