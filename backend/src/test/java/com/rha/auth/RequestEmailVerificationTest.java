package com.rha.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rha.IntegrationTestSupport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
@Import(RequestEmailVerificationTest.MailerConfig.class)
class RequestEmailVerificationTest extends IntegrationTestSupport {

    @TestConfiguration
    static class MailerConfig {

        @Bean
        @Primary
        RecordingEmailVerificationMailer recordingMailer() {
            return new RecordingEmailVerificationMailer();
        }
    }

    private static final String EMAIL = "user@example.com";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RecordingEmailVerificationMailer mailer;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM email_verifications");
        jdbc.update("DELETE FROM members");
        mailer.clear();
    }

    @Test
    void 가입되지_않은_이메일이면_204를_응답하고_인증_코드를_메일로_발송한다() throws Exception {
        requestVerification(EMAIL).andExpect(status().isNoContent());

        assertThat(mailer.sent()).hasSize(1);
        assertThat(mailer.sent().get(0).email()).isEqualTo(EMAIL);
        assertThat(mailer.sent().get(0).code()).matches("\\d{6}");
    }

    @Test
    void 인증_코드는_해시로_저장되고_유효_시간이_설정된다() throws Exception {
        Instant before = Instant.now();

        requestVerification(EMAIL).andExpect(status().isNoContent());

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT code_hash, code_expires_at, verified_at FROM email_verifications WHERE email = ?", EMAIL);
        String sentCode = mailer.sent().get(0).code();
        assertThat((String) row.get("code_hash")).isNotBlank().isNotEqualTo(sentCode);
        assertThat(row.get("verified_at")).isNull();
        Instant expiresAt = ((java.sql.Timestamp) row.get("code_expires_at")).toInstant();
        assertThat(expiresAt).isBetween(before.plus(4, ChronoUnit.MINUTES), Instant.now().plus(6, ChronoUnit.MINUTES));
    }

    @Test
    void 같은_이메일로_재요청하면_이전_코드는_무효화된다() throws Exception {
        requestVerification(EMAIL).andExpect(status().isNoContent());
        Long firstId = jdbc.queryForObject("SELECT id FROM email_verifications WHERE email = ?", Long.class, EMAIL);

        requestVerification(EMAIL).andExpect(status().isNoContent());

        List<Long> ids = jdbc.queryForList("SELECT id FROM email_verifications WHERE email = ?", Long.class, EMAIL);
        assertThat(ids).hasSize(1).doesNotContain(firstId);
        assertThat(mailer.sent()).hasSize(2);
    }

    @Test
    void 이미_가입된_이메일이면_409_EMAIL_ALREADY_EXISTS를_응답한다() throws Exception {
        insertMember(EMAIL, "ACTIVE");

        requestVerification(EMAIL)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));

        assertThat(mailer.sent()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_verifications", Integer.class)).isZero();
    }

    @Test
    void 탈퇴한_계정의_이메일도_409_EMAIL_ALREADY_EXISTS를_응답한다() throws Exception {
        insertMember(EMAIL, "WITHDRAWN");

        requestVerification(EMAIL)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void 이메일_형식이_올바르지_않으면_400_INVALID_INPUT을_응답한다() throws Exception {
        requestVerification("not-an-email")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.invalidParams[0].field").value("email"));

        assertThat(mailer.sent()).isEmpty();
    }

    private ResultActions requestVerification(String email) throws Exception {
        return mockMvc.perform(post("/auth/email-verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private void insertMember(String email, String status) {
        jdbc.update("""
                INSERT INTO members (email, password_hash, name, phone_number, birth_date, role, status,
                                     created_at, updated_at)
                VALUES (?, 'hash', '홍길동', '01012345678', DATE '1990-01-01', 'MEMBER', ?, now(), now())
                """, email, status);
    }
}
