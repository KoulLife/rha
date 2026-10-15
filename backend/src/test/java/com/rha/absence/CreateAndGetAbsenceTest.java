package com.rha.absence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

class CreateAndGetAbsenceTest extends AbsenceTestSupport {

    long instructorId;
    String instructorToken;

    @BeforeEach
    void prepare() throws Exception {
        instructorId = insertInstructor("i@example.com", "김강사");
        instructorToken = login("i@example.com");
    }

    // ===== createAbsence =====

    @Test
    void 강사는_부재를_신청할_수_있고_승인_대기_상태로_등록된다() throws Exception {
        Instant start = daysFromNow(5);
        Instant end = daysFromNow(7);

        createAbsence(instructorToken, period(start, end))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.instructor.id").value(instructorId))
                .andExpect(jsonPath("$.instructor.name").value("김강사"))
                .andExpect(jsonPath("$.startAt").value(start.toString()))
                .andExpect(jsonPath("$.endAt").value(end.toString()))
                .andExpect(jsonPath("$.reason").value("휴가"))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.processedBy").doesNotExist())
                .andExpect(jsonPath("$.processedAt").doesNotExist())
                .andExpect(jsonPath("$.createdAt").isString());
        assertThat(absenceCount()).isEqualTo(1);
    }

    @Test
    void 사유는_생략할_수_있다() throws Exception {
        createAbsence(instructorToken,
                "{\"startAt\":\"" + daysFromNow(5) + "\",\"endAt\":\"" + daysFromNow(6) + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reason").doesNotExist());
    }

    @Test
    void 종료가_시작보다_늦지_않으면_400_INVALID_ABSENCE_PERIOD이다() throws Exception {
        Instant start = daysFromNow(5);

        createAbsence(instructorToken, period(start, start))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ABSENCE_PERIOD"));
        createAbsence(instructorToken, period(start, daysFromNow(4)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ABSENCE_PERIOD"));
    }

    @Test
    void 시작이_과거이면_400_INVALID_ABSENCE_PERIOD이다() throws Exception {
        createAbsence(instructorToken, period(daysFromNow(-1), daysFromNow(2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ABSENCE_PERIOD"));
        assertThat(absenceCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"startAt\":\"2099-01-01T00:00:00Z\"}",
            "{\"endAt\":\"2099-01-02T00:00:00Z\"}",
            "{\"startAt\":\"not-a-date\",\"endAt\":\"2099-01-02T00:00:00Z\"}",
            "not json"
    })
    void 요청_형식이_올바르지_않으면_400_INVALID_INPUT이다(String json) throws Exception {
        createAbsence(instructorToken, json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 사유가_200자를_넘으면_400이다() throws Exception {
        createAbsence(instructorToken, "{\"startAt\":\"" + daysFromNow(5) + "\",\"endAt\":\"" + daysFromNow(6)
                + "\",\"reason\":\"" + "가".repeat(201) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"REQUESTED", "APPROVED"})
    void 승인_대기나_승인된_부재와_기간이_겹치면_409_ABSENCE_PERIOD_OVERLAP이다(String existing) throws Exception {
        insertAbsence(instructorId, daysFromNow(5), daysFromNow(8), existing);

        createAbsence(instructorToken, period(daysFromNow(7), daysFromNow(10)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ABSENCE_PERIOD_OVERLAP"));
        assertThat(absenceCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"REJECTED", "CANCELED"})
    void 반려되거나_취소된_부재와는_겹쳐도_된다(String existing) throws Exception {
        insertAbsence(instructorId, daysFromNow(5), daysFromNow(8), existing);

        createAbsence(instructorToken, period(daysFromNow(6), daysFromNow(9))).andExpect(status().isCreated());
    }

    @Test
    void 맞닿는_기간과_다른_강사의_부재와는_겹침이_아니다() throws Exception {
        long other = insertInstructor("i2@example.com", "박강사");
        Instant boundary = daysFromNow(8);
        insertAbsence(instructorId, daysFromNow(5), boundary, "APPROVED");
        insertAbsence(other, daysFromNow(9), daysFromNow(12), "APPROVED");

        createAbsence(instructorToken, period(boundary, daysFromNow(10))).andExpect(status().isCreated());
        createAbsence(instructorToken, period(daysFromNow(11), daysFromNow(13)))
                .andExpect(status().isCreated());
    }

    @Test
    void 강사가_아니면_403이고_잘못된_본문이어도_403이다() throws Exception {
        String admin = adminToken();
        String member = memberToken();

        for (String token : new String[] {admin, member}) {
            createAbsence(token, period(daysFromNow(5), daysFromNow(6)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
            createAbsence(token, "{}").andExpect(status().isForbidden());
        }
    }

    @Test
    void 비활성_강사의_기존_토큰은_403이다() throws Exception {
        jdbc.update("UPDATE members SET status = 'INACTIVE' WHERE id = ?", instructorId);

        createAbsence(instructorToken, period(daysFromNow(5), daysFromNow(6))).andExpect(status().isForbidden());
    }

    @Test
    void 인증하지_않으면_401이다() throws Exception {
        mockMvc.perform(post("/absences").contentType(MediaType.APPLICATION_JSON)
                        .content(period(daysFromNow(5), daysFromNow(6))))
                .andExpect(status().isUnauthorized());
    }

    // ===== getAbsence =====

    @Test
    void 관리자와_신청한_강사는_상세를_볼_수_있다() throws Exception {
        long id = insertAbsence(instructorId, daysFromNow(5), daysFromNow(6), "REQUESTED");
        String admin = adminToken();

        for (String token : new String[] {admin, instructorToken}) {
            detail(token, id)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.instructor.id").value(instructorId))
                    .andExpect(jsonPath("$.status").value("REQUESTED"));
        }
    }

    @Test
    void 다른_강사와_회원은_404이다() throws Exception {
        long id = insertAbsence(instructorId, daysFromNow(5), daysFromNow(6), "REQUESTED");
        insertInstructor("i2@example.com", "박강사");
        insertMember("m@example.com", "회원", "MEMBER", "ACTIVE", BASE);

        detail(login("i2@example.com"), id).andExpect(status().isNotFound());
        detail(login("m@example.com"), id).andExpect(status().isNotFound());
    }

    @Test
    void 없는_부재면_404이고_숫자가_아니면_400이다() throws Exception {
        String admin = adminToken();

        detail(admin, 999999).andExpect(status().isNotFound());
        mockMvc.perform(get("/absences/abc").header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 처리된_부재는_처리자_정보가_함께_나온다() throws Exception {
        String admin = adminToken();
        long adminId = jdbc.queryForObject("SELECT id FROM members WHERE email = 'admin@example.com'", Long.class);
        long id = insertAbsence(instructorId, daysFromNow(5), daysFromNow(6), "REJECTED");
        jdbc.update("UPDATE absences SET processed_by = ?, processed_at = now(), reject_reason = '사유' WHERE id = ?",
                adminId, id);

        detail(admin, id)
                .andExpect(jsonPath("$.processedBy.id").value(adminId))
                .andExpect(jsonPath("$.processedBy.name").value("관리자"))
                .andExpect(jsonPath("$.processedAt").isString())
                .andExpect(jsonPath("$.rejectReason").value("사유"));
    }

    @ParameterizedTest
    @CsvSource({"/absences/1"})
    void 상세도_인증하지_않으면_401이다(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
}
