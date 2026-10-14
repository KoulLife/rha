package com.rha;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** 인증이 필요한 API 테스트가 공유하는 데이터 준비와 로그인 도우미. */
@AutoConfigureMockMvc
public abstract class ApiTestSupport extends IntegrationTestSupport {

    protected static final String PASSWORD = "password123!";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    protected long insertMember(String email, String name, String role, String memberStatus, Instant createdAt) {
        return jdbc.queryForObject("""
                INSERT INTO members (email, password_hash, name, phone_number, birth_date, role, status,
                                     created_at, updated_at)
                VALUES (?, ?, ?, '01012345678', DATE '1990-01-31', ?, ?, ?, ?)
                RETURNING id
                """, Long.class, email, new BCryptPasswordEncoder().encode(PASSWORD), name, role, memberStatus,
                Timestamp.from(createdAt), Timestamp.from(createdAt));
    }

    protected long insertMember(String email, String role) {
        return insertMember(email, "홍길동", role, "ACTIVE", Instant.now());
    }

    protected void grant(long memberId, String permissionCode, long grantedBy) {
        jdbc.update("""
                INSERT INTO member_permissions (member_id, permission_code, granted_by, created_at)
                VALUES (?, ?, ?, now())
                """, memberId, permissionCode, grantedBy);
    }

    protected String login(String email) throws Exception {
        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    /** 관리자를 만들고 로그인해 액세스 토큰을 반환한다. */
    protected String adminToken() throws Exception {
        insertMember("admin@example.com", "관리자", "ADMIN", "ACTIVE", Instant.parse("2026-10-01T00:00:00Z"));
        return login("admin@example.com");
    }

    protected String memberToken() throws Exception {
        insertMember("m@example.com", "회원", "MEMBER", "ACTIVE", Instant.parse("2026-10-01T00:00:00Z"));
        return login("m@example.com");
    }

    protected String instructorToken() throws Exception {
        insertMember("t@example.com", "강사", "INSTRUCTOR", "ACTIVE", Instant.parse("2026-10-01T00:00:00Z"));
        return login("t@example.com");
    }
}
