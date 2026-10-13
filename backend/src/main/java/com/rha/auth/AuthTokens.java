package com.rha.auth;

import java.time.Duration;

public record LoginResult(TokenResponse tokens, String refreshToken, Duration refreshTokenTtl) {
}
