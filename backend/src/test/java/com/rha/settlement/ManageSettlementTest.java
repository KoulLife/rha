package com.rha.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/** 정산 목록·상세와 검토 요청, 확정, 지급 기록, 폐기. */
class ManageSettlementTest extends SettlementTestSupport {

    long roomId;
    long typeId;
    long instructorId;
    long memberId;
    long passId;
    String token;

    @BeforeEach
    void prepare() throws Exception {
        roomId = insertRoom();
        typeId = insertType("GROUP", "단체");
        instructorId = insertInstructor("i@example.com", "박강사");
        memberId = insertMember("a@example.com", "김회원", "MEMBER", "ACTIVE", BASE);
        passId = insertPass(insertProduct(), memberId, instructorId);
        setRate(instructorId, typeId, 30000);
        token = adminToken();
    }

    // ===== getSettlements =====

    @Test
    void 목록은_생성_최신순이고_같으면_id_내림차순이다() throws Exception {
        long old = insertSettlement(instructorId, days(-60), days(-31), "PAID", 1000, BASE);
        Instant same = BASE.plusSeconds(3600);
        long first = insertSettlement(instructorId, days(-30), days(-21), "CALCULATED", 2000, same);
        long second = insertSettlement(instructorId, days(-20), days(-11), "CONFIRMED", 3000, same);

        list(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].id").value(second))
                .andExpect(jsonPath("$.content[1].id").value(first))
                .andExpect(jsonPath("$.content[2].id").value(old))
                .andExpect(jsonPath("$.content[0].instructor.name").value("박강사"))
                .andExpect(jsonPath("$.content[0].totalAmount").value(3000))
                .andExpect(jsonPath("$.content[0].items").doesNotExist())
                .andExpect(jsonPath("$.pageInfo.totalElements").value(3));
    }

