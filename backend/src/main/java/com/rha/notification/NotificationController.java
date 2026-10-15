package com.rha.notification;

import com.rha.auth.AuthenticatedMember;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public NotificationPageResponse getMyNotifications(@AuthenticationPrincipal AuthenticatedMember me,
            @RequestParam(required = false) Boolean unread,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return notificationService.getMyNotifications(me.id(), unread, page, size);
    }

    @GetMapping("/unread-count")
    public UnreadNotificationCountResponse getMyUnreadNotificationCount(
            @AuthenticationPrincipal AuthenticatedMember me) {
        return notificationService.getMyUnreadCount(me.id());
    }

    @PutMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAllNotifications(@AuthenticationPrincipal AuthenticatedMember me) {
        notificationService.readAll(me.id());
    }

    @PutMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readNotification(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long notificationId) {
        notificationService.read(me.id(), notificationId);
    }
}
