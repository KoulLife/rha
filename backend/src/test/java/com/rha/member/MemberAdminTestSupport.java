package com.rha.member;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.rha.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** 회원 관리(관리자) 조회 테스트가 공유하는 데이터 준비와 로그인 도우미. */
@AutoConfigureMockMvc
abstract class MemberAdminTestSupport extends IntegrationTestSupport {

    static final String PASSWORD = "password123!";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    long insertMember(String email, String name, String role, String memberStatus, Instant createdAt) {
        return jdbc.queryForObject("""
                INSERT INTO members (email, password_hash, name, phone_number, birth_date, role, status,
                                     created_at, updated_at)
                VALUES (?, ?, ?, '01012345678', DATE '1990-01-31', ?, ?, ?, ?)
                RETURNING id
                """, Long.class, email, new BCryptPasswordEncoder().encode(PASSWORD), name, role, memberStatus,
                Timestamp.from(createdAt), Timestamp.from(createdAt));
    }

    long insertMember(String email, String role) {
        return insertMember(email, "홍길동", role, "ACTIVE", Instant.now());
    }

    void grant(long memberId, String permissionCode, long grantedBy) {
        jdbc.update("""
                INSERT INTO member_permissions (member_id, permission_code, granted_by, created_at)
                VALUES (?, ?, ?, now())
                """, memberId, permissionCode, grantedBy);
    }

    String login(String email) throws Exception {
        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }
}
