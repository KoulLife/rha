package com.rha.payrate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rha.ApiTestSupport;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class PayRateTest extends ApiTestSupport {

    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    long instructorId;
    long groupType;
    long privateType;
    String token;

    @BeforeEach
    void prepare() throws Exception {
        instructorId = insertMember("i@example.com", "박강사", "INSTRUCTOR", "ACTIVE", BASE);
        groupType = insertType("GROUP", "단체", true);
        privateType = insertType("PRIVATE", "개인", true);
        token = adminToken();
    }

    private long insertType(String code, String name, boolean active) {
        return jdbc.queryForObject("""
                INSERT INTO lesson_types (code, name, capacity, active, created_at, updated_at)
                VALUES (?, ?, 3, ?, now(), now()) RETURNING id
                """, Long.class, code, name, active);
    }

    private void insertRate(long instructor, long typeId, long price) {
        jdbc.update("""
                INSERT INTO pay_rates (instructor_id, lesson_type_id, unit_price, updated_at)
                VALUES (?, ?, ?, now())
                """, instructor, typeId, price);
    }

    private int rateCount(long instructor) {
        return jdbc.queryForObject("SELECT count(*) FROM pay_rates WHERE instructor_id = ?", Integer.class,
                instructor);
    }

    private ResultActions read(String who, long memberId) throws Exception {
        return mockMvc.perform(get("/members/" + memberId + "/pay-rates")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + who));
    }

    private ResultActions replace(String who, long memberId, String json) throws Exception {
        return mockMvc.perform(put("/members/" + memberId + "/pay-rates")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + who)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String rates(String... entries) {
        return "{\"payRates\":[" + String.join(",", entries) + "]}";
    }

    private static String rate(long typeId, long price) {
        return "{\"lessonTypeId\":" + typeId + ",\"unitPrice\":" + price + "}";
    }

    // ===== getPayRates =====

    @Test
    void 설정되지_않았으면_빈_목록이다() throws Exception {
        read(token, instructorId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instructor.id").value(instructorId))
                .andExpect(jsonPath("$.instructor.name").value("박강사"))
                .andExpect(jsonPath("$.payRates.length()").value(0));
    }

    @Test
    void 단가가_설정된_유형만_유형_id_오름차순으로_반환한다() throws Exception {
        long inactive = insertType("OLD", "옛 유형", false);
        insertType("UNSET", "미설정", true);
        insertRate(instructorId, inactive, 10000);
        insertRate(instructorId, privateType, 50000);
        insertRate(instructorId, groupType, 30000);

        read(token, instructorId)
                .andExpect(jsonPath("$.payRates.length()").value(3))
                .andExpect(jsonPath("$.payRates[0].lessonType.id").value(groupType))
                .andExpect(jsonPath("$.payRates[0].lessonType.code").value("GROUP"))
                .andExpect(jsonPath("$.payRates[0].lessonType.name").value("단체"))
                .andExpect(jsonPath("$.payRates[0].unitPrice").value(30000))
                .andExpect(jsonPath("$.payRates[1].lessonType.id").value(privateType))
                .andExpect(jsonPath("$.payRates[2].lessonType.id").value(inactive));
    }

    @Test
    void 다른_강사의_단가는_섞이지_않고_퇴사한_강사도_조회된다() throws Exception {
        long resigned = insertMember("r@example.com", "퇴사", "INSTRUCTOR", "RESIGNED", BASE);
        insertRate(instructorId, groupType, 30000);
        insertRate(resigned, groupType, 20000);

        read(token, resigned)
                .andExpect(jsonPath("$.payRates.length()").value(1))
                .andExpect(jsonPath("$.payRates[0].unitPrice").value(20000));
    }

    @Test
    void 대상이_강사가_아니거나_없으면_404이다() throws Exception {
        long member = insertMember("m@example.com", "회원", "MEMBER", "ACTIVE", BASE);

        read(token, member).andExpect(status().isNotFound());
        read(token, 999999).andExpect(status().isNotFound());
    }

    @Test
    void 조회는_관리자만_할_수_있다() throws Exception {
        read(instructorToken(), instructorId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        read(memberToken(), instructorId).andExpect(status().isForbidden());
        mockMvc.perform(get("/members/" + instructorId + "/pay-rates")).andExpect(status().isUnauthorized());
    }

    // ===== updatePayRates =====

    @Test
    void 단가를_설정하면_저장되고_조회된다() throws Exception {
        replace(token, instructorId, rates(rate(privateType, 50000), rate(groupType, 30000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instructor.id").value(instructorId))
                .andExpect(jsonPath("$.payRates.length()").value(2))
                .andExpect(jsonPath("$.payRates[0].lessonType.id").value(groupType))
                .andExpect(jsonPath("$.payRates[0].unitPrice").value(30000))
                .andExpect(jsonPath("$.payRates[1].unitPrice").value(50000));

        assertThat(rateCount(instructorId)).isEqualTo(2);
        read(token, instructorId).andExpect(jsonPath("$.payRates.length()").value(2));
    }

    @Test
    void 목록으로_통째로_교체하고_빠진_유형의_단가는_삭제된다() throws Exception {
        insertRate(instructorId, groupType, 30000);
        insertRate(instructorId, privateType, 50000);

        replace(token, instructorId, rates(rate(privateType, 60000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payRates.length()").value(1))
                .andExpect(jsonPath("$.payRates[0].lessonType.id").value(privateType))
                .andExpect(jsonPath("$.payRates[0].unitPrice").value(60000));
        assertThat(rateCount(instructorId)).isEqualTo(1);
    }

    @Test
    void 빈_목록이면_모든_단가를_삭제한다() throws Exception {
        insertRate(instructorId, groupType, 30000);

        replace(token, instructorId, rates())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payRates.length()").value(0));
        assertThat(rateCount(instructorId)).isZero();
    }

    @Test
    void 단가_0과_비활성_유형도_설정할_수_있다() throws Exception {
        long inactive = insertType("OLD", "옛 유형", false);

        replace(token, instructorId, rates(rate(groupType, 0), rate(inactive, 10000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payRates.length()").value(2));
    }

    @Test
    void 퇴사한_강사의_단가도_설정할_수_있다() throws Exception {
        long resigned = insertMember("r@example.com", "퇴사", "INSTRUCTOR", "RESIGNED", BASE);

        replace(token, resigned, rates(rate(groupType, 30000))).andExpect(status().isOk());
    }

    @Test
    void 다른_강사의_단가는_바뀌지_않는다() throws Exception {
        long other = insertMember("o@example.com", "다른", "INSTRUCTOR", "ACTIVE", BASE);
        insertRate(other, groupType, 20000);

        replace(token, instructorId, rates()).andExpect(status().isOk());

        assertThat(rateCount(other)).isEqualTo(1);
    }

    @Test
    void 대상이_강사가_아니면_400_INVALID_INSTRUCTOR이다() throws Exception {
        long member = insertMember("m@example.com", "회원", "MEMBER", "ACTIVE", BASE);

        replace(token, member, rates(rate(groupType, 30000)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INSTRUCTOR"));
        assertThat(rateCount(member)).isZero();
    }

    @Test
    void 없는_회원이면_404이다() throws Exception {
        replace(token, 999999, rates()).andExpect(status().isNotFound());
    }

    @Test
    void 수업_유형이_중복되면_400_INVALID_PAY_RATE이고_기존_단가는_유지된다() throws Exception {
        insertRate(instructorId, groupType, 30000);

        replace(token, instructorId, rates(rate(groupType, 1), rate(groupType, 2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAY_RATE"));
        assertThat(jdbc.queryForObject("SELECT unit_price FROM pay_rates WHERE instructor_id = ?", Long.class,
                instructorId)).isEqualTo(30000L);
    }

    @Test
    void 존재하지_않는_수업_유형이면_400_INVALID_PAY_RATE이다() throws Exception {
        replace(token, instructorId, rates(rate(groupType, 1), rate(999999, 2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAY_RATE"));
        assertThat(rateCount(instructorId)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"payRates\":null}",
            "{\"payRates\":[{\"unitPrice\":1000}]}",
            "{\"payRates\":[{\"lessonTypeId\":1}]}",
            "{\"payRates\":[{\"lessonTypeId\":1,\"unitPrice\":-1}]}",
            "{\"payRates\":[{\"lessonTypeId\":\"x\",\"unitPrice\":1000}]}",
            "{\"payRates\":[null]}",
            "not json"
    })
    void 요청_형식이_올바르지_않으면_400_INVALID_INPUT이다(String json) throws Exception {
        replace(token, instructorId, json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 설정은_관리자만_할_수_있고_잘못된_본문이어도_403이다() throws Exception {
        replace(instructorToken(), instructorId, rates(rate(groupType, 1)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        replace(memberToken(), instructorId, "{}").andExpect(status().isForbidden());
        mockMvc.perform(put("/members/" + instructorId + "/pay-rates").contentType(MediaType.APPLICATION_JSON)
                        .content(rates()))
                .andExpect(status().isUnauthorized());
        assertThat(rateCount(instructorId)).isZero();
    }
}
