package com.rha.auth;

import com.rha.member.MemberResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    private final EmailVerificationService emailVerificationService;
    private final SignUpService signUpService;
    private final LoginService loginService;
    private final String refreshCookiePath;

    public AuthController(EmailVerificationService emailVerificationService, SignUpService signUpService,
            LoginService loginService, @Value("${server.servlet.context-path:}") String contextPath) {
        this.emailVerificationService = emailVerificationService;
        this.signUpService = signUpService;
        this.loginService = loginService;
        this.refreshCookiePath = contextPath + "/auth";
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

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = loginService.login(request.email(), request.password());

        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, result.refreshToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(refreshCookiePath)
                .maxAge(result.refreshTokenTtl())
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.tokens());
    }
}
