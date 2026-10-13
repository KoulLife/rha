package com.rha.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.rha.IntegrationTestSupport;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
@Import(TestMailerConfig.class)
class SignUpTest extends IntegrationTestSupport {

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "password123!";

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
    void 유효한_요청이면_201과_가입된_회원을_응답한다() throws Exception {
        String token = verifiedToken(EMAIL);

        signUp(validBody(EMAIL, token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.permissions").isEmpty())
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.phoneNumber").value("01012345678"))
                .andExpect(jsonPath("$.birthDate").value("1990-01-31"))
                .andExpect(jsonPath("$.address").value("서울특별시 강남구 테헤란로 123"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void 비밀번호는_해시로_저장된다() throws Exception {
        String token = verifiedToken(EMAIL);

        signUp(validBody(EMAIL, token)).andExpect(status().isCreated());

        String stored = jdbc.queryForObject("SELECT password_hash FROM members WHERE email = ?", String.class, EMAIL);
        assertThat(stored).isNotEqualTo(PASSWORD);
        assertThat(new BCryptPasswordEncoder().matches(PASSWORD, stored)).isTrue();
    }

    @Test
    void 요청에_역할을_담아도_MEMBER로_가입된다() throws Exception {
        String token = verifiedToken(EMAIL);
        Map<String, Object> body = validBody(EMAIL, token);
        body.put("role", "ADMIN");

        signUp(body).andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("MEMBER"));

        assertThat(jdbc.queryForObject("SELECT role FROM members WHERE email = ?", String.class, EMAIL))
                .isEqualTo("MEMBER");
    }

    @Test
    void 이미_사용한_토큰으로는_다시_가입할_수_없다() throws Exception {
        String token = verifiedToken(EMAIL);
        signUp(validBody(EMAIL, token)).andExpect(status().isCreated());

        signUp(validBody(EMAIL, token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
    }

    @Test
    void 존재하지_않는_토큰이면_400_INVALID_VERIFICATION_TOKEN을_응답한다() throws Exception {
        signUp(validBody(EMAIL, "evt_unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM members", Integer.class)).isZero();
    }

    @Test
    void 다른_이메일의_토큰이면_400_INVALID_VERIFICATION_TOKEN을_응답한다() throws Exception {
        String token = verifiedToken("other@example.com");

        signUp(validBody(EMAIL, token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
    }

    @Test
    void 만료된_토큰이면_400_INVALID_VERIFICATION_TOKEN을_응답한다() throws Exception {
        String token = verifiedToken(EMAIL);
        // DB 컨테이너와 호스트의 시계 차이에 영향받지 않도록 충분히 과거로 설정한다.
        jdbc.update("UPDATE email_verifications SET token_expires_at = now() - interval '1 day' WHERE email = ?",
                EMAIL);

        signUp(validBody(EMAIL, token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
    }

    @Test
    void 이미_가입된_이메일이면_409_EMAIL_ALREADY_EXISTS를_응답한다() throws Exception {
        String token = verifiedToken(EMAIL);
        jdbc.update("""
                INSERT INTO members (email, password_hash, name, phone_number, birth_date, role, status,
                                     created_at, updated_at)
                VALUES (?, 'hash', '기존회원', '01099999999', DATE '1985-05-05', 'MEMBER', 'WITHDRAWN', now(), now())
                """, EMAIL);

        signUp(validBody(EMAIL, token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void 필드_형식이_올바르지_않으면_400_INVALID_INPUT과_invalidParams를_응답한다() throws Exception {
        String token = verifiedToken(EMAIL);
        Map<String, Object> body = validBody(EMAIL, token);
        body.put("password", "short");
        body.put("phoneNumber", "010-1234-5678");

        signUp(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.invalidParams[?(@.field == 'password')]").isNotEmpty())
                .andExpect(jsonPath("$.invalidParams[?(@.field == 'phoneNumber')]").isNotEmpty())
                .andExpect(jsonPath("$.invalidParams[?(@.field == 'password')].value").isEmpty());
    }

    private Map<String, Object> validBody(String email, String token) {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", PASSWORD);
        body.put("name", "홍길동");
        body.put("phoneNumber", "01012345678");
        body.put("birthDate", "1990-01-31");
        body.put("address", "서울특별시 강남구 테헤란로 123");
        body.put("emailVerificationToken", token);
        return body;
    }

    private ResultActions signUp(Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/auth/sign-up")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(body)));
    }

    /** 이메일 인증 코드 발송과 확인을 거쳐 emailVerificationToken을 발급받는다. */
    private String verifiedToken(String email) throws Exception {
        mockMvc.perform(post("/auth/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isNoContent());
        String code = mailer.sent().get(mailer.sent().size() - 1).code();

        String response = mockMvc.perform(post("/auth/email-verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.emailVerificationToken");
    }
}
