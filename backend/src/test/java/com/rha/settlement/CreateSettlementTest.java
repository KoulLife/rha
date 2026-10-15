package com.rha.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

class CreateSettlementTest extends SettlementTestSupport {

    long roomId;
    long groupType;
    long privateType;
    long instructorId;
    long memberA;
    long memberB;
    long passA;
    long passB;
    String token;

    @BeforeEach
    void prepare() throws Exception {
        roomId = insertRoom();
        groupType = insertType("GROUP", "단체");
        privateType = insertType("PRIVATE", "개인");
        instructorId = insertInstructor("i@example.com", "박강사");
        long product = insertProduct();
        memberA = insertMember("a@example.com", "김회원", "MEMBER", "ACTIVE", BASE);
        memberB = insertMember("b@example.com", "이회원", "MEMBER", "ACTIVE", BASE);
        passA = insertPass(product, memberA, instructorId);
        passB = insertPass(product, memberB, instructorId);
        setRate(instructorId, groupType, 30000);
        setRate(instructorId, privateType, 50000);
        token = adminToken();
    }

    // ===== 계산 =====

    @Test
    void 기간_안의_수업을_계산하면_금액과_수업별_내역이_만들어진다() throws Exception {
        long group = insertLesson(groupType, instructorId, roomId, at(-5, 10), "PUBLISHED");
        insertReservation(group, memberA, passA, "ATTENDED");
        insertReservation(group, memberB, passB, "NO_SHOW");
        long privateLesson = insertLesson(privateType, instructorId, roomId, at(-3, 14), "PUBLISHED");
        insertReservation(privateLesson, memberA, passA, "NO_SHOW");
        long empty = insertLesson(groupType, instructorId, roomId, at(-4, 9), "PUBLISHED");
        insertReservation(empty, memberA, passA, "CANCELED");

        calculate(token, body(instructorId, days(-10), days(-1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.instructor.id").value(instructorId))
                .andExpect(jsonPath("$.instructor.name").value("박강사"))
                .andExpect(jsonPath("$.periodStart").value(days(-10).toString()))
                .andExpect(jsonPath("$.periodEnd").value(days(-1).toString()))
                .andExpect(jsonPath("$.status").value("CALCULATED"))
                .andExpect(jsonPath("$.totalAmount").value(80000))
                .andExpect(jsonPath("$.settledLessonCount").value(2))
                .andExpect(jsonPath("$.excludedLessonCount").value(1))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.payment").doesNotExist())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].lesson.id").value(group))
                .andExpect(jsonPath("$.items[0].lesson.lessonType.code").value("GROUP"))
                .andExpect(jsonPath("$.items[0].attendedCount").value(1))
                .andExpect(jsonPath("$.items[0].noShowCount").value(1))
                .andExpect(jsonPath("$.items[0].unitPrice").value(30000))
                .andExpect(jsonPath("$.items[0].amount").value(30000))
                .andExpect(jsonPath("$.items[0].settled").value(true))
                .andExpect(jsonPath("$.items[0].excludedReason").doesNotExist())
                .andExpect(jsonPath("$.items[1].lesson.id").value(empty))
                .andExpect(jsonPath("$.items[1].settled").value(false))
                .andExpect(jsonPath("$.items[1].unitPrice").doesNotExist())
                .andExpect(jsonPath("$.items[1].amount").value(0))
                .andExpect(jsonPath("$.items[1].excludedReason").value("NO_RESERVATION"))
                .andExpect(jsonPath("$.items[2].lesson.id").value(privateLesson))
                .andExpect(jsonPath("$.items[2].noShowCount").value(1))
                .andExpect(jsonPath("$.items[2].amount").value(50000));

        assertThat(settlementCount()).isEqualTo(1);
        assertThat(itemCount()).isEqualTo(3);
    }

    @Test
    void 전원_노쇼여도_정산_대상이다() throws Exception {
        long lesson = insertLesson(groupType, instructorId, roomId, at(-2, 10), "PUBLISHED");
        insertReservation(lesson, memberA, passA, "NO_SHOW");

        calculate(token, body(instructorId, days(-3), days(-1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(30000))
                .andExpect(jsonPath("$.settledLessonCount").value(1));
    }

    @Test
    void 취소된_수업과_기간_밖_수업과_다른_강사의_수업은_대상이_아니다() throws Exception {
        long other = insertInstructor("o@example.com", "다른강사");
        setRate(other, groupType, 99999);
        long canceled = insertLesson(groupType, instructorId, roomId, at(-5, 10), "CANCELED");
        insertReservation(canceled, memberA, passA, "CANCELED");
        long before = insertLesson(groupType, instructorId, roomId, at(-20, 10), "PUBLISHED");
        insertReservation(before, memberA, passA, "ATTENDED");
        long after = insertLesson(groupType, instructorId, roomId, at(-1, 10), "PUBLISHED");
        insertReservation(after, memberA, passA, "ATTENDED");
        long others = insertLesson(groupType, other, roomId, at(-6, 10), "PUBLISHED");
        insertReservation(others, memberA, passA, "ATTENDED");

        calculate(token, body(instructorId, days(-10), days(-2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(0))
                .andExpect(jsonPath("$.settledLessonCount").value(0))
                .andExpect(jsonPath("$.excludedLessonCount").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void 기간의_시작일과_종료일을_포함하고_현지_날짜로_판단한다() throws Exception {
        // 현지 00:30은 UTC로는 전날이다. 현지 날짜 기준으로 시작일에 속해야 한다.
        long first = insertLesson(groupType, instructorId, roomId, at(-5, 0).plusSeconds(1800), "PUBLISHED");
        long last = insertLesson(groupType, instructorId, roomId, at(-2, 23), "PUBLISHED");
        insertReservation(first, memberA, passA, "ATTENDED");
        insertReservation(last, memberA, passA, "ATTENDED");

        calculate(token, body(instructorId, days(-5), days(-2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.settledLessonCount").value(2))
                .andExpect(jsonPath("$.totalAmount").value(60000));
    }

    @Test
    void 계산_뒤_단가가_바뀌어도_정산의_단가는_유지된다() throws Exception {
        long lesson = insertLesson(groupType, instructorId, roomId, at(-2, 10), "PUBLISHED");
        insertReservation(lesson, memberA, passA, "ATTENDED");
        String response = calculate(token, body(instructorId, days(-3), days(-1))).andReturn().getResponse()
                .getContentAsString();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(response, "$.id")).longValue();

        setRate(instructorId, groupType, 1);

        detail(token, id)
                .andExpect(jsonPath("$.totalAmount").value(30000))
                .andExpect(jsonPath("$.items[0].unitPrice").value(30000));
    }

    @Test
    void 퇴사한_강사도_정산할_수_있다() throws Exception {
        long resigned = insertMember("r@example.com", "퇴사", "INSTRUCTOR", "RESIGNED", BASE);

        calculate(token, body(resigned, days(-5), days(-1))).andExpect(status().isCreated());
    }

    @Test
    void 이미_다른_정산에_포함된_수업은_대상에서_제외된다() throws Exception {
        long other = insertInstructor("o@example.com", "다른강사");
        setRate(other, groupType, 10000);
        long lesson = insertLesson(groupType, instructorId, roomId, at(-2, 10), "PUBLISHED");
        insertReservation(lesson, memberA, passA, "ATTENDED");
        calculate(token, body(instructorId, days(-3), days(-1))).andExpect(status().isCreated());
        jdbc.update("UPDATE lessons SET instructor_id = ? WHERE id = ?", other, lesson);

        calculate(token, body(other, days(-3), days(-1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    // ===== 409 =====

    @Test
    void 출결이_기록되지_않은_예약이_있으면_409이고_수업_ID가_담긴다() throws Exception {
        long done = insertLesson(groupType, instructorId, roomId, at(-5, 10), "PUBLISHED");
        insertReservation(done, memberA, passA, "ATTENDED");
        long open1 = insertLesson(groupType, instructorId, roomId, at(-4, 10), "PUBLISHED");
        insertReservation(open1, memberA, passA, "RESERVED");
        long open2 = insertLesson(groupType, instructorId, roomId, at(-3, 10), "PUBLISHED");
        insertReservation(open2, memberA, passA, "ATTENDED");
        insertReservation(open2, memberB, passB, "RESERVED");

        calculate(token, body(instructorId, days(-10), days(-1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UNRECORDED_ATTENDANCE_EXISTS"))
                .andExpect(jsonPath("$.invalidParams.length()").value(2))
                .andExpect(jsonPath("$.invalidParams[0].field").value(String.valueOf(open1)))
                .andExpect(jsonPath("$.invalidParams[0].reason").value("UNRECORDED_ATTENDANCE_EXISTS"))
                .andExpect(jsonPath("$.invalidParams[1].field").value(String.valueOf(open2)));
        assertThat(settlementCount()).isZero();
        assertThat(itemCount()).isZero();
    }

    @Test
    void 단가가_없는_수업_유형이_있으면_409이고_수업_유형_ID가_담긴다() throws Exception {
        long noRate = insertType("YOGA", "요가");
        long a = insertLesson(noRate, instructorId, roomId, at(-5, 10), "PUBLISHED");
        long b = insertLesson(noRate, instructorId, roomId, at(-4, 10), "PUBLISHED");
        insertReservation(a, memberA, passA, "ATTENDED");
        insertReservation(b, memberA, passA, "ATTENDED");

        calculate(token, body(instructorId, days(-10), days(-1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAY_RATE_NOT_SET"))
                .andExpect(jsonPath("$.invalidParams.length()").value(1))
                .andExpect(jsonPath("$.invalidParams[0].field").value(String.valueOf(noRate)))
                .andExpect(jsonPath("$.invalidParams[0].reason").value("PAY_RATE_NOT_SET"));
        assertThat(settlementCount()).isZero();
    }

    @Test
    void 예약이_없는_수업의_유형은_단가가_없어도_된다() throws Exception {
        long noRate = insertType("YOGA", "요가");
        insertLesson(noRate, instructorId, roomId, at(-5, 10), "PUBLISHED");

        calculate(token, body(instructorId, days(-10), days(-1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.excludedLessonCount").value(1));
    }

    @Test
    void 같은_강사의_정산과_기간이_겹치면_409이다() throws Exception {
        insertSettlement(instructorId, days(-10), days(-5), "CALCULATED", 0, BASE);

        calculate(token, body(instructorId, days(-6), days(-2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_PERIOD_OVERLAP"));
        calculate(token, body(instructorId, days(-12), days(-10)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_PERIOD_OVERLAP"));
        assertThat(settlementCount()).isEqualTo(1);
    }

    @Test
    void 맞닿지_않은_기간과_다른_강사의_정산은_겹침이_아니다() throws Exception {
        long other = insertInstructor("o@example.com", "다른강사");
        insertSettlement(instructorId, days(-10), days(-5), "CONFIRMED", 0, BASE);
        insertSettlement(other, days(-4), days(-2), "CALCULATED", 0, BASE);

        calculate(token, body(instructorId, days(-4), days(-2))).andExpect(status().isCreated());
    }

    @Test
    void 기간_겹침이_출결_미기록보다_먼저_확인된다() throws Exception {
        insertSettlement(instructorId, days(-10), days(-5), "CALCULATED", 0, BASE);
        long lesson = insertLesson(groupType, instructorId, roomId, at(-3, 10), "PUBLISHED");
        insertReservation(lesson, memberA, passA, "RESERVED");

        calculate(token, body(instructorId, days(-6), days(-2)))
                .andExpect(jsonPath("$.code").value("SETTLEMENT_PERIOD_OVERLAP"));
    }

    // ===== 400, 404 =====

    @Test
    void 시작일이_종료일보다_늦으면_400_INVALID_PERIOD이다() throws Exception {
        calculate(token, body(instructorId, days(-1), days(-3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
    }

    @Test
    void 종료일이_오늘이거나_미래이면_400_INVALID_PERIOD이다() throws Exception {
        calculate(token, body(instructorId, days(-3), today()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
        calculate(token, body(instructorId, days(-3), days(2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
    }

    @Test
    void 대상이_강사가_아니면_400_INVALID_INSTRUCTOR이고_없으면_404이다() throws Exception {
        calculate(token, body(memberA, days(-3), days(-1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INSTRUCTOR"));
        calculate(token, body(999999, days(-3), days(-1))).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"periodStart\":\"2026-10-01\",\"periodEnd\":\"2026-10-02\"}",
            "{\"instructorId\":1,\"periodEnd\":\"2026-10-02\"}",
            "{\"instructorId\":1,\"periodStart\":\"2026-10-01\"}",
            "{\"instructorId\":1,\"periodStart\":\"not-a-date\",\"periodEnd\":\"2026-10-02\"}",
            "{\"instructorId\":\"x\",\"periodStart\":\"2026-10-01\",\"periodEnd\":\"2026-10-02\"}",
            "not json"
    })
    void 요청_형식이_올바르지_않으면_400_INVALID_INPUT이다(String json) throws Exception {
        calculate(token, json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 관리자가_아니면_403이고_잘못된_본문이어도_403이다() throws Exception {
        for (String other : new String[] {instructorToken(), memberToken()}) {
            calculate(other, body(instructorId, days(-3), days(-1)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
            calculate(other, "{}").andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/settlements").contentType(MediaType.APPLICATION_JSON)
                        .content(body(instructorId, days(-3), days(-1))))
                .andExpect(status().isUnauthorized());
    }
}
