package com.rha.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 예약이 생기면서 수업·부재·일시정지·회원 상태 변경이 받게 된 제약과 수업 취소 시 예약 처리. */
class ReservationIntegrationTest extends ReservationTestSupport {

    long roomId;
    long typeId;
    long instructorId;
    long productId;
    long memberId;
    long passId;
    long lessonId;
    String admin;

    @BeforeEach
    void prepare() throws Exception {
        roomId = insertRoom();
        typeId = insertLessonType(5);
        instructorId = insertInstructor("i@example.com", "박강사");
        // 수업 개설·수정 검증을 통과하도록 영업시간과 강사 가능시간을 맞춘다.
        jdbc.update("UPDATE business_hours SET closed = false, open_time = '00:00', close_time = '23:59'");
        for (String day : new String[] {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"}) {
            jdbc.update("""
                    INSERT INTO instructor_availabilities (instructor_id, day_of_week, start_time, end_time)
                    VALUES (?, ?, '00:00', '23:59')
                    """, instructorId, day);
        }
        productId = insertProduct();
        memberId = insertMemberNamed("mem@example.com", "김회원");
        passId = insertPass(productId, memberId, instructorId, 1, 10, 9, days(-10), days(30), "ACTIVE");
        lessonId = insertLesson(typeId, instructorId, roomId, at(3, 10), 5, "PUBLISHED");
        admin = adminToken();
    }

    private ResultActions cancelLesson(String json) throws Exception {
        var request = post("/lessons/" + lessonId + "/cancellations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    private ResultActions updateLesson(String json) throws Exception {
        return mockMvc.perform(patch("/lessons/" + lessonId).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String field(String name, long id) {
        return jdbc.queryForObject("SELECT " + name + "::text FROM reservations WHERE id = ?", String.class, id);
    }

    // ===== 수업 취소 → 예약 일괄 취소 =====

    @Test
    void 수업을_취소하면_예약이_자동_취소되고_이용권_횟수가_복구된다() throws Exception {
        long other = insertMemberNamed("o@example.com", "오회원");
        long otherPass = insertPass(productId, other, instructorId, 1, 10, 4, days(-10), days(30), "ACTIVE");
        long r1 = insertReservation(lessonId, memberId, passId, "RESERVED");
        long r2 = insertReservation(lessonId, other, otherPass, "RESERVED");

        cancelLesson("{\"reason\":\"강사 사정\"}").andExpect(status().isOk());

        assertThat(reservationStatus(r1)).isEqualTo("CANCELED");
        assertThat(reservationStatus(r2)).isEqualTo("CANCELED");
        assertThat(field("canceled_by", r1)).isEqualTo("LESSON_CANCELED");
        assertThat(field("cancel_reason", r1)).isEqualTo("강사 사정");
        assertThat(field("canceled_at", r1)).isNotNull();
        assertThat(remaining(passId)).isEqualTo(10);
        assertThat(remaining(otherPass)).isEqualTo(5);
    }

    @Test
    void 수업_취소는_취소된_예약과_다른_수업의_예약에_영향이_없다() throws Exception {
        long other = insertLesson(typeId, instructorId, roomId, at(4, 10), 5, "PUBLISHED");
        long already = insertReservation(lessonId, memberId, passId, "CANCELED");
        long untouched = insertReservation(other, memberId, passId, "RESERVED");

        cancelLesson(null).andExpect(status().isOk());

        assertThat(reservationStatus(already)).isEqualTo("CANCELED");
        assertThat(field("canceled_by", already)).isNull();
        assertThat(reservationStatus(untouched)).isEqualTo("RESERVED");
        assertThat(remaining(passId)).isEqualTo(9);
    }

    @Test
    void 취소_사유_없이_취소해도_예약은_자동_취소된다() throws Exception {
        long id = insertReservation(lessonId, memberId, passId, "RESERVED");

        cancelLesson(null).andExpect(status().isOk());

        assertThat(reservationStatus(id)).isEqualTo("CANCELED");
        assertThat(field("cancel_reason", id)).isNull();
    }

    // ===== updateLesson: LESSON_HAS_RESERVATIONS =====

    @Test
    void 예약이_있는_수업의_시간은_바꿀_수_없다() throws Exception {
        insertReservation(lessonId, memberId, passId, "RESERVED");

        updateLesson("{\"startAt\":\"" + at(3, 14) + "\",\"endAt\":\"" + at(3, 15) + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LESSON_HAS_RESERVATIONS"));
    }

    @Test
    void 예약이_있어도_제목_강사_룸은_바꿀_수_있다() throws Exception {
        insertReservation(lessonId, memberId, passId, "RESERVED");

        updateLesson("{\"title\":\"새 제목\"}").andExpect(status().isOk());
    }

    @Test
    void 취소된_예약만_있으면_시간을_바꿀_수_있다() throws Exception {
        insertReservation(lessonId, memberId, passId, "CANCELED");

        updateLesson("{\"startAt\":\"" + at(3, 14) + "\",\"endAt\":\"" + at(3, 15) + "\"}")
                .andExpect(status().isOk());
    }

    // ===== approveAbsence: ABSENCE_HAS_RESERVATIONS =====

    @Test
    void 부재_기간의_수업에_예약이_있으면_승인할_수_없다() throws Exception {
        long withReservation = lessonId;
        long without = insertLesson(typeId, instructorId, roomId, at(3, 15), 5, "PUBLISHED");
        insertReservation(withReservation, memberId, passId, "RESERVED");
        long absenceId = jdbc.queryForObject("""
                INSERT INTO absences (instructor_id, start_at, end_at, status, created_at)
                VALUES (?, ?, ?, 'REQUESTED', now()) RETURNING id
                """, Long.class, instructorId, Timestamp.from(at(3, 0)), Timestamp.from(at(3, 23)));

        mockMvc.perform(post("/absences/" + absenceId + "/approvals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ABSENCE_HAS_RESERVATIONS"))
                .andExpect(jsonPath("$.invalidParams.length()").value(1))
                .andExpect(jsonPath("$.invalidParams[0].field").value(String.valueOf(withReservation)))
                .andExpect(jsonPath("$.invalidParams[0].reason").value("ABSENCE_HAS_RESERVATIONS"));

        assertThat(jdbc.queryForObject("SELECT status FROM absences WHERE id = ?", String.class, absenceId))
                .isEqualTo("REQUESTED");
        assertThat(jdbc.queryForObject("SELECT status FROM lessons WHERE id = ?", String.class, without))
                .isEqualTo("PUBLISHED");
    }

    @Test
    void 취소된_예약만_있으면_부재를_승인하고_수업이_자동_취소된다() throws Exception {
        insertReservation(lessonId, memberId, passId, "CANCELED");
        long absenceId = jdbc.queryForObject("""
                INSERT INTO absences (instructor_id, start_at, end_at, status, created_at)
                VALUES (?, ?, ?, 'REQUESTED', now()) RETURNING id
                """, Long.class, instructorId, Timestamp.from(at(3, 0)), Timestamp.from(at(3, 23)));

        mockMvc.perform(post("/absences/" + absenceId + "/approvals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT status FROM lessons WHERE id = ?", String.class, lessonId))
                .isEqualTo("CANCELED");
    }

    // ===== createPassPause: PAUSE_HAS_RESERVATIONS =====

    @Test
    void 정지_기간에_속한_수업의_예약이_있으면_정지를_등록할_수_없다() throws Exception {
        insertReservation(lessonId, memberId, passId, "RESERVED");

        mockMvc.perform(post("/passes/" + passId + "/pauses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"" + days(2) + "\",\"endDate\":\"" + days(4) + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAUSE_HAS_RESERVATIONS"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pass_pauses", Integer.class)).isZero();
    }

    @Test
    void 정지_기간_밖의_예약이나_다른_이용권의_예약은_정지를_막지_않는다() throws Exception {
        long other = insertMemberNamed("o@example.com", "오회원");
        long otherPass = insertPass(productId, other, instructorId, 1, 10, 9, days(-10), days(30), "ACTIVE");
        insertReservation(lessonId, memberId, passId, "RESERVED");
        insertReservation(lessonId, other, otherPass, "RESERVED");

        // 수업일(days(3))이 아닌 기간
        mockMvc.perform(post("/passes/" + passId + "/pauses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"" + days(5) + "\",\"endDate\":\"" + days(6) + "\"}"))
                .andExpect(status().isCreated());
        // 다른 이용권으로 한 예약은 이 이용권의 정지를 막지 않는다
        jdbc.update("UPDATE reservations SET status = 'CANCELED' WHERE pass_id = ?", passId);
        mockMvc.perform(post("/passes/" + passId + "/pauses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"" + days(3) + "\",\"endDate\":\"" + days(3) + "\"}"))
                .andExpect(status().isCreated());
    }

    // ===== MEMBER_HAS_UPCOMING_RESERVATIONS =====

    @Test
    void 시작_전_예약이_있으면_탈퇴할_수_없다() throws Exception {
        insertReservation(lessonId, memberId, passId, "RESERVED");

        mockMvc.perform(delete("/members/me").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + login("mem@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_HAS_UPCOMING_RESERVATIONS"));
        assertThat(jdbc.queryForObject("SELECT status FROM members WHERE id = ?", String.class, memberId))
                .isEqualTo("ACTIVE");
    }

    @Test
    void 예약을_취소하면_탈퇴할_수_있다() throws Exception {
        long id = insertReservation(lessonId, memberId, passId, "RESERVED");
        String member = login("mem@example.com");

        mockMvc.perform(post("/reservations/" + id + "/cancellations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + member))
                .andExpect(status().isNoContent());
    }

    @Test
    void 이미_지난_수업의_예약만_있으면_탈퇴할_수_있다() throws Exception {
        long past = insertLesson(typeId, instructorId, roomId, Instant.now().minusSeconds(7200), 5, "PUBLISHED");
        insertReservation(past, memberId, passId, "ATTENDED");

        mockMvc.perform(delete("/members/me").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + login("mem@example.com")))
                .andExpect(status().isNoContent());
    }

    @Test
    void 시작_전_예약이_있는_회원은_비활성화할_수_없다() throws Exception {
        insertReservation(lessonId, memberId, passId, "RESERVED");

        mockMvc.perform(put("/members/" + memberId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_HAS_UPCOMING_RESERVATIONS"));
        assertThat(jdbc.queryForObject("SELECT status FROM members WHERE id = ?", String.class, memberId))
                .isEqualTo("ACTIVE");
    }

    @Test
    void 취소된_예약만_있으면_비활성화할_수_있다() throws Exception {
        insertReservation(lessonId, memberId, passId, "CANCELED");

        mockMvc.perform(put("/members/" + memberId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk());
    }
}
