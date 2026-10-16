package com.rha.pass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 상품 유효 기간 일괄 반영과 일시정지 해제가 만료일을 서로 어긋나게 하지 않는지. */
class PassPauseProductChangeTest extends PassTestSupport {

    long productId;
    long memberId;
    long instructorId;
    String token;

    @BeforeEach
    void prepare() throws Exception {
        productId = insertProduct();
        memberId = insertMemberNamed("mem@example.com", "김회원");
        instructorId = insertInstructor("i@example.com", "박강사");
        token = adminToken();
    }

    @Test
    void 유효_기간_반영은_정지로_연장된_일수를_더해_만료일을_계산한다() throws Exception {
        long passId = insertPass(productId, memberId, instructorId, 300000, 10, 10, days(-5), days(24), "ACTIVE");
        createPauseId(passId, days(3), days(5));
        assertThat(endDateOf(passId)).isEqualTo(days(27));

        applyValidity(60);

        // 시작일 + 60 - 1 + 정지 3일
        assertThat(endDateOf(passId)).isEqualTo(days(-5).plusDays(59 + 3));
    }

    @Test
    void 취소된_정지는_유효_기간_반영에_더하지_않는다() throws Exception {
        long passId = insertPass(productId, memberId, instructorId, 300000, 10, 10, days(-5), days(24), "ACTIVE");
        insertPause(passId, days(3), days(5), "CANCELED", adminId());

        applyValidity(60);

        assertThat(endDateOf(passId)).isEqualTo(days(-5).plusDays(59));
    }

    @Test
    void 유효_기간_반영_뒤_정지를_해제해도_오류없이_만료일이_시작일_아래로_내려가지_않는다() throws Exception {
        long passId = insertPass(productId, memberId, instructorId, 300000, 10, 10, days(-5), days(24), "ACTIVE");
        long pauseId = createPauseId(passId, days(3), days(5));

        applyValidity(1);
        // 시작일 + 1 - 1 + 정지 3일
        assertThat(endDateOf(passId)).isEqualTo(days(-5).plusDays(3));

        terminate(token, passId, pauseId).andExpect(status().isOk());

        assertThat(endDateOf(passId)).isEqualTo(days(-5));
        assertThat(pauseStatus(pauseId)).isEqualTo("CANCELED");
    }

    @Test
    void 정지를_해제해도_만료일은_시작일보다_앞서지_않는다() throws Exception {
        // 관리자가 만료일을 시작일로 줄여 둔 이용권에 정지(5일)가 남아 있는 경우
        long passId = insertPass(productId, memberId, instructorId, 300000, 10, 10, days(5), days(5), "UPCOMING");
        long pauseId = insertPause(passId, days(6), days(10), "SCHEDULED", adminId());

        terminate(token, passId, pauseId).andExpect(status().isOk());

        assertThat(endDateOf(passId)).isEqualTo(days(5));
        assertThat(pauseStatus(pauseId)).isEqualTo("CANCELED");
    }

    private void applyValidity(int validityDays) throws Exception {
        mockMvc.perform(patch("/pass-products/" + productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"validityDays\":" + validityDays + ",\"applyToIssued\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedPassCount").value(1));
    }

    private long createPauseId(long passId, LocalDate start, LocalDate end) throws Exception {
        String body = createPause(token, passId, "{\"startDate\":\"" + start + "\",\"endDate\":\"" + end + "\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
