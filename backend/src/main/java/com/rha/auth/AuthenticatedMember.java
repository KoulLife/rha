package com.rha.auth;

import com.rha.member.MemberRole;

/** Access Token에서 꺼낸, 요청을 보낸 회원. 컨트롤러에서 `@AuthenticationPrincipal`로 받는다. */
public record AuthenticatedMember(Long id, MemberRole role) {
}
