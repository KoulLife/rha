package com.rha.activity;

import com.rha.auth.AuthenticatedMember;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 필터 형식 오류는 관리자 여부를 확인한 뒤(403 다음)에 알려야 하므로 값을 문자열로 받아 서비스에서 검증한다. */
@RestController
public class ActivityLogController {

    private final ActivityLogService activityLogService;

    public ActivityLogController(ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    @GetMapping("/activity-logs")
    public ActivityLogPageResponse getActivityLogs(@AuthenticationPrincipal AuthenticatedMember me,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String actorRole,
            @RequestParam(required = false) String subjectMemberId,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String order,
            @RequestParam(defaultValue = "1") String page,
            @RequestParam(defaultValue = "20") String size) {
        return activityLogService.getActivityLogs(me.id(), new ActivityLogService.Filter(category, action, actorId,
                actorRole, subjectMemberId, targetType, targetId, from, to, order), page, size);
    }

    @GetMapping("/activity-log-actions")
    public ActivityLogActionCatalogResponse getActivityLogActions(@AuthenticationPrincipal AuthenticatedMember me) {
        return activityLogService.getActivityLogActions(me.id());
    }
}
