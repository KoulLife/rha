package com.rha.reservation;

import com.rha.common.paging.PageInfo;
import java.util.List;

public record ReservationPageResponse(List<ReservationResponse> content, PageInfo pageInfo) {
}
