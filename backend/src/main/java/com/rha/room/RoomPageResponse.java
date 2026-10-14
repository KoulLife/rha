package com.rha.room;

import com.rha.common.paging.PageInfo;
import java.util.List;

public record RoomPageResponse(List<RoomResponse> content, PageInfo pageInfo) {
}
