package com.rha.auth;

import com.rha.member.MemberResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final EmailVerificationService emailVerificationService;
    private final SignUpService signUpService;

    public AuthController(EmailVerificationService emailVerificationService, SignUpService signUpService) {
        this.emailVerificationService = emailVerificationService;
        this.signUpService = signUpService;
    }

    @PostMapping("/email-verifications")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestEmailVerification(@Valid @RequestBody EmailVerificationRequest request) {
        emailVerificationService.requestVerification(request.email());
    }

    @PostMapping("/email-verifications/confirm")
    public EmailVerificationToken confirmEmailVerification(
            @Valid @RequestBody EmailVerificationConfirmRequest request) {
        return emailVerificationService.confirm(request.email(), request.code());
    }

    @PostMapping("/sign-up")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse signUp(@Valid @RequestBody SignUpRequest request) {
        return MemberResponse.from(signUpService.signUp(request));
    }
}
