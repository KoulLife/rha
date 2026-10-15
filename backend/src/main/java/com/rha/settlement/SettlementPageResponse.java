package com.rha.settlement;

import com.rha.common.paging.PageInfo;
import java.util.List;

public record SettlementPageResponse(List<SettlementResponse> content, PageInfo pageInfo) {
}
