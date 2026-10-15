package com.rha.instructor;

import com.rha.common.paging.PageInfo;
import java.util.List;

public record InstructorPageResponse(List<InstructorSummary> content, PageInfo pageInfo) {
}
