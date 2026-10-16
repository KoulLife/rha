package com.rha.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rha.IntegrationTestSupport;
import com.rha.RhaApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** local 이 아닌 환경에서는 개발용 기본값에 기대지 못한다. */
class SecurityConfigurationTest extends IntegrationTestSupport {

    private SpringApplicationBuilder app(String profile, String... extra) {
        String[] base = {
                "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "spring.datasource.username=" + POSTGRES.getUsername(),
                "spring.datasource.password=" + POSTGRES.getPassword(),
                "server.port=0",
                "rha.lesson.auto-publish.enabled=false",
                "rha.pass.status-refresh.enabled=false"};
        String[] all = new String[base.length + extra.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(extra, 0, all, base.length, extra.length);
        return new SpringApplicationBuilder(RhaApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles(profile)
                .properties(all);
    }

    @Test
    void 운영_프로필에서_JWT_키가_없으면_기동에_실패한다() {
        assertThatThrownBy(() -> app("prod").run().close())
                .hasStackTraceContaining("JWT_SECRET");
    }

    @Test
    void 운영_프로필에서는_로그로만_남기는_메일러가_없어_기동에_실패한다() {
        assertThatThrownBy(() -> app("prod", "JWT_SECRET=prod-secret-prod-secret-prod-secret-123").run().close())
                .hasStackTraceContaining("Mailer");
    }

    @Test
    void 프로필을_지정하지_않으면_local로_기동하고_로그_메일러를_쓴다() {
        try (ConfigurableApplicationContext context = app("local").run()) {
            assertThat(context.getBeansOfType(EmailVerificationMailer.class)).hasSize(1);
            assertThat(context.getBeansOfType(PasswordResetMailer.class)).hasSize(1);
        }
    }
}
