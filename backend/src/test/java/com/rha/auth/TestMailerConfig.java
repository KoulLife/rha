package com.rha.auth;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
class TestMailerConfig {

    @Bean
    @Primary
    RecordingEmailVerificationMailer recordingMailer() {
        return new RecordingEmailVerificationMailer();
    }

    @Bean
    @Primary
    RecordingPasswordResetMailer recordingPasswordResetMailer() {
        return new RecordingPasswordResetMailer();
    }
}
