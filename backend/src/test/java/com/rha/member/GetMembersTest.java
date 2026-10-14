package com.rha.member;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

class GetMembersTest extends MemberAdminTestSupport {

    private static final String ADMIN = "admin@example.com";
    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    void 관리자는_회원_목록을_content와_pageInfo_형태로_받는다() throws Exception {
        long adminId = insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        insertMember("a@example.com", "김회원", "MEMBER", "ACTIVE", BASE.plus(1, ChronoUnit.DAYS));
        String token = login(ADMIN);

        list(token, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].email").value("a@example.com"))
                .andExpect(jsonPath("$.content[0].name").value("김회원"))
                .andExpect(jsonPath("$.content[0].role").value("MEMBER"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].permissions").isEmpty())
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.content[1].id").value(adminId))
                .andExpect(jsonPath("$.pageInfo.page").value(1))
                .andExpect(jsonPath("$.pageInfo.size").value(20))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(2))
                .andExpect(jsonPath("$.pageInfo.totalPages").value(1));
    }

    @Test
    void 최신_가입순으로_정렬하고_가입_시각이_같으면_id가_큰_회원이_먼저다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        insertMember("old@example.com", "오래된", "MEMBER", "ACTIVE", BASE.plus(1, ChronoUnit.DAYS));
        insertMember("same1@example.com", "동시1", "MEMBER", "ACTIVE", BASE.plus(2, ChronoUnit.DAYS));
        insertMember("same2@example.com", "동시2", "MEMBER", "ACTIVE", BASE.plus(2, ChronoUnit.DAYS));
        String token = login(ADMIN);

