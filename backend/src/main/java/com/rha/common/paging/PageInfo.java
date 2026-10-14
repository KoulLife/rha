package com.rha.common.paging;

import org.springframework.data.domain.Page;

/** 목록 응답의 페이지 정보. 페이지 번호는 1부터 시작한다. */
public record PageInfo(int page, int size, long totalElements, int totalPages) {

    public static PageInfo of(Page<?> page) {
        return new PageInfo(page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
