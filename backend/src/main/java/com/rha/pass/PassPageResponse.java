package com.rha.pass;

import com.rha.common.paging.PageInfo;
import java.util.List;

public record PassPageResponse(List<PassResponse> content, PageInfo pageInfo) {
}
