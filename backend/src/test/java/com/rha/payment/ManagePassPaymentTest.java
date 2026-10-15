package com.rha.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/** 결제 기록 정정, 삭제, 환불 체크. */
class ManagePassPaymentTest extends PaymentTestSupport {

    long productId;
    long memberId;
    long instructorId;
    long passId;
    long paymentId;
    String token;

    @BeforeEach
    void prepare() throws Exception {
        productId = insertProduct();
        memberId = insertMember("mem@example.com", "김회원", "MEMBER", "ACTIVE", BASE);
        instructorId = insertInstructor();
        passId = insertPass(productId, memberId, instructorId, 300000, days(-5), days(24), "ACTIVE");
        token = adminToken();
        paymentId = insertPayment(passId, "CARD", 100000, days(-3), adminId());
    }

    private String column(String name) {
        return jdbc.queryForObject("SELECT " + name + "::text FROM pass_payments WHERE id = ?", String.class,
                paymentId);
    }

    // ===== updatePassPayment =====

    @Test
    void 전달한_필드만_정정되고_나머지는_그대로이다() throws Exception {
        correct(token, passId, paymentId, "{\"amount\":150000}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId))
                .andExpect(jsonPath("$.amount").value(150000))
                .andExpect(jsonPath("$.method").value("CARD"))
                .andExpect(jsonPath("$.paidAt").value(days(-3).toString()));
        passDetail(token, passId).andExpect(jsonPath("$.paidAmount").value(150000));
    }

    @Test
    void 수단_결제일_메모를_정정할_수_있고_메모는_null로_지운다() throws Exception {
        correct(token, passId, paymentId, "{\"method\":\"CASH\",\"paidAt\":\"" + days(-4) + "\",\"memo\":\"수정\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("CASH"))
                .andExpect(jsonPath("$.paidAt").value(days(-4).toString()))
                .andExpect(jsonPath("$.memo").value("수정"));

        correct(token, passId, paymentId, "{\"memo\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memo").doesNotExist());
        assertThat(column("memo")).isNull();
    }

    @Test
    void 정정_후_합계가_금액을_넘으면_409이고_자기_자신은_합계에서_뺀다() throws Exception {
        insertPayment(passId, "CASH", 150000, days(-2), adminId());

        correct(token, passId, paymentId, "{\"amount\":150000}").andExpect(status().isOk());
        correct(token, passId, paymentId, "{\"amount\":150001}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_EXCEEDS_PRICE"));
        assertThat(column("amount")).isEqualTo("150000");
    }

