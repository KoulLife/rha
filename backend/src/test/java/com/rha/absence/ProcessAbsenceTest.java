package com.rha.absence;

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

class ProcessAbsenceTest extends AbsenceTestSupport {

    long instructorId;
    long adminId;
    String adminToken;
    long typeId;
    long roomId;

    @BeforeEach
    void prepare() throws Exception {
        instructorId = insertInstructor("i@example.com", "김강사");
        adminToken = adminToken();
        adminId = jdbc.queryForObject("SELECT id FROM members WHERE email = 'admin@example.com'", Long.class);
        typeId = insertLessonType();
        roomId = insertRoom();
    }

    private String lessonStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM lessons WHERE id = ?", String.class, id);
    }

    // ===== approveAbsence =====

    @Test
    void 관리자가_승인하면_APPROVED가_되고_처리자가_기록된다() throws Exception {
        long id = insertAbsence(instructorId, daysFromNow(5), daysFromNow(6), "REQUESTED");

        action(adminToken, id, "approvals", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.processedBy.id").value(adminId))
                .andExpect(jsonPath("$.processedBy.name").value("관리자"))
                .andExpect(jsonPath("$.processedAt").isString());
        assertThat(absenceStatus(id)).isEqualTo("APPROVED");
    }

    @Test
    void 승인하면_부재_기간과_겹치는_시작_전_수업이_자동_취소된다() throws Exception {
        Instant start = daysFromNow(5);
        Instant end = daysFromNow(8);
        long id = insertAbsence(instructorId, start, end, "REQUESTED");
        long inside = insertLesson(typeId, instructorId, roomId, start.plusSeconds(3600), start.plusSeconds(7200),
                "PUBLISHED");
        long straddle = insertLesson(typeId, instructorId, roomId, end.minusSeconds(1800), end.plusSeconds(1800),
                "CREATED");
        long before = insertLesson(typeId, instructorId, roomId, start.minusSeconds(7200), start, "PUBLISHED");
        long after = insertLesson(typeId, instructorId, roomId, end, end.plusSeconds(3600), "PUBLISHED");
        long canceled = insertLesson(typeId, instructorId, roomId, start.plusSeconds(86400),
                start.plusSeconds(90000), "CANCELED");
        long other = insertInstructor("i2@example.com", "박강사");
        long othersLesson = insertLesson(typeId, other, roomId, start.plusSeconds(3600), start.plusSeconds(7200),
                "PUBLISHED");

        action(adminToken, id, "approvals", null).andExpect(status().isOk());

        assertThat(lessonStatus(inside)).isEqualTo("CANCELED");
        assertThat(lessonStatus(straddle)).isEqualTo("CANCELED");
        assertThat(lessonStatus(before)).isEqualTo("PUBLISHED");
        assertThat(lessonStatus(after)).isEqualTo("PUBLISHED");
        assertThat(lessonStatus(canceled)).isEqualTo("CANCELED");
        assertThat(lessonStatus(othersLesson)).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT cancel_reason FROM lessons WHERE id = ?", String.class, inside))
                .isEqualTo("강사 부재 승인으로 자동 취소");
        assertThat(jdbc.queryForObject("SELECT canceled_at FROM lessons WHERE id = ?", Timestamp.class, inside))
                .isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPROVED", "REJECTED", "CANCELED"})
    void 승인_대기가_아니면_승인할_수_없다(String current) throws Exception {
        long id = insertAbsence(instructorId, daysFromNow(5), daysFromNow(6), current);

        action(adminToken, id, "approvals", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ABSENCE_STATUS"));
        assertThat(absenceStatus(id)).isEqualTo(current);
    }

    @Test
    void 승인은_관리자만_할_수_있다() throws Exception {
        long id = insertAbsence(instructorId, daysFromNow(5), daysFromNow(6), "REQUESTED");
        String own = login("i@example.com");

        action(own, id, "approvals", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        action(own, 999999, "approvals", null).andExpect(status().isForbidden());
        assertThat(absenceStatus(id)).isEqualTo("REQUESTED");
    }

    @Test
    void 없는_부재면_404이다() throws Exception {
        action(adminToken, 999999, "approvals", null).andExpect(status().isNotFound());
    }

    @Test
    void 승인도_인증하지_않으면_401이다() throws Exception {
        mockMvc.perform(post("/absences/1/approvals")).andExpect(status().isUnauthorized());
    }

    // ===== rejectAbsence =====

    @Test
    void 반려하면_REJECTED가_되고_사유와_처리자가_기록된다() throws Exception {
        long id = insertAbsence(instructorId, daysFromNow(5), daysFromNow(6), "REQUESTED");

        action(adminToken, id, "rejections", "{\"reason\":\"수업이 많습니다\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").value("수업이 많습니다"))
                .andExpect(jsonPath("$.processedBy.id").value(adminId))
                .andExpect(jsonPath("$.processedAt").isString());
    }

    @Test
    void 본문_없이도_반려할_수_있다() throws Exception {
        long id = insertAbsence(instructorId, daysFromNow(5), daysFromNow(6), "REQUESTED");

        action(adminToken, id, "rejections", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").doesNotExist());
    }

    @Test
    void 반려해도_수업은_그대로이다() throws Exception {
        long id = insertAbsence(instructorId, daysFromNow(5), daysFromNow(6), "REQUESTED");
        long lessonId = insertLesson(typeId, instructorId, roomId, daysFromNow(5).plusSeconds(60),
                daysFromNow(5).plusSeconds(3600), "PUBLISHED");

        action(adminToken, id, "rejections", null).andExpect(status().isOk());

        assertThat(lessonStatus(lessonId)).isEqualTo("PUBLISHED");
    }

    @Test
    void 반려_사유가_200자를_넘으면_400이다() throws Exception {
        long id = insertAbsence(instructorId, daysFromNow(5), daysFromNow(6), "REQUESTED");

        action(adminToken, id, "rejections", "{\"reason\":\"" + "가".repeat(201) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(absenceStatus(id)).isEqualTo("REQUESTED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPROVED", "REJECTED", "CANCELED"})
    void 승인_대기가_아니면_반려할_수_없다(String current) throws Exception {
        long id = insertAbsence(instructorId, daysFromNow(5), daysFromNow(6), current);

        action(adminToken, id, "rejections", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ABSENCE_STATUS"));
    }

    @Test
    void 반려는_관리자만_할_수_있고_없는_부재는_404이다() throws Exception {
        long id = insertAbsence(instructorId, daysFromNow(5), daysFromNow(6), "REQUESTED");

        action(login("i@example.com"), id, "rejections", null).andExpect(status().isForbidden());
        action(adminToken, 999999, "rejections", null).andExpect(status().isNotFound());
    }

    // ===== cancelAbsence =====

    @Test
    void 강사는_승인_전_부재를_철회할_수_있다() throws Exception {
        long id = insertAbsence(instructorId, daysFromNow(5), daysFromNow(6), "REQUESTED");

        action(login("i@example.com"), id, "cancellations", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.processedBy.id").value(instructorId));
    }

    @Test
    void 강사는_승인된_부재를_취소할_수_없다() throws Exception {
        long id = insertAbsence(instructorId, daysFromNow(5), daysFromNow(6), "APPROVED");

        action(login("i@example.com"), id, "cancellations", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        assertThat(absenceStatus(id)).isEqualTo("APPROVED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"REQUESTED", "APPROVED"})
    void 관리자는_승인_대기와_승인된_부재를_취소할_수_있다(String current) throws Exception {
        long id = insertAbsence(instructorId, daysFromNow(5), daysFromNow(6), current);

        action(adminToken, id, "cancellations", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.processedBy.id").value(adminId));
    }

    @Test
    void 승인으로_취소된_수업은_부재를_취소해도_되살아나지_않는다() throws Exception {
        long id = insertAbsence(instructorId, daysFromNow(5), daysFromNow(6), "REQUESTED");
        long lessonId = insertLesson(typeId, instructorId, roomId, daysFromNow(5).plusSeconds(60),
                daysFromNow(5).plusSeconds(3600), "PUBLISHED");
        action(adminToken, id, "approvals", null).andExpect(status().isOk());

        action(adminToken, id, "cancellations", null).andExpect(status().isOk());

        assertThat(lessonStatus(lessonId)).isEqualTo("CANCELED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"REJECTED", "CANCELED"})
    void 처리가_끝난_부재는_취소할_수_없다(String current) throws Exception {
        long id = insertAbsence(instructorId, daysFromNow(5), daysFromNow(6), current);

        action(adminToken, id, "cancellations", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ABSENCE_STATUS"));
        action(login("i@example.com"), id, "cancellations", null)
                .andExpect(status().isConflict());
    }

    @Test
    void 다른_강사와_회원은_404이고_없는_부재도_404이다() throws Exception {
        long id = insertAbsence(instructorId, daysFromNow(5), daysFromNow(6), "REQUESTED");
        insertInstructor("i2@example.com", "박강사");
        insertMember("m@example.com", "회원", "MEMBER", "ACTIVE", BASE);

        action(login("i2@example.com"), id, "cancellations", null).andExpect(status().isNotFound());
        action(login("m@example.com"), id, "cancellations", null).andExpect(status().isNotFound());
        action(adminToken, 999999, "cancellations", null).andExpect(status().isNotFound());
        assertThat(absenceStatus(id)).isEqualTo("REQUESTED");
    }

    @Test
    void 취소도_인증하지_않으면_401이다() throws Exception {
        mockMvc.perform(post("/absences/1/cancellations")).andExpect(status().isUnauthorized());
    }
}
