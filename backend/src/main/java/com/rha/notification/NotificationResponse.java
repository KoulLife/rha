package com.rha.notification;

import java.time.Instant;

public record NotificationResponse(Long id, String type, String title, String message, String relatedType,
        Long relatedId, boolean read, Instant readAt, Instant createdAt) {

    static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getTitle(),
                notification.getMessage(), notification.getRelatedType(), notification.getRelatedId(),
                notification.getReadAt() != null, notification.getReadAt(), notification.getCreatedAt());
    }
}
