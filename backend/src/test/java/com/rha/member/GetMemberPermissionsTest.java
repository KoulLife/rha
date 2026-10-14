package com.rha.member;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

class GetMemberPermissionsTest extends MemberAdminTestSupport {

    private static final String ADMIN = "admin@example.com";
    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    void 관리자는_강사의_보유_권한을_코드_오름차순으로_조회할_수_있다() throws Exception {
        long adminId = insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long instructorId = insertMember("t@example.com", "강사", "INSTRUCTOR", "ACTIVE", BASE);
        grant(instructorId, "MEMBER_READ", adminId);
        grant(instructorId, "LESSON_UPDATE:GROUP", adminId);
        grant(instructorId, "LESSON_CREATE:PRIVATE", adminId);
        String token = login(ADMIN);

        permissions(token, instructorId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.length()").value(3))
                .andExpect(jsonPath("$.permissions[0]").value("LESSON_CREATE:PRIVATE"))
                .andExpect(jsonPath("$.permissions[1]").value("LESSON_UPDATE:GROUP"))
                .andExpect(jsonPath("$.permissions[2]").value("MEMBER_READ"));
    }

    @Test
    void 권한이_없는_강사는_빈_목록을_반환한다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long instructorId = insertMember("t@example.com", "강사", "INSTRUCTOR", "ACTIVE", BASE);
        String token = login(ADMIN);

        permissions(token, instructorId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").isEmpty());
    }

    @Test
    void 관리자_계정은_빈_목록을_반환한다() throws Exception {
        long adminId = insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        String token = login(ADMIN);

        permissions(token, adminId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").isEmpty());
    }

    @Test
    void 일반_회원은_빈_목록을_반환한다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long memberId = insertMember("m@example.com", "회원", "MEMBER", "ACTIVE", BASE);
        String token = login(ADMIN);

        permissions(token, memberId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"INACTIVE", "WITHDRAWN", "RESIGNED"})
    void ACTIVE가_아닌_강사의_권한도_조회할_수_있다(String memberStatus) throws Exception {
        long adminId = insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long instructorId = insertMember("t@example.com", "강사", "INSTRUCTOR", memberStatus, BASE);
        grant(instructorId, "MEMBER_READ", adminId);
        String token = login(ADMIN);

        permissions(token, instructorId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[0]").value("MEMBER_READ"));
    }

    @Test
    void 없는_회원이면_404를_응답한다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        String token = login(ADMIN);

        permissions(token, 999999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void MEMBER_READ를_가진_강사도_403_PERMISSION_DENIED이다() throws Exception {
        long adminId = insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long instructorId = insertMember("t@example.com", "강사", "INSTRUCTOR", "ACTIVE", BASE);
        grant(instructorId, "MEMBER_READ", adminId);
        String token = login("t@example.com");

        permissions(token, instructorId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void 권한이_없으면_없는_회원이어도_404가_아니라_403이다() throws Exception {
        insertMember("m@example.com", "회원", "MEMBER", "ACTIVE", BASE);
        String token = login("m@example.com");

        permissions(token, 999999L).andExpect(status().isForbidden());
    }

    @Test
    void 인증하지_않으면_401_UNAUTHENTICATED를_응답한다() throws Exception {
        mockMvc.perform(get("/members/1/permissions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void memberId가_숫자가_아니면_400_INVALID_INPUT을_응답한다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        String token = login(ADMIN);

        mockMvc.perform(get("/members/abc/permissions").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private ResultActions permissions(String accessToken, long memberId) throws Exception {
        return mockMvc.perform(get("/members/" + memberId + "/permissions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }
}
