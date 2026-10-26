package com.rha.auth;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP로 실제 메일을 보내는 운영용 구현. 접속 정보는 spring.mail.*(MAIL_* 환경변수)로만 정하므로
 * Gmail SMTP에서 SES SMTP로 옮길 때 코드는 바꾸지 않는다.
 * 발송에 실패하면 예외가 호출한 쪽의 트랜잭션을 롤백한다 (초대 메일 실패 시 계정도 생성되지 않는다).
 */
@Component
@Profile("!local")
class SmtpMailer implements EmailVerificationMailer, PasswordResetMailer {

    private final JavaMailSender mailSender;
    private final String from;
    private final Duration codeTtl;
    private final Duration resetTokenTtl;

    SmtpMailer(JavaMailSender mailSender,
            @Value("${rha.mail.from}") String from,
            @Value("${rha.auth.email-verification.code-ttl:5m}") Duration codeTtl,
            @Value("${rha.auth.password-reset.token-ttl:30m}") Duration resetTokenTtl) {
        this.mailSender = mailSender;
        this.from = from;
        this.codeTtl = codeTtl;
        this.resetTokenTtl = resetTokenTtl;
    }

    @Override
    public void sendCode(String email, String code) {
        send(email, "[R.HA] 이메일 인증 코드", """
                R.HA 회원가입 이메일 인증 코드입니다.

                인증 코드: %s

                %d분 안에 회원가입 화면에 입력해 주세요.
                본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                """.formatted(code, codeTtl.toMinutes()));
    }

    @Override
    public void sendResetToken(String email, String token) {
        send(email, "[R.HA] 비밀번호 재설정 안내", """
                비밀번호 재설정 요청을 받았습니다.

                재설정 토큰: %s

                %d분 안에 비밀번호 재설정 화면에 입력해 주세요.
                본인이 요청하지 않았다면 이 메일을 무시해 주세요. 비밀번호는 바뀌지 않습니다.
                """.formatted(token, resetTokenTtl.toMinutes()));
    }

    @Override
    public void sendInvitationToken(String email, String token) {
        send(email, "[R.HA] 계정 등록 안내", """
                R.HA 관리자가 계정을 등록했습니다.

                비밀번호 설정 토큰: %s

                로그인 화면의 비밀번호 재설정에서 %d분 안에 위 토큰을 입력해 비밀번호를 설정해 주세요.
                시간이 지났다면 같은 화면에서 이메일을 입력해 새 토큰을 받을 수 있습니다.
                """.formatted(token, resetTokenTtl.toMinutes()));
    }

    private void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}
