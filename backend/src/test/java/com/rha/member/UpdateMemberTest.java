package com.rha.member;

import static org.assertj.core.api.Assertions.assertThat;
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

class UpdateMemberTest extends MemberAdminTestSupport {

    private static final String ADMIN = "admin@example.com";
    private static final String TARGET = "target@example.com";
    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    void 관리자는_회원_정보를_부분_수정할_수_있다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long targetId = insertMember(TARGET, "김회원", "MEMBER", "ACTIVE", BASE);
        String token = login(ADMIN);

        update(token, targetId, "{\"name\":\"새이름\",\"phoneNumber\":\"01099998888\",\"birthDate\":\"1995-05-05\","
                + "\"address\":\"서울시 강남구\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(targetId))
                .andExpect(jsonPath("$.email").value(TARGET))
                .andExpect(jsonPath("$.name").value("새이름"))
                .andExpect(jsonPath("$.phoneNumber").value("01099998888"))
                .andExpect(jsonPath("$.birthDate").value("1995-05-05"))
                .andExpect(jsonPath("$.address").value("서울시 강남구"))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertThat(column("name", targetId)).isEqualTo("새이름");
        assertThat(column("phone_number", targetId)).isEqualTo("01099998888");
    }

    @Test
    void 전달하지_않은_필드는_바뀌지_않는다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long targetId = insertMember(TARGET, "김회원", "MEMBER", "ACTIVE", BASE);
        String token = login(ADMIN);

        update(token, targetId, "{\"name\":\"새이름\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value("01012345678"))
                .andExpect(jsonPath("$.birthDate").value("1990-01-31"));
    }

    @Test
    void 주소를_null로_보내면_주소가_삭제된다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long targetId = insertMember(TARGET, "김회원", "MEMBER", "ACTIVE", BASE);
        jdbc.update("UPDATE members SET address = '서울시' WHERE id = ?", targetId);
        String token = login(ADMIN);

        update(token, targetId, "{\"address\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value((Object) null));

        assertThat(column("address", targetId)).isNull();
    }

    @Test
    void 이메일은_변경되지_않는다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long targetId = insertMember(TARGET, "김회원", "MEMBER", "ACTIVE", BASE);
        String token = login(ADMIN);

        update(token, targetId, "{\"name\":\"새이름\",\"email\":\"changed@example.com\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(TARGET));

        assertThat(column("email", targetId)).isEqualTo(TARGET);
    }

    @Test
    void 역할을_바꾸면_해당_회원의_Refresh_Token이_모두_폐기된다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long targetId = insertMember(TARGET, "김회원", "MEMBER", "ACTIVE", BASE);
        long otherId = insertMember("other@example.com", "다른회원", "MEMBER", "ACTIVE", BASE);
        String token = login(ADMIN);
        login(TARGET);
        login(TARGET);
        login("other@example.com");

        update(token, targetId, "{\"role\":\"INSTRUCTOR\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("INSTRUCTOR"));

        assertThat(activeRefreshTokens(targetId)).isZero();
        assertThat(activeRefreshTokens(otherId)).isEqualTo(1);
    }

    @Test
    void 강사의_역할을_바꾸면_부여된_권한이_모두_회수된다() throws Exception {
        long adminId = insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long targetId = insertMember(TARGET, "강사", "INSTRUCTOR", "ACTIVE", BASE);
        long otherId = insertMember("other@example.com", "다른강사", "INSTRUCTOR", "ACTIVE", BASE);
        grant(targetId, "MEMBER_READ", adminId);
        grant(targetId, "LESSON_UPDATE:GROUP", adminId);
        grant(otherId, "MEMBER_READ", adminId);
        String token = login(ADMIN);

        update(token, targetId, "{\"role\":\"MEMBER\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.permissions").isEmpty());

