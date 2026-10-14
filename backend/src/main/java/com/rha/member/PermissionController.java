package com.rha.member;

import com.rha.auth.AuthenticatedMember;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping("/permissions")
    public PermissionListResponse getPermissions(@AuthenticationPrincipal AuthenticatedMember me) {
        return permissionService.getPermissions(me.id());
    }

    @GetMapping("/members/{memberId}/permissions")
    public MemberPermissionsResponse getMemberPermissions(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long memberId) {
        return permissionService.getMemberPermissions(me.id(), memberId);
    }

    /** 본문 검증은 관리자 여부를 확인한 뒤에 하므로 `@Valid`를 붙이지 않는다. */
    @PutMapping("/members/{memberId}/permissions")
    public MemberPermissionsResponse updateMemberPermissions(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long memberId, @RequestBody MemberPermissionsRequest request) {
        return permissionService.updateMemberPermissions(me.id(), memberId, request);
    }
}
