package com.rha.lesson;

import com.rha.common.paging.PageInfo;
import java.util.List;

public record LessonPageResponse(List<LessonResponse> content, PageInfo pageInfo) {
}
