package com.rha.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class ChangeMemberStatusTest extends MemberAdminTestSupport {

    private static final String ADMIN = "admin@example.com";
    private static final String TARGET = "target@example.com";
    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    void 회원을_비활성화하면_상태가_바뀌고_Refresh_Token이_모두_폐기된다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long targetId = insertMember(TARGET, "김회원", "MEMBER", "ACTIVE", BASE);
        long otherId = insertMember("other@example.com", "다른회원", "MEMBER", "ACTIVE", BASE);
        String token = login(ADMIN);
        login(TARGET);
        login(TARGET);
        login("other@example.com");

        changeStatus(token, targetId, "INACTIVE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(targetId))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        assertThat(statusOf(targetId)).isEqualTo("INACTIVE");
        assertThat(activeRefreshTokens(targetId)).isZero();
        assertThat(activeRefreshTokens(otherId)).isEqualTo(1);
    }

    @Test
    void 비활성화된_회원은_로그인할_수_없고_데이터는_보존된다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long targetId = insertMember(TARGET, "김회원", "MEMBER", "ACTIVE", BASE);
        String token = login(ADMIN);

        changeStatus(token, targetId, "INACTIVE").andExpect(status().isOk());

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + TARGET + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
        assertThat(jdbc.queryForObject("SELECT name FROM members WHERE id = ?", String.class, targetId))
                .isEqualTo("김회원");
    }

    @Test
    void 비활성_회원을_활성화하면_다시_로그인할_수_있다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long targetId = insertMember(TARGET, "김회원", "MEMBER", "INACTIVE", BASE);
        String token = login(ADMIN);

        changeStatus(token, targetId, "ACTIVE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(login(TARGET)).isNotBlank();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "INACTIVE"})
    void 이미_같은_상태면_변경_없이_200을_응답한다(String memberStatus) throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long targetId = insertMember(TARGET, "김회원", "MEMBER", "ACTIVE", BASE);
        String token = login(ADMIN);
        login(TARGET);
        if (memberStatus.equals("INACTIVE")) {
            jdbc.update("UPDATE members SET status = 'INACTIVE' WHERE id = ?", targetId);
        }

        changeStatus(token, targetId, memberStatus)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(memberStatus));

        assertThat(activeRefreshTokens(targetId)).isEqualTo(1);
    }

    @Test
    void 탈퇴한_회원을_ACTIVE로_복구하면_탈퇴일이_지워진다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long targetId = insertMember(TARGET, "김회원", "MEMBER", "WITHDRAWN", BASE);
        jdbc.update("UPDATE members SET withdrawn_at = now() WHERE id = ?", targetId);
        String token = login(ADMIN);

        changeStatus(token, targetId, "ACTIVE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(jdbc.queryForObject("SELECT withdrawn_at FROM members WHERE id = ?", Timestamp.class, targetId))
                .isNull();
        assertThat(login(TARGET)).isNotBlank();
    }

    @Test
    void 퇴사한_강사를_ACTIVE로_복구하면_퇴사일이_지워진다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long targetId = insertMember(TARGET, "강사", "INSTRUCTOR", "RESIGNED", BASE);
        jdbc.update("UPDATE members SET resignation_date = DATE '2026-09-30' WHERE id = ?", targetId);
        String token = login(ADMIN);

        changeStatus(token, targetId, "ACTIVE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.resignationDate").value((Object) null));

        assertThat(jdbc.queryForObject("SELECT resignation_date FROM members WHERE id = ?", java.sql.Date.class,
                targetId)).isNull();
    }

    @Test
    void 탈퇴한_회원을_INACTIVE로_바꾸면_409_MEMBER_WITHDRAWN이다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long targetId = insertMember(TARGET, "김회원", "MEMBER", "WITHDRAWN", BASE);
        String token = login(ADMIN);

        changeStatus(token, targetId, "INACTIVE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_WITHDRAWN"));

        assertThat(statusOf(targetId)).isEqualTo("WITHDRAWN");
    }

    @Test
    void 퇴사한_강사를_INACTIVE로_바꾸면_409_MEMBER_RESIGNED이다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long targetId = insertMember(TARGET, "강사", "INSTRUCTOR", "RESIGNED", BASE);
        String token = login(ADMIN);

        changeStatus(token, targetId, "INACTIVE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_RESIGNED"));

        assertThat(statusOf(targetId)).isEqualTo("RESIGNED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "INACTIVE"})
    void 본인_계정의_상태는_바꿀_수_없다(String requested) throws Exception {
        long adminId = insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        String token = login(ADMIN);

        changeStatus(token, adminId, requested)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CANNOT_CHANGE_OWN_STATUS"));

        assertThat(statusOf(adminId)).isEqualTo("ACTIVE");
    }

    @Test
    void 없는_회원이면_404를_응답한다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        String token = login(ADMIN);

        changeStatus(token, 999999L, "INACTIVE")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 관리자가_아니면_403_PERMISSION_DENIED이다() throws Exception {
        long adminId = insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long instructorId = insertMember("t@example.com", "강사", "INSTRUCTOR", "ACTIVE", BASE);
        long targetId = insertMember(TARGET, "김회원", "MEMBER", "ACTIVE", BASE);
        grant(instructorId, "MEMBER_READ", adminId);
        String token = login("t@example.com");

        changeStatus(token, targetId, "INACTIVE")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        assertThat(statusOf(targetId)).isEqualTo("ACTIVE");
    }

    @Test
    void 비활성화된_관리자의_기존_토큰은_403이다() throws Exception {
        long adminId = insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long targetId = insertMember(TARGET, "김회원", "MEMBER", "ACTIVE", BASE);
        String token = login(ADMIN);
        jdbc.update("UPDATE members SET status = 'INACTIVE' WHERE id = ?", adminId);

        changeStatus(token, targetId, "INACTIVE")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void 관리자가_아니면_잘못된_본문이어도_400이_아니라_403이다() throws Exception {
        long memberId = insertMember("m@example.com", "회원", "MEMBER", "ACTIVE", BASE);
        String token = login("m@example.com");

        mockMvc.perform(put("/members/" + memberId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"WITHDRAWN\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void 인증하지_않으면_401_UNAUTHENTICATED를_응답한다() throws Exception {
        mockMvc.perform(put("/members/1/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"status\":null}",
            "{\"status\":\"\"}",
            "{\"status\":\"WITHDRAWN\"}",
            "{\"status\":\"RESIGNED\"}",
            "{\"status\":\"UNKNOWN\"}",
            "not json"
    })
    void 잘못된_요청_본문이면_400_INVALID_INPUT을_응답한다(String body) throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long targetId = insertMember(TARGET, "김회원", "MEMBER", "ACTIVE", BASE);
        String token = login(ADMIN);

        mockMvc.perform(put("/members/" + targetId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 없는_회원이어도_본문이_잘못되면_404보다_400이_먼저다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        String token = login(ADMIN);

        mockMvc.perform(put("/members/999999/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"WITHDRAWN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void memberId가_숫자가_아니면_400_INVALID_INPUT을_응답한다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        String token = login(ADMIN);

        mockMvc.perform(put("/members/abc/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private ResultActions changeStatus(String accessToken, long memberId, String requested) throws Exception {
        return mockMvc.perform(put("/members/" + memberId + "/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + requested + "\"}"));
    }

    private String statusOf(long memberId) {
        return jdbc.queryForObject("SELECT status FROM members WHERE id = ?", String.class, memberId);
    }

    private int activeRefreshTokens(long memberId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE member_id = ? AND revoked_at IS NULL", Integer.class,
                memberId);
    }
}
