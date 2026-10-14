package com.rha.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.rha.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
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
class WithdrawMyAccountTest extends IntegrationTestSupport {

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
    void 회원이_탈퇴하면_204를_응답하고_상태가_WITHDRAWN이_된다() throws Exception {
        insertMember(EMAIL, "MEMBER");
        Session session = login(EMAIL);

        withdraw(session.accessToken())
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(statusOf(EMAIL)).isEqualTo("WITHDRAWN");
    }

    @Test
    void 탈퇴해도_회원_정보는_삭제되지_않고_보존된다() throws Exception {
        insertMember(EMAIL, "MEMBER");
        Session session = login(EMAIL);

        withdraw(session.accessToken()).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT name FROM members WHERE email = ?", String.class, EMAIL))
                .isEqualTo("홍길동");
    }

    @Test
    void 탈퇴하면_탈퇴_시각이_기록된다() throws Exception {
        insertMember(EMAIL, "MEMBER");
        Session session = login(EMAIL);
        assertThat(jdbc.queryForObject("SELECT withdrawn_at FROM members WHERE email = ?", Timestamp.class, EMAIL))
                .isNull();

        withdraw(session.accessToken()).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT withdrawn_at FROM members WHERE email = ?", Timestamp.class, EMAIL))
                .isNotNull();
    }

    @Test
    void 탈퇴하면_수정_시각이_갱신된다() throws Exception {
        insertMember(EMAIL, "MEMBER");
        Session session = login(EMAIL);
        jdbc.update("UPDATE members SET updated_at = now() - interval '1 day' WHERE email = ?", EMAIL);
        Timestamp before = jdbc.queryForObject(
                "SELECT updated_at FROM members WHERE email = ?", Timestamp.class, EMAIL);

        withdraw(session.accessToken()).andExpect(status().isNoContent());

        Timestamp after = jdbc.queryForObject(
                "SELECT updated_at FROM members WHERE email = ?", Timestamp.class, EMAIL);
        assertThat(after.toInstant()).isAfter(before.toInstant());
    }

    @Test
    void 탈퇴하면_Refresh_Token_쿠키를_만료시킨다() throws Exception {
        insertMember(EMAIL, "MEMBER");
        Session session = login(EMAIL);

        MvcResult result = withdraw(session.accessToken()).andExpect(status().isNoContent()).andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith("refreshToken=;");
        assertThat(setCookie).contains("Max-Age=0", "Path=/v1/auth", "HttpOnly", "Secure", "SameSite=Strict");
    }

    @Test
    void 탈퇴하면_모든_기기의_Refresh_Token이_폐기된다() throws Exception {
        insertMember(EMAIL, "MEMBER");
        Session phone = login(EMAIL);
        Session laptop = login(EMAIL);

        withdraw(phone.accessToken()).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE revoked_at IS NULL",
                Integer.class)).isZero();
        for (Session session : new Session[] {phone, laptop}) {
            mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", session.refreshToken())))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        }
    }

    @Test
    void 다른_회원의_계정과_토큰에는_영향이_없다() throws Exception {
        insertMember(EMAIL, "MEMBER");
        insertMember(OTHER_EMAIL, "MEMBER");
        Session me = login(EMAIL);
        login(OTHER_EMAIL);

        withdraw(me.accessToken()).andExpect(status().isNoContent());

        assertThat(statusOf(OTHER_EMAIL)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM refresh_tokens t JOIN members m ON m.id = t.member_id
                WHERE m.email = ? AND t.revoked_at IS NULL
                """, Integer.class, OTHER_EMAIL)).isEqualTo(1);
    }

    @Test
    void 탈퇴한_계정은_로그인할_수_없다() throws Exception {
        insertMember(EMAIL, "MEMBER");
        Session session = login(EMAIL);
        withdraw(session.accessToken()).andExpect(status().isNoContent());

        loginRequest(EMAIL)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void 탈퇴한_계정의_이메일로는_다시_가입할_수_없다() throws Exception {
        insertMember(EMAIL, "MEMBER");
        Session session = login(EMAIL);
        withdraw(session.accessToken()).andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"INSTRUCTOR", "ADMIN"})
    void 강사와_관리자는_탈퇴할_수_없고_403_FORBIDDEN_ROLE을_응답한다(String role) throws Exception {
        insertMember(EMAIL, role);
        Session session = login(EMAIL);

        withdraw(session.accessToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_ROLE"));
    }

    @Test
    void 거절되면_상태와_Refresh_Token을_바꾸지_않고_쿠키도_내려주지_않는다() throws Exception {
        insertMember(EMAIL, "INSTRUCTOR");
        Session session = login(EMAIL);

        MvcResult result = withdraw(session.accessToken()).andExpect(status().isForbidden()).andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(statusOf(EMAIL)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE revoked_at IS NULL",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void 인증하지_않으면_401_UNAUTHENTICATED를_응답한다() throws Exception {
        mockMvc.perform(delete("/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private ResultActions withdraw(String accessToken) throws Exception {
        return mockMvc.perform(delete("/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private ResultActions loginRequest(String email) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"));
    }

    private Session login(String email) throws Exception {
        MvcResult result = loginRequest(email).andExpect(status().isOk()).andReturn();
        String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
        String refreshToken = result.getResponse().getCookie("refreshToken").getValue();
        return new Session(accessToken, refreshToken);
    }

    private String statusOf(String email) {
        return jdbc.queryForObject("SELECT status FROM members WHERE email = ?", String.class, email);
    }

    private void insertMember(String email, String role) {
        jdbc.update("""
                INSERT INTO members (email, password_hash, name, phone_number, birth_date, role, status,
                                     created_at, updated_at)
                VALUES (?, ?, '홍길동', '01012345678', DATE '1990-01-01', ?, 'ACTIVE', now(), now())
                """, email, new BCryptPasswordEncoder().encode(PASSWORD), role);
    }
}
