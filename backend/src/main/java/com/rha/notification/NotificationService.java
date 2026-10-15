package com.rha.notification;

import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import com.rha.common.paging.PageInfo;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그인한 사용자 본인의 알림 조회와 읽음 처리. 알림 생성은 {@link NotificationPublisher}가 맡는다. */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /** 최신순(같으면 id 내림차순). {@code unread}가 true이면 읽지 않은 알림만 조회한다. */
    @Transactional(readOnly = true)
    public NotificationPageResponse getMyNotifications(Long callerId, Boolean unread, int page, int size) {
        PageRequest pageable = PageRequest.of(page - 1, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Notification> result = Boolean.TRUE.equals(unread)
                ? notificationRepository.findByRecipientIdAndReadAtIsNull(callerId, pageable)
                : notificationRepository.findByRecipientId(callerId, pageable);
        List<NotificationResponse> content = result.getContent().stream().map(NotificationResponse::from).toList();
        return new NotificationPageResponse(content, PageInfo.of(result));
    }

    @Transactional(readOnly = true)
    public UnreadNotificationCountResponse getMyUnreadCount(Long callerId) {
        return new UnreadNotificationCountResponse(notificationRepository.countByRecipientIdAndReadAtIsNull(callerId));
    }

    @Transactional
    public void readAll(Long callerId) {
        notificationRepository.markAllRead(callerId, Instant.now());
    }

    /** 다른 사용자의 알림이거나 없는 알림이면 404이다. */
    @Transactional
    public void read(Long callerId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, callerId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        notification.markRead(Instant.now());
    }
}
