package com.rha.pass;

/**
 * 전달한 필드만 수정한다. 잔여 횟수의 범위는 총 횟수를 알아야 하므로 서비스에서 확인한다.
 * 날짜는 문자열로 받아 서비스에서 해석한다.
 */
public record PassUpdateRequest(String endDate, Integer remainingCount, Long instructorId) {

    public boolean hasAnyField() {
        return endDate != null || remainingCount != null || instructorId != null;
    }
}
