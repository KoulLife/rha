package com.rha.absence;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GetAbsencesTest extends AbsenceTestSupport {

    long a;
    long b;

    @BeforeEach
    void prepare() {
        a = insertInstructor("a@example.com", "김강사");
        b = insertInstructor("b@example.com", "박강사");
    }

    @Test
    void 관리자는_모든_부재를_시작_시각_내림차순으로_본다() throws Exception {
        long first = insertAbsence(a, daysFromNow(5), daysFromNow(6), "REQUESTED");
        long second = insertAbsence(b, daysFromNow(10), daysFromNow(11), "APPROVED");
        long third = insertAbsence(a, daysFromNow(20), daysFromNow(21), "CANCELED");
        String token = adminToken();

        list(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].id").value(third))
                .andExpect(jsonPath("$.content[1].id").value(second))
                .andExpect(jsonPath("$.content[2].id").value(first))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(3));
    }

    @Test
    void 시작이_같으면_id_내림차순이다() throws Exception {
        var start = daysFromNow(5);
        long first = insertAbsence(a, start, daysFromNow(6), "REJECTED");
        long second = insertAbsence(b, start, daysFromNow(6), "REJECTED");
        String token = adminToken();

        list(token)
                .andExpect(jsonPath("$.content[0].id").value(second))
                .andExpect(jsonPath("$.content[1].id").value(first));
    }

    @Test
    void 강사는_본인_부재만_보고_다른_강사를_지정해도_본인_것만_나온다() throws Exception {
        long mine = insertAbsence(a, daysFromNow(5), daysFromNow(6), "REQUESTED");
        insertAbsence(b, daysFromNow(10), daysFromNow(11), "REQUESTED");
        String token = login("a@example.com");

        list(token)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(mine));
        list(token, "instructorId", String.valueOf(b))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(mine));
    }

    @Test
    void 회원은_빈_목록이다() throws Exception {
        insertAbsence(a, daysFromNow(5), daysFromNow(6), "REQUESTED");
        String token = memberToken();

        list(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(0));
    }

    @Test
    void 강사_상태_기간으로_필터한다() throws Exception {
        long near = insertAbsence(a, daysFromNow(5), daysFromNow(6), "REQUESTED");
        long far = insertAbsence(b, daysFromNow(20), daysFromNow(22), "APPROVED");
        String token = adminToken();

        list(token, "instructorId", String.valueOf(b))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(far));
        list(token, "status", "REQUESTED")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(near));
        // from: 이 시각 이후에 끝나는 부재 (포함), to: 이 시각 이전에 시작하는 부재 (미포함)
        list(token, "from", daysFromNow(10).toString())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(far));
        list(token, "to", daysFromNow(10).toString())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(near));
        list(token, "from", daysFromNow(5).plusSeconds(1).toString(), "to", daysFromNow(21).toString())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void 페이지네이션이_동작한다() throws Exception {
        for (int i = 0; i < 5; i++) {
            insertAbsence(a, daysFromNow(10 + i * 2), daysFromNow(11 + i * 2), "REJECTED");
        }
        String token = adminToken();

        list(token, "page", "2", "size", "2")
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.pageInfo.page").value(2))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(5))
                .andExpect(jsonPath("$.pageInfo.totalPages").value(3));
    }

    @ParameterizedTest
    @CsvSource({"page,0", "size,0", "size,101", "status,UNKNOWN", "from,not-a-date", "to,not-a-date",
            "instructorId,abc"})
    void 잘못된_파라미터면_400이다(String key, String value) throws Exception {
        String token = adminToken();

        list(token, key, value)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void to가_from보다_늦지_않으면_400이다() throws Exception {
        String token = adminToken();

        list(token, "from", daysFromNow(5).toString(), "to", daysFromNow(5).toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 인증하지_않으면_401이다() throws Exception {
        mockMvc.perform(get("/absences")).andExpect(status().isUnauthorized());
    }
}