        assertThat(permissionCount(targetId)).isZero();
        assertThat(permissionCount(otherId)).isEqualTo(1);
    }

    @Test
    void 같은_역할을_보내면_권한_회수와_토큰_폐기가_일어나지_않는다() throws Exception {
        long adminId = insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long targetId = insertMember(TARGET, "강사", "INSTRUCTOR", "ACTIVE", BASE);
        grant(targetId, "MEMBER_READ", adminId);
        String token = login(ADMIN);
        login(TARGET);

        update(token, targetId, "{\"role\":\"INSTRUCTOR\",\"name\":\"새이름\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("새이름"))
                .andExpect(jsonPath("$.permissions[0]").value("MEMBER_READ"));

        assertThat(permissionCount(targetId)).isEqualTo(1);
        assertThat(activeRefreshTokens(targetId)).isEqualTo(1);
    }

    @Test
    void 본인의_역할을_다른_역할로_바꾸면_403_CANNOT_CHANGE_OWN_ROLE이다() throws Exception {
        long adminId = insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        String token = login(ADMIN);

        update(token, adminId, "{\"role\":\"MEMBER\",\"name\":\"새이름\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CANNOT_CHANGE_OWN_ROLE"));

        assertThat(column("role", adminId)).isEqualTo("ADMIN");
        assertThat(column("name", adminId)).isEqualTo("관리자");
    }

    @Test
    void 본인에게_현재와_같은_역할을_보내면_나머지_필드는_수정된다() throws Exception {
        long adminId = insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        String token = login(ADMIN);

        update(token, adminId, "{\"role\":\"ADMIN\",\"name\":\"새이름\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("새이름"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"INACTIVE", "WITHDRAWN", "RESIGNED"})
    void ACTIVE가_아닌_회원의_정보도_수정할_수_있다(String memberStatus) throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long targetId = insertMember(TARGET, "김회원", "MEMBER", memberStatus, BASE);
        String token = login(ADMIN);

        update(token, targetId, "{\"name\":\"새이름\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("새이름"))
                .andExpect(jsonPath("$.status").value(memberStatus));
    }

    @Test
    void 없는_회원이면_404를_응답한다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        String token = login(ADMIN);

        update(token, 999999L, "{\"name\":\"새이름\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void MEMBER_READ를_가진_강사도_403_PERMISSION_DENIED이다() throws Exception {
        long adminId = insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long instructorId = insertMember("t@example.com", "강사", "INSTRUCTOR", "ACTIVE", BASE);
        long targetId = insertMember(TARGET, "김회원", "MEMBER", "ACTIVE", BASE);
        grant(instructorId, "MEMBER_READ", adminId);
        String token = login("t@example.com");

        update(token, targetId, "{\"name\":\"새이름\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        assertThat(column("name", targetId)).isEqualTo("김회원");
    }

    @Test
    void 일반_회원은_자기_정보도_이_API로_수정할_수_없다() throws Exception {
        long memberId = insertMember("m@example.com", "회원", "MEMBER", "ACTIVE", BASE);
        String token = login("m@example.com");

        update(token, memberId, "{\"name\":\"새이름\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void 비활성화된_관리자의_기존_토큰은_403이다() throws Exception {
        long adminId = insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long targetId = insertMember(TARGET, "김회원", "MEMBER", "ACTIVE", BASE);
        String token = login(ADMIN);
        jdbc.update("UPDATE members SET status = 'INACTIVE' WHERE id = ?", adminId);

        update(token, targetId, "{\"name\":\"새이름\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void 관리자가_아니면_잘못된_본문이어도_400이_아니라_403이다() throws Exception {
        long memberId = insertMember("m@example.com", "회원", "MEMBER", "ACTIVE", BASE);
        String token = login("m@example.com");

        update(token, memberId, "{\"phoneNumber\":\"abc\",\"role\":\"SUPER\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void 인증하지_않으면_401_UNAUTHENTICATED를_응답한다() throws Exception {
        mockMvc.perform(patch("/members/1").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"a\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"name\":\"\"}",
            "{\"name\":\"   \"}",
            "{\"phoneNumber\":\"abc\"}",
            "{\"birthDate\":\"not-a-date\"}",
            "{\"role\":\"SUPER\"}",
            "{\"role\":\"\"}",
            "{\"email\":\"changed@example.com\"}",
            "not json"
    })
    void 잘못된_요청_본문이면_400_INVALID_INPUT을_응답한다(String body) throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long targetId = insertMember(TARGET, "김회원", "MEMBER", "ACTIVE", BASE);
        String token = login(ADMIN);

        update(token, targetId, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 없는_회원이어도_본문이_잘못되면_404보다_400이_먼저다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        String token = login(ADMIN);

        update(token, 999999L, "{\"phoneNumber\":\"abc\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 본인의_역할을_바꾸려_해도_본문이_잘못되면_400이_먼저다() throws Exception {
        long adminId = insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        String token = login(ADMIN);

        update(token, adminId, "{\"role\":\"MEMBER\",\"phoneNumber\":\"abc\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void memberId가_숫자가_아니면_400_INVALID_INPUT을_응답한다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        String token = login(ADMIN);

        mockMvc.perform(patch("/members/abc").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"a\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private ResultActions update(String accessToken, long memberId, String body) throws Exception {
        return mockMvc.perform(patch("/members/" + memberId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String column(String column, long memberId) {
        return jdbc.queryForObject("SELECT " + column + " FROM members WHERE id = ?", String.class, memberId);
    }

    private int permissionCount(long memberId) {
        return jdbc.queryForObject("SELECT count(*) FROM member_permissions WHERE member_id = ?", Integer.class,
                memberId);
    }

    private int activeRefreshTokens(long memberId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE member_id = ? AND revoked_at IS NULL", Integer.class,
                memberId);
    }
}
