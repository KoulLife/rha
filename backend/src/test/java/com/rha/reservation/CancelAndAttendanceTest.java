package com.rha.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/** 예약 취소와 출결 기록. */
class CancelAndAttendanceTest extends ReservationTestSupport {

    long roomId;
    long typeId;
    long instructorId;
    long productId;
    long memberId;
    long passId;
    long futureLesson;
    long reservationId;

    @BeforeEach
    void prepare() {
        roomId = insertRoom();
        typeId = insertLessonType(5);
        instructorId = insertInstructor("i@example.com", "박강사");
        productId = insertProduct();
        memberId = insertMemberNamed("mem@example.com", "김회원");
        passId = insertPass(productId, memberId, instructorId, 1, 10, 9, days(-10), days(30), "ACTIVE");
        futureLesson = insertLesson(typeId, instructorId, roomId, at(3, 10), 5, "PUBLISHED");
        reservationId = insertReservation(futureLesson, memberId, passId, "RESERVED");
    }

    private long startedLesson() {
        return insertLesson(typeId, instructorId, roomId, Instant.now().minusSeconds(7200), 5, "PUBLISHED");
    }

    private String column(String name, long id) {
        return jdbc.queryForObject("SELECT " + name + "::text FROM reservations WHERE id = ?", String.class, id);
    }

    // ===== cancelReservation =====

