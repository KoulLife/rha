package com.rha.member;

import com.rha.auth.AuthenticatedMember;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
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
}
