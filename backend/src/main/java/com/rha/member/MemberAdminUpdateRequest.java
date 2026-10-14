package com.rha.member;

import jakarta.validation.constraints.Pattern;

/**
 * 관리자의 회원 정보 부분 수정 요청. 본인 정보 수정 필드에 {@code role}이 더해진다.
 * 이메일 등 정의되지 않은 필드는 무시된다.
 */
public class MemberAdminUpdateRequest extends MemberUpdateRequest {

    @Pattern(regexp = "^(ADMIN|INSTRUCTOR|MEMBER)$", message = "ADMIN, INSTRUCTOR, MEMBER 중 하나여야 합니다.")
    private String role;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    @Override
    public boolean hasAnyField() {
        return super.hasAnyField() || role != null;
    }
}
