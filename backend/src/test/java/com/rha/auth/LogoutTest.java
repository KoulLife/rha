package com.rha.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.rha.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
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
class LogoutTest extends IntegrationTestSupport {

    private static final String EMAIL = "user@example.com";
    private static final String OTHER_EMAIL = "other@example.com";
    private static final String PASSWORD = "password123!";

    record Session(String accessToken, String refreshToken) {
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 로그아웃하면_204를_응답하고_RefreshToken이_폐기된다() throws Exception {
        insertMember(EMAIL);
        Session session = login(EMAIL);

        logout(session.accessToken(), session.refreshToken()).andExpect(status().isNoContent());

        assertThat(revokedAtOf(session.refreshToken())).isNotNull();
    }

    @Test
    void 로그아웃하면_RefreshToken_쿠키를_만료시킨다() throws Exception {
        insertMember(EMAIL);
        Session session = login(EMAIL);

        MvcResult result = logout(session.accessToken(), session.refreshToken())
                .andExpect(status().isNoContent()).andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith("refreshToken=;");
        assertThat(setCookie).contains("Max-Age=0", "Path=/v1/auth", "HttpOnly", "Secure", "SameSite=Strict");
    }

    @Test
    void 로그아웃한_RefreshToken으로는_재발급할_수_없다() throws Exception {
        insertMember(EMAIL);
        Session session = login(EMAIL);
        logout(session.accessToken(), session.refreshToken()).andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", session.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void 다른_기기의_RefreshToken은_폐기되지_않는다() throws Exception {
        insertMember(EMAIL);
        Session phone = login(EMAIL);
        Session laptop = login(EMAIL);

        logout(phone.accessToken(), phone.refreshToken()).andExpect(status().isNoContent());

        assertThat(revokedAtOf(phone.refreshToken())).isNotNull();
        assertThat(revokedAtOf(laptop.refreshToken())).isNull();
        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", laptop.refreshToken())))
                .andExpect(status().isOk());
    }

    @Test
    void 쿠키가_없어도_204를_응답하고_쿠키를_만료시킨다() throws Exception {
        insertMember(EMAIL);
        Session session = login(EMAIL);

        MvcResult result = mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + session.accessToken()))
                .andExpect(status().isNoContent()).andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        assertThat(revokedAtOf(session.refreshToken())).isNull();
    }

    @Test
    void 알_수_없는_RefreshToken이어도_204를_응답한다() throws Exception {
        insertMember(EMAIL);
        Session session = login(EMAIL);

        logout(session.accessToken(), "unknown-token").andExpect(status().isNoContent());
    }

    @Test
    void 이미_폐기된_RefreshToken으로_다시_로그아웃해도_204를_응답한다() throws Exception {
        insertMember(EMAIL);
        Session session = login(EMAIL);
        logout(session.accessToken(), session.refreshToken()).andExpect(status().isNoContent());

        logout(session.accessToken(), session.refreshToken()).andExpect(status().isNoContent());
    }

    @Test
    void 다른_회원의_RefreshToken_쿠키로는_폐기되지_않는다() throws Exception {
        insertMember(EMAIL);
        insertMember(OTHER_EMAIL);
        Session me = login(EMAIL);
        Session other = login(OTHER_EMAIL);

        logout(me.accessToken(), other.refreshToken()).andExpect(status().isNoContent());

        assertThat(revokedAtOf(other.refreshToken())).isNull();
    }

    @Test
    void AccessToken이_없으면_401_UNAUTHENTICATED를_응답하고_토큰을_폐기하지_않는다() throws Exception {
        insertMember(EMAIL);
        Session session = login(EMAIL);

        mockMvc.perform(post("/auth/logout").cookie(new Cookie("refreshToken", session.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertThat(revokedAtOf(session.refreshToken())).isNull();
    }

    private ResultActions logout(String accessToken, String refreshToken) throws Exception {
        MockHttpServletRequestBuilder request = post("/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .cookie(new Cookie("refreshToken", refreshToken));
        return mockMvc.perform(request);
    }

    /** 실제 로그인 API를 거쳐 Access Token과 Refresh Token을 발급받는다. */
    private Session login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
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

    private Object revokedAtOf(String rawToken) {
        return jdbc.queryForMap("SELECT revoked_at FROM refresh_tokens WHERE token_hash = ?",
                TokenHasher.sha256(rawToken)).get("revoked_at");
    }
}
