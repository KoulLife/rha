package com.rha.auth;

public record EmailVerificationToken(String emailVerificationToken, long expiresIn) {
}
