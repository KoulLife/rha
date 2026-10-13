package com.rha.member;

import com.rha.auth.AuthenticatedMember;
import com.rha.auth.PasswordChangeRequest;
import com.rha.auth.PasswordChangeService;
import com.rha.auth.RefreshTokenCookieFactory;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/members")
public class MemberController {

    private final MemberService memberService;
    private final PasswordChangeService passwordChangeService;
    private final RefreshTokenCookieFactory cookies;

    public MemberController(MemberService memberService, PasswordChangeService passwordChangeService,
            RefreshTokenCookieFactory cookies) {
        this.memberService = memberService;
        this.passwordChangeService = passwordChangeService;
        this.cookies = cookies;
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

    @PutMapping("/me/password")
    public ResponseEntity<Void> changeMyPassword(@AuthenticationPrincipal AuthenticatedMember me,
            @Valid @RequestBody PasswordChangeRequest request) {
        passwordChangeService.change(me.id(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.expire().toString())
                .build();
    }
}
