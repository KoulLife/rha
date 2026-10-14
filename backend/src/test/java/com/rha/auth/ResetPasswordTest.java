package com.rha.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rha.IntegrationTestSupport;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class ResetPasswordTest extends IntegrationTestSupport {

    private static final String EMAIL = "user@example.com";
    private static final String TOKEN = "prt_valid-token-for-test";
    private static final String NEW_PASSWORD = "newPassword123!";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void 유효한_토큰이면_204를_응답하고_비밀번호를_변경한다() throws Exception {
        long memberId = insertMember(EMAIL, "ACTIVE", passwordEncoder.encode("oldPassword123!"));
        insertToken(memberId, TOKEN, "1 hour", null);

        reset(TOKEN, NEW_PASSWORD)
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(header().doesNotExist("Set-Cookie"));

        String hash = jdbc.queryForObject("SELECT password_hash FROM members WHERE id = ?", String.class, memberId);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, hash)).isTrue();
    }

    @Test
    void 변경_후_새_비밀번호로_로그인할_수_있다() throws Exception {
        long memberId = insertMember(EMAIL, "ACTIVE", passwordEncoder.encode("oldPassword123!"));
        insertToken(memberId, TOKEN, "1 hour", null);

        reset(TOKEN, NEW_PASSWORD).andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void 비밀번호가_없던_초대_계정도_최초_비밀번호를_설정할_수_있다() throws Exception {
        long memberId = insertMember(EMAIL, "ACTIVE", null);
        insertToken(memberId, TOKEN, "1 hour", null);

        reset(TOKEN, NEW_PASSWORD).andExpect(status().isNoContent());

        String hash = jdbc.queryForObject("SELECT password_hash FROM members WHERE id = ?", String.class, memberId);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, hash)).isTrue();
    }

    @Test
    void 새_비밀번호가_기존과_같아도_허용한다() throws Exception {
        long memberId = insertMember(EMAIL, "ACTIVE", passwordEncoder.encode(NEW_PASSWORD));
        insertToken(memberId, TOKEN, "1 hour", null);

        reset(TOKEN, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void 사용한_토큰은_사용_처리되어_다시_쓸_수_없다() throws Exception {
        long memberId = insertMember(EMAIL, "ACTIVE", "hash");
        insertToken(memberId, TOKEN, "1 hour", null);

        reset(TOKEN, NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT used_at FROM password_reset_tokens", Timestamp.class)).isNotNull();
        reset(TOKEN, "anotherPassword1!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
    }

    @Test
    void 성공하면_그_회원의_모든_Refresh_Token을_폐기하고_다른_회원의_토큰은_유지한다() throws Exception {
        long memberId = insertMember(EMAIL, "ACTIVE", "hash");
        long otherId = insertMember("other@example.com", "ACTIVE", "hash");
        insertRefreshToken(memberId, "rt-1");
        insertRefreshToken(memberId, "rt-2");
        insertRefreshToken(otherId, "rt-3");
        insertToken(memberId, TOKEN, "1 hour", null);

        reset(TOKEN, NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE member_id = ? AND revoked_at IS NULL",
                Integer.class, memberId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE member_id = ? AND revoked_at IS NULL",
                Integer.class, otherId)).isEqualTo(1);
    }

    @Test
    void 성공하면_같은_회원의_다른_미사용_재설정_토큰도_무효화된다() throws Exception {
        long memberId = insertMember(EMAIL, "ACTIVE", "hash");
        insertToken(memberId, TOKEN, "1 hour", null);
        insertToken(memberId, "prt_other-unused", "1 hour", null);

        reset(TOKEN, NEW_PASSWORD).andExpect(status().isNoContent());

        reset("prt_other-unused", "anotherPassword1!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
    }

    @Test
    void 존재하지_않는_토큰이면_400_INVALID_RESET_TOKEN을_응답한다() throws Exception {
        reset("prt_unknown", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
    }

    @Test
    void 만료된_토큰이면_400_INVALID_RESET_TOKEN을_응답하고_비밀번호를_바꾸지_않는다() throws Exception {
        long memberId = insertMember(EMAIL, "ACTIVE", "original-hash");
        insertToken(memberId, TOKEN, "-1 day", null);

        reset(TOKEN, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));

        assertThat(jdbc.queryForObject("SELECT password_hash FROM members WHERE id = ?", String.class, memberId))
                .isEqualTo("original-hash");
    }

    @Test
    void 이미_사용한_토큰이면_400_INVALID_RESET_TOKEN을_응답한다() throws Exception {
        long memberId = insertMember(EMAIL, "ACTIVE", "original-hash");
        insertToken(memberId, TOKEN, "1 hour", Instant.now());

        reset(TOKEN, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
    }

    @Test
    void 계정이_ACTIVE가_아니면_400_INVALID_RESET_TOKEN을_응답하고_토큰은_사용_처리되지_않는다() throws Exception {
        long memberId = insertMember(EMAIL, "INACTIVE", "original-hash");
        insertToken(memberId, TOKEN, "1 hour", null);

        reset(TOKEN, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));

        assertThat(jdbc.queryForObject("SELECT password_hash FROM members WHERE id = ?", String.class, memberId))
                .isEqualTo("original-hash");
        assertThat(jdbc.queryForObject("SELECT used_at FROM password_reset_tokens", Timestamp.class)).isNull();
    }

    @Test
    void 새_비밀번호가_8자_미만이면_400_INVALID_INPUT을_응답하고_토큰을_사용하지_않는다() throws Exception {
        long memberId = insertMember(EMAIL, "ACTIVE", "original-hash");
        insertToken(memberId, TOKEN, "1 hour", null);

        reset(TOKEN, "short1!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.invalidParams[0].field").value("newPassword"));

        assertThat(jdbc.queryForObject("SELECT used_at FROM password_reset_tokens", Timestamp.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT password_hash FROM members WHERE id = ?", String.class, memberId))
                .isEqualTo("original-hash");
    }

    @Test
    void 필수_값이_없으면_400_INVALID_INPUT을_응답한다() throws Exception {
        mockMvc.perform(post("/auth/password-resets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private ResultActions reset(String token, String newPassword) throws Exception {
        return mockMvc.perform(post("/auth/password-resets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}"));
    }

    private long insertMember(String email, String memberStatus, String passwordHash) {
        return jdbc.queryForObject("""
                INSERT INTO members (email, password_hash, name, phone_number, birth_date, role, status,
                                     created_at, updated_at)
                VALUES (?, ?, '홍길동', '01012345678', DATE '1990-01-01', 'MEMBER', ?, now(), now())
                RETURNING id
                """, Long.class, email, passwordHash, memberStatus);
    }

    /** 만료 시각은 DB 시계 기준 상대값으로 지정한다 (컨테이너와 호스트의 시계 차이에 영향받지 않게). */
    private void insertToken(long memberId, String rawToken, String expiresIn, Instant usedAt) {
        jdbc.update("""
                INSERT INTO password_reset_tokens (member_id, token_hash, expires_at, used_at, created_at)
                VALUES (?, ?, now() + CAST(? AS interval), ?, now())
                """, memberId, sha256(rawToken), expiresIn, usedAt == null ? null : Timestamp.from(usedAt));
    }

    private void insertRefreshToken(long memberId, String rawToken) {
        jdbc.update("""
                INSERT INTO refresh_tokens (member_id, token_hash, expires_at, created_at)
                VALUES (?, ?, now() + interval '1 day', now())
                """, memberId, sha256(rawToken));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
