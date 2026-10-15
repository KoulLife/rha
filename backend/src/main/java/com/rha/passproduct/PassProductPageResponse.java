package com.rha.passproduct;

import com.rha.common.paging.PageInfo;
import java.util.List;

public record PassProductPageResponse(List<PassProductResponse> content, PageInfo pageInfo) {
}
