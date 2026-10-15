package com.rha.instructor;

/** 담당 강사를 고르는 용도의 최소 정보. 연락처 같은 개인정보는 담지 않는다. */
public record InstructorSummary(Long id, String name) {
}
