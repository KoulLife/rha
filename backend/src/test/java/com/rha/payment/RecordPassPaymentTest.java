package com.rha.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

class RecordPassPaymentTest extends PaymentTestSupport {

    long productId;
    long memberId;
    long instructorId;
    long passId;
    String token;

    @BeforeEach
    void prepare() throws Exception {
        productId = insertProduct();
        memberId = insertMember("mem@example.com", "김회원", "MEMBER", "ACTIVE", BASE);
        instructorId = insertInstructor();
        passId = insertPass(productId, memberId, instructorId, 300000, days(0), days(29), "ACTIVE");
        token = adminToken();
    }

    @Test
    void 관리자는_결제를_기록할_수_있다() throws Exception {
        record(token, passId, "{\"method\":\"CARD\",\"amount\":100000,\"paidAt\":\"" + days(-1)
                + "\",\"memo\":\"1차 결제\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.method").value("CARD"))
                .andExpect(jsonPath("$.amount").value(100000))
                .andExpect(jsonPath("$.paidAt").value(days(-1).toString()))
                .andExpect(jsonPath("$.memo").value("1차 결제"))
                .andExpect(jsonPath("$.refunded").value(false))
                .andExpect(jsonPath("$.refundedAt").doesNotExist())
                .andExpect(jsonPath("$.refundMemo").doesNotExist())
                .andExpect(jsonPath("$.member.id").value(memberId))
                .andExpect(jsonPath("$.member.name").value("김회원"))
                .andExpect(jsonPath("$.pass.id").value(passId))
                .andExpect(jsonPath("$.pass.passProduct.name").value("필라테스 10회권"))
                .andExpect(jsonPath("$.pass.endDate").value(days(29).toString()))
                .andExpect(jsonPath("$.recordedBy.id").value(adminId()))
                .andExpect(jsonPath("$.recordedBy.name").value("관리자"))
                .andExpect(jsonPath("$.createdAt").isString());
        assertThat(paymentCount()).isEqualTo(1);
    }

    @Test
    void 기록하면_이용권의_결제_합계와_결제_상태가_갱신된다() throws Exception {
        passDetail(token, passId)
                .andExpect(jsonPath("$.paidAmount").value(0))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"));

        record(token, passId, paymentBody("CARD", 100000, today())).andExpect(status().isCreated());
        passDetail(token, passId)
                .andExpect(jsonPath("$.paidAmount").value(100000))
                .andExpect(jsonPath("$.unpaidAmount").value(200000))
                .andExpect(jsonPath("$.paymentStatus").value("PARTIAL"));

        record(token, passId, paymentBody("CASH", 200000, today())).andExpect(status().isCreated());
        passDetail(token, passId)
                .andExpect(jsonPath("$.paidAmount").value(300000))
                .andExpect(jsonPath("$.unpaidAmount").value(0))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));
    }

    @Test
    void 다른_이용권의_결제는_합계에_포함되지_않는다() throws Exception {
        long other = insertPass(productId, memberId, instructorId, 300000, days(0), days(29), "ACTIVE");
        record(token, other, paymentBody("CARD", 300000, today())).andExpect(status().isCreated());

        record(token, passId, paymentBody("CARD", 300000, today())).andExpect(status().isCreated());
    }

    @Test
    void 결제_합계가_금액을_넘으면_409_PAYMENT_EXCEEDS_PRICE이다() throws Exception {
        record(token, passId, paymentBody("CARD", 250000, today())).andExpect(status().isCreated());

        record(token, passId, paymentBody("CARD", 50001, today()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_EXCEEDS_PRICE"));
        assertThat(paymentCount()).isEqualTo(1);
    }

    @Test
    void 환불로_체크된_기록도_결제_합계에_포함된다() throws Exception {
        long done = insertPayment(passId, "CARD", 300000, days(-1), adminId());
        markRefunded(done, today(), "환불");

        record(token, passId, paymentBody("CARD", 1, today()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_EXCEEDS_PRICE"));
    }

    @Test
    void 금액이_0인_이용권에는_결제를_기록할_수_없다() throws Exception {
        long free = insertPass(productId, memberId, instructorId, 0, days(0), days(29), "ACTIVE");

        record(token, free, paymentBody("CASH", 1, today()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_EXCEEDS_PRICE"));
    }

    @Test
    void 만료된_이용권에도_기록할_수_있다() throws Exception {
        long expired = insertPass(productId, memberId, instructorId, 300000, days(-40), days(-10), "EXPIRED");

        record(token, expired, paymentBody("TRANSFER", 100000, days(-20))).andExpect(status().isCreated());
    }

    @Test
    void 결제일이_미래이면_400_INVALID_PAYMENT_DATE이다() throws Exception {
        record(token, passId, paymentBody("CARD", 1000, days(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_DATE"));
        assertThat(paymentCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"amount\":1000,\"paidAt\":\"2026-10-01\"}",
            "{\"method\":\"CARD\",\"paidAt\":\"2026-10-01\"}",
            "{\"method\":\"CARD\",\"amount\":1000}",
            "{\"method\":\"CARD\",\"amount\":0,\"paidAt\":\"2026-10-01\"}",
            "{\"method\":\"CARD\",\"amount\":-5,\"paidAt\":\"2026-10-01\"}",
            "{\"method\":\"BITCOIN\",\"amount\":1000,\"paidAt\":\"2026-10-01\"}",
            "{\"method\":\"CARD\",\"amount\":1000,\"paidAt\":\"not-a-date\"}",
            "{\"method\":\"CARD\",\"amount\":\"x\",\"paidAt\":\"2026-10-01\"}",
            "not json"
    })
    void 요청_값이_올바르지_않으면_400_INVALID_INPUT이다(String json) throws Exception {
        record(token, passId, json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 메모가_200자를_넘으면_400이다() throws Exception {
        record(token, passId, "{\"method\":\"CARD\",\"amount\":1000,\"paidAt\":\"" + today() + "\",\"memo\":\""
                + "가".repeat(201) + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void 없는_이용권이면_404이다() throws Exception {
        record(token, 999999, paymentBody("CARD", 1000, today())).andExpect(status().isNotFound());
    }

    @Test
    void 관리자가_아니면_403이고_잘못된_본문이어도_403이다() throws Exception {
        for (String other : new String[] {memberToken(), instructorToken()}) {
            record(other, passId, paymentBody("CARD", 1000, today()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
            record(other, 999999, "{}").andExpect(status().isForbidden());
        }
        assertThat(paymentCount()).isZero();
    }

    @Test
    void 인증하지_않으면_401이다() throws Exception {
        mockMvc.perform(post("/passes/" + passId + "/payments").contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("CARD", 1000, today())))
                .andExpect(status().isUnauthorized());
    }
}
