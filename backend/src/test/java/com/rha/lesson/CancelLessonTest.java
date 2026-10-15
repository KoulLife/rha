package com.rha.lesson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CancelLessonTest extends LessonTestSupport {

    long roomId;
    long typeId;
    long instructorId;

    @BeforeEach
    void prepare() {
        openEveryDay();
        roomId = insertRoom("A룸");
        typeId = insertLessonType("GROUP_3", "단체 3:1", 3, true);
        instructorId = insertInstructor("i@example.com", "김강사");
    }

    private long lesson(String status) {
        return insertLesson("수업", typeId, instructorId, roomId, at(2, 10), at(2, 11), status);
    }

    private long idOf(String email) {
        return jdbc.queryForObject("SELECT id FROM members WHERE email = ?", Long.class, email);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATED", "SCHEDULED", "PUBLISHED", "UNPUBLISHED"})
    void 공개_상태와_관계없이_취소할_수_있다(String status) throws Exception {
        String token = adminToken();
        long lessonId = lesson(status);

        cancel(token, lessonId, "{\"reason\":\"강사 사정\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(lessonId))
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.cancelReason").value("강사 사정"))
                .andExpect(jsonPath("$.canceledAt").isString())
                .andExpect(jsonPath("$.publishAt").doesNotExist());
        assertThat(lessonStatus(lessonId)).isEqualTo("CANCELED");
    }

    @Test
    void 본문_없이도_취소할_수_있고_사유는_비어_있다() throws Exception {
        String token = adminToken();
        long lessonId = lesson("PUBLISHED");

        cancel(token, lessonId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.cancelReason").doesNotExist())
                .andExpect(jsonPath("$.canceledAt").isString());
    }

    @Test
    void 빈_객체_본문으로도_취소할_수_있다() throws Exception {
        String token = adminToken();

        cancel(token, lesson("PUBLISHED"), "{}").andExpect(status().isOk());
    }

    @Test
    void 공개_예약_중이던_수업을_취소하면_공개_예약_시각이_지워진다() throws Exception {
        String token = adminToken();
        long lessonId = lesson("SCHEDULED");
        jdbc.update("UPDATE lessons SET publish_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().plusSeconds(1800)), lessonId);

        cancel(token, lessonId, null).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT publish_at FROM lessons WHERE id = ?", Timestamp.class, lessonId))
                .isNull();
    }

    @Test
    void 취소하면_같은_시간에_강사와_룸으로_새_수업을_개설할_수_있다() throws Exception {
        String token = adminToken();
        long lessonId = lesson("PUBLISHED");

        cancel(token, lessonId, null).andExpect(status().isOk());

        createLesson(token, body("새 수업", typeId, instructorId, roomId, at(2, 10), at(2, 11)))
                .andExpect(status().isCreated());
    }

    @Test
    void 사유가_200자를_넘으면_400이다() throws Exception {
        String token = adminToken();
        long lessonId = lesson("PUBLISHED");

        cancel(token, lessonId, "{\"reason\":\"" + "가".repeat(201) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(lessonStatus(lessonId)).isEqualTo("PUBLISHED");
    }

    @Test
    void 이미_취소된_수업은_409_LESSON_CANCELED이다() throws Exception {
        String token = adminToken();

        cancel(token, lesson("CANCELED"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LESSON_CANCELED"));
    }

    @Test
    void 이미_시작한_수업은_409_LESSON_ALREADY_STARTED이다() throws Exception {
        String token = adminToken();
        long started = insertPastLesson("지난 수업", typeId, instructorId, roomId, "PUBLISHED");

        cancel(token, started, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LESSON_ALREADY_STARTED"));
        assertThat(lessonStatus(started)).isEqualTo("PUBLISHED");
    }

    @Test
    void 없는_수업이면_404이다() throws Exception {
        String token = adminToken();

        cancel(token, 999999, null).andExpect(status().isNotFound());
    }

    @Test
    void LESSON_UPDATE_권한이_있는_강사는_취소할_수_있다() throws Exception {
        String token = instructorToken();
        long callerId = idOf("t@example.com");
        grant(callerId, "LESSON_UPDATE:GROUP_3", callerId);

        cancel(token, lesson("PUBLISHED"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
    }

    @Test
    void 권한이_없는_강사는_본인_담당이어도_403이다() throws Exception {
        String token = instructorToken();
        long callerId = idOf("t@example.com");
        long own = insertLesson("내 수업", typeId, callerId, roomId, at(2, 10), at(2, 11), "PUBLISHED");

        cancel(token, own, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        assertThat(lessonStatus(own)).isEqualTo("PUBLISHED");
    }

    @Test
    void LESSON_CREATE_권한만으로는_403이다() throws Exception {
        String token = instructorToken();
        long callerId = idOf("t@example.com");
        grant(callerId, "LESSON_CREATE:GROUP_3", callerId);

        cancel(token, lesson("PUBLISHED"), null).andExpect(status().isForbidden());
    }

    @Test
    void 회원은_403이고_없는_수업이어도_403이다() throws Exception {
        String token = memberToken();

        cancel(token, lesson("PUBLISHED"), null).andExpect(status().isForbidden());
        cancel(token, 999999, null).andExpect(status().isForbidden());
    }

    @Test
    void 인증하지_않으면_401이다() throws Exception {
        mockMvc.perform(post("/lessons/1/cancellations")).andExpect(status().isUnauthorized());
    }
}
