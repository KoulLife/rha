package com.rha.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 정산에 포함된 수업의 출결 정정 제한(LESSON_SETTLED). */
class SettledAttendanceTest extends SettlementTestSupport {

    long roomId;
    long typeId;
    long instructorId;
    long memberId;
    long passId;
    long lessonId;
    long reservationId;
    String token;

    @BeforeEach
    void prepare() throws Exception {
        roomId = insertRoom();
        typeId = insertType("GROUP", "단체");
        instructorId = insertInstructor("i@example.com", "박강사");
        memberId = insertMember("a@example.com", "김회원", "MEMBER", "ACTIVE", BASE);
        passId = insertPass(insertProduct(), memberId, instructorId);
        setRate(instructorId, typeId, 30000);
        lessonId = insertLesson(typeId, instructorId, roomId, at(-3, 10), "PUBLISHED");
        reservationId = insertReservation(lessonId, memberId, passId, "ATTENDED");
        token = adminToken();
    }

    private String reservationStatus() {
        return jdbc.queryForObject("SELECT status FROM reservations WHERE id = ?", String.class, reservationId);
    }

    @Test
    void 정산되기_전에는_출결을_정정할_수_있다() throws Exception {
        attendance(token, reservationId, "{\"status\":\"NO_SHOW\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"));
    }

    @Test
    void 정산에_포함된_수업은_관리자와_담당_강사_모두_정정할_수_없다() throws Exception {
        calculate(token, body(instructorId, days(-5), days(-1))).andExpect(status().isCreated());

        attendance(token, reservationId, "{\"status\":\"NO_SHOW\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LESSON_SETTLED"));
        attendance(login("i@example.com"), reservationId, "{\"status\":\"NO_SHOW\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LESSON_SETTLED"));
        assertThat(reservationStatus()).isEqualTo("ATTENDED");
    }

    @Test
    void 정산을_폐기하면_다시_정정할_수_있다() throws Exception {
        long settlementId = insertSettlement(instructorId, days(-5), days(-1), "CALCULATED", 30000, BASE);
        insertItem(settlementId, lessonId);
        attendance(token, reservationId, "{\"status\":\"NO_SHOW\"}").andExpect(status().isConflict());

        discard(token, settlementId).andExpect(status().isNoContent());

        attendance(token, reservationId, "{\"status\":\"NO_SHOW\"}").andExpect(status().isOk());
    }

    @Test
    void 확정된_정산의_수업도_정정할_수_없다() throws Exception {
        long settlementId = insertSettlement(instructorId, days(-5), days(-1), "CONFIRMED", 30000, BASE);
        insertItem(settlementId, lessonId);

        attendance(token, reservationId, "{\"status\":\"NO_SHOW\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LESSON_SETTLED"));
    }

    @Test
    void 정산되지_않은_다른_수업은_정정할_수_있다() throws Exception {
        long other = insertLesson(typeId, instructorId, roomId, at(-2, 10), "PUBLISHED");
        long otherReservation = insertReservation(other, memberId, passId, "ATTENDED");
        long settlementId = insertSettlement(instructorId, days(-5), days(-1), "CALCULATED", 30000, BASE);
        insertItem(settlementId, lessonId);

        attendance(token, otherReservation, "{\"status\":\"NO_SHOW\"}").andExpect(status().isOk());
    }
}
