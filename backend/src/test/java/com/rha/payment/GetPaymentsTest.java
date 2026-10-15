package com.rha.payment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GetPaymentsTest extends PaymentTestSupport {

    long productId;
    long memberA;
    long memberB;
    long instructorId;
    long passA;
    long passB;
    String token;

    @BeforeEach
    void prepare() throws Exception {
        productId = insertProduct();
        memberA = insertMember("a@example.com", "김회원", "MEMBER", "ACTIVE", BASE);
        memberB = insertMember("b@example.com", "이회원", "MEMBER", "ACTIVE", BASE);
        instructorId = insertInstructor();
        passA = insertPass(productId, memberA, instructorId, 1000000, days(-30), days(30), "ACTIVE");
        passB = insertPass(productId, memberB, instructorId, 1000000, days(-30), days(30), "ACTIVE");
        token = adminToken();
    }

    // ===== getPassPayments =====

    @Test
    void 이용권의_결제_기록은_결제일_내림차순이고_같으면_최신순이다() throws Exception {
        long old = insertPayment(passA, "CARD", 1000, days(-10), adminId());
        long firstSame = insertPayment(passA, "CASH", 2000, days(-1), adminId());
        long secondSame = insertPayment(passA, "CARD", 3000, days(-1), adminId());
        insertPayment(passB, "CARD", 9999, days(-1), adminId());

        passPayments(token, passA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].id").value(secondSame))
                .andExpect(jsonPath("$.content[1].id").value(firstSame))
                .andExpect(jsonPath("$.content[2].id").value(old))
                .andExpect(jsonPath("$.content[0].member.name").value("김회원"))
                .andExpect(jsonPath("$.content[0].pass.id").value(passA))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(3));
    }

    @Test
    void 이용권_결제_기록의_페이지네이션과_404와_권한() throws Exception {
        for (int i = 0; i < 3; i++) {
            insertPayment(passA, "CARD", 1000 + i, days(-i), adminId());
        }

        passPayments(token, passA, "page", "2", "size", "2")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.pageInfo.totalPages").value(2));
        passPayments(token, 999999).andExpect(status().isNotFound());
        passPayments(memberToken(), passA).andExpect(status().isForbidden());
        mockMvc.perform(get("/passes/" + passA + "/payments")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @CsvSource({"page,0", "size,0", "size,101"})
    void 이용권_결제_기록의_잘못된_파라미터는_400이다(String key, String value) throws Exception {
        passPayments(token, passA, key, value)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // ===== getPayments =====

    @Test
    void 전체_결제_기록은_모든_이용권을_가로질러_결제일_내림차순이다() throws Exception {
        long a = insertPayment(passA, "CARD", 1000, days(-3), adminId());
        long b = insertPayment(passB, "CASH", 2000, days(-1), adminId());
        long c = insertPayment(passA, "CARD", 3000, days(-2), adminId());

        allPayments(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].id").value(b))
                .andExpect(jsonPath("$.content[1].id").value(c))
                .andExpect(jsonPath("$.content[2].id").value(a))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(3));
    }

    @Test
    void 결제일_범위는_시작일과_종료일을_포함한다() throws Exception {
        insertPayment(passA, "CARD", 1000, days(-5), adminId());
        long in1 = insertPayment(passA, "CARD", 2000, days(-4), adminId());
        long in2 = insertPayment(passA, "CARD", 3000, days(-2), adminId());
        insertPayment(passA, "CARD", 4000, days(-1), adminId());

        allPayments(token, "paidFrom", days(-4).toString(), "paidTo", days(-2).toString())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(in2))
                .andExpect(jsonPath("$.content[1].id").value(in1));
        allPayments(token, "paidFrom", days(-1).toString()).andExpect(jsonPath("$.content.length()").value(1));
        allPayments(token, "paidTo", days(-5).toString()).andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void 수단_회원_키워드_환불_여부로_필터한다() throws Exception {
        insertPayment(passA, "CARD", 1000, days(-3), adminId());
        long cash = insertPayment(passB, "CASH", 2000, days(-2), adminId());
        long refunded = insertPayment(passA, "TRANSFER", 3000, days(-1), adminId());
        markRefunded(refunded, today(), "환불");

        allPayments(token, "method", "CASH")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(cash));
        allPayments(token, "memberId", String.valueOf(memberA)).andExpect(jsonPath("$.content.length()").value(2));
        allPayments(token, "keyword", "이회")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].member.name").value("이회원"));
        allPayments(token, "refunded", "true")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(refunded))
                .andExpect(jsonPath("$.content[0].refunded").value(true));
        allPayments(token, "refunded", "false").andExpect(jsonPath("$.content.length()").value(2));
        allPayments(token).andExpect(jsonPath("$.content.length()").value(3));
    }

    @Test
    void 정렬_기준과_방향을_지정할_수_있고_같으면_id가_정렬_방향을_따른다() throws Exception {
        long small = insertPayment(passA, "CARD", 1000, days(-1), adminId());
        long big = insertPayment(passA, "CARD", 5000, days(-3), adminId());
        long sameBig = insertPayment(passB, "CARD", 5000, days(-2), adminId());

        allPayments(token, "sortBy", "amount")
                .andExpect(jsonPath("$.content[0].id").value(sameBig))
                .andExpect(jsonPath("$.content[1].id").value(big))
                .andExpect(jsonPath("$.content[2].id").value(small));
        allPayments(token, "sortBy", "amount", "order", "ASC")
                .andExpect(jsonPath("$.content[0].id").value(small))
                .andExpect(jsonPath("$.content[1].id").value(big));
        allPayments(token, "sortBy", "paidAt", "order", "ASC")
                .andExpect(jsonPath("$.content[0].id").value(big));
    }

    @Test
    void 전체_기록의_페이지네이션이_동작한다() throws Exception {
        for (int i = 0; i < 5; i++) {
            insertPayment(passA, "CARD", 1000 + i, days(-i), adminId());
        }

        allPayments(token, "page", "3", "size", "2")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.pageInfo.page").value(3))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(5));
    }

    @ParameterizedTest
    @CsvSource({"page,0", "size,101", "method,BITCOIN", "sortBy,memo", "order,UP", "refunded,maybe",
            "paidFrom,not-a-date", "memberId,abc"})
    void 전체_기록의_잘못된_파라미터는_400이다(String key, String value) throws Exception {
        allPayments(token, key, value)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void paidTo가_paidFrom보다_빠르면_400이다() throws Exception {
        allPayments(token, "paidFrom", days(-1).toString(), "paidTo", days(-2).toString())
                .andExpect(status().isBadRequest());
    }

    @Test
    void 전체_기록은_관리자만_볼_수_있다() throws Exception {
        allPayments(memberToken()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        allPayments(instructorToken()).andExpect(status().isForbidden());
        mockMvc.perform(get("/payments")).andExpect(status().isUnauthorized());
    }
}
