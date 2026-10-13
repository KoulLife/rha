package com.rha.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.rha.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
class RefreshTokenTest extends IntegrationTestSupport {

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "password123!";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 유효한_RefreshToken_쿠키면_200과_새_AccessToken을_응답한다() throws Exception {
        long memberId = insertMember("MEMBER", "ACTIVE");
        String refreshToken = loginAndGetRefreshToken();

        String body = refresh(refreshToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andReturn().getResponse().getContentAsString();

        String payload = payloadOf(JsonPath.read(body, "$.accessToken"));
        assertThat(JsonPath.<String>read(payload, "$.sub")).isEqualTo(String.valueOf(memberId));
        assertThat(JsonPath.<String>read(payload, "$.role")).isEqualTo("MEMBER");
    }

    @Test
    void 재발급하면_새_RefreshToken이_쿠키로_내려간다() throws Exception {
        insertMember("MEMBER", "ACTIVE");
        String refreshToken = loginAndGetRefreshToken();

        MvcResult result = refresh(refreshToken).andExpect(status().isOk()).andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith("refreshToken=");
        assertThat(setCookie).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/v1/auth");
        assertThat(setCookie).contains("Max-Age=" + Duration.ofDays(14).toSeconds());
        assertThat(result.getResponse().getCookie("refreshToken").getValue()).isNotEqualTo(refreshToken);
    }

    @Test
    void 재발급하면_이전_토큰은_폐기되고_새_토큰이_저장된다() throws Exception {
        long memberId = insertMember("MEMBER", "ACTIVE");
        String oldToken = loginAndGetRefreshToken();

        MvcResult result = refresh(oldToken).andExpect(status().isOk()).andReturn();
        String newToken = result.getResponse().getCookie("refreshToken").getValue();

        assertThat(revokedAtOf(oldToken)).isNotNull();
        assertThat(revokedAtOf(newToken)).isNull();
        assertThat(jdbc.queryForObject("SELECT member_id FROM refresh_tokens WHERE token_hash = ?", Long.class,
                TokenHasher.sha256(newToken))).isEqualTo(memberId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class)).isEqualTo(2);
    }

    @Test
    void 이미_회전된_이전_토큰으로는_다시_재발급할_수_없다() throws Exception {
        insertMember("MEMBER", "ACTIVE");
        String oldToken = loginAndGetRefreshToken();
        refresh(oldToken).andExpect(status().isOk());

        refresh(oldToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void 회전된_새_토큰으로_계속_재발급할_수_있다() throws Exception {
        insertMember("MEMBER", "ACTIVE");
        String first = loginAndGetRefreshToken();
        String second = refresh(first).andReturn().getResponse().getCookie("refreshToken").getValue();

        refresh(second).andExpect(status().isOk());
    }

    @Test
    void 쿠키가_없으면_401_INVALID_REFRESH_TOKEN을_응답한다() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void 존재하지_않는_토큰이면_401_INVALID_REFRESH_TOKEN을_응답한다() throws Exception {
        refresh("unknown-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void 만료된_토큰이면_401_INVALID_REFRESH_TOKEN을_응답한다() throws Exception {
        insertMember("MEMBER", "ACTIVE");
        String refreshToken = loginAndGetRefreshToken();
        // DB 컨테이너와 호스트의 시계 차이에 영향받지 않도록 충분히 과거로 설정한다.
        jdbc.update("UPDATE refresh_tokens SET expires_at = now() - interval '1 day'");

        refresh(refreshToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void 폐기된_토큰이면_401_INVALID_REFRESH_TOKEN을_응답한다() throws Exception {
        insertMember("MEMBER", "ACTIVE");
        String refreshToken = loginAndGetRefreshToken();
        jdbc.update("UPDATE refresh_tokens SET revoked_at = now()");

        refresh(refreshToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void 활성_상태가_아닌_회원의_토큰이면_401_INVALID_REFRESH_TOKEN을_응답한다() throws Exception {
        insertMember("MEMBER", "ACTIVE");
        String refreshToken = loginAndGetRefreshToken();
        jdbc.update("UPDATE members SET status = 'INACTIVE'");

        refresh(refreshToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void 재발급에_실패하면_쿠키를_내려주지_않고_토큰도_만들지_않는다() throws Exception {
        MvcResult result = refresh("unknown-token").andExpect(status().isUnauthorized()).andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class)).isZero();
    }

    @Test
    void 재발급된_AccessToken에는_현재_역할이_반영된다() throws Exception {
        insertMember("MEMBER", "ACTIVE");
        String refreshToken = loginAndGetRefreshToken();
        jdbc.update("UPDATE members SET role = 'INSTRUCTOR'");

        String body = refresh(refreshToken).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        String payload = payloadOf(JsonPath.read(body, "$.accessToken"));
        assertThat(JsonPath.<String>read(payload, "$.role")).isEqualTo("INSTRUCTOR");
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        MockHttpServletRequestBuilder request = post("/auth/refresh").cookie(new Cookie("refreshToken", refreshToken));
        return mockMvc.perform(request);
    }

    /** 실제 로그인 API를 거쳐 Refresh Token을 발급받는다. */
    private String loginAndGetRefreshToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("refreshToken").getValue();
    }

    private long insertMember(String role, String accountStatus) {
        jdbc.update("""
                INSERT INTO members (email, password_hash, name, phone_number, birth_date, role, status,
                                     created_at, updated_at)
                VALUES (?, ?, '홍길동', '01012345678', DATE '1990-01-31', ?, ?, now(), now())
                """, EMAIL, new BCryptPasswordEncoder().encode(PASSWORD), role, accountStatus);
        return jdbc.queryForObject("SELECT id FROM members WHERE email = ?", Long.class, EMAIL);
    }

    private Object revokedAtOf(String rawToken) {
        return jdbc.queryForMap("SELECT revoked_at FROM refresh_tokens WHERE token_hash = ?",
                TokenHasher.sha256(rawToken)).get("revoked_at");
    }

    /** JWT의 payload(두 번째 조각)를 서명 검증 없이 JSON 문자열로 꺼낸다. */
    private static String payloadOf(String jwt) {
        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);
        return new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
    }
}