        list(token, "")
                .andExpect(jsonPath("$.content[0].email").value("same2@example.com"))
                .andExpect(jsonPath("$.content[1].email").value("same1@example.com"))
                .andExpect(jsonPath("$.content[2].email").value("old@example.com"))
                .andExpect(jsonPath("$.content[3].email").value(ADMIN));
    }

    @Test
    void 페이지와_크기를_지정하면_해당_페이지만_반환한다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        for (int i = 1; i <= 4; i++) {
            insertMember("m" + i + "@example.com", "회원" + i, "MEMBER", "ACTIVE", BASE.plus(i, ChronoUnit.DAYS));
        }
        String token = login(ADMIN);

        list(token, "?page=2&size=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].email").value("m2@example.com"))
                .andExpect(jsonPath("$.content[1].email").value("m1@example.com"))
                .andExpect(jsonPath("$.pageInfo.page").value(2))
                .andExpect(jsonPath("$.pageInfo.size").value(2))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(5))
                .andExpect(jsonPath("$.pageInfo.totalPages").value(3));
    }

    @Test
    void 마지막_페이지를_넘어서면_빈_content를_응답한다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        String token = login(ADMIN);

        list(token, "?page=5")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.pageInfo.page").value(5))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(1));
    }

    @Test
    void 상태_필터가_없으면_모든_상태의_회원을_반환한다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        insertMember("i@example.com", "비활성", "MEMBER", "INACTIVE", BASE);
        insertMember("w@example.com", "탈퇴", "MEMBER", "WITHDRAWN", BASE);
        insertMember("r@example.com", "퇴사", "INSTRUCTOR", "RESIGNED", BASE);
        String token = login(ADMIN);

        list(token, "").andExpect(jsonPath("$.pageInfo.totalElements").value(4));
    }

    @Test
    void 역할과_상태로_필터링한다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        insertMember("m1@example.com", "회원1", "MEMBER", "ACTIVE", BASE);
        insertMember("m2@example.com", "회원2", "MEMBER", "WITHDRAWN", BASE);
        insertMember("t1@example.com", "강사1", "INSTRUCTOR", "ACTIVE", BASE);
        String token = login(ADMIN);

        list(token, "?role=MEMBER")
                .andExpect(jsonPath("$.pageInfo.totalElements").value(2));
        list(token, "?status=WITHDRAWN")
                .andExpect(jsonPath("$.pageInfo.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("m2@example.com"));
        list(token, "?role=MEMBER&status=ACTIVE")
                .andExpect(jsonPath("$.pageInfo.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("m1@example.com"));
    }

    @Test
    void keyword는_이름_또는_이메일에_부분_일치한다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        insertMember("kim@example.com", "홍길동", "MEMBER", "ACTIVE", BASE);
        insertMember("lee@example.com", "김철수", "MEMBER", "ACTIVE", BASE);
        String token = login(ADMIN);

        list(token, "?keyword=" + "길동")
                .andExpect(jsonPath("$.pageInfo.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("kim@example.com"));
        list(token, "?keyword=lee@exam")
                .andExpect(jsonPath("$.pageInfo.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("lee@example.com"));
    }

    @Test
    void keyword는_대소문자를_구분하지_않고_앞뒤_공백을_무시한다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        insertMember("Kim@Example.com", "홍길동", "MEMBER", "ACTIVE", BASE);
        String token = login(ADMIN);

        list(token, "?keyword=" + "  KIM@example  ")
                .andExpect(jsonPath("$.pageInfo.totalElements").value(1));
    }

    @Test
    void 비어_있는_keyword는_필터를_적용하지_않는다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        insertMember("a@example.com", "회원", "MEMBER", "ACTIVE", BASE);
        String token = login(ADMIN);

        list(token, "?keyword=").andExpect(jsonPath("$.pageInfo.totalElements").value(2));
        list(token, "?keyword=" + "   ").andExpect(jsonPath("$.pageInfo.totalElements").value(2));
    }

    @Test
    void keyword의_와일드카드_문자는_문자_그대로_취급한다() throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        insertMember("a@example.com", "100% 회원", "MEMBER", "ACTIVE", BASE);
        insertMember("b@example.com", "일반", "MEMBER", "ACTIVE", BASE);
        String token = login(ADMIN);

        list(token, "?keyword=" + "%")
                .andExpect(jsonPath("$.pageInfo.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("a@example.com"));
        list(token, "?keyword=" + "_")
                .andExpect(jsonPath("$.pageInfo.totalElements").value(0));
    }

    @Test
    void 부여된_권한을_응답에_채운다() throws Exception {
        long adminId = insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long instructorId = insertMember("t@example.com", "강사", "INSTRUCTOR", "ACTIVE", BASE.plus(1, ChronoUnit.DAYS));
        grant(instructorId, "MEMBER_READ", adminId);
        grant(instructorId, "LESSON_CREATE:PRIVATE", adminId);
        String token = login(ADMIN);

        list(token, "?role=INSTRUCTOR")
                .andExpect(jsonPath("$.content[0].permissions.length()").value(2))
                .andExpect(jsonPath("$.content[0].permissions[0]").value("LESSON_CREATE:PRIVATE"))
                .andExpect(jsonPath("$.content[0].permissions[1]").value("MEMBER_READ"));
    }

    @Test
    void MEMBER_READ를_가진_강사는_조회할_수_있다() throws Exception {
        long adminId = insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long instructorId = insertMember("t@example.com", "강사", "INSTRUCTOR", "ACTIVE", BASE);
        grant(instructorId, "MEMBER_READ", adminId);
        String token = login("t@example.com");

        list(token, "").andExpect(status().isOk());
    }

    @Test
    void 권한이_없는_강사는_403_PERMISSION_DENIED를_응답한다() throws Exception {
        insertMember("t@example.com", "강사", "INSTRUCTOR", "ACTIVE", BASE);
        String token = login("t@example.com");

        list(token, "")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void MEMBER_READ가_아닌_다른_권한만_가진_강사는_403을_응답한다() throws Exception {
        long adminId = insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long instructorId = insertMember("t@example.com", "강사", "INSTRUCTOR", "ACTIVE", BASE);
        grant(instructorId, "LESSON_CREATE:PRIVATE", adminId);
        String token = login("t@example.com");

        list(token, "").andExpect(status().isForbidden());
    }

    @Test
    void 일반_회원은_403_PERMISSION_DENIED를_응답한다() throws Exception {
        insertMember("m@example.com", "회원", "MEMBER", "ACTIVE", BASE);
        String token = login("m@example.com");

        list(token, "")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void 권한을_회수하면_이미_발급된_토큰으로도_즉시_거절된다() throws Exception {
        long adminId = insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        long instructorId = insertMember("t@example.com", "강사", "INSTRUCTOR", "ACTIVE", BASE);
        grant(instructorId, "MEMBER_READ", adminId);
        String token = login("t@example.com");
        list(token, "").andExpect(status().isOk());

        jdbc.update("DELETE FROM member_permissions WHERE member_id = ?", instructorId);

        list(token, "").andExpect(status().isForbidden());
    }

    @Test
    void 인증하지_않으면_401_UNAUTHENTICATED를_응답한다() throws Exception {
        mockMvc.perform(get("/members"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=0", "page=-1", "size=0", "size=101", "role=UNKNOWN", "status=UNKNOWN", "page=abc"})
    void 잘못된_조회_조건은_400_INVALID_INPUT을_응답한다(String query) throws Exception {
        insertMember(ADMIN, "관리자", "ADMIN", "ACTIVE", BASE);
        String token = login(ADMIN);

        list(token, "?" + query)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    /** query는 "?a=1&b=2" 형태이며, 값은 인코딩하지 않은 원문이다 (MockMvc가 파라미터를 직접 다룬다). */
    private ResultActions list(String accessToken, String query) throws Exception {
        var request = get("/members").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        if (query.length() > 1) {
            for (String pair : query.substring(1).split("&")) {
                int idx = pair.indexOf('=');
                request.param(pair.substring(0, idx), pair.substring(idx + 1));
            }
        }
        return mockMvc.perform(request);
    }
}
