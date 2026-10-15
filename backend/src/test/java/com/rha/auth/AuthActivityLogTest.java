package com.rha.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.rha.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** 인증 계열 API의 활동 이력 기록. */
@Import(TestMailerConfig.class)
class AuthActivityLogTest extends ApiTestSupport {

    static final String EMAIL = "user@example.com";

    @Autowired
    RecordingEmailVerificationMailer verificationMailer;

    @BeforeEach
    void setUp() {
        verificationMailer.clear();
    }

    private List<Map<String, Object>> logs(String action) {
        return jdbc.queryForList("SELECT * FROM activity_logs WHERE action = ? ORDER BY id", action);
    }

    private Map<String, Object> onlyLog(String action) {
        List<Map<String, Object>> found = logs(action);
        assertThat(found).as("%s 이력", action).hasSize(1);
        return found.get(0);
    }

    private long idOf(String email) {
        return jdbc.queryForObject("SELECT id FROM members WHERE email = ?", Long.class, email);
    }

    private ResultActions loginRequest(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void 회원가입을_기록한다() throws Exception {
        mockMvc.perform(post("/auth/email-verifications").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\"}")).andExpect(status().isNoContent());
        String code = verificationMailer.sent().get(0).code();
        String confirm = mockMvc.perform(post("/auth/email-verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(confirm, "$.emailVerificationToken");
        // 이메일 인증 코드 발송과 확인은 이력에 남기지 않는다.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM activity_logs", Integer.class)).isZero();

        mockMvc.perform(post("/auth/sign-up").contentType(MediaType.APPLICATION_JSON).content(
                "{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\",\"name\":\"홍길동\","
                        + "\"phoneNumber\":\"01012345678\",\"birthDate\":\"1990-01-31\","
                        + "\"emailVerificationToken\":\"" + token + "\"}"))
                .andExpect(status().isCreated());

        Map<String, Object> log = onlyLog("SIGN_UP");
        long memberId = idOf(EMAIL);
        assertThat(log.get("category")).isEqualTo("AUTH");
        assertThat(((Number) log.get("actor_id")).longValue()).isEqualTo(memberId);
        assertThat(log.get("actor_role")).isEqualTo("MEMBER");
        assertThat(((Number) log.get("subject_member_id")).longValue()).isEqualTo(memberId);
        assertThat(log.get("target_type")).isEqualTo("MEMBER");
        assertThat(((Number) log.get("target_id")).longValue()).isEqualTo(memberId);
        assertThat((String) log.get("summary")).contains("홍길동");
        assertThat((String) log.get("summary")).doesNotContain("01012345678").doesNotContain(PASSWORD);
    }

    @Test
    void 로그인을_기록한다() throws Exception {
        insertMember(EMAIL, "홍길동", "INSTRUCTOR", "ACTIVE", Instant.now());

        loginRequest(EMAIL, PASSWORD).andExpect(status().isOk());

        Map<String, Object> log = onlyLog("LOGIN");
        long memberId = idOf(EMAIL);
        assertThat(((Number) log.get("actor_id")).longValue()).isEqualTo(memberId);
        assertThat(log.get("actor_role")).isEqualTo("INSTRUCTOR");
        assertThat(((Number) log.get("subject_member_id")).longValue()).isEqualTo(memberId);
        assertThat((String) log.get("summary")).startsWith("홍길동이 ");
    }

    @Test
    void 비밀번호가_틀린_로그인_실패는_요청이_거절되어도_기록한다() throws Exception {
        insertMember(EMAIL, "홍길동", "MEMBER", "ACTIVE", Instant.now());

        loginRequest(EMAIL, "wrong-password1!").andExpect(status().isUnauthorized());

        Map<String, Object> log = onlyLog("LOGIN_FAILED");
        long memberId = idOf(EMAIL);
        assertThat(((Number) log.get("actor_id")).longValue()).isEqualTo(memberId);
        assertThat(log.get("actor_role")).isEqualTo("MEMBER");
        assertThat(((Number) log.get("subject_member_id")).longValue()).isEqualTo(memberId);
        assertThat((String) log.get("summary")).contains("u***@example.com")
                .doesNotContain(EMAIL).doesNotContain("wrong-password1!");
        assertThat(logs("LOGIN")).isEmpty();
    }

    @Test
    void 가입되지_않은_이메일의_로그인_실패는_행위자가_없다() throws Exception {
        loginRequest("nobody@example.com", PASSWORD).andExpect(status().isUnauthorized());

        Map<String, Object> log = onlyLog("LOGIN_FAILED");
        assertThat(log.get("actor_id")).isNull();
        assertThat(log.get("actor_role")).isNull();
        assertThat(log.get("subject_member_id")).isNull();
        assertThat((String) log.get("summary")).contains("n***@example.com").doesNotContain("nobody@example.com");
    }

    @Test
    void 비활성_계정의_로그인_실패도_기록한다() throws Exception {
        insertMember(EMAIL, "홍길동", "MEMBER", "INACTIVE", Instant.now());

        loginRequest(EMAIL, PASSWORD).andExpect(status().isUnauthorized());

        Map<String, Object> log = onlyLog("LOGIN_FAILED");
        assertThat(((Number) log.get("actor_id")).longValue()).isEqualTo(idOf(EMAIL));
    }

    @Test
    void 로그아웃을_기록한다() throws Exception {
        insertMember(EMAIL, "홍길동", "MEMBER", "ACTIVE", Instant.now());
        MvcResult result = loginRequest(EMAIL, PASSWORD).andExpect(status().isOk()).andReturn();
        String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        Map<String, Object> log = onlyLog("LOGOUT");
        assertThat(((Number) log.get("actor_id")).longValue()).isEqualTo(idOf(EMAIL));
    }

    @Test
    void 토큰_재발급은_기록하지_않는다() throws Exception {
        insertMember(EMAIL, "홍길동", "MEMBER", "ACTIVE", Instant.now());
        MvcResult result = loginRequest(EMAIL, PASSWORD).andExpect(status().isOk()).andReturn();
        String refresh = result.getResponse().getCookie("refreshToken").getValue();

        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", refresh)))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM activity_logs WHERE action <> 'LOGIN'",
                Integer.class)).isZero();
    }

