package com.rha.lessontype;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rha.ApiTestSupport;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class LessonTypeTest extends ApiTestSupport {

    // ===== getLessonTypes =====

    @Test
    void 수업_유형_목록은_등록_순으로_조회된다() throws Exception {
        insertType("PRIVATE", "개인", 1, true);
        insertType("GROUP_3", "단체 3:1", 3, false);
        insertType("GROUP_8", "단체 8:1", 8, true);
        String token = memberToken();

        list(token, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].code").value("PRIVATE"))
                .andExpect(jsonPath("$.content[0].name").value("개인"))
                .andExpect(jsonPath("$.content[0].capacity").value(1))
                .andExpect(jsonPath("$.content[0].active").value(true))
                .andExpect(jsonPath("$.content[0].id").isNumber())
                .andExpect(jsonPath("$.content[0].createdAt").isString())
                .andExpect(jsonPath("$.content[1].code").value("GROUP_3"))
                .andExpect(jsonPath("$.content[1].active").value(false))
                .andExpect(jsonPath("$.content[2].code").value("GROUP_8"))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(3));
    }

    @Test
    void active로_필터링할_수_있다() throws Exception {
        insertType("PRIVATE", "개인", 1, true);
        insertType("GROUP_3", "단체 3:1", 3, false);
        String token = memberToken();

        list(token, "?active=true")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].code").value("PRIVATE"));
        list(token, "?active=false")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].code").value("GROUP_3"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"?page=0", "?size=101", "?active=maybe"})
    void 잘못된_조회_파라미터는_400이다(String query) throws Exception {
        String token = memberToken();

        list(token, query)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 강사도_목록을_조회할_수_있고_로그인하지_않으면_401이다() throws Exception {
        String token = instructorToken();

        list(token, "").andExpect(status().isOk());
        mockMvc.perform(get("/lesson-types")).andExpect(status().isUnauthorized());
    }

    // ===== createLessonType =====

    @Test
    void 관리자는_수업_유형을_등록할_수_있고_활성_상태이다() throws Exception {
        String token = adminToken();

        create(token, "{\"code\":\"GROUP_3\",\"name\":\"단체 3:1\",\"capacity\":3}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.code").value("GROUP_3"))
                .andExpect(jsonPath("$.name").value("단체 3:1"))
                .andExpect(jsonPath("$.capacity").value(3))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").isString());
    }

    @Test
    void 같은_코드가_있으면_409이고_비활성_유형의_코드도_포함한다() throws Exception {
        insertType("PRIVATE", "개인", 1, false);
        String token = adminToken();

        create(token, "{\"code\":\"PRIVATE\",\"name\":\"다른 이름\",\"capacity\":2}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LESSON_TYPE_CODE_DUPLICATED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"name\":\"개인\",\"capacity\":1}",
            "{\"code\":\"PRIVATE\",\"capacity\":1}",
            "{\"code\":\"PRIVATE\",\"name\":\"개인\"}",
            "{\"code\":\"private\",\"name\":\"개인\",\"capacity\":1}",
            "{\"code\":\"P\",\"name\":\"개인\",\"capacity\":1}",
            "{\"code\":\"1PRIVATE\",\"name\":\"개인\",\"capacity\":1}",
            "{\"code\":\"PRI-VATE\",\"name\":\"개인\",\"capacity\":1}",
            "{\"code\":\"PRIVATE\",\"name\":\"  \",\"capacity\":1}",
            "{\"code\":\"PRIVATE\",\"name\":\"개인\",\"capacity\":0}",
            "{\"code\":\"PRIVATE\",\"name\":\"개인\",\"capacity\":101}",
            "not json"
    })
    void 잘못된_등록_요청은_400_INVALID_INPUT이다(String body) throws Exception {
        String token = adminToken();

        create(token, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 코드는_30자까지_허용한다() throws Exception {
        String token = adminToken();

        create(token, "{\"code\":\"" + "A".repeat(30) + "\",\"name\":\"긴 코드\",\"capacity\":1}")
                .andExpect(status().isCreated());
        create(token, "{\"code\":\"" + "B".repeat(31) + "\",\"name\":\"긴 코드\",\"capacity\":1}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void 관리자가_아니면_등록은_403이고_잘못된_본문이어도_403이다() throws Exception {
        String token = instructorToken();

        create(token, "{\"code\":\"PRIVATE\",\"name\":\"개인\",\"capacity\":1}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        create(token, "{}").andExpect(status().isForbidden());
    }

    @Test
    void 로그인하지_않으면_등록은_401이다() throws Exception {
        mockMvc.perform(post("/lesson-types").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"PRIVATE\",\"name\":\"개인\",\"capacity\":1}"))
                .andExpect(status().isUnauthorized());
    }

    // ===== updateLessonType =====

    @Test
    void 이름_정원_활성_여부를_수정할_수_있다() throws Exception {
        long id = insertType("GROUP_3", "단체 3:1", 3, true);
        String token = adminToken();

        update(token, id, "{\"name\":\"단체 4:1\",\"capacity\":4,\"active\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.code").value("GROUP_3"))
                .andExpect(jsonPath("$.name").value("단체 4:1"))
                .andExpect(jsonPath("$.capacity").value(4))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void 전달한_필드만_수정되고_code는_바뀌지_않는다() throws Exception {
        long id = insertType("GROUP_3", "단체 3:1", 3, true);
        String token = adminToken();

        update(token, id, "{\"capacity\":5,\"code\":\"OTHER\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("GROUP_3"))
                .andExpect(jsonPath("$.name").value("단체 3:1"))
                .andExpect(jsonPath("$.capacity").value(5))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void 비활성_유형도_수정할_수_있고_다시_활성화할_수_있다() throws Exception {
        long id = insertType("GROUP_3", "단체 3:1", 3, false);
        String token = adminToken();

        update(token, id, "{\"name\":\"이름 변경\"}").andExpect(status().isOk());
        update(token, id, "{\"active\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void 없는_유형_수정은_404이다() throws Exception {
        String token = adminToken();

        update(token, 999999L, "{\"name\":\"x\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"code\":\"OTHER\"}",
            "{\"name\":\"\"}",
            "{\"capacity\":0}",
            "{\"capacity\":101}",
            "{\"active\":\"yes\"}",
            "not json"
    })
    void 잘못된_수정_요청은_400_INVALID_INPUT이다(String body) throws Exception {
        long id = insertType("GROUP_3", "단체 3:1", 3, true);
        String token = adminToken();

        update(token, id, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 없는_유형이어도_본문이_잘못되면_404보다_400이_먼저다() throws Exception {
        String token = adminToken();

        update(token, 999999L, "{\"capacity\":0}").andExpect(status().isBadRequest());
    }

    @Test
    void 관리자가_아니면_수정은_403이다() throws Exception {
        long id = insertType("GROUP_3", "단체 3:1", 3, true);
        String token = instructorToken();

        update(token, id, "{\"name\":\"x\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    // ===== 권한 목록 연동 =====

    @Test
    void 활성_유형마다_개설과_수정_권한이_권한_목록에_나타난다() throws Exception {
        insertType("PRIVATE", "개인", 1, true);
        insertType("GROUP_3", "단체 3:1", 3, false);
        String token = adminToken();

        mockMvc.perform(get("/permissions").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.length()").value(3))
                .andExpect(jsonPath("$.permissions[0].code").value("LESSON_CREATE:PRIVATE"))
                .andExpect(jsonPath("$.permissions[0].name").value("개인 수업 개설"))
                .andExpect(jsonPath("$.permissions[1].code").value("LESSON_UPDATE:PRIVATE"))
                .andExpect(jsonPath("$.permissions[1].name").value("개인 수업 수정"))
                .andExpect(jsonPath("$.permissions[2].code").value("MEMBER_READ"));
    }

    @Test
    void 유형_이름을_바꾸면_권한_이름도_바뀌고_비활성화하면_목록에서_빠진다() throws Exception {
        long id = insertType("PRIVATE", "개인", 1, true);
        String token = adminToken();

        update(token, id, "{\"name\":\"1:1 수업\"}").andExpect(status().isOk());
        mockMvc.perform(get("/permissions").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.permissions[0].name").value("1:1 수업 수업 개설"));

        update(token, id, "{\"active\":false}").andExpect(status().isOk());
        mockMvc.perform(get("/permissions").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.permissions.length()").value(1));
    }

    @Test
    void 등록한_유형의_권한을_강사에게_부여할_수_있다() throws Exception {
        insertType("PRIVATE", "개인", 1, true);
        String token = adminToken();
        long instructorId = insertMember("t@example.com", "강사", "INSTRUCTOR", "ACTIVE",
                Instant.parse("2026-10-01T00:00:00Z"));

        mockMvc.perform(put("/members/" + instructorId + "/permissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"LESSON_CREATE:PRIVATE\",\"MEMBER_READ\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[0]").value("LESSON_CREATE:PRIVATE"))
                .andExpect(jsonPath("$.permissions[1]").value("MEMBER_READ"));
    }

    @Test
    void 비활성_유형의_권한은_부여할_수_없다() throws Exception {
        insertType("PRIVATE", "개인", 1, false);
        String token = adminToken();
        long instructorId = insertMember("t@example.com", "강사", "INSTRUCTOR", "ACTIVE",
                Instant.parse("2026-10-01T00:00:00Z"));

        mockMvc.perform(put("/members/" + instructorId + "/permissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"LESSON_CREATE:PRIVATE\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERMISSION"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_permissions", Integer.class)).isZero();
    }

    private long insertType(String code, String name, int capacity, boolean active) {
        return jdbc.queryForObject("""
                INSERT INTO lesson_types (code, name, capacity, active, created_at, updated_at)
                VALUES (?, ?, ?, ?, now(), now())
                RETURNING id
                """, Long.class, code, name, capacity, active);
    }

    private ResultActions list(String token, String query) throws Exception {
        return mockMvc.perform(get("/lesson-types" + query).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions create(String token, String body) throws Exception {
        return mockMvc.perform(post("/lesson-types").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions update(String token, long id, String body) throws Exception {
        return mockMvc.perform(patch("/lesson-types/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
