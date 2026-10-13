package com.rha.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
class MyProfileTest extends IntegrationTestSupport {

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "password123!";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Value("${rha.auth.jwt.secret}")
    String jwtSecret;

    @Test
    void 유효한_AccessToken이면_200과_내_정보를_응답한다() throws Exception {
        long memberId = insertMember("MEMBER");
        String accessToken = loginAndGetAccessToken();

        getMyProfile(accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(memberId))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.permissions").isArray())
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
    void 토큰의_회원_정보만_조회된다() throws Exception {
        insertMember("MEMBER");
        long otherId = insertOtherMember();
        String accessToken = loginAndGetAccessToken();

        String body = getMyProfile(accessToken).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();

        assertThat(JsonPath.<Integer>read(body, "$.id").longValue()).isNotEqualTo(otherId);
        assertThat(JsonPath.<String>read(body, "$.email")).isEqualTo(EMAIL);
    }

    @Test
    void 강사_역할도_자신의_정보를_조회할_수_있다() throws Exception {
        insertMember("INSTRUCTOR");
        String accessToken = loginAndGetAccessToken();

        getMyProfile(accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("INSTRUCTOR"));
    }

    @Test
    void Authorization_헤더가_없으면_401_UNAUTHENTICATED를_응답한다() throws Exception {
        mockMvc.perform(get("/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/members/me"))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void Bearer_방식이_아니면_401_UNAUTHENTICATED를_응답한다() throws Exception {
        insertMember("MEMBER");
        String accessToken = loginAndGetAccessToken();

        mockMvc.perform(get("/members/me").header(HttpHeaders.AUTHORIZATION, "Basic " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 형식이_올바르지_않은_토큰이면_401_UNAUTHENTICATED를_응답한다() throws Exception {
        getMyProfile("not-a-jwt")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 만료된_토큰이면_401_UNAUTHENTICATED를_응답한다() throws Exception {
        long memberId = insertMember("MEMBER");
        Instant now = Instant.now();
        String expired = tokenFor(memberId, "MEMBER", jwtSecret, now.minus(Duration.ofHours(2)),
                now.minus(Duration.ofHours(1)));

        getMyProfile(expired)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 다른_키로_서명된_토큰이면_401_UNAUTHENTICATED를_응답한다() throws Exception {
        long memberId = insertMember("MEMBER");
        Instant now = Instant.now();
        String forged = tokenFor(memberId, "ADMIN", "another-secret-key-that-is-long-enough-32b", now,
                now.plus(Duration.ofHours(1)));

        getMyProfile(forged)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 서명이_없는_토큰이면_401_UNAUTHENTICATED를_응답한다() throws Exception {
        long memberId = insertMember("MEMBER");
        Instant now = Instant.now();
        String unsigned = Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim("role", "ADMIN")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofHours(1))))
                .compact();

        getMyProfile(unsigned)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 존재하지_않는_회원의_토큰이면_401_UNAUTHENTICATED를_응답한다() throws Exception {
        Instant now = Instant.now();
        String token = tokenFor(999_999L, "MEMBER", jwtSecret, now, now.plus(Duration.ofHours(1)));

        getMyProfile(token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 인증이_필요_없는_API는_잘못된_토큰이_붙어도_처리된다() throws Exception {
        insertMember("MEMBER");

        mockMvc.perform(post("/auth/login")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void 인증된_사용자가_없는_경로를_요청하면_401이_아니라_404를_응답한다() throws Exception {
        insertMember("MEMBER");
        String accessToken = loginAndGetAccessToken();

        mockMvc.perform(get("/no-such-path").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    private ResultActions getMyProfile(String accessToken) throws Exception {
        return mockMvc.perform(get("/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
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

    private String tokenFor(long memberId, String role, String secret, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim("role", role)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private long insertMember(String role) {
        jdbc.update("""
                INSERT INTO members (email, password_hash, name, phone_number, birth_date, address, role, status,
                                     created_at, updated_at)
                VALUES (?, ?, '홍길동', '01012345678', DATE '1990-01-31', '서울특별시 강남구 테헤란로 123', ?, 'ACTIVE',
                        now(), now())
                """, EMAIL, new BCryptPasswordEncoder().encode(PASSWORD), role);
        return jdbc.queryForObject("SELECT id FROM members WHERE email = ?", Long.class, EMAIL);
    }

    private long insertOtherMember() {
        jdbc.update("""
                INSERT INTO members (email, password_hash, name, phone_number, birth_date, role, status,
                                     created_at, updated_at)
                VALUES ('other@example.com', 'hash', '김철수', '01099998888', DATE '1985-05-05', 'MEMBER', 'ACTIVE',
                        now(), now())
                """);
        return jdbc.queryForObject("SELECT id FROM members WHERE email = 'other@example.com'", Long.class);
    }
}
