package com.rha.auth;

public interface PasswordResetMailer {

    void sendResetToken(String email, String token);
}
