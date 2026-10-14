package com.rha.member;

import com.rha.auth.AccountWithdrawalService;
import com.rha.auth.AuthenticatedMember;
import com.rha.auth.PasswordChangeRequest;
import com.rha.auth.PasswordChangeService;
import com.rha.auth.RefreshTokenCookieFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/members")
public class MemberController {

    private final MemberService memberService;
    private final MemberAdminService memberAdminService;
    private final PasswordChangeService passwordChangeService;
    private final AccountWithdrawalService accountWithdrawalService;
    private final RefreshTokenCookieFactory cookies;

    public MemberController(MemberService memberService, MemberAdminService memberAdminService,
            PasswordChangeService passwordChangeService, AccountWithdrawalService accountWithdrawalService,
            RefreshTokenCookieFactory cookies) {
        this.memberService = memberService;
        this.memberAdminService = memberAdminService;
        this.passwordChangeService = passwordChangeService;
        this.accountWithdrawalService = accountWithdrawalService;
        this.cookies = cookies;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse createMember(@AuthenticationPrincipal AuthenticatedMember me,
            @Valid @RequestBody MemberCreateRequest request) {
        return MemberResponse.from(memberAdminService.createMember(me.id(), request));
    }

    @GetMapping
    public MemberPageResponse getMembers(@AuthenticationPrincipal AuthenticatedMember me,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) MemberRole role,
            @RequestParam(required = false) MemberStatus status,
            @RequestParam(required = false) @Size(max = 100) String keyword) {
        return memberAdminService.getMembers(me.id(), role, status, keyword, page, size);
    }

    @GetMapping("/{memberId}")
    public MemberResponse getMember(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long memberId) {
        return memberAdminService.getMember(me.id(), memberId);
    }

    /** 본문 검증은 관리자 여부를 확인한 뒤에 하므로 `@Valid`를 붙이지 않는다. */
    @PatchMapping("/{memberId}")
    public MemberResponse updateMember(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long memberId, @RequestBody MemberAdminUpdateRequest request) {
        return memberAdminService.updateMember(me.id(), memberId, request);
    }

    @PutMapping("/{memberId}/status")
    public MemberResponse changeMemberStatus(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long memberId, @RequestBody MemberStatusChangeRequest request) {
        return memberAdminService.changeMemberStatus(me.id(), memberId, request);
    }

    @GetMapping("/me")
    public MemberResponse getMyProfile(@AuthenticationPrincipal AuthenticatedMember me) {
        return memberService.getMyProfile(me.id());
    }

    @PatchMapping("/me")
    public MemberResponse updateMyProfile(@AuthenticationPrincipal AuthenticatedMember me,
            @Valid @RequestBody MemberUpdateRequest request) {
        return memberService.updateMyProfile(me.id(), request);
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> withdrawMyAccount(@AuthenticationPrincipal AuthenticatedMember me) {
        accountWithdrawalService.withdraw(me.id());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.expire().toString())
                .build();
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changeMyPassword(@AuthenticationPrincipal AuthenticatedMember me,
            @Valid @RequestBody PasswordChangeRequest request) {
        passwordChangeService.change(me.id(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.expire().toString())
                .build();
    }
}
