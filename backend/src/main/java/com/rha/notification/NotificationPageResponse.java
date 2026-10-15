package com.rha.notification;

import com.rha.common.paging.PageInfo;
import java.util.List;

public record NotificationPageResponse(List<NotificationResponse> content, PageInfo pageInfo) {
}
