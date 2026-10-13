package com.rha.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.rha.IntegrationTestSupport;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class ChangeMyPasswordTest extends IntegrationTestSupport {

    private static final String EMAIL = "user@example.com";
    private static final String OTHER_EMAIL = "other@example.com";
    private static final String PASSWORD = "password123!";
    private static final String NEW_PASSWORD = "newPassword456!";

    record Session(String accessToken, String refreshToken) {
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Value("${rha.auth.jwt.secret}")
    String jwtSecret;

    @Test
    void 현재_비밀번호가_맞으면_204를_응답하고_비밀번호가_변경된다() throws Exception {
        insertMember(EMAIL);
        Session session = login(EMAIL, PASSWORD);

        changePassword(session.accessToken(), PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        String stored = passwordHashOf(EMAIL);
        assertThat(stored).isNotEqualTo(NEW_PASSWORD);
        assertThat(new BCryptPasswordEncoder().matches(NEW_PASSWORD, stored)).isTrue();
        assertThat(new BCryptPasswordEncoder().matches(PASSWORD, stored)).isFalse();
    }

    @Test
    void 변경하면_수정_시각이_갱신된다() throws Exception {
        insertMember(EMAIL);
        Session session = login(EMAIL, PASSWORD);
        jdbc.update("UPDATE members SET updated_at = now() - interval '1 day' WHERE email = ?", EMAIL);
        java.sql.Timestamp before = jdbc.queryForObject(
                "SELECT updated_at FROM members WHERE email = ?", java.sql.Timestamp.class, EMAIL);

        changePassword(session.accessToken(), PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        java.sql.Timestamp after = jdbc.queryForObject(
                "SELECT updated_at FROM members WHERE email = ?", java.sql.Timestamp.class, EMAIL);
        assertThat(after.toInstant()).isAfter(before.toInstant());
    }

    @Test
    void 변경하면_Refresh_Token_쿠키를_만료시킨다() throws Exception {
        insertMember(EMAIL);
        Session session = login(EMAIL, PASSWORD);

        MvcResult result = changePassword(session.accessToken(), PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent()).andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith("refreshToken=;");
        assertThat(setCookie).contains("Max-Age=0", "Path=/v1/auth", "HttpOnly", "Secure", "SameSite=Strict");
    }

    @Test
    void 변경하면_새_비밀번호로만_로그인할_수_있다() throws Exception {
        insertMember(EMAIL);
        Session session = login(EMAIL, PASSWORD);
        changePassword(session.accessToken(), PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        loginRequest(EMAIL, NEW_PASSWORD).andExpect(status().isOk());
        loginRequest(EMAIL, PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void 변경하면_모든_기기의_Refresh_Token이_폐기된다() throws Exception {
        insertMember(EMAIL);
        Session phone = login(EMAIL, PASSWORD);
        Session laptop = login(EMAIL, PASSWORD);

        changePassword(phone.accessToken(), PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE revoked_at IS NULL",
                Integer.class)).isZero();
        for (Session session : new Session[] {phone, laptop}) {
            mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", session.refreshToken())))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        }
    }

    @Test
    void 다른_회원의_Refresh_Token은_폐기되지_않는다() throws Exception {
        insertMember(EMAIL);
        insertMember(OTHER_EMAIL);
        Session me = login(EMAIL, PASSWORD);
        Session other = login(OTHER_EMAIL, PASSWORD);

        changePassword(me.accessToken(), PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", other.refreshToken())))
                .andExpect(status().isOk());
        assertThat(new BCryptPasswordEncoder().matches(PASSWORD, passwordHashOf(OTHER_EMAIL))).isTrue();
    }

    @Test
    void 이미_발급된_AccessToken은_만료_전까지_사용할_수_있다() throws Exception {
        insertMember(EMAIL);
        Session session = login(EMAIL, PASSWORD);
        changePassword(session.accessToken(), PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        mockMvc.perform(get("/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void 현재_비밀번호가_틀리면_400_INVALID_CURRENT_PASSWORD를_응답하고_아무것도_바뀌지_않는다() throws Exception {
        insertMember(EMAIL);
        Session session = login(EMAIL, PASSWORD);
        String hashBefore = passwordHashOf(EMAIL);

        MvcResult result = changePassword(session.accessToken(), "wrong-password1!", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"))
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(passwordHashOf(EMAIL)).isEqualTo(hashBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE revoked_at IS NULL",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void 새_비밀번호가_현재와_같으면_400_SAME_AS_CURRENT_PASSWORD를_응답하고_토큰을_유지한다() throws Exception {
        insertMember(EMAIL);
        Session session = login(EMAIL, PASSWORD);

        changePassword(session.accessToken(), PASSWORD, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAME_AS_CURRENT_PASSWORD"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE revoked_at IS NULL",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void 현재_비밀번호가_틀리면_새_비밀번호가_같은지는_알려주지_않는다() throws Exception {
        insertMember(EMAIL);
        Session session = login(EMAIL, PASSWORD);

        // 새 비밀번호가 실제 현재 비밀번호와 같더라도, 현재 비밀번호 검증이 먼저다.
        changePassword(session.accessToken(), "wrong-password1!", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
    }

    @Test
    void 새_비밀번호_길이가_올바르지_않으면_400_INVALID_INPUT을_응답한다() throws Exception {
        insertMember(EMAIL);
        Session session = login(EMAIL, PASSWORD);

        for (String tooShortOrLong : new String[] {"short1!", "a".repeat(65)}) {
            changePassword(session.accessToken(), PASSWORD, tooShortOrLong)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.invalidParams[?(@.field == 'newPassword')]").isNotEmpty())
                    .andExpect(jsonPath("$.invalidParams[?(@.field == 'newPassword')].value").isEmpty());
        }
        assertThat(new BCryptPasswordEncoder().matches(PASSWORD, passwordHashOf(EMAIL))).isTrue();
    }

    @Test
    void 필드가_누락되면_400_INVALID_INPUT을_응답한다() throws Exception {
        insertMember(EMAIL);
        Session session = login(EMAIL, PASSWORD);

        mockMvc.perform(put("/members/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.invalidParams[?(@.field == 'currentPassword')]").isNotEmpty())
                .andExpect(jsonPath("$.invalidParams[?(@.field == 'newPassword')]").isNotEmpty());
    }

    @Test
    void AccessToken이_없으면_401_UNAUTHENTICATED를_응답하고_변경하지_않는다() throws Exception {
        insertMember(EMAIL);
        String hashBefore = passwordHashOf(EMAIL);

        mockMvc.perform(put("/members/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertThat(passwordHashOf(EMAIL)).isEqualTo(hashBefore);
    }

    @Test
    void 존재하지_않는_회원의_토큰이면_401_UNAUTHENTICATED를_응답한다() throws Exception {
        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject("999999")
                .claim("role", "MEMBER")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofHours(1))))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        changePassword(token, PASSWORD, NEW_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private ResultActions changePassword(String accessToken, String currentPassword, String newPassword)
            throws Exception {
        return mockMvc.perform(put("/members/me/password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(currentPassword, newPassword)));
    }

    private static String body(String currentPassword, String newPassword) {
        return "{\"currentPassword\":\"" + currentPassword + "\",\"newPassword\":\"" + newPassword + "\"}";
    }

    private ResultActions loginRequest(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    /** 실제 로그인 API를 거쳐 Access Token과 Refresh Token을 발급받는다. */
    private Session login(String email, String password) throws Exception {
        MvcResult result = loginRequest(email, password).andExpect(status().isOk()).andReturn();
        String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
        return new Session(accessToken, result.getResponse().getCookie("refreshToken").getValue());
    }

    private void insertMember(String email) {
        jdbc.update("""
                INSERT INTO members (email, password_hash, name, phone_number, birth_date, role, status,
                                     created_at, updated_at)
                VALUES (?, ?, '홍길동', '01012345678', DATE '1990-01-31', 'MEMBER', 'ACTIVE', now(), now())
                """, email, new BCryptPasswordEncoder().encode(PASSWORD));
    }

    private String passwordHashOf(String email) {
        return jdbc.queryForObject("SELECT password_hash FROM members WHERE email = ?", String.class, email);
    }
}
