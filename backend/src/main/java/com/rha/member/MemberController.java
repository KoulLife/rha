package com.rha.member;

import com.rha.auth.AccountWithdrawalService;
import com.rha.auth.AuthenticatedMember;
import com.rha.auth.PasswordChangeRequest;
import com.rha.auth.PasswordChangeService;
import com.rha.auth.RefreshTokenCookieFactory;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @GetMapping("/me")
    public MemberResponse getMyProfile(@AuthenticationPrincipal AuthenticatedMember me) {
        return MemberResponse.from(memberService.getMyProfile(me.id()));
    }

    @PatchMapping("/me")
    public MemberResponse updateMyProfile(@AuthenticationPrincipal AuthenticatedMember me,
            @Valid @RequestBody MemberUpdateRequest request) {
        return MemberResponse.from(memberService.updateMyProfile(me.id(), request));
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
