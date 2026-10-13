package com.rha.auth;

/** Access Token 응답 body. Refresh Token은 body가 아니라 HttpOnly 쿠키로 내려간다. */
public record TokenResponse(String accessToken, long expiresIn) {
}
