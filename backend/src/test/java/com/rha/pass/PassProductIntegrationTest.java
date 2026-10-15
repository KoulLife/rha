package com.rha.pass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 이용권이 생기면서 상품 삭제·수정이 받게 된 제약과 발급 이용권 반영. */
class PassProductIntegrationTest extends PassTestSupport {

    long productId;
    long memberId;
    long instructorId;
    String token;

    @BeforeEach
    void prepare() throws Exception {
        productId = insertProduct("필라테스", 300000, 10, 30);
        memberId = insertMemberNamed("mem@example.com", "김회원");
        instructorId = insertInstructor("i@example.com", "박강사");
        token = adminToken();
    }

    private ResultActions updateProduct(String json) throws Exception {
        return mockMvc.perform(patch("/pass-products/" + productId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void 발급된_이용권이_있는_상품은_삭제할_수_없다() throws Exception {
        insertActivePass(productId, memberId, instructorId);

        mockMvc.perform(delete("/pass-products/" + productId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PASS_PRODUCT_IN_USE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pass_products", Integer.class)).isEqualTo(1);
    }

    @Test
    void 만료된_이용권만_있어도_삭제할_수_없다() throws Exception {
        insertPass(productId, memberId, instructorId, 1, 10, 10, days(-40), days(-10), "EXPIRED");

        mockMvc.perform(delete("/pass-products/" + productId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PASS_PRODUCT_IN_USE"));
    }

    @Test
    void 다른_상품의_이용권은_삭제를_막지_않는다() throws Exception {
        long other = insertProduct("요가", 1, 5, 10);
        insertActivePass(other, memberId, instructorId);

        mockMvc.perform(delete("/pass-products/" + productId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void applyToIssued가_false이면_발급된_이용권은_그대로이다() throws Exception {
        long passId = insertActivePass(productId, memberId, instructorId);

        updateProduct("{\"totalCount\":12,\"validityDays\":60}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedPassCount").value(0));

        assertThat(totalCountOf(passId)).isEqualTo(10);
        assertThat(endDateOf(passId)).isEqualTo(days(29));
    }

    @Test
    void 총_횟수를_반영하면_총_횟수와_잔여_횟수가_변경된_만큼_증감한다() throws Exception {
        long passId = insertPass(productId, memberId, instructorId, 1, 10, 4, days(0), days(29), "ACTIVE");

        updateProduct("{\"totalCount\":12,\"applyToIssued\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passProduct.totalCount").value(12))
                .andExpect(jsonPath("$.affectedPassCount").value(1));

        assertThat(totalCountOf(passId)).isEqualTo(12);
        assertThat(remainingCountOf(passId)).isEqualTo(6);
    }

    @Test
    void 총_횟수를_줄이면_잔여_횟수는_0_미만이_되지_않는다() throws Exception {
        long passId = insertPass(productId, memberId, instructorId, 1, 10, 1, days(0), days(29), "ACTIVE");

        updateProduct("{\"totalCount\":5,\"applyToIssued\":true}").andExpect(status().isOk());

        assertThat(totalCountOf(passId)).isEqualTo(5);
        assertThat(remainingCountOf(passId)).isZero();
    }

    @Test
    void 총_횟수는_1_미만이_되지_않고_잔여_횟수는_총_횟수를_넘지_않는다() throws Exception {
        // 상품이 10회일 때 3회로 발급된 이용권(예: 이전 상품 값)에 10 → 2 변경이 반영되는 경우
        long passId = insertPass(productId, memberId, instructorId, 1, 3, 3, days(0), days(29), "ACTIVE");

        updateProduct("{\"totalCount\":2,\"applyToIssued\":true}").andExpect(status().isOk());

        assertThat(totalCountOf(passId)).isEqualTo(1);
        assertThat(remainingCountOf(passId)).isLessThanOrEqualTo(1);
    }

    @Test
    void 유효_기간을_반영하면_만료일이_시작일_더하기_유효기간_빼기_1일로_다시_계산된다() throws Exception {
        long passId = insertPass(productId, memberId, instructorId, 1, 10, 10, days(-5), days(100), "ACTIVE");

        updateProduct("{\"validityDays\":60,\"applyToIssued\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedPassCount").value(1));

        assertThat(endDateOf(passId)).isEqualTo(days(-5).plusDays(59));
        assertThat(passStatus(passId)).isEqualTo("ACTIVE");
    }

    @Test
    void 새_만료일이_과거이면_이용권은_EXPIRED가_된다() throws Exception {
        long passId = insertPass(productId, memberId, instructorId, 1, 10, 10, days(-20), days(9), "ACTIVE");

        updateProduct("{\"validityDays\":10,\"applyToIssued\":true}").andExpect(status().isOk());

        assertThat(endDateOf(passId)).isEqualTo(days(-11));
        assertThat(passStatus(passId)).isEqualTo("EXPIRED");
    }

    @Test
    void 만료된_이용권과_다른_상품의_이용권은_반영되지_않는다() throws Exception {
        long other = insertProduct("요가", 1, 5, 10);
        long expired = insertPass(productId, memberId, instructorId, 1, 10, 10, days(-40), days(-10), "EXPIRED");
        long otherPass = insertPass(other, memberId, instructorId, 1, 5, 5, days(0), days(9), "ACTIVE");
        long upcoming = insertPass(productId, memberId, instructorId, 1, 10, 10, days(3), days(32), "UPCOMING");
        long paused = insertPass(productId, memberId, instructorId, 1, 10, 10, days(-2), days(27), "PAUSED");

        updateProduct("{\"totalCount\":15,\"applyToIssued\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedPassCount").value(2 + 0));

        assertThat(totalCountOf(expired)).isEqualTo(10);
        assertThat(totalCountOf(otherPass)).isEqualTo(5);
        assertThat(totalCountOf(upcoming)).isEqualTo(15);
        assertThat(totalCountOf(paused)).isEqualTo(15);
    }

    @Test
    void 이름_가격_설명만_바꾸면_applyToIssued여도_영향받은_이용권은_0이다() throws Exception {
        long passId = insertActivePass(productId, memberId, instructorId);

        updateProduct("{\"name\":\"새 이름\",\"price\":1,\"applyToIssued\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedPassCount").value(0));

        assertThat(totalCountOf(passId)).isEqualTo(10);
    }

    @Test
    void 상품_이름은_발급된_이용권_응답에_바로_반영된다() throws Exception {
        long passId = insertActivePass(productId, memberId, instructorId);

        updateProduct("{\"name\":\"새 이름\"}").andExpect(status().isOk());

        detail(token, passId).andExpect(jsonPath("$.passProduct.name").value("새 이름"));
    }
}
