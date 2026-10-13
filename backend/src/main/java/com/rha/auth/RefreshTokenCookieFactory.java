package com.rha.auth;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Refresh Token 쿠키를 만든다. 쿠키는 인증 API 경로(`/auth`)에만 전송된다. */
@Component
public class RefreshTokenCookieFactory {

    public static final String NAME = "refreshToken";

    private final String path;

    public RefreshTokenCookieFactory(@Value("${server.servlet.context-path:}") String contextPath) {
        this.path = contextPath + "/auth";
    }

    public ResponseCookie issue(String refreshToken, Duration maxAge) {
        return ResponseCookie.from(NAME, refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    /** 브라우저의 Refresh Token 쿠키를 삭제하는 쿠키. */
    public ResponseCookie expire() {
        return issue("", Duration.ZERO);
    }
}
