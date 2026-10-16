package com.rha.activity;

import com.rha.common.error.ApiException;
import com.rha.common.error.ApiException.Detail;
import com.rha.common.error.ErrorCode;
import com.rha.common.paging.PageInfo;
import com.rha.member.AdminGuard;
import com.rha.member.Member;
import com.rha.member.MemberRepository;
import com.rha.member.MemberRole;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 활동 이력 조회(관리자 전용). 이력은 {@link ActivityLogRecorder}가 업무 변경과 같은 트랜잭션에서 남긴다. */
@Service
public class ActivityLogService {

    private static final int MAX_SIZE = 100;

    private final ActivityLogRepository activityLogRepository;
    private final MemberRepository memberRepository;
    private final AdminGuard adminGuard;

    public ActivityLogService(ActivityLogRepository activityLogRepository, MemberRepository memberRepository,
            AdminGuard adminGuard) {
        this.activityLogRepository = activityLogRepository;
        this.memberRepository = memberRepository;
        this.adminGuard = adminGuard;
    }

    /** 필터 값. 형식 오류를 관리자 확인(403) 뒤에 알리기 위해 컨트롤러는 page, size를 포함해 문자열 그대로 넘긴다. */
    public record Filter(String category, String action, String actorId, String actorRole, String subjectMemberId,
            String targetType, String targetId, String from, String to, String order) {
    }

    @Transactional(readOnly = true)
    public ActivityLogPageResponse getActivityLogs(Long callerId, Filter filter, String pageValue,
            String sizeValue) {
        adminGuard.requireAdmin(callerId);

        List<Detail> invalid = new ArrayList<>();
        Integer parsedPage = parseInt("page", pageValue, invalid);
        Integer parsedSize = parseInt("size", sizeValue, invalid);
        if (parsedPage != null && parsedPage < 1) {
            invalid.add(new Detail("page", "1 이상이어야 합니다."));
        }
        if (parsedSize != null && (parsedSize < 1 || parsedSize > MAX_SIZE)) {
            invalid.add(new Detail("size", "1 이상 " + MAX_SIZE + " 이하여야 합니다."));
        }
        Long actorId = parseLong("actorId", filter.actorId(), invalid);
        Long subjectMemberId = parseLong("subjectMemberId", filter.subjectMemberId(), invalid);
        Long targetId = parseLong("targetId", filter.targetId(), invalid);
        Instant from = parseInstant("from", filter.from(), invalid);
        Instant to = parseInstant("to", filter.to(), invalid);
        MemberRole actorRole = parseRole(filter.actorRole(), invalid);
        boolean ascending = parseAscending(filter.order(), invalid);
        if (from != null && to != null && !from.isBefore(to)) {
            invalid.add(new Detail("from", "from은 to보다 이전이어야 합니다."));
        }
        if (targetId != null && isBlank(filter.targetType())) {
            invalid.add(new Detail("targetType", "targetId와 함께 지정해야 합니다."));
        }
        if (!invalid.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_INPUT, invalid);
        }

        Specification<ActivityLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!isBlank(filter.category())) {
                predicates.add(cb.equal(root.get("category"), filter.category().strip()));
            }
            if (!isBlank(filter.action())) {
                predicates.add(cb.equal(root.get("action"), filter.action().strip()));
            }
            if (actorId != null) {
                predicates.add(cb.equal(root.get("actorId"), actorId));
            }
            if (actorRole != null) {
                predicates.add(cb.equal(root.get("actorRole"), actorRole));
            }
            if (subjectMemberId != null) {
                predicates.add(cb.equal(root.get("subjectMemberId"), subjectMemberId));
            }
            if (!isBlank(filter.targetType())) {
                predicates.add(cb.equal(root.get("targetType"), filter.targetType().strip()));
            }
            if (targetId != null) {
                predicates.add(cb.equal(root.get("targetId"), targetId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("occurredAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.<Instant>get("occurredAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Sort sort = ascending
                ? Sort.by(Sort.Order.asc("occurredAt"), Sort.Order.asc("id"))
                : Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));
        Page<ActivityLog> result = activityLogRepository.findAll(spec,
                PageRequest.of(parsedPage - 1, parsedSize, sort));
        return new ActivityLogPageResponse(toResponses(result.getContent()), PageInfo.of(result));
    }

    @Transactional(readOnly = true)
    public ActivityLogActionCatalogResponse getActivityLogActions(Long callerId) {
        adminGuard.requireAdmin(callerId);
        List<ActivityLogActionCatalogResponse.Category> categories = Arrays.stream(ActivityCategory.values())
                .map(category -> new ActivityLogActionCatalogResponse.Category(category.name(),
                        category.displayName(),
                        Arrays.stream(ActivityAction.values())
                                .filter(action -> action.category() == category)
                                .map(action -> new ActivityLogActionCatalogResponse.Action(action.name(),
                                        action.displayName()))
                                .toList()))
                .toList();
        return new ActivityLogActionCatalogResponse(categories);
    }

    private List<ActivityLogResponse> toResponses(List<ActivityLog> logs) {
        Set<Long> memberIds = new HashSet<>();
        for (ActivityLog log : logs) {
            if (log.getActorId() != null) {
                memberIds.add(log.getActorId());
            }
            if (log.getSubjectMemberId() != null) {
                memberIds.add(log.getSubjectMemberId());
            }
        }
        Map<Long, Member> members = memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));
        return logs.stream().map(log -> {
            Member actor = log.getActorId() == null ? null : members.get(log.getActorId());
            Member subject = log.getSubjectMemberId() == null ? null : members.get(log.getSubjectMemberId());
            ActivityAction action = actionOf(log.getAction());
            return new ActivityLogResponse(log.getId(), log.getOccurredAt(), log.getCategory(), log.getAction(),
                    action == null ? log.getAction() : action.displayName(),
                    actor == null ? null
                            : new ActivityLogResponse.Actor(actor.getId(), actor.getName(), log.getActorRole()),
                    subject == null ? null : new ActivityLogResponse.MemberSummary(subject.getId(), subject.getName()),
                    log.getTargetType(), log.getTargetId(), log.getSummary(), log.getChanges());
        }).toList();
    }

    private static ActivityAction actionOf(String code) {
        try {
            return ActivityAction.valueOf(code);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static Integer parseInt(String field, String value, List<Detail> invalid) {
        try {
            return Integer.valueOf(value.strip());
        } catch (NumberFormatException e) {
            invalid.add(new Detail(field, "값의 형식이 올바르지 않습니다."));
            return null;
        }
    }

    private static Long parseLong(String field, String value, List<Detail> invalid) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.strip());
        } catch (NumberFormatException e) {
            invalid.add(new Detail(field, "값의 형식이 올바르지 않습니다."));
            return null;
        }
    }

    private static Instant parseInstant(String field, String value, List<Detail> invalid) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Instant.parse(value.strip());
        } catch (DateTimeParseException e) {
            invalid.add(new Detail(field, "값의 형식이 올바르지 않습니다."));
            return null;
        }
    }

    private static MemberRole parseRole(String value, List<Detail> invalid) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return MemberRole.valueOf(value.strip());
        } catch (IllegalArgumentException e) {
            invalid.add(new Detail("actorRole", "값의 형식이 올바르지 않습니다."));
            return null;
        }
    }

    private static boolean parseAscending(String value, List<Detail> invalid) {
        if (isBlank(value) || "DESC".equals(value.strip())) {
            return false;
        }
        if ("ASC".equals(value.strip())) {
            return true;
        }
        invalid.add(new Detail("order", "ASC 또는 DESC여야 합니다."));
        return false;
    }
}