    @Test
    void 강사_상태_기간으로_필터한다() throws Exception {
        long other = insertInstructor("o@example.com", "다른강사");
        insertSettlement(instructorId, days(-30), days(-21), "CALCULATED", 1, BASE);
        long b = insertSettlement(other, days(-30), days(-21), "CONFIRMED", 1, BASE.plusSeconds(1));
        long c = insertSettlement(instructorId, days(-20), days(-11), "PAID", 1, BASE.plusSeconds(2));

        list(token, "instructorId", String.valueOf(other))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(b));
        list(token, "status", "PAID")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(c));
        // periodFrom: 이 날짜 이후에 끝나는 기간, periodTo: 이 날짜 이전에 시작하는 기간 (둘 다 포함)
        list(token, "periodFrom", days(-20).toString())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(c));
        list(token, "periodTo", days(-21).toString())
                .andExpect(jsonPath("$.content.length()").value(2));
        list(token, "periodFrom", days(-21).toString(), "periodTo", days(-21).toString())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void 목록_페이지네이션이_동작한다() throws Exception {
        for (int i = 0; i < 5; i++) {
            insertSettlement(instructorId, days(-100 + i * 10), days(-91 + i * 10), "PAID", i,
                    BASE.plusSeconds(i));
        }

        list(token, "page", "2", "size", "2")
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.pageInfo.totalPages").value(3));
    }

    @ParameterizedTest
    @CsvSource({"page,0", "size,101", "status,UNKNOWN", "periodFrom,not-a-date", "instructorId,abc"})
    void 목록의_잘못된_파라미터는_400이다(String key, String value) throws Exception {
        list(token, key, value)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void periodTo가_periodFrom보다_빠르면_400이다() throws Exception {
        list(token, "periodFrom", days(-1).toString(), "periodTo", days(-5).toString())
                .andExpect(status().isBadRequest());
    }

    @Test
    void 목록과_상세는_관리자만_볼_수_있다() throws Exception {
        long id = insertSettlement(instructorId, days(-30), days(-21), "CALCULATED", 1, BASE);

        list(instructorToken()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        detail(memberToken(), id).andExpect(status().isForbidden());
        mockMvc.perform(get("/settlements")).andExpect(status().isUnauthorized());
    }

    // ===== getSettlement =====

    @Test
    void 상세는_수업별_내역을_포함하고_없는_정산은_404이다() throws Exception {
        long lesson = insertLesson(typeId, instructorId, roomId, at(-5, 10), "PUBLISHED");
        long id = insertSettlement(instructorId, days(-10), days(-1), "CONFIRMED", 1000, BASE);
        insertItem(id, lesson);
        jdbc.update("UPDATE settlements SET confirmed_at = now(), review_requested_at = now() WHERE id = ?", id);

        detail(token, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.reviewRequestedAt").isString())
                .andExpect(jsonPath("$.confirmedAt").isString())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].lesson.id").value(lesson));
        detail(token, 999999).andExpect(status().isNotFound());
        mockMvc.perform(get("/settlements/abc").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    // ===== 상태 전이 =====

    @Test
    void 검토_요청_확정_지급_기록으로_상태가_진행된다() throws Exception {
        long id = insertSettlement(instructorId, days(-10), days(-1), "CALCULATED", 1000, BASE);

        action(token, id, "review-requests", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEW_REQUESTED"))
                .andExpect(jsonPath("$.reviewRequestedAt").isString());
        action(token, id, "confirmations", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedAt").isString());
        action(token, id, "payments", "{\"paidAt\":\"" + days(0) + "\",\"memo\":\"계좌이체\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.payment.paidAt").value(days(0).toString()))
                .andExpect(jsonPath("$.payment.memo").value("계좌이체"))
                .andExpect(jsonPath("$.payment.recordedAt").isString())
                .andExpect(jsonPath("$.totalAmount").value(1000));
    }

    @Test
    void 지급일은_과거와_미래_모두_허용하고_메모는_생략할_수_있다() throws Exception {
        long past = insertSettlement(instructorId, days(-30), days(-21), "CONFIRMED", 1, BASE);
        long future = insertSettlement(instructorId, days(-20), days(-11), "CONFIRMED", 1, BASE.plusSeconds(1));

        action(token, past, "payments", "{\"paidAt\":\"" + days(-3) + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.memo").doesNotExist());
        action(token, future, "payments", "{\"paidAt\":\"" + days(5) + "\"}").andExpect(status().isOk());
    }

    @Test
    void 검토_요청은_계산됨_상태에서만_가능하다() throws Exception {
        for (String current : new String[] {"REVIEW_REQUESTED", "CONFIRMED", "PAID"}) {
            long id = insertSettlement(instructorId, days(-300), days(-290), current, 1, BASE);
            action(token, id, "review-requests", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_SETTLEMENT_STATUS"));
            jdbc.update("DELETE FROM settlements WHERE id = ?", id);
        }
    }

    @Test
    void 확정은_검토_요청됨_상태에서만_가능하다() throws Exception {
        for (String current : new String[] {"CALCULATED", "CONFIRMED", "PAID"}) {
            long id = insertSettlement(instructorId, days(-300), days(-290), current, 1, BASE);
            action(token, id, "confirmations", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_SETTLEMENT_STATUS"));
            jdbc.update("DELETE FROM settlements WHERE id = ?", id);
        }
    }

    @Test
    void 지급_기록은_확정됨_상태에서만_가능하다() throws Exception {
        for (String current : new String[] {"CALCULATED", "REVIEW_REQUESTED", "PAID"}) {
            long id = insertSettlement(instructorId, days(-300), days(-290), current, 1, BASE);
            action(token, id, "payments", "{\"paidAt\":\"" + days(0) + "\"}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_SETTLEMENT_STATUS"));
            jdbc.update("DELETE FROM settlements WHERE id = ?", id);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"paidAt\":null}", "{\"paidAt\":\"not-a-date\"}", "not json"})
    void 지급_기록의_형식이_올바르지_않으면_400이다(String json) throws Exception {
        long id = insertSettlement(instructorId, days(-10), days(-1), "CONFIRMED", 1, BASE);

        action(token, id, "payments", json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(settlementStatus(id)).isEqualTo("CONFIRMED");
    }

    @Test
    void 지급_메모가_200자를_넘으면_400이다() throws Exception {
        long id = insertSettlement(instructorId, days(-10), days(-1), "CONFIRMED", 1, BASE);

        action(token, id, "payments", "{\"paidAt\":\"" + days(0) + "\",\"memo\":\"" + "가".repeat(201) + "\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void 없는_정산이면_상태_전이는_404이다() throws Exception {
        action(token, 999999, "review-requests", null).andExpect(status().isNotFound());
        action(token, 999999, "confirmations", null).andExpect(status().isNotFound());
        action(token, 999999, "payments", "{\"paidAt\":\"" + days(0) + "\"}").andExpect(status().isNotFound());
    }

    @Test
    void 상태_전이는_관리자만_할_수_있다() throws Exception {
        long id = insertSettlement(instructorId, days(-10), days(-1), "CALCULATED", 1, BASE);

        String instructor = instructorToken();
        for (String path : new String[] {"review-requests", "confirmations"}) {
            action(instructor, id, path, null)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        }
        action(memberToken(), id, "payments", "{}").andExpect(status().isForbidden());
        mockMvc.perform(post("/settlements/" + id + "/review-requests")).andExpect(status().isUnauthorized());
        assertThat(settlementStatus(id)).isEqualTo("CALCULATED");
    }

    // ===== discardSettlement =====

    @ParameterizedTest
    @ValueSource(strings = {"CALCULATED", "REVIEW_REQUESTED"})
    void 폐기하면_정산과_내역이_삭제되어_수업이_풀리고_다시_계산할_수_있다(String current) throws Exception {
        long lesson = insertLesson(typeId, instructorId, roomId, at(-5, 10), "PUBLISHED");
        insertReservation(lesson, memberId, passId, "ATTENDED");
        long id = insertSettlement(instructorId, days(-10), days(-1), current, 30000, BASE);
        insertItem(id, lesson);

        discard(token, id).andExpect(status().isNoContent()).andExpect(content().string(""));

        assertThat(settlementCount()).isZero();
        assertThat(itemCount()).isZero();
        calculate(token, body(instructorId, days(-10), days(-1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.settledLessonCount").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONFIRMED", "PAID"})
    void 확정_이후에는_폐기할_수_없다(String current) throws Exception {
        long id = insertSettlement(instructorId, days(-10), days(-1), current, 1, BASE);

        discard(token, id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_SETTLEMENT_STATUS"));
        assertThat(settlementCount()).isEqualTo(1);
    }

    @Test
    void 폐기는_없는_정산이면_404이고_관리자만_할_수_있다() throws Exception {
        long id = insertSettlement(instructorId, days(-10), days(-1), "CALCULATED", 1, BASE);

        discard(token, 999999).andExpect(status().isNotFound());
        String instructor = instructorToken();
        discard(instructor, id).andExpect(status().isForbidden());
        discard(instructor, 999999).andExpect(status().isForbidden());
        mockMvc.perform(delete("/settlements/" + id)).andExpect(status().isUnauthorized());
        assertThat(settlementCount()).isEqualTo(1);
    }
}
