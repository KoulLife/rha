package com.rha.pass;

import com.rha.common.paging.PageInfo;
import java.util.List;

public record PassPausePageResponse(List<PassPauseResponse> content, PageInfo pageInfo) {
}
