package com.rha.room;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

class RoomReadTest extends RoomTestSupport {

    @Test
    void 룸_목록은_이름_오름차순으로_조회된다() throws Exception {
        insertRoom("C룸", 4);
        insertRoom("A룸", 8);
        insertRoom("B룸", 6);
        String token = memberToken();

        list(token, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].name").value("A룸"))
                .andExpect(jsonPath("$.content[0].capacity").value(8))
                .andExpect(jsonPath("$.content[0].id").isNumber())
                .andExpect(jsonPath("$.content[0].createdAt").isString())
                .andExpect(jsonPath("$.content[1].name").value("B룸"))
                .andExpect(jsonPath("$.content[2].name").value("C룸"))
                .andExpect(jsonPath("$.pageInfo.page").value(1))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(3));
    }

    @Test
    void 삭제된_룸은_목록에서_제외된다() throws Exception {
        insertRoom("A룸", 8);
        insertDeletedRoom("B룸", 6);
        String token = memberToken();

        list(token, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(1));
    }

    @Test
    void 페이징이_적용된다() throws Exception {
        for (int i = 1; i <= 5; i++) {
            insertRoom("룸" + i, 4);
        }
        String token = memberToken();

        list(token, "?page=2&size=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("룸3"))
                .andExpect(jsonPath("$.pageInfo.page").value(2))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(5));
    }

    @ParameterizedTest
    @ValueSource(strings = {"?page=0", "?size=0", "?size=101", "?page=abc"})
    void 잘못된_페이지_파라미터는_400이다(String query) throws Exception {
        String token = memberToken();

        list(token, query)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 로그인하지_않으면_목록은_401이다() throws Exception {
        mockMvc.perform(get("/rooms"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 강사도_목록과_상세를_조회할_수_있다() throws Exception {
        long id = insertRoom("A룸", 8);
        String token = instructorToken();

        list(token, "").andExpect(status().isOk());
        detail(token, id).andExpect(status().isOk());
    }

    @Test
    void 룸_상세를_조회할_수_있다() throws Exception {
        long id = insertRoom("A룸", 8);
        String token = memberToken();

        detail(token, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("A룸"))
                .andExpect(jsonPath("$.capacity").value(8))
                .andExpect(jsonPath("$.createdAt").isString());
    }

    @Test
    void 없는_룸이면_404이다() throws Exception {
        String token = memberToken();

        detail(token, 999999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 삭제된_룸은_상세_조회가_404이다() throws Exception {
        long id = insertDeletedRoom("A룸", 8);
        String token = memberToken();

        detail(token, id).andExpect(status().isNotFound());
    }

    @Test
    void roomId가_숫자가_아니면_400이다() throws Exception {
        String token = memberToken();

        mockMvc.perform(get("/rooms/abc").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 로그인하지_않으면_상세는_401이다() throws Exception {
        mockMvc.perform(get("/rooms/1")).andExpect(status().isUnauthorized());
    }

    private ResultActions list(String token, String query) throws Exception {
        return mockMvc.perform(get("/rooms" + query).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions detail(String token, long id) throws Exception {
        return mockMvc.perform(get("/rooms/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}