    @Test
    void 비밀번호_변경을_기록하고_비밀번호는_남기지_않는다() throws Exception {
        insertMember(EMAIL, "홍길동", "MEMBER", "ACTIVE", Instant.now());
        String token = login(EMAIL);

        mockMvc.perform(put("/members/me/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"newPassword1!\"}"))
                .andExpect(status().isNoContent());

        Map<String, Object> log = onlyLog("PASSWORD_CHANGED");
        assertThat(((Number) log.get("actor_id")).longValue()).isEqualTo(idOf(EMAIL));
        assertThat((String) log.get("summary")).doesNotContain("newPassword1!").doesNotContain(PASSWORD);
        assertThat(String.valueOf(log.get("changes"))).isEqualTo("[]");
    }

    @Test
    void 현재_비밀번호가_틀린_변경은_기록하지_않는다() throws Exception {
        insertMember(EMAIL, "홍길동", "MEMBER", "ACTIVE", Instant.now());
        String token = login(EMAIL);

        mockMvc.perform(put("/members/me/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-password1!\",\"newPassword\":\"newPassword1!\"}"))
                .andExpect(status().isBadRequest());

        assertThat(logs("PASSWORD_CHANGED")).isEmpty();
    }

    @Test
    void 비밀번호_재설정을_기록한다() throws Exception {
        long memberId = insertMember(EMAIL, "홍길동", "MEMBER", "ACTIVE", Instant.now());
        jdbc.update("""
                INSERT INTO password_reset_tokens (member_id, token_hash, expires_at, created_at)
                VALUES (?, ?, now() + interval '1 hour', now())
                """, memberId, sha256("prt_valid"));

        mockMvc.perform(post("/auth/password-resets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"prt_valid\",\"newPassword\":\"newPassword1!\"}"))
                .andExpect(status().isNoContent());

        Map<String, Object> log = onlyLog("PASSWORD_RESET");
        assertThat(((Number) log.get("actor_id")).longValue()).isEqualTo(memberId);
        assertThat((String) log.get("summary")).doesNotContain("prt_valid").doesNotContain("newPassword1!");
    }

    @Test
    void 회원탈퇴를_기록한다() throws Exception {
        insertMember(EMAIL, "홍길동", "MEMBER", "ACTIVE", Instant.now());
        String token = login(EMAIL);

        mockMvc.perform(delete("/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        Map<String, Object> log = onlyLog("ACCOUNT_WITHDRAWN");
        assertThat(((Number) log.get("actor_id")).longValue()).isEqualTo(idOf(EMAIL));
        assertThat(((Number) log.get("subject_member_id")).longValue()).isEqualTo(idOf(EMAIL));
        assertThat((String) log.get("summary")).contains("홍길동");
    }
}
