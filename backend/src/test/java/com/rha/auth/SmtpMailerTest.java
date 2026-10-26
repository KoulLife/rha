package com.rha.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpMailerTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final SmtpMailer mailer = new SmtpMailer(mailSender, "rha.pilates@gmail.com",
            Duration.ofMinutes(5), Duration.ofMinutes(30));

    @Test
    void 인증_코드를_설정한_발신_주소로_보낸다() {
        mailer.sendCode("member@example.com", "123456");

        SimpleMailMessage message = sentMessage();
        assertThat(message.getFrom()).isEqualTo("rha.pilates@gmail.com");
        assertThat(message.getTo()).containsExactly("member@example.com");
        assertThat(message.getSubject()).isEqualTo("[R.HA] 이메일 인증 코드");
        assertThat(message.getText()).contains("123456", "5분");
    }

    @Test
    void 재설정_토큰과_초대_토큰을_본문에_담는다() {
        mailer.sendResetToken("member@example.com", "prt_reset");
        assertThat(sentMessage().getText()).contains("prt_reset", "30분");

        mailer.sendInvitationToken("instructor@example.com", "prt_invite");
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, org.mockito.Mockito.times(2)).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("instructor@example.com");
        assertThat(captor.getValue().getText()).contains("prt_invite");
    }

    private SimpleMailMessage sentMessage() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }
}