    @Test
    void 환불로_체크된_기록은_정정할_수_없다() throws Exception {
        markRefunded(paymentId, today(), "환불");

        correct(token, passId, paymentId, "{\"memo\":\"x\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_REFUNDED"));
    }

    @Test
    void 정정_결제일이_미래이면_400_INVALID_PAYMENT_DATE이다() throws Exception {
        correct(token, passId, paymentId, "{\"paidAt\":\"" + days(1) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_DATE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"amount\":0}",
            "{\"method\":\"BITCOIN\"}",
            "{\"paidAt\":\"not-a-date\"}",
            "{\"amount\":\"x\"}",
            "not json"
    })
    void 정정_요청_값이_올바르지_않으면_400이다(String json) throws Exception {
        correct(token, passId, paymentId, json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 다른_이용권의_기록이거나_없으면_404이다() throws Exception {
        long other = insertPass(productId, memberId, instructorId, 300000, days(0), days(29), "ACTIVE");

        correct(token, other, paymentId, "{\"amount\":1000}").andExpect(status().isNotFound());
        correct(token, passId, 999999, "{\"amount\":1000}").andExpect(status().isNotFound());
        correct(token, 999999, paymentId, "{\"amount\":1000}").andExpect(status().isNotFound());
    }

    @Test
    void 정정은_관리자만_할_수_있다() throws Exception {
        correct(memberToken(), passId, paymentId, "{\"amount\":1000}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        correct(instructorToken(), 999999, 999999, "{}").andExpect(status().isForbidden());
        assertThat(column("amount")).isEqualTo("100000");
        mockMvc.perform(patch("/passes/" + passId + "/payments/" + paymentId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1}"))
                .andExpect(status().isUnauthorized());
    }

    // ===== deletePassPayment =====

    @Test
    void 삭제하면_204이고_미수금이_다시_늘어난다() throws Exception {
        remove(token, passId, paymentId).andExpect(status().isNoContent());

        assertThat(paymentCount()).isZero();
        passDetail(token, passId)
                .andExpect(jsonPath("$.paidAmount").value(0))
                .andExpect(jsonPath("$.unpaidAmount").value(300000))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"));
    }

    @Test
    void 환불로_체크된_기록은_삭제할_수_없다() throws Exception {
        markRefunded(paymentId, today(), null);

        remove(token, passId, paymentId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_REFUNDED"));
        assertThat(paymentCount()).isEqualTo(1);
    }

    @Test
    void 삭제도_다른_이용권의_기록이거나_없으면_404이다() throws Exception {
        long other = insertPass(productId, memberId, instructorId, 300000, days(0), days(29), "ACTIVE");

        remove(token, other, paymentId).andExpect(status().isNotFound());
        remove(token, passId, 999999).andExpect(status().isNotFound());
        assertThat(paymentCount()).isEqualTo(1);
    }

    @Test
    void 삭제는_관리자만_할_수_있다() throws Exception {
        remove(memberToken(), passId, paymentId).andExpect(status().isForbidden());
        remove(instructorToken(), 999999, 999999).andExpect(status().isForbidden());
        mockMvc.perform(delete("/passes/" + passId + "/payments/" + paymentId))
                .andExpect(status().isUnauthorized());
        assertThat(paymentCount()).isEqualTo(1);
    }

    // ===== setPassPaymentRefund =====

    @Test
    void 환불로_체크하면_환불일과_메모가_기록되고_결제_합계는_그대로이다() throws Exception {
        refund(token, passId, paymentId,
                "{\"refunded\":true,\"refundedAt\":\"" + days(-1) + "\",\"memo\":\"카드 취소\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refunded").value(true))
                .andExpect(jsonPath("$.refundedAt").value(days(-1).toString()))
                .andExpect(jsonPath("$.refundMemo").value("카드 취소"));

        passDetail(token, passId)
                .andExpect(jsonPath("$.paidAmount").value(100000))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void 환불일을_생략하면_오늘이다() throws Exception {
        refund(token, passId, paymentId, "{\"refunded\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundedAt").value(today().toString()))
                .andExpect(jsonPath("$.refundMemo").doesNotExist());
    }

    @Test
    void 체크를_해제하면_환불일과_메모가_지워진다() throws Exception {
        markRefunded(paymentId, days(-1), "메모");

        refund(token, passId, paymentId, "{\"refunded\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refunded").value(false))
                .andExpect(jsonPath("$.refundedAt").doesNotExist())
                .andExpect(jsonPath("$.refundMemo").doesNotExist());
        assertThat(column("refunded_at")).isNull();
        assertThat(column("refund_memo")).isNull();
    }

    @Test
    void 같은_값으로_다시_요청해도_오류_없이_처리된다() throws Exception {
        refund(token, passId, paymentId, "{\"refunded\":false}").andExpect(status().isOk());
        refund(token, passId, paymentId, "{\"refunded\":false}").andExpect(status().isOk());
    }

    @Test
    void 이미_환불_체크된_기록에_다시_true를_보내면_전달한_값으로_갱신하고_생략하면_유지한다() throws Exception {
        markRefunded(paymentId, days(-2), "처음 메모");

        refund(token, passId, paymentId, "{\"refunded\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundedAt").value(days(-2).toString()))
                .andExpect(jsonPath("$.refundMemo").value("처음 메모"));
        refund(token, passId, paymentId,
                "{\"refunded\":true,\"refundedAt\":\"" + days(-1) + "\",\"memo\":\"바뀐 메모\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundedAt").value(days(-1).toString()))
                .andExpect(jsonPath("$.refundMemo").value("바뀐 메모"));
    }

    @Test
    void 환불일이_미래이거나_결제일보다_빠르면_400_INVALID_PAYMENT_DATE이다() throws Exception {
        refund(token, passId, paymentId, "{\"refunded\":true,\"refundedAt\":\"" + days(1) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_DATE"));
        refund(token, passId, paymentId, "{\"refunded\":true,\"refundedAt\":\"" + days(-4) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_DATE"));
        assertThat(column("refunded")).isEqualTo("false");
    }

    @Test
    void 결제일과_같은_날의_환불은_허용한다() throws Exception {
        refund(token, passId, paymentId, "{\"refunded\":true,\"refundedAt\":\"" + days(-3) + "\"}")
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"refunded\":\"yes\"}", "{\"refunded\":null}",
            "{\"refunded\":true,\"refundedAt\":\"not-a-date\"}", "not json"})
    void 환불_요청_형식이_올바르지_않으면_400_INVALID_INPUT이다(String json) throws Exception {
        refund(token, passId, paymentId, json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 환불은_다른_이용권의_기록이거나_없으면_404이다() throws Exception {
        long other = insertPass(productId, memberId, instructorId, 300000, days(0), days(29), "ACTIVE");

        refund(token, other, paymentId, "{\"refunded\":true}").andExpect(status().isNotFound());
        refund(token, passId, 999999, "{\"refunded\":true}").andExpect(status().isNotFound());
    }

    @Test
    void 환불은_관리자만_할_수_있다() throws Exception {
        refund(memberToken(), passId, paymentId, "{\"refunded\":true}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        refund(instructorToken(), 999999, 999999, "{}").andExpect(status().isForbidden());
        mockMvc.perform(put("/passes/" + passId + "/payments/" + paymentId + "/refund")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"refunded\":true}"))
                .andExpect(status().isUnauthorized());
        assertThat(column("refunded")).isEqualTo("false");
    }
}
