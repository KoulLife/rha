package com.rha.auth;

public interface EmailVerificationMailer {

    void sendCode(String email, String code);
}
