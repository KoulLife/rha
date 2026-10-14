package com.rha.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 실제 메일 발송이 연동되기 전까지 사용하는 개발용 구현. 토큰을 로그로만 남긴다. */
@Component
class LoggingPasswordResetMailer implements PasswordResetMailer {

    private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetMailer.class);

    @Override
    public void sendResetToken(String email, String token) {
        log.info("비밀번호 재설정 토큰 발송 (개발용 로그): email={}, token={}", email, token);
    }

    @Override
    public void sendInvitationToken(String email, String token) {
        log.info("계정 초대 토큰 발송 (개발용 로그): email={}, token={}", email, token);
    }
}
