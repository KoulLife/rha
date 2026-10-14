package com.rha.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class RoomWriteTest extends RoomTestSupport {

    // ===== createRoom =====

    @Test
    void 관리자는_룸을_등록할_수_있다() throws Exception {
        String token = adminToken();

        create(token, "{\"name\":\"A룸\",\"capacity\":8}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("A룸"))
                .andExpect(jsonPath("$.capacity").value(8))
                .andExpect(jsonPath("$.createdAt").isString());

        assertThat(roomCount()).isEqualTo(1);
    }

    @Test
    void 이름의_앞뒤_공백은_제거해_저장한다() throws Exception {
        String token = adminToken();

        create(token, "{\"name\":\"  A룸  \",\"capacity\":8}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("A룸"));
    }

    @Test
    void 같은_이름의_룸이_있으면_409_ROOM_NAME_DUPLICATED이다() throws Exception {
        insertRoom("A룸", 8);
        String token = adminToken();

        create(token, "{\"name\":\"A룸\",\"capacity\":4}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_NAME_DUPLICATED"));

        assertThat(roomCount()).isEqualTo(1);
    }

    @Test
    void 공백을_제거하면_같은_이름이어도_409이다() throws Exception {
        insertRoom("A룸", 8);
        String token = adminToken();

        create(token, "{\"name\":\" A룸 \",\"capacity\":4}").andExpect(status().isConflict());
    }

    @Test
    void 삭제된_룸의_이름은_다시_사용할_수_있다() throws Exception {
        insertDeletedRoom("A룸", 8);
        String token = adminToken();

        create(token, "{\"name\":\"A룸\",\"capacity\":4}").andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"name\":\"A룸\"}",
            "{\"capacity\":8}",
            "{\"name\":\"\",\"capacity\":8}",
            "{\"name\":\"   \",\"capacity\":8}",
            "{\"name\":\"A룸\",\"capacity\":0}",
            "{\"name\":\"A룸\",\"capacity\":101}",
            "{\"name\":\"A룸\",\"capacity\":\"abc\"}",
            "not json"
    })
    void 잘못된_등록_요청은_400_INVALID_INPUT이다(String body) throws Exception {
        String token = adminToken();

        create(token, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 이름이_50자를_넘으면_400이다() throws Exception {
        String token = adminToken();

        create(token, "{\"name\":\"" + "가".repeat(51) + "\",\"capacity\":8}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 관리자가_아니면_등록은_403이고_잘못된_본문이어도_403이다() throws Exception {
        String token = instructorToken();

        create(token, "{\"name\":\"A룸\",\"capacity\":8}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        create(token, "{}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void 로그인하지_않으면_등록은_401이다() throws Exception {
        mockMvc.perform(post("/rooms").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A룸\",\"capacity\":8}"))
                .andExpect(status().isUnauthorized());
    }

    // ===== updateRoom =====

    @Test
    void 이름과_수용_인원을_수정할_수_있다() throws Exception {
        long id = insertRoom("A룸", 8);
        String token = adminToken();

        update(token, id, "{\"name\":\"새 룸\",\"capacity\":10}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("새 룸"))
                .andExpect(jsonPath("$.capacity").value(10));
    }

    @Test
    void 전달한_필드만_수정된다() throws Exception {
        long id = insertRoom("A룸", 8);
        String token = adminToken();

        update(token, id, "{\"capacity\":12}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("A룸"))
                .andExpect(jsonPath("$.capacity").value(12));
    }

    @Test
    void 자기_자신과_같은_이름으로_수정은_허용된다() throws Exception {
        long id = insertRoom("A룸", 8);
        String token = adminToken();

        update(token, id, "{\"name\":\"A룸\",\"capacity\":9}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(9));
    }

    @Test
    void 다른_룸과_같은_이름으로_수정하면_409이다() throws Exception {
        long id = insertRoom("A룸", 8);
        insertRoom("B룸", 6);
        String token = adminToken();

        update(token, id, "{\"name\":\"B룸\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_NAME_DUPLICATED"));
    }

    @Test
    void 삭제된_룸이_쓰던_이름으로_수정할_수_있다() throws Exception {
        long id = insertRoom("A룸", 8);
        insertDeletedRoom("B룸", 6);
        String token = adminToken();

        update(token, id, "{\"name\":\"B룸\"}").andExpect(status().isOk());
    }

    @Test
    void 없거나_삭제된_룸_수정은_404이다() throws Exception {
        long deletedId = insertDeletedRoom("A룸", 8);
        String token = adminToken();

        update(token, 999999L, "{\"name\":\"새 룸\"}").andExpect(status().isNotFound());
        update(token, deletedId, "{\"name\":\"새 룸\"}").andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"name\":\"\"}",
            "{\"name\":\"   \"}",
            "{\"capacity\":0}",
            "{\"capacity\":101}",
            "not json"
    })
    void 잘못된_수정_요청은_400_INVALID_INPUT이다(String body) throws Exception {
        long id = insertRoom("A룸", 8);
        String token = adminToken();

        update(token, id, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 없는_룸이어도_본문이_잘못되면_404보다_400이_먼저다() throws Exception {
        String token = adminToken();

        update(token, 999999L, "{\"capacity\":0}").andExpect(status().isBadRequest());
    }

    @Test
    void 관리자가_아니면_수정은_403이다() throws Exception {
        long id = insertRoom("A룸", 8);
        String token = instructorToken();

        update(token, id, "{\"name\":\"새 룸\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    // ===== deleteRoom =====

    @Test
    void 룸을_삭제하면_204이고_조회되지_않는다() throws Exception {
        long id = insertRoom("A룸", 8);
        String token = adminToken();

        remove(token, id)
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(roomCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM rooms WHERE id = ?", Timestamp.class, id))
                .isNotNull();
        mockMvc.perform(get("/rooms/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void 삭제는_행을_지우지_않고_소프트_삭제한다() throws Exception {
        long id = insertRoom("A룸", 8);
        String token = adminToken();

        remove(token, id).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM rooms WHERE id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void 없거나_이미_삭제된_룸_삭제는_404이다() throws Exception {
        long deletedId = insertDeletedRoom("A룸", 8);
        String token = adminToken();

        remove(token, 999999L).andExpect(status().isNotFound());
        remove(token, deletedId).andExpect(status().isNotFound());
    }

    @Test
    void 관리자가_아니면_삭제는_403이고_없는_룸이어도_403이다() throws Exception {
        long id = insertRoom("A룸", 8);
        String token = instructorToken();

        remove(token, id)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        remove(token, 999999L).andExpect(status().isForbidden());
        assertThat(roomCount()).isEqualTo(1);
    }

    @Test
    void 로그인하지_않으면_삭제는_401이다() throws Exception {
        mockMvc.perform(delete("/rooms/1")).andExpect(status().isUnauthorized());
    }

    private ResultActions create(String token, String body) throws Exception {
        return mockMvc.perform(post("/rooms").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions update(String token, long id, String body) throws Exception {
        return mockMvc.perform(patch("/rooms/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions remove(String token, long id) throws Exception {
        return mockMvc.perform(delete("/rooms/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}
