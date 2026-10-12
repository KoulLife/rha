package com.rha.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 실제 메일 발송이 연동되기 전까지 사용하는 개발용 구현. 코드를 로그로만 남긴다. */
@Component
class LoggingEmailVerificationMailer implements EmailVerificationMailer {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailVerificationMailer.class);

    @Override
    public void sendCode(String email, String code) {
        log.info("이메일 인증 코드 발송 (개발용 로그): email={}, code={}", email, code);
    }
}
