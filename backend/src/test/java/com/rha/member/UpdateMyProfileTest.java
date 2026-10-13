package com.rha.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.rha.IntegrationTestSupport;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class UpdateMyProfileTest extends IntegrationTestSupport {

    private static final String EMAIL = "user@example.com";
    private static final String OTHER_EMAIL = "other@example.com";
    private static final String PASSWORD = "password123!";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Value("${rha.auth.jwt.secret}")
    String jwtSecret;

    @Test
    void 모든_필드를_수정하면_200과_수정된_회원_정보를_응답한다() throws Exception {
        insertMember(EMAIL);
        String token = loginAndGetAccessToken();

        update(token, """
                {"name":"김철수","phoneNumber":"01099998888","birthDate":"1985-05-05","address":"부산광역시 해운대구"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.name").value("김철수"))
                .andExpect(jsonPath("$.phoneNumber").value("01099998888"))
                .andExpect(jsonPath("$.birthDate").value("1985-05-05"))
                .andExpect(jsonPath("$.address").value("부산광역시 해운대구"));

        Map<String, Object> row = rowOf(EMAIL);
        assertThat(row.get("name")).isEqualTo("김철수");
        assertThat(row.get("phone_number")).isEqualTo("01099998888");
        assertThat(row.get("birth_date").toString()).isEqualTo("1985-05-05");
        assertThat(row.get("address")).isEqualTo("부산광역시 해운대구");
    }

    @Test
    void 전달한_필드만_수정하고_나머지는_유지한다() throws Exception {
        insertMember(EMAIL);
        String token = loginAndGetAccessToken();

        update(token, "{\"name\":\"김철수\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("김철수"))
                .andExpect(jsonPath("$.phoneNumber").value("01012345678"))
                .andExpect(jsonPath("$.birthDate").value("1990-01-31"))
                .andExpect(jsonPath("$.address").value("서울특별시 강남구 테헤란로 123"));
    }

    @Test
    void 수정하면_수정_시각이_갱신된다() throws Exception {
        insertMember(EMAIL);
        String token = loginAndGetAccessToken();
        jdbc.update("UPDATE members SET updated_at = now() - interval '1 day' WHERE email = ?", EMAIL);
        Object before = rowOf(EMAIL).get("updated_at");

        update(token, "{\"name\":\"김철수\"}").andExpect(status().isOk());

        Object after = rowOf(EMAIL).get("updated_at");
        assertThat(((java.sql.Timestamp) after).toInstant()).isAfter(((java.sql.Timestamp) before).toInstant());
    }

    @Test
    void address에_null을_보내면_주소를_삭제한다() throws Exception {
        insertMember(EMAIL);
        String token = loginAndGetAccessToken();

        update(token, "{\"address\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").isEmpty());

        assertThat(rowOf(EMAIL).get("address")).isNull();
    }

    @Test
    void address를_보내지_않으면_주소를_유지한다() throws Exception {
        insertMember(EMAIL);
        String token = loginAndGetAccessToken();

        update(token, "{\"name\":\"김철수\"}").andExpect(status().isOk());

        assertThat(rowOf(EMAIL).get("address")).isEqualTo("서울특별시 강남구 테헤란로 123");
    }

    @Test
    void address_외_필드의_null은_전달하지_않은_것으로_본다() throws Exception {
        insertMember(EMAIL);
        String token = loginAndGetAccessToken();

        update(token, "{\"name\":null,\"phoneNumber\":\"01099998888\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.phoneNumber").value("01099998888"));
    }

    @Test
    void 기존과_같은_값을_보내도_200을_응답한다() throws Exception {
        insertMember(EMAIL);
        String token = loginAndGetAccessToken();

        update(token, "{\"name\":\"홍길동\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("홍길동"));
    }

    @Test
    void 이메일과_역할은_요청에_담아도_변경되지_않는다() throws Exception {
        insertMember(EMAIL);
        String token = loginAndGetAccessToken();

        update(token, "{\"name\":\"김철수\",\"email\":\"changed@example.com\",\"role\":\"ADMIN\",\"status\":\"INACTIVE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        Map<String, Object> row = rowOf(EMAIL);
        assertThat(row.get("role")).isEqualTo("MEMBER");
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM members WHERE email = 'changed@example.com'",
                Integer.class)).isZero();
    }

    @Test
    void 수정할_필드가_하나도_없으면_400_INVALID_INPUT을_응답한다() throws Exception {
        insertMember(EMAIL);
        String token = loginAndGetAccessToken();

        update(token, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 수정_불가_필드만_보내면_400_INVALID_INPUT을_응답한다() throws Exception {
        insertMember(EMAIL);
        String token = loginAndGetAccessToken();

        update(token, "{\"email\":\"changed@example.com\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(rowOf(EMAIL).get("email")).isEqualTo(EMAIL);
    }

    @Test
    void 필드가_모두_null이면_400_INVALID_INPUT을_응답한다() throws Exception {
        insertMember(EMAIL);
        String token = loginAndGetAccessToken();

        update(token, "{\"name\":null,\"phoneNumber\":null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 이름이_비었거나_공백뿐이면_400_INVALID_INPUT을_응답한다() throws Exception {
        insertMember(EMAIL);
        String token = loginAndGetAccessToken();

        update(token, "{\"name\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.invalidParams[?(@.field == 'name')]").isNotEmpty());
        update(token, "{\"name\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidParams[?(@.field == 'name')]").isNotEmpty());

        assertThat(rowOf(EMAIL).get("name")).isEqualTo("홍길동");
    }

    @Test
    void 필드_형식이나_길이가_올바르지_않으면_400_INVALID_INPUT과_invalidParams를_응답한다() throws Exception {
        insertMember(EMAIL);
        String token = loginAndGetAccessToken();

        update(token, "{\"name\":\"" + "가".repeat(51) + "\",\"phoneNumber\":\"010-1234-5678\","
                + "\"address\":\"" + "a".repeat(201) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.invalidParams[?(@.field == 'name')]").isNotEmpty())
                .andExpect(jsonPath("$.invalidParams[?(@.field == 'phoneNumber')]").isNotEmpty())
                .andExpect(jsonPath("$.invalidParams[?(@.field == 'address')]").isNotEmpty());
    }

    @Test
    void 날짜_형식이_올바르지_않으면_400_INVALID_INPUT을_응답한다() throws Exception {
        insertMember(EMAIL);
        String token = loginAndGetAccessToken();

        update(token, "{\"birthDate\":\"1990-13-45\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 본인의_정보만_수정된다() throws Exception {
        insertMember(EMAIL);
        insertMember(OTHER_EMAIL);
        String token = loginAndGetAccessToken();

        update(token, "{\"name\":\"김철수\"}").andExpect(status().isOk());

        assertThat(rowOf(OTHER_EMAIL).get("name")).isEqualTo("홍길동");
    }

    @Test
    void AccessToken이_없으면_401_UNAUTHENTICATED를_응답한다() throws Exception {
        insertMember(EMAIL);

        mockMvc.perform(patch("/members/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"김철수\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertThat(rowOf(EMAIL).get("name")).isEqualTo("홍길동");
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

        update(token, "{\"name\":\"김철수\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private ResultActions update(String accessToken, String json) throws Exception {
        return mockMvc.perform(patch("/members/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    /** 실제 로그인 API를 거쳐 Access Token을 발급받는다. */
    private String loginAndGetAccessToken() throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private void insertMember(String email) {
        jdbc.update("""
                INSERT INTO members (email, password_hash, name, phone_number, birth_date, address, role, status,
                                     created_at, updated_at)
                VALUES (?, ?, '홍길동', '01012345678', DATE '1990-01-31', '서울특별시 강남구 테헤란로 123', 'MEMBER',
                        'ACTIVE', now(), now())
                """, email, new BCryptPasswordEncoder().encode(PASSWORD));
    }

    private Map<String, Object> rowOf(String email) {
        return jdbc.queryForMap("SELECT * FROM members WHERE email = ?", email);
    }
}
