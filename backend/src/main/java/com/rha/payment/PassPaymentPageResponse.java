package com.rha.payment;

import com.rha.common.paging.PageInfo;
import java.util.List;

public record PassPaymentPageResponse(List<PassPaymentResponse> content, PageInfo pageInfo) {
}
