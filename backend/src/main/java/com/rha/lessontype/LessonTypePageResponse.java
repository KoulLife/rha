package com.rha.lessontype;

import com.rha.common.paging.PageInfo;
import java.util.List;

public record LessonTypePageResponse(List<LessonTypeResponse> content, PageInfo pageInfo) {
}
