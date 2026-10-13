package com.rha.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rha.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
@Import(TestMailerConfig.class)
class RequestPasswordResetTest extends IntegrationTestSupport {

    private static final String EMAIL = "user@example.com";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RecordingPasswordResetMailer mailer;

    @BeforeEach
    void setUp() {
        mailer.clear();
    }

    @Test
    void ACTIVE_회원의_이메일이면_204를_응답하고_재설정_토큰을_메일로_발송한다() throws Exception {
        insertMember(EMAIL, "ACTIVE", "hash");

        requestReset(EMAIL)
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(mailer.sent()).hasSize(1);
        assertThat(mailer.sent().get(0).email()).isEqualTo(EMAIL);
        assertThat(mailer.sent().get(0).token()).startsWith("prt_").hasSizeGreaterThan(20);
    }

    @Test
    void 토큰은_해시로_저장되고_회원과_30분의_유효_시간이_설정된다() throws Exception {
        long memberId = insertMember(EMAIL, "ACTIVE", "hash");
        Instant before = Instant.now();

        requestReset(EMAIL).andExpect(status().isNoContent());

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT member_id, token_hash, expires_at, used_at FROM password_reset_tokens");
        String sentToken = mailer.sent().get(0).token();
        assertThat(((Number) row.get("member_id")).longValue()).isEqualTo(memberId);
        assertThat((String) row.get("token_hash")).isNotBlank().isNotEqualTo(sentToken);
        assertThat(row.get("used_at")).isNull();
        Instant expiresAt = ((Timestamp) row.get("expires_at")).toInstant();
        assertThat(expiresAt).isBetween(before.plus(29, ChronoUnit.MINUTES), Instant.now().plus(31, ChronoUnit.MINUTES));
    }

    @Test
    void 비밀번호를_설정하지_않은_초대_계정도_재설정_메일을_받는다() throws Exception {
        insertMember(EMAIL, "ACTIVE", null);

        requestReset(EMAIL).andExpect(status().isNoContent());

        assertThat(mailer.sent()).hasSize(1);
    }

    @Test
    void 같은_회원이_다시_요청하면_이전_토큰은_무효화된다() throws Exception {
        insertMember(EMAIL, "ACTIVE", "hash");
        requestReset(EMAIL).andExpect(status().isNoContent());
        Long firstId = jdbc.queryForObject("SELECT id FROM password_reset_tokens", Long.class);

        requestReset(EMAIL).andExpect(status().isNoContent());

        List<Long> ids = jdbc.queryForList("SELECT id FROM password_reset_tokens", Long.class);
        assertThat(ids).hasSize(1).doesNotContain(firstId);
        assertThat(mailer.sent()).hasSize(2);
        assertThat(mailer.sent().get(1).token()).isNotEqualTo(mailer.sent().get(0).token());
    }

    @Test
    void 다른_회원의_토큰은_무효화하지_않는다() throws Exception {
        insertMember(EMAIL, "ACTIVE", "hash");
        insertMember("other@example.com", "ACTIVE", "hash");
        requestReset("other@example.com").andExpect(status().isNoContent());

        requestReset(EMAIL).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM password_reset_tokens", Integer.class)).isEqualTo(2);
    }

    @Test
    void 가입되지_않은_이메일이어도_204를_응답하되_메일은_보내지_않는다() throws Exception {
        requestReset("nobody@example.com")
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(mailer.sent()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM password_reset_tokens", Integer.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"INACTIVE", "WITHDRAWN", "RESIGNED"})
    void ACTIVE가_아닌_계정이어도_204를_응답하되_메일은_보내지_않는다(String memberStatus) throws Exception {
        insertMember(EMAIL, memberStatus, "hash");

        requestReset(EMAIL).andExpect(status().isNoContent());

        assertThat(mailer.sent()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM password_reset_tokens", Integer.class)).isZero();
    }

    @Test
    void 이메일_형식이_올바르지_않으면_400_INVALID_INPUT을_응답한다() throws Exception {
        requestReset("not-an-email")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.invalidParams[0].field").value("email"));

        assertThat(mailer.sent()).isEmpty();
    }

    @Test
    void 이메일이_없으면_400_INVALID_INPUT을_응답한다() throws Exception {
        mockMvc.perform(post("/auth/password-reset-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.invalidParams[0].field").value("email"));
    }

    private ResultActions requestReset(String email) throws Exception {
        return mockMvc.perform(post("/auth/password-reset-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private long insertMember(String email, String memberStatus, String passwordHash) {
        return jdbc.queryForObject("""
                INSERT INTO members (email, password_hash, name, phone_number, birth_date, role, status,
                                     created_at, updated_at)
                VALUES (?, ?, '홍길동', '01012345678', DATE '1990-01-01', 'MEMBER', ?, now(), now())
                RETURNING id
                """, Long.class, email, passwordHash, memberStatus);
    }
}
