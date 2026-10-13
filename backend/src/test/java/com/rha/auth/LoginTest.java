package com.rha.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.rha.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class LoginTest extends IntegrationTestSupport {

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "password123!";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM members");
    }

    @Test
    void 올바른_이메일과_비밀번호면_200과_AccessToken을_응답한다() throws Exception {
        insertMember(EMAIL, PASSWORD, "MEMBER", "ACTIVE");

        login(EMAIL, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void AccessToken에는_회원_ID와_역할이_담긴다() throws Exception {
        long memberId = insertMember(EMAIL, PASSWORD, "INSTRUCTOR", "ACTIVE");

        String body = login(EMAIL, PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String payload = payloadOf(JsonPath.read(body, "$.accessToken"));

        assertThat(JsonPath.<String>read(payload, "$.sub")).isEqualTo(String.valueOf(memberId));
        assertThat(JsonPath.<String>read(payload, "$.role")).isEqualTo("INSTRUCTOR");
        assertThat(JsonPath.<Integer>read(body, "$.expiresIn")).isPositive();
    }

    @Test
    void RefreshToken은_HttpOnly_쿠키로_내려간다() throws Exception {
        insertMember(EMAIL, PASSWORD, "MEMBER", "ACTIVE");

        MvcResult result = login(EMAIL, PASSWORD).andExpect(status().isOk()).andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith("refreshToken=");
        assertThat(setCookie).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/v1/auth");
        assertThat(setCookie).contains("Max-Age=" + Duration.ofDays(14).toSeconds());
    }

    @Test
    void RefreshToken은_해시로_저장되고_회원과_연결된다() throws Exception {
        long memberId = insertMember(EMAIL, PASSWORD, "MEMBER", "ACTIVE");

        MvcResult result = login(EMAIL, PASSWORD).andExpect(status().isOk()).andReturn();

        String rawToken = refreshTokenCookieValue(result);
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM refresh_tokens");
        assertThat(row.get("member_id")).isEqualTo(memberId);
        assertThat(row.get("token_hash")).isEqualTo(TokenHasher.sha256(rawToken)).isNotEqualTo(rawToken);
        assertThat(row.get("revoked_at")).isNull();
        Instant expiresAt = ((java.sql.Timestamp) row.get("expires_at")).toInstant();
        assertThat(expiresAt).isCloseTo(Instant.now().plus(Duration.ofDays(14)), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void 로그인할_때마다_새로운_RefreshToken이_발급된다() throws Exception {
        insertMember(EMAIL, PASSWORD, "MEMBER", "ACTIVE");

        String first = refreshTokenCookieValue(login(EMAIL, PASSWORD).andReturn());
        String second = refreshTokenCookieValue(login(EMAIL, PASSWORD).andReturn());

        assertThat(first).isNotEqualTo(second);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class)).isEqualTo(2);
    }

    @Test
    void 비밀번호가_틀리면_401_INVALID_CREDENTIALS를_응답한다() throws Exception {
        insertMember(EMAIL, PASSWORD, "MEMBER", "ACTIVE");

        login(EMAIL, "wrong-password1!")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void 가입되지_않은_이메일이어도_401_INVALID_CREDENTIALS를_응답한다() throws Exception {
        login("nobody@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void 이메일과_비밀번호_중_어느_쪽이_틀렸는지_구분할_수_없다() throws Exception {
        insertMember(EMAIL, PASSWORD, "MEMBER", "ACTIVE");

        String wrongPassword = login(EMAIL, "wrong-password1!").andReturn().getResponse().getContentAsString();
        String unknownEmail = login("nobody@example.com", PASSWORD).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(wrongPassword, "$.detail"))
                .isEqualTo(JsonPath.<String>read(unknownEmail, "$.detail"));
        assertThat(JsonPath.<String>read(wrongPassword, "$.title"))
                .isEqualTo(JsonPath.<String>read(unknownEmail, "$.title"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"INACTIVE", "WITHDRAWN", "RESIGNED"})
    void 활성_상태가_아닌_계정이면_401_ACCOUNT_DISABLED를_응답한다(String accountStatus) throws Exception {
        insertMember(EMAIL, PASSWORD, "MEMBER", accountStatus);

        login(EMAIL, PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class)).isZero();
    }

    @Test
    void 비활성_계정이어도_비밀번호가_틀리면_계정_상태를_알려주지_않는다() throws Exception {
        insertMember(EMAIL, PASSWORD, "MEMBER", "WITHDRAWN");

        login(EMAIL, "wrong-password1!")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void 비밀번호를_아직_설정하지_않은_초대_계정은_로그인할_수_없다() throws Exception {
        jdbc.update("""
                INSERT INTO members (email, password_hash, name, phone_number, birth_date, role, status,
                                     created_at, updated_at)
                VALUES (?, NULL, '초대강사', '01011112222', DATE '1990-01-01', 'INSTRUCTOR', 'ACTIVE', now(), now())
                """, EMAIL);

        login(EMAIL, PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class)).isZero();
    }

    @Test
    void 로그인에_실패하면_쿠키를_내려주지_않는다() throws Exception {
        insertMember(EMAIL, PASSWORD, "MEMBER", "ACTIVE");

        MvcResult result = login(EMAIL, "wrong-password1!").andExpect(status().isUnauthorized()).andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(result.getResponse().getCookies()).isEmpty();
    }

    @Test
    void 필드가_누락되거나_형식이_올바르지_않으면_400_INVALID_INPUT을_응답한다() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.invalidParams[?(@.field == 'email')]").isNotEmpty())
                .andExpect(jsonPath("$.invalidParams[?(@.field == 'password')]").isNotEmpty());
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private long insertMember(String email, String rawPassword, String role, String accountStatus) {
        jdbc.update("""
                INSERT INTO members (email, password_hash, name, phone_number, birth_date, role, status,
                                     created_at, updated_at)
                VALUES (?, ?, '홍길동', '01012345678', DATE '1990-01-31', ?, ?, now(), now())
                """, email, new BCryptPasswordEncoder().encode(rawPassword), role, accountStatus);
        return jdbc.queryForObject("SELECT id FROM members WHERE email = ?", Long.class, email);
    }

    private static String refreshTokenCookieValue(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("refreshToken");
        assertThat(cookie).as("refreshToken 쿠키").isNotNull();
        return cookie.getValue();
    }

    /** JWT의 payload(두 번째 조각)를 서명 검증 없이 JSON 문자열로 꺼낸다. */
    private static String payloadOf(String jwt) {
        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);
        return new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
    }
}
