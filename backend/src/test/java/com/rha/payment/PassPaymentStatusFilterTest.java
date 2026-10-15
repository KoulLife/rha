package com.rha.payment;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 결제 기록이 생기면서 이용권 목록의 결제 상태 필터와 응답이 실제 합계를 쓰게 된 동작. */
class PassPaymentStatusFilterTest extends PaymentTestSupport {

    long productId;
    long memberId;
    long instructorId;
    String token;

    @BeforeEach
    void prepare() throws Exception {
        productId = insertProduct();
        memberId = insertMember("mem@example.com", "김회원", "MEMBER", "ACTIVE", BASE);
        instructorId = insertInstructor();
        token = adminToken();
    }

    @Test
    void 결제_상태_필터가_실제_합계를_기준으로_동작한다() throws Exception {
        long unpaid = insertPass(productId, memberId, instructorId, 100000, days(0), days(29), "ACTIVE");
        long partial = insertPass(productId, memberId, instructorId, 100000, days(0), days(29), "ACTIVE");
        long paid = insertPass(productId, memberId, instructorId, 100000, days(0), days(29), "ACTIVE");
        insertPass(productId, memberId, instructorId, 0, days(0), days(29), "ACTIVE");
        insertPayment(partial, "CARD", 40000, today(), adminId());
        insertPayment(paid, "CARD", 60000, today(), adminId());
        insertPayment(paid, "CASH", 40000, today(), adminId());

        listAt("/passes", token, "paymentStatus", "UNPAID")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(unpaid));
        listAt("/passes", token, "paymentStatus", "PARTIAL")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(partial))
                .andExpect(jsonPath("$.content[0].paidAmount").value(40000))
                .andExpect(jsonPath("$.content[0].unpaidAmount").value(60000));
        listAt("/passes", token, "paymentStatus", "PAID")
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(2));
    }

    @Test
    void 내_이용권에도_결제_합계가_반영된다() throws Exception {
        long passId = insertPass(productId, memberId, instructorId, 100000, days(0), days(29), "ACTIVE");
        insertPayment(passId, "CARD", 30000, today(), adminId());
        String member = login("mem@example.com");

        listAt("/members/me/passes", member)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].paidAmount").value(30000))
                .andExpect(jsonPath("$.content[0].unpaidAmount").value(70000))
                .andExpect(jsonPath("$.content[0].paymentStatus").value("PARTIAL"));
    }
}
