package com.rha.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.rha.IntegrationTestSupport;
import com.rha.auth.RecordingPasswordResetMailer;
import com.rha.auth.TestMailerConfig;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
@Import(TestMailerConfig.class)
class CreateMemberTest extends IntegrationTestSupport {

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String NEW_EMAIL = "instructor@example.com";
    private static final String PASSWORD = "password123!";

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
    void 관리자가_강사를_등록하면_201과_ACTIVE_상태의_회원_정보를_응답한다() throws Exception {
        String token = loginAs(ADMIN_EMAIL, "ADMIN");

        create(token, body(NEW_EMAIL, "INSTRUCTOR"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value(NEW_EMAIL))
                .andExpect(jsonPath("$.role").value("INSTRUCTOR"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.permissions").isEmpty())
                .andExpect(jsonPath("$.name").value("김강사"))
                .andExpect(jsonPath("$.phoneNumber").value("01012345678"))
                .andExpect(jsonPath("$.birthDate").value("1990-01-31"))
                .andExpect(jsonPath("$.address").value("서울특별시 강남구"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void 비밀번호_없이_저장된다() throws Exception {
        String token = loginAs(ADMIN_EMAIL, "ADMIN");

        create(token, body(NEW_EMAIL, "INSTRUCTOR")).andExpect(status().isCreated());

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT password_hash, role, status FROM members WHERE email = ?", NEW_EMAIL);
        assertThat(row.get("password_hash")).isNull();
        assertThat(row.get("role")).isEqualTo("INSTRUCTOR");
        assertThat(row.get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void 관리자_역할도_등록할_수_있다() throws Exception {
        String token = loginAs(ADMIN_EMAIL, "ADMIN");

        create(token, body(NEW_EMAIL, "ADMIN"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void 주소는_선택이다() throws Exception {
        String token = loginAs(ADMIN_EMAIL, "ADMIN");

        create(token, """
                {"email":"%s","name":"김강사","phoneNumber":"01012345678","birthDate":"1990-01-31","role":"INSTRUCTOR"}
                """.formatted(NEW_EMAIL))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.address").doesNotExist());
    }

    @Test
    void 등록하면_초대_메일을_발송하고_토큰은_해시로_30분_유효하게_저장한다() throws Exception {
        String token = loginAs(ADMIN_EMAIL, "ADMIN");
        Instant before = Instant.now();

        create(token, body(NEW_EMAIL, "INSTRUCTOR")).andExpect(status().isCreated());

        assertThat(mailer.invitations()).hasSize(1);
        assertThat(mailer.invitations().get(0).email()).isEqualTo(NEW_EMAIL);
        String sentToken = mailer.invitations().get(0).token();
        assertThat(sentToken).startsWith("prt_");

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT t.token_hash, t.expires_at, t.used_at, m.email
                FROM password_reset_tokens t JOIN members m ON m.id = t.member_id
                """);
        assertThat(row.get("email")).isEqualTo(NEW_EMAIL);
        assertThat((String) row.get("token_hash")).isNotBlank().isNotEqualTo(sentToken);
        assertThat(row.get("used_at")).isNull();
        Instant expiresAt = ((Timestamp) row.get("expires_at")).toInstant();
        assertThat(expiresAt).isBetween(before.plus(29, ChronoUnit.MINUTES), Instant.now().plus(31, ChronoUnit.MINUTES));
    }

    @Test
    void 비밀번호를_설정하기_전에는_로그인할_수_없다() throws Exception {
        String token = loginAs(ADMIN_EMAIL, "ADMIN");
        create(token, body(NEW_EMAIL, "INSTRUCTOR")).andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + NEW_EMAIL + "\",\"password\":\"anything123!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void 초대_메일의_토큰으로_비밀번호를_설정하면_로그인할_수_있다() throws Exception {
        String token = loginAs(ADMIN_EMAIL, "ADMIN");
        create(token, body(NEW_EMAIL, "INSTRUCTOR")).andExpect(status().isCreated());
        String inviteToken = mailer.invitations().get(0).token();

        mockMvc.perform(post("/auth/password-resets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + inviteToken + "\",\"newPassword\":\"newPassword123!\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + NEW_EMAIL + "\",\"password\":\"newPassword123!\"}"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBER", "INSTRUCTOR"})
    void 관리자가_아니면_403_PERMISSION_DENIED를_응답하고_아무것도_만들지_않는다(String role) throws Exception {
        String token = loginAs("caller@example.com", role);

        create(token, body(NEW_EMAIL, "INSTRUCTOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        assertThat(count("SELECT count(*) FROM members WHERE email = ?", NEW_EMAIL)).isZero();
        assertThat(mailer.invitations()).isEmpty();
    }

    @Test
    void 인증하지_않으면_401_UNAUTHENTICATED를_응답한다() throws Exception {
        mockMvc.perform(post("/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(NEW_EMAIL, "INSTRUCTOR")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "INACTIVE", "WITHDRAWN", "RESIGNED"})
    void 이미_있는_이메일이면_계정_상태와_무관하게_409_EMAIL_ALREADY_EXISTS를_응답한다(String existingStatus) throws Exception {
        String token = loginAs(ADMIN_EMAIL, "ADMIN");
        insertMember(NEW_EMAIL, "MEMBER", existingStatus);

        create(token, body(NEW_EMAIL, "INSTRUCTOR"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));

        assertThat(mailer.invitations()).isEmpty();
        assertThat(count("SELECT count(*) FROM password_reset_tokens")).isZero();
    }

    @Test
    void 일반_회원_역할로는_등록할_수_없고_400_INVALID_INPUT을_응답한다() throws Exception {
        String token = loginAs(ADMIN_EMAIL, "ADMIN");

        create(token, body(NEW_EMAIL, "MEMBER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.invalidParams[0].field").value("role"));

        assertThat(count("SELECT count(*) FROM members WHERE email = ?", NEW_EMAIL)).isZero();
    }

    @Test
    void 이메일_형식이_올바르지_않으면_400_INVALID_INPUT을_응답한다() throws Exception {
        String token = loginAs(ADMIN_EMAIL, "ADMIN");

        create(token, body("not-an-email", "INSTRUCTOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.invalidParams[0].field").value("email"));
    }

    @Test
    void 휴대폰_번호_형식이_올바르지_않으면_400_INVALID_INPUT을_응답한다() throws Exception {
        String token = loginAs(ADMIN_EMAIL, "ADMIN");

        create(token, body(NEW_EMAIL, "INSTRUCTOR").replace("01012345678", "010-1234-5678"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.invalidParams[0].field").value("phoneNumber"));
    }

    @Test
    void 필수_값이_없으면_400_INVALID_INPUT을_응답한다() throws Exception {
        String token = loginAs(ADMIN_EMAIL, "ADMIN");

        create(token, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 메일_발송에_실패하면_계정도_만들어지지_않는다() throws Exception {
        String token = loginAs(ADMIN_EMAIL, "ADMIN");
        mailer.failOnSend(true);

        assertThatThrownBy(() -> create(token, body(NEW_EMAIL, "INSTRUCTOR")))
                .hasRootCauseInstanceOf(IllegalStateException.class);

        assertThat(count("SELECT count(*) FROM members WHERE email = ?", NEW_EMAIL)).isZero();
        assertThat(count("SELECT count(*) FROM password_reset_tokens")).isZero();
    }

    private ResultActions create(String accessToken, String json) throws Exception {
        return mockMvc.perform(post("/members")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String body(String email, String role) {
        return """
                {"email":"%s","name":"김강사","phoneNumber":"01012345678","birthDate":"1990-01-31",
                 "address":"서울특별시 강남구","role":"%s"}
                """.formatted(email, role);
    }

    private String loginAs(String email, String role) throws Exception {
        insertMember(email, role, "ACTIVE");
        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    private void insertMember(String email, String role, String memberStatus) {
        jdbc.update("""
                INSERT INTO members (email, password_hash, name, phone_number, birth_date, role, status,
                                     created_at, updated_at)
                VALUES (?, ?, '홍길동', '01012345678', DATE '1990-01-01', ?, ?, now(), now())
                """, email, new BCryptPasswordEncoder().encode(PASSWORD), role, memberStatus);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }
}