    @Test
    void 회원이_마감_전에_취소하면_취소되고_횟수가_복구된다() throws Exception {
        String token = login("mem@example.com");

        cancelReservation(token, reservationId, "{\"reason\":\"일정 변경\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reservationId))
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.canceledBy").value("MEMBER"))
                .andExpect(jsonPath("$.cancelReason").value("일정 변경"))
                .andExpect(jsonPath("$.canceledAt").isString());

        assertThat(remaining(passId)).isEqualTo(10);
    }

    @Test
    void 본문_없이도_취소할_수_있다() throws Exception {
        cancelReservation(login("mem@example.com"), reservationId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelReason").doesNotExist());
    }

    @Test
    void 취소_마감이_지났으면_회원은_409_CANCELLATION_DEADLINE_PASSED이다() throws Exception {
        long near = insertLesson(typeId, instructorId, roomId, Instant.now().plusSeconds(3600 * 10), 5,
                "PUBLISHED");
        long id = insertReservation(near, memberId, passId, "RESERVED");

        cancelReservation(login("mem@example.com"), id, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCELLATION_DEADLINE_PASSED"));
        assertThat(reservationStatus(id)).isEqualTo("RESERVED");
        assertThat(remaining(passId)).isEqualTo(9);
    }

    @Test
    void 마감_시간을_줄이면_가까운_수업도_취소할_수_있다() throws Exception {
        setPolicy(1, true);
        long near = insertLesson(typeId, instructorId, roomId, Instant.now().plusSeconds(3600 * 10), 5,
                "PUBLISHED");
        long id = insertReservation(near, memberId, passId, "RESERVED");

        cancelReservation(login("mem@example.com"), id, null).andExpect(status().isOk());
    }

    @Test
    void 관리자는_마감과_무관하게_취소하고_취소_주체는_ADMIN이다() throws Exception {
        long near = insertLesson(typeId, instructorId, roomId, Instant.now().plusSeconds(3600), 5, "PUBLISHED");
        long id = insertReservation(near, memberId, passId, "RESERVED");

        cancelReservation(adminToken(), id, "{\"reason\":\"관리자 취소\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canceledBy").value("ADMIN"))
                .andExpect(jsonPath("$.cancelReason").value("관리자 취소"));
        assertThat(remaining(passId)).isEqualTo(10);
    }

    @Test
    void 복구되는_횟수는_총_횟수를_넘지_않는다() throws Exception {
        jdbc.update("UPDATE passes SET remaining_count = 10 WHERE id = ?", passId);

        cancelReservation(login("mem@example.com"), reservationId, null).andExpect(status().isOk());

        assertThat(remaining(passId)).isEqualTo(10);
    }

    @Test
    void 만료된_이용권도_횟수만_복구된다() throws Exception {
        jdbc.update("UPDATE passes SET status = 'EXPIRED', end_date = ? WHERE id = ?",
                java.sql.Date.valueOf(days(-1)), passId);

        cancelReservation(adminToken(), reservationId, null).andExpect(status().isOk());

        assertThat(remaining(passId)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT status FROM passes WHERE id = ?", String.class, passId))
                .isEqualTo("EXPIRED");
    }

    @Test
    void 이미_시작한_수업의_예약은_누구도_취소할_수_없다() throws Exception {
        long started = startedLesson();
        long id = insertReservation(started, memberId, passId, "RESERVED");

        cancelReservation(adminToken(), id, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LESSON_ALREADY_STARTED"));
        cancelReservation(login("mem@example.com"), id, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LESSON_ALREADY_STARTED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ATTENDED", "NO_SHOW", "CANCELED"})
    void 예약_상태가_아니면_409_INVALID_RESERVATION_STATUS이다(String current) throws Exception {
        long other = insertLesson(typeId, instructorId, roomId, at(4, 10), 5, "PUBLISHED");
        long id = insertReservation(other, memberId, passId, current);

        cancelReservation(adminToken(), id, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RESERVATION_STATUS"));
    }

    @Test
    void 다른_회원은_볼_수_없어_404이고_담당_강사는_403이다() throws Exception {
        insertMemberNamed("other@example.com", "다른회원");

        cancelReservation(login("other@example.com"), reservationId, null).andExpect(status().isNotFound());
        cancelReservation(login("i@example.com"), reservationId, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        cancelReservation(adminToken(), 999999, null).andExpect(status().isNotFound());
        assertThat(reservationStatus(reservationId)).isEqualTo("RESERVED");
    }

    @Test
    void 취소_사유가_200자를_넘으면_400이다() throws Exception {
        cancelReservation(login("mem@example.com"), reservationId, "{\"reason\":\"" + "가".repeat(201) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 취소도_인증하지_않으면_401이다() throws Exception {
        mockMvc.perform(post("/reservations/" + reservationId + "/cancellations"))
                .andExpect(status().isUnauthorized());
    }

    // ===== confirmAttendance =====

    @Test
    void 관리자와_담당_강사는_시작한_수업의_출결을_기록하고_정정할_수_있다() throws Exception {
        long started = startedLesson();
        long id = insertReservation(started, memberId, passId, "RESERVED");

        attendance(adminToken(), id, "{\"status\":\"ATTENDED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ATTENDED"))
                .andExpect(jsonPath("$.attendanceConfirmedAt").isString());
        attendance(login("i@example.com"), id, "{\"status\":\"NO_SHOW\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"));
        assertThat(column("attendance_confirmed_at", id)).isNotNull();
    }

    @Test
    void 출석과_노쇼_모두_이용권_횟수에_영향이_없다() throws Exception {
        long started = startedLesson();
        long id = insertReservation(started, memberId, passId, "RESERVED");

        String admin = adminToken();
        attendance(admin, id, "{\"status\":\"NO_SHOW\"}").andExpect(status().isOk());
        attendance(admin, id, "{\"status\":\"ATTENDED\"}").andExpect(status().isOk());

        assertThat(remaining(passId)).isEqualTo(9);
    }

    @Test
    void 수업이_시작되기_전이면_409_LESSON_NOT_STARTED이다() throws Exception {
        attendance(adminToken(), reservationId, "{\"status\":\"ATTENDED\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LESSON_NOT_STARTED"));
        assertThat(reservationStatus(reservationId)).isEqualTo("RESERVED");
    }

    @Test
    void 취소된_예약은_409_INVALID_RESERVATION_STATUS이다() throws Exception {
        long started = startedLesson();
        long id = insertReservation(started, memberId, passId, "CANCELED");

        attendance(adminToken(), id, "{\"status\":\"ATTENDED\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RESERVATION_STATUS"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":\"CANCELED\"}", "{\"status\":\"RESERVED\"}",
            "{\"status\":\"UNKNOWN\"}", "{\"status\":null}", "not json"})
    void 출결_값이_올바르지_않으면_400이다(String json) throws Exception {
        long started = startedLesson();
        long id = insertReservation(started, memberId, passId, "RESERVED");

        attendance(adminToken(), id, json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 담당이_아닌_강사와_회원은_403이다() throws Exception {
        long started = startedLesson();
        long id = insertReservation(started, memberId, passId, "RESERVED");
        insertInstructor("other@example.com", "다른강사");

        attendance(login("other@example.com"), id, "{\"status\":\"ATTENDED\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        attendance(login("mem@example.com"), id, "{\"status\":\"ATTENDED\"}").andExpect(status().isForbidden());
        attendance(login("mem@example.com"), 999999, "{}").andExpect(status().isForbidden());
        assertThat(reservationStatus(id)).isEqualTo("RESERVED");
    }

    @Test
    void 없는_예약이면_404이다() throws Exception {
        attendance(adminToken(), 999999, "{\"status\":\"ATTENDED\"}").andExpect(status().isNotFound());
    }

    @Test
    void 출결도_인증하지_않으면_401이다() throws Exception {
        mockMvc.perform(put("/reservations/" + reservationId + "/attendance")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ATTENDED\"}"))
                .andExpect(status().isUnauthorized());
    }
}
