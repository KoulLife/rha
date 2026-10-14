package com.rha.member;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class GetMemberTest extends MemberAdminTestSupport {

    private static final String ADMIN = "admin@example.com";
    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    void 관리자는_회원_상세를_조회할_수_있다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long targetId = insertMember("target@example.com", "김회원", "MEMBER", "ACTIVE", BASE);
        String token = login(ADMIN);

        detail(token, targetId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(targetId))
                .andExpect(jsonPath("$.email").value("target@example.com"))
                .andExpect(jsonPath("$.name").value("김회원"))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.phoneNumber").value("01012345678"))
                .andExpect(jsonPath("$.birthDate").value("1990-01-31"))
                .andExpect(jsonPath("$.permissions").isEmpty())
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"INACTIVE", "WITHDRAWN", "RESIGNED"})
    void ACTIVE가_아닌_회원도_조회할_수_있다(String memberStatus) throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long targetId = insertMember("target@example.com", "김회원", "MEMBER", memberStatus, BASE);
        String token = login(ADMIN);

        detail(token, targetId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(memberStatus));
    }

    @Test
    void 부여된_권한과_퇴사일을_응답에_채운다() throws Exception {
        long adminId = insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long instructorId = insertMember("t@example.com", "강사", "INSTRUCTOR", "ACTIVE", BASE);
        grant(instructorId, "MEMBER_READ", adminId);
        grant(instructorId, "LESSON_UPDATE:GROUP", adminId);
        jdbc.update("UPDATE members SET resignation_date = DATE '2026-12-31' WHERE id = ?", instructorId);
        String token = login(ADMIN);

        detail(token, instructorId)
                .andExpect(jsonPath("$.permissions.length()").value(2))
                .andExpect(jsonPath("$.permissions[0]").value("LESSON_UPDATE:GROUP"))
                .andExpect(jsonPath("$.permissions[1]").value("MEMBER_READ"))
                .andExpect(jsonPath("$.resignationDate").value("2026-12-31"));
    }

    @Test
    void 내_정보_조회와_수정_응답에도_부여된_권한이_채워진다() throws Exception {
        long adminId = insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long instructorId = insertMember("t@example.com", "강사", "INSTRUCTOR", "ACTIVE", BASE);
        grant(instructorId, "MEMBER_READ", adminId);
        String token = login("t@example.com");

        mockMvc.perform(get("/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[0]").value("MEMBER_READ"));
        mockMvc.perform(patch("/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"새이름\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("새이름"))
                .andExpect(jsonPath("$.permissions[0]").value("MEMBER_READ"));
    }

    @Test
    void 퇴사일이_없으면_resignationDate는_null이다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long targetId = insertMember("target@example.com", "김회원", "MEMBER", "ACTIVE", BASE);
        String token = login(ADMIN);

        detail(token, targetId).andExpect(jsonPath("$.resignationDate").value((Object) null));
    }

    @Test
    void 없는_회원이면_404를_응답한다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        String token = login(ADMIN);

        detail(token, 999999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void MEMBER_READ를_가진_강사는_조회할_수_있다() throws Exception {
        long adminId = insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long instructorId = insertMember("t@example.com", "강사", "INSTRUCTOR", "ACTIVE", BASE);
        grant(instructorId, "MEMBER_READ", adminId);
        String token = login("t@example.com");

        detail(token, adminId).andExpect(status().isOk());
    }

    @Test
    void 권한이_없는_강사는_403_PERMISSION_DENIED를_응답한다() throws Exception {
        long adminId = insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        insertMember("t@example.com", "강사", "INSTRUCTOR", "ACTIVE", BASE);
        String token = login("t@example.com");

        detail(token, adminId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void 일반_회원은_자기_id를_조회해도_403이다() throws Exception {
        long memberId = insertMember("m@example.com", "회원", "MEMBER", "ACTIVE", BASE);
        String token = login("m@example.com");

        detail(token, memberId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void 권한이_없으면_없는_회원이어도_404가_아니라_403이다() throws Exception {
        insertMember("m@example.com", "회원", "MEMBER", "ACTIVE", BASE);
        String token = login("m@example.com");

        detail(token, 999999L).andExpect(status().isForbidden());
    }

    @Test
    void 인증하지_않으면_401_UNAUTHENTICATED를_응답한다() throws Exception {
        mockMvc.perform(get("/members/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void memberId가_숫자가_아니면_400_INVALID_INPUT을_응답한다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        String token = login(ADMIN);

        mockMvc.perform(get("/members/abc").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private ResultActions detail(String accessToken, long memberId) throws Exception {
        return mockMvc.perform(get("/members/" + memberId).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }
}
