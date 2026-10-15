package com.rha.lesson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 승인된 부재가 수업 개설·수정·인계와 퇴사 처리에 미치는 영향. */
class LessonAbsenceTest extends LessonTestSupport {

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

    private void insertAbsence(long memberId, Instant start, Instant end, String absenceStatus) {
        jdbc.update("""
                INSERT INTO absences (instructor_id, start_at, end_at, status, created_at)
                VALUES (?, ?, ?, ?, now())
                """, memberId, Timestamp.from(start), Timestamp.from(end), absenceStatus);
    }

    @Test
    void 승인된_부재_기간과_겹치는_수업은_개설할_수_없다() throws Exception {
        String token = adminToken();
        insertAbsence(instructorId, at(1, 9), at(1, 12), "APPROVED");

        createLesson(token, body("a", typeId, instructorId, roomId, at(1, 11), at(1, 13)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTRUCTOR_ABSENT"));
        assertThat(lessonCount()).isZero();
    }

    @Test
    void 부재_기간과_맞닿는_수업은_개설할_수_있다() throws Exception {
        String token = adminToken();
        insertAbsence(instructorId, at(1, 9), at(1, 12), "APPROVED");

        createLesson(token, body("a", typeId, instructorId, roomId, at(1, 12), at(1, 13)))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"REQUESTED", "REJECTED", "CANCELED"})
    void 승인되지_않은_부재는_배정을_막지_않는다(String absenceStatus) throws Exception {
        String token = adminToken();
        insertAbsence(instructorId, at(1, 9), at(1, 12), absenceStatus);

        createLesson(token, body("a", typeId, instructorId, roomId, at(1, 10), at(1, 11)))
                .andExpect(status().isCreated());
    }

    @Test
    void 다른_강사의_부재는_영향이_없다() throws Exception {
        String token = adminToken();
        long other = insertInstructor("i2@example.com", "박강사");
        insertAbsence(other, at(1, 9), at(1, 12), "APPROVED");

        createLesson(token, body("a", typeId, instructorId, roomId, at(1, 10), at(1, 11)))
                .andExpect(status().isCreated());
    }

    @Test
    void 가능시간_위반이_부재보다_먼저이고_부재가_퇴사일보다_먼저다() throws Exception {
        String token = adminToken();
        insertAbsence(instructorId, at(1, 9), at(1, 12), "APPROVED");
        jdbc.update("UPDATE members SET resignation_date = ? WHERE id = ?",
                Date.valueOf(LocalDate.now(SEOUL).plusDays(1)), instructorId);

        createLesson(token, body("a", typeId, instructorId, roomId, at(1, 10), at(1, 11)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTRUCTOR_ABSENT"));

        clearAvailability(instructorId);
        createLesson(token, body("a", typeId, instructorId, roomId, at(1, 10), at(1, 11)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTRUCTOR_UNAVAILABLE"));
    }

    @Test
    void 수업을_부재_기간으로_옮기면_409_INSTRUCTOR_ABSENT이다() throws Exception {
        String token = adminToken();
        long lessonId = insertLesson("a", typeId, instructorId, roomId, at(3, 10), at(3, 11), "PUBLISHED");
        insertAbsence(instructorId, at(1, 9), at(1, 12), "APPROVED");

        updateLesson(token, lessonId, "{\"startAt\":\"" + at(1, 10) + "\",\"endAt\":\"" + at(1, 11) + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTRUCTOR_ABSENT"));
    }

    @Test
    void 부재_중인_강사에게_수업을_인계할_수_없다() throws Exception {
        String token = adminToken();
        long to = insertInstructor("to@example.com", "박강사");
        long lessonId = insertLesson("a", typeId, instructorId, roomId, at(1, 10), at(1, 11), "PUBLISHED");
        insertAbsence(to, at(1, 9), at(1, 12), "APPROVED");

        mockMvc.perform(post("/members/" + instructorId + "/lesson-reassignments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"toInstructorId\":" + to + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REASSIGNMENT_FAILED"))
                .andExpect(jsonPath("$.invalidParams[0].field").value(String.valueOf(lessonId)))
                .andExpect(jsonPath("$.invalidParams[0].reason").value("INSTRUCTOR_ABSENT"));
    }

    @Test
    void 퇴사_처리하면_승인_대기와_시작_전_승인_부재가_취소된다() throws Exception {
        String token = adminToken();
        long adminId = jdbc.queryForObject("SELECT id FROM members WHERE email = 'admin@example.com'", Long.class);
        jdbc.update("UPDATE members SET resignation_date = ? WHERE id = ?",
                Date.valueOf(LocalDate.now(SEOUL)), instructorId);
        insertAbsence(instructorId, at(2, 9), at(2, 12), "REQUESTED");
        insertAbsence(instructorId, at(5, 9), at(5, 12), "APPROVED");
        insertAbsence(instructorId, at(8, 9), at(8, 12), "REJECTED");
        insertAbsence(instructorId, Instant.now().minusSeconds(86400 * 3), Instant.now().minusSeconds(86400),
                "APPROVED");

        mockMvc.perform(post("/members/" + instructorId + "/resignation/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForList("SELECT status FROM absences WHERE instructor_id = ? ORDER BY start_at",
                String.class, instructorId))
                .containsExactly("APPROVED", "CANCELED", "CANCELED", "REJECTED");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM absences
                WHERE instructor_id = ? AND status = 'CANCELED' AND processed_by = ? AND processed_at IS NOT NULL
                """, Integer.class, instructorId, adminId)).isEqualTo(2);
    }
}
