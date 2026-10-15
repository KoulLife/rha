package com.rha.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 인앱 알림. {@code readAt}이 null이면 읽지 않은 알림이다. */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long recipientId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String message;

    private String relatedType;

    private Long relatedId;

    private Instant readAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public static Notification create(Long recipientId, String type, String title, String message,
            String relatedType, Long relatedId, Instant now) {
        Notification notification = new Notification();
        notification.recipientId = recipientId;
        notification.type = type;
        notification.title = title;
        notification.message = message;
        notification.relatedType = relatedType;
        notification.relatedId = relatedId;
        notification.createdAt = now;
        return notification;
    }

    /** 이미 읽은 알림은 읽은 시각을 바꾸지 않는다. */
    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getRelatedType() {
        return relatedType;
    }

    public Long getRelatedId() {
        return relatedId;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
