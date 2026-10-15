package com.rha.lesson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

class ChangeLessonStatusTest extends LessonTestSupport {

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

    private Instant publishAt() {
        return Instant.now().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    }

    private String statusBody(String target) {
        if ("SCHEDULED".equals(target)) {
            return "{\"status\":\"SCHEDULED\",\"publishAt\":\"" + publishAt() + "\"}";
        }
        return "{\"status\":\"" + target + "\"}";
    }

    private long idOf(String email) {
        return jdbc.queryForObject("SELECT id FROM members WHERE email = ?", Long.class, email);
    }

    // ===== 상태 전이 =====

    @ParameterizedTest
    @CsvSource({
            "CREATED,PUBLISHED", "CREATED,SCHEDULED",
            "SCHEDULED,PUBLISHED", "SCHEDULED,UNPUBLISHED", "SCHEDULED,SCHEDULED",
            "PUBLISHED,UNPUBLISHED",
            "UNPUBLISHED,PUBLISHED", "UNPUBLISHED,SCHEDULED"
    })
    void 허용된_상태_전이는_200이고_상태가_바뀐다(String from, String to) throws Exception {
        String token = adminToken();
        long lessonId = lesson(from);
        if ("SCHEDULED".equals(from)) {
            jdbc.update("UPDATE lessons SET publish_at = ? WHERE id = ?",
                    Timestamp.from(Instant.now().plusSeconds(1800)), lessonId);
        }

        changeStatus(token, lessonId, statusBody(to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(lessonId))
                .andExpect(jsonPath("$.status").value(to));
        assertThat(lessonStatus(lessonId)).isEqualTo(to);
    }

    @ParameterizedTest
    @CsvSource({
            "CREATED,UNPUBLISHED",
            "PUBLISHED,PUBLISHED", "PUBLISHED,SCHEDULED",
            "UNPUBLISHED,UNPUBLISHED"
    })
    void 허용되지_않은_상태_전이는_409_INVALID_STATUS_TRANSITION이다(String from, String to) throws Exception {
        String token = adminToken();
        long lessonId = lesson(from);
        if ("SCHEDULED".equals(from)) {
            jdbc.update("UPDATE lessons SET publish_at = ? WHERE id = ?",
                    Timestamp.from(Instant.now().plusSeconds(1800)), lessonId);
        }

        changeStatus(token, lessonId, statusBody(to))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        assertThat(lessonStatus(lessonId)).isEqualTo(from);
    }

    @Test
    void 공개하면_공개_예약_시각이_지워진다() throws Exception {
        String token = adminToken();
        long lessonId = lesson("SCHEDULED");
        jdbc.update("UPDATE lessons SET publish_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().plusSeconds(1800)), lessonId);

        changeStatus(token, lessonId, "{\"status\":\"PUBLISHED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishAt").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT publish_at FROM lessons WHERE id = ?", Timestamp.class, lessonId))
                .isNull();
    }

    @Test
    void 공개_예약하면_publishAt이_응답에_들어_있다() throws Exception {
        String token = adminToken();
        long lessonId = lesson("CREATED");
        Instant at = publishAt();

        changeStatus(token, lessonId, "{\"status\":\"SCHEDULED\",\"publishAt\":\"" + at + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.publishAt").value(at.toString()));
    }

    @Test
    void 공개_예약_시각을_변경할_수_있다() throws Exception {
        String token = adminToken();
        long lessonId = lesson("SCHEDULED");
        jdbc.update("UPDATE lessons SET publish_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().plusSeconds(1800)), lessonId);
        Instant changed = Instant.now().plusSeconds(7200).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        changeStatus(token, lessonId, "{\"status\":\"SCHEDULED\",\"publishAt\":\"" + changed + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishAt").value(changed.toString()));
    }

    // ===== 상태와 시간 =====

    @Test
    void 이미_시작한_수업은_409_LESSON_ALREADY_STARTED이다() throws Exception {
        String token = adminToken();
        long started = insertPastLesson("지난 수업", typeId, instructorId, roomId, "UNPUBLISHED");

        changeStatus(token, started, "{\"status\":\"PUBLISHED\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LESSON_ALREADY_STARTED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUBLISHED", "UNPUBLISHED", "SCHEDULED"})
    void 취소된_수업은_409_LESSON_CANCELED이다(String target) throws Exception {
        String token = adminToken();
        long canceled = lesson("CANCELED");

        changeStatus(token, canceled, statusBody(target))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LESSON_CANCELED"));
    }

    @Test
    void 취소된_수업은_publishAt이_잘못되어도_409가_먼저다() throws Exception {
        String token = adminToken();
        long canceled = lesson("CANCELED");

        changeStatus(token, canceled,
                "{\"status\":\"SCHEDULED\",\"publishAt\":\"" + at(-1, 10) + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LESSON_CANCELED"));
    }

    // ===== 400 =====

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"status\":\"CREATED\"}",
            "{\"status\":\"CANCELED\"}",
            "{\"status\":\"UNKNOWN\"}",
            "{\"status\":1}",
            "not json"
    })
    void 상태가_없거나_변경할_수_없는_값이면_400_INVALID_INPUT이다(String json) throws Exception {
        String token = adminToken();
        long lessonId = lesson("CREATED");

        changeStatus(token, lessonId, json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(lessonStatus(lessonId)).isEqualTo("CREATED");
    }

    @Test
    void SCHEDULED인데_publishAt이_없으면_400_INVALID_PUBLISH_AT이다() throws Exception {
        String token = adminToken();
        long lessonId = lesson("CREATED");

        changeStatus(token, lessonId, "{\"status\":\"SCHEDULED\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PUBLISH_AT"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUBLISHED", "UNPUBLISHED"})
    void SCHEDULED가_아닌데_publishAt을_보내면_400_INVALID_PUBLISH_AT이다(String target) throws Exception {
        String token = adminToken();
        long lessonId = lesson("CREATED");

        changeStatus(token, lessonId, "{\"status\":\"" + target + "\",\"publishAt\":\"" + publishAt() + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PUBLISH_AT"));
    }

    @Test
    void publishAt이_과거이면_400_INVALID_PUBLISH_AT이다() throws Exception {
        String token = adminToken();
        long lessonId = lesson("CREATED");

        changeStatus(token, lessonId,
                "{\"status\":\"SCHEDULED\",\"publishAt\":\"" + Instant.now().minusSeconds(60) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PUBLISH_AT"));
    }

    @Test
    void publishAt이_수업_시작_이후이면_400_INVALID_PUBLISH_AT이다() throws Exception {
        String token = adminToken();
        long lessonId = lesson("CREATED");

        changeStatus(token, lessonId, "{\"status\":\"SCHEDULED\",\"publishAt\":\"" + at(2, 10) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PUBLISH_AT"));
        changeStatus(token, lessonId, "{\"status\":\"SCHEDULED\",\"publishAt\":\"" + at(3, 10) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PUBLISH_AT"));
    }

    @Test
    void 없는_수업이면_404이다() throws Exception {
        String token = adminToken();

        changeStatus(token, 999999, "{\"status\":\"PUBLISHED\"}").andExpect(status().isNotFound());
    }

    // ===== 권한 =====

    @Test
    void LESSON_UPDATE_권한이_있는_강사는_공개_상태를_바꿀_수_있다() throws Exception {
        String token = instructorToken();
        long callerId = idOf("t@example.com");
        grant(callerId, "LESSON_UPDATE:GROUP_3", callerId);
        long lessonId = lesson("CREATED");

        changeStatus(token, lessonId, "{\"status\":\"PUBLISHED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void 권한이_없거나_다른_유형_권한이면_403이다() throws Exception {
        insertLessonType("PRIVATE_1", "개인", 1, true);
        String token = instructorToken();
        long callerId = idOf("t@example.com");
        long lessonId = lesson("CREATED");

        changeStatus(token, lessonId, "{\"status\":\"PUBLISHED\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        grant(callerId, "LESSON_UPDATE:PRIVATE_1", callerId);
        changeStatus(token, lessonId, "{\"status\":\"PUBLISHED\"}").andExpect(status().isForbidden());
        assertThat(lessonStatus(lessonId)).isEqualTo("CREATED");
    }

    @Test
    void LESSON_CREATE_권한만으로는_403이다() throws Exception {
        String token = instructorToken();
        long callerId = idOf("t@example.com");
        grant(callerId, "LESSON_CREATE:GROUP_3", callerId);

        changeStatus(token, lesson("CREATED"), "{\"status\":\"PUBLISHED\"}").andExpect(status().isForbidden());
    }

    @Test
    void 회원은_403이다() throws Exception {
        String token = memberToken();

        changeStatus(token, lesson("CREATED"), "{\"status\":\"PUBLISHED\"}").andExpect(status().isForbidden());
    }

    @Test
    void 권한이_없으면_잘못된_본문이어도_없는_수업이어도_403이다() throws Exception {
        String token = instructorToken();

        changeStatus(token, lesson("CREATED"), "{}").andExpect(status().isForbidden());
        changeStatus(token, 999999, "{\"status\":\"PUBLISHED\"}").andExpect(status().isForbidden());
    }

    @Test
    void 인증하지_않으면_401이다() throws Exception {
        mockMvc.perform(put("/lessons/1/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isUnauthorized());
    }
}
