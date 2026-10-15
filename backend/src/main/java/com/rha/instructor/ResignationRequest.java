package com.rha.instructor;

import jakarta.validation.constraints.NotNull;

/** 날짜는 문자열로 받아 서비스에서 해석한다. 관리자 확인(403) 전에 형식 오류(400)가 나지 않게 하기 위함이다. */
public record ResignationRequest(@NotNull String resignationDate) {
}
