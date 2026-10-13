package com.rha.auth;

import java.time.Duration;

/** 로그인 또는 재발급으로 발급된 토큰 한 쌍. Access Token은 body, Refresh Token은 쿠키로 내려간다. */
public record AuthTokens(TokenResponse tokens, String refreshToken, Duration refreshTokenTtl) {
}
