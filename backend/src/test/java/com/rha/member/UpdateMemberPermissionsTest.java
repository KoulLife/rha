package com.rha.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class UpdateMemberPermissionsTest extends MemberAdminTestSupport {

    private static final String ADMIN = "admin@example.com";
    private static final String INSTRUCTOR = "t@example.com";
    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    void 관리자는_강사에게_권한을_부여할_수_있다() throws Exception {
        long adminId = insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long instructorId = insertMember(INSTRUCTOR, "강사", "INSTRUCTOR", "ACTIVE", BASE);
        String token = login(ADMIN);

        replace(token, instructorId, "[\"MEMBER_READ\"]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.length()").value(1))
                .andExpect(jsonPath("$.permissions[0]").value("MEMBER_READ"));

        assertThat(codes(instructorId)).containsExactly("MEMBER_READ");
        assertThat(jdbc.queryForObject(
                "SELECT granted_by FROM member_permissions WHERE member_id = ?", Long.class, instructorId))
                .isEqualTo(adminId);
    }

    @Test
    void 부여한_권한은_토큰을_다시_발급받지_않아도_즉시_반영된다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long instructorId = insertMember(INSTRUCTOR, "강사", "INSTRUCTOR", "ACTIVE", BASE);
        String adminToken = login(ADMIN);
        String instructorToken = login(INSTRUCTOR);

        mockMvc.perform(get("/members").header(HttpHeaders.AUTHORIZATION, "Bearer " + instructorToken))
                .andExpect(status().isForbidden());

        replace(adminToken, instructorId, "[\"MEMBER_READ\"]").andExpect(status().isOk());

        mockMvc.perform(get("/members").header(HttpHeaders.AUTHORIZATION, "Bearer " + instructorToken))
                .andExpect(status().isOk());

        replace(adminToken, instructorId, "[]").andExpect(status().isOk());

        mockMvc.perform(get("/members").header(HttpHeaders.AUTHORIZATION, "Bearer " + instructorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void 빈_배열이면_모든_권한을_회수한다() throws Exception {
        long adminId = insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long instructorId = insertMember(INSTRUCTOR, "강사", "INSTRUCTOR", "ACTIVE", BASE);
        long otherId = insertMember("other@example.com", "다른강사", "INSTRUCTOR", "ACTIVE", BASE);
        grant(instructorId, "MEMBER_READ", adminId);
        grant(instructorId, "LESSON_UPDATE:GROUP", adminId);
        grant(otherId, "MEMBER_READ", adminId);
        String token = login(ADMIN);

        replace(token, instructorId, "[]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").isEmpty());

        assertThat(codes(instructorId)).isEmpty();
        assertThat(codes(otherId)).containsExactly("MEMBER_READ");
    }

    @Test
    void 교체_후에도_유지되는_권한은_부여자와_부여_시각이_그대로다() throws Exception {
        long adminId = insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long firstAdminId = insertMember("first@example.com", "첫관리자", "ADMIN", "ACTIVE", BASE);
        long instructorId = insertMember(INSTRUCTOR, "강사", "INSTRUCTOR", "ACTIVE", BASE);
        jdbc.update("""
                INSERT INTO member_permissions (member_id, permission_code, granted_by, created_at)
                VALUES (?, 'MEMBER_READ', ?, ?)
                """, instructorId, firstAdminId, Timestamp.from(BASE));
        String token = login(ADMIN);

        replace(token, instructorId, "[\"MEMBER_READ\"]").andExpect(status().isOk());

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT granted_by, created_at FROM member_permissions WHERE member_id = ?", instructorId);
        assertThat(row.get("granted_by")).isEqualTo(firstAdminId);
        assertThat(((Timestamp) row.get("created_at")).toInstant()).isEqualTo(BASE);
        assertThat(adminId).isNotEqualTo(firstAdminId);
    }

    @Test
    void 목록에_없는_기존_권한은_교체하면_회수된다() throws Exception {
        long adminId = insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long instructorId = insertMember(INSTRUCTOR, "강사", "INSTRUCTOR", "ACTIVE", BASE);
        grant(instructorId, "LESSON_UPDATE:GROUP", adminId);
        String token = login(ADMIN);

        replace(token, instructorId, "[\"MEMBER_READ\"]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[0]").value("MEMBER_READ"));

        assertThat(codes(instructorId)).containsExactly("MEMBER_READ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"INACTIVE", "WITHDRAWN", "RESIGNED"})
    void ACTIVE가_아닌_강사에게도_설정할_수_있다(String memberStatus) throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long instructorId = insertMember(INSTRUCTOR, "강사", "INSTRUCTOR", memberStatus, BASE);
        String token = login(ADMIN);

        replace(token, instructorId, "[\"MEMBER_READ\"]").andExpect(status().isOk());

        assertThat(codes(instructorId)).containsExactly("MEMBER_READ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBER", "ADMIN"})
    void 강사가_아니면_400_PERMISSION_TARGET_INVALID이다(String role) throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long targetId = insertMember("target@example.com", "대상", role, "ACTIVE", BASE);
        String token = login(ADMIN);

        replace(token, targetId, "[\"MEMBER_READ\"]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PERMISSION_TARGET_INVALID"));

        assertThat(codes(targetId)).isEmpty();
    }

    @Test
    void 강사가_아닌_대상에게는_빈_배열도_400이다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long memberId = insertMember("m@example.com", "회원", "MEMBER", "ACTIVE", BASE);
        String token = login(ADMIN);

        replace(token, memberId, "[]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PERMISSION_TARGET_INVALID"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[\"UNKNOWN_PERMISSION\"]",
            "[\"LESSON_CREATE:PRIVATE\"]",
            "[\"MEMBER_READ\",\"MEMBER_READ\"]",
            "[\"MEMBER_READ\",\"UNKNOWN_PERMISSION\"]"
    })
    void 목록에_없거나_중복된_코드면_400_INVALID_PERMISSION이고_기존_권한은_유지된다(String permissions) throws Exception {
        long adminId = insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long instructorId = insertMember(INSTRUCTOR, "강사", "INSTRUCTOR", "ACTIVE", BASE);
        grant(instructorId, "LESSON_UPDATE:GROUP", adminId);
        String token = login(ADMIN);

        replace(token, instructorId, permissions)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERMISSION"));

        assertThat(codes(instructorId)).containsExactly("LESSON_UPDATE:GROUP");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"permissions\":null}",
            "{\"permissions\":\"MEMBER_READ\"}",
            "{\"permissions\":[null]}",
            "{\"permissions\":[\"\"]}",
            "{\"permissions\":[\"member_read\"]}",
            "{\"permissions\":[\"MEMBER READ\"]}",
            "{\"permissions\":[1]}",
            "not json"
    })
    void 요청_형식이_잘못되면_400_INVALID_INPUT이다(String body) throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long instructorId = insertMember(INSTRUCTOR, "강사", "INSTRUCTOR", "ACTIVE", BASE);
        String token = login(ADMIN);

        send(token, instructorId, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 없는_회원이면_404를_응답한다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        String token = login(ADMIN);

        replace(token, 999999L, "[\"MEMBER_READ\"]")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 대상이_강사가_아니면_권한_코드_검증보다_먼저_확인한다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long memberId = insertMember("m@example.com", "회원", "MEMBER", "ACTIVE", BASE);
        String token = login(ADMIN);

        replace(token, memberId, "[\"UNKNOWN_PERMISSION\"]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PERMISSION_TARGET_INVALID"));
    }

    @Test
    void 없는_회원이면_권한_코드가_잘못되어도_404이다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        String token = login(ADMIN);

        replace(token, 999999L, "[\"UNKNOWN_PERMISSION\"]").andExpect(status().isNotFound());
    }

    @Test
    void 요청_형식이_잘못되면_없는_회원이어도_404보다_400이_먼저다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        String token = login(ADMIN);

        send(token, 999999L, "{\"permissions\":[\"member_read\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void MEMBER_READ를_가진_강사도_403_PERMISSION_DENIED이다() throws Exception {
        long adminId = insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long instructorId = insertMember(INSTRUCTOR, "강사", "INSTRUCTOR", "ACTIVE", BASE);
        grant(instructorId, "MEMBER_READ", adminId);
        String token = login(INSTRUCTOR);

        replace(token, instructorId, "[\"MEMBER_READ\"]")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void 관리자가_아니면_잘못된_본문이어도_400이_아니라_403이다() throws Exception {
        long memberId = insertMember("m@example.com", "회원", "MEMBER", "ACTIVE", BASE);
        String token = login("m@example.com");

        send(token, memberId, "{\"permissions\":[\"member_read\"]}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void 인증하지_않으면_401_UNAUTHENTICATED를_응답한다() throws Exception {
        mockMvc.perform(put("/members/1/permissions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void memberId가_숫자가_아니면_400_INVALID_INPUT을_응답한다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        String token = login(ADMIN);

        mockMvc.perform(put("/members/abc/permissions").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"permissions\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private ResultActions replace(String accessToken, long memberId, String permissionsJson) throws Exception {
        return send(accessToken, memberId, "{\"permissions\":" + permissionsJson + "}");
    }

    private ResultActions send(String accessToken, long memberId, String body) throws Exception {
        return mockMvc.perform(put("/members/" + memberId + "/permissions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private List<String> codes(long memberId) {
        return jdbc.queryForList(
                "SELECT permission_code FROM member_permissions WHERE member_id = ? ORDER BY permission_code",
                String.class, memberId);
    }
}
