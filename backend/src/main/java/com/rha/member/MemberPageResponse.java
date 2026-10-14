package com.rha.member;

import com.rha.common.paging.PageInfo;
import java.util.List;

public record MemberPageResponse(List<MemberResponse> content, PageInfo pageInfo) {
}
