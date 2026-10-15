package com.rha.passproduct;

/** {@code affectedPassCount}는 총 횟수·유효 기간 변경이 반영된 발급 이용권 수이다. */
public record PassProductUpdateResponse(PassProductResponse passProduct, int affectedPassCount) {
}
