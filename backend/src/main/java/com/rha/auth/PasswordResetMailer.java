package com.rha.auth;

public interface PasswordResetMailer {

    void sendResetToken(String email, String token);

    /** 관리자가 등록한 계정에 최초 비밀번호 설정 링크(초대 메일)를 보낸다. */
    void sendInvitationToken(String email, String token);
}
