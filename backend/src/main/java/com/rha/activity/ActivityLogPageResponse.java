package com.rha.activity;

import com.rha.common.paging.PageInfo;
import java.util.List;

public record ActivityLogPageResponse(List<ActivityLogResponse> content, PageInfo pageInfo) {
}
