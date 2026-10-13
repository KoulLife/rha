package com.rha.auth;

import com.rha.member.MemberResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
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
    private final LoginService loginService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;
    private final RefreshTokenCookieFactory cookies;

    public AuthController(EmailVerificationService emailVerificationService, SignUpService signUpService,
            LoginService loginService, RefreshTokenService refreshTokenService,
            PasswordResetService passwordResetService, RefreshTokenCookieFactory cookies) {
        this.emailVerificationService = emailVerificationService;
        this.signUpService = signUpService;
        this.loginService = loginService;
        this.refreshTokenService = refreshTokenService;
        this.passwordResetService = passwordResetService;
        this.cookies = cookies;
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

    @PostMapping("/password-reset-requests")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.request(request.email());
    }

    @PostMapping("/password-resets")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.reset(request.token(), request.newPassword());
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshTokenCookie(loginService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refreshToken(
            @CookieValue(name = RefreshTokenCookieFactory.NAME, required = false) String refreshToken) {
        return withRefreshTokenCookie(refreshTokenService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedMember me,
            @CookieValue(name = RefreshTokenCookieFactory.NAME, required = false) String refreshToken) {
        refreshTokenService.revoke(me.id(), refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.expire().toString())
                .build();
    }

    private ResponseEntity<TokenResponse> withRefreshTokenCookie(AuthTokens result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookies.issue(result.refreshToken(), result.refreshTokenTtl()).toString())
                .body(result.tokens());
    }
}
