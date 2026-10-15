package com.rha.absence;

import com.rha.common.paging.PageInfo;
import java.util.List;

public record AbsencePageResponse(List<AbsenceResponse> content, PageInfo pageInfo) {
}
