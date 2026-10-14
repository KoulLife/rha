package com.rha.businesshour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rha.ApiTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class BusinessHourTest extends ApiTestSupport {

    private static final List<String> DAYS = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

    @Test
    void 설정한_적이_없으면_모든_요일이_휴무로_월요일부터_일요일_순으로_반환된다() throws Exception {
        String token = memberToken();

        ResultActions result = readHours(token).andExpect(status().isOk())
                .andExpect(jsonPath("$.businessHours.length()").value(7));
        for (int i = 0; i < 7; i++) {
            result.andExpect(jsonPath("$.businessHours[" + i + "].dayOfWeek").value(DAYS.get(i)))
                    .andExpect(jsonPath("$.businessHours[" + i + "].closed").value(true))
                    .andExpect(jsonPath("$.businessHours[" + i + "].openTime").value((Object) null))
                    .andExpect(jsonPath("$.businessHours[" + i + "].closeTime").value((Object) null));
        }
    }

    @Test
    void 강사도_조회할_수_있고_로그인하지_않으면_401이다() throws Exception {
        String token = instructorToken();

        readHours(token).andExpect(status().isOk());
        mockMvc.perform(get("/business-hours")).andExpect(status().isUnauthorized());
    }

    @Test
    void 관리자는_영업시간을_설정할_수_있고_응답과_조회가_월요일부터_일요일_순이다() throws Exception {
        String token = adminToken();

        // 요일 순서를 섞어서 보내도 응답은 월~일 순이다.
        String body = body(open("SUN", "10:00", "18:00"), closed("SAT"), open("FRI", "09:00", "22:00"),
                open("THU", "09:00", "22:00"), open("WED", "09:00", "22:00"), open("TUE", "09:00", "22:00"),
                open("MON", "09:00", "22:00"));

        writeHours(token, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessHours.length()").value(7))
                .andExpect(jsonPath("$.businessHours[0].dayOfWeek").value("MON"))
                .andExpect(jsonPath("$.businessHours[0].closed").value(false))
                .andExpect(jsonPath("$.businessHours[0].openTime").value("09:00"))
                .andExpect(jsonPath("$.businessHours[0].closeTime").value("22:00"))
                .andExpect(jsonPath("$.businessHours[5].dayOfWeek").value("SAT"))
                .andExpect(jsonPath("$.businessHours[5].closed").value(true))
                .andExpect(jsonPath("$.businessHours[5].openTime").value((Object) null))
                .andExpect(jsonPath("$.businessHours[6].dayOfWeek").value("SUN"))
                .andExpect(jsonPath("$.businessHours[6].openTime").value("10:00"));

        readHours(token)
                .andExpect(jsonPath("$.businessHours[0].openTime").value("09:00"))
                .andExpect(jsonPath("$.businessHours[5].closed").value(true))
                .andExpect(jsonPath("$.businessHours[6].closeTime").value("18:00"));
    }

    @Test
    void 다시_설정하면_전체가_교체된다() throws Exception {
        String token = adminToken();
        writeHours(token, allOpen("09:00", "22:00")).andExpect(status().isOk());

        writeHours(token, allClosed()).andExpect(status().isOk());

        readHours(token)
                .andExpect(jsonPath("$.businessHours[0].closed").value(true))
                .andExpect(jsonPath("$.businessHours[0].openTime").value((Object) null));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_hours", Integer.class)).isEqualTo(7);
    }

    @Test
    void 요일이_누락되면_400_INVALID_BUSINESS_HOURS이다() throws Exception {
        String token = adminToken();

        writeHours(token, body(open("MON", "09:00", "22:00"), closed("TUE"), closed("WED"), closed("THU"),
                closed("FRI"), closed("SAT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BUSINESS_HOURS"));
    }

    @Test
    void 요일이_중복되면_400_INVALID_BUSINESS_HOURS이다() throws Exception {
        String token = adminToken();

        writeHours(token, body(open("MON", "09:00", "22:00"), closed("MON"), closed("WED"), closed("THU"),
                closed("FRI"), closed("SAT"), closed("SUN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BUSINESS_HOURS"));
    }

    @Test
    void 요일이_7개보다_많거나_비어_있으면_400_INVALID_BUSINESS_HOURS이다() throws Exception {
        String token = adminToken();

        writeHours(token, body(closed("MON"), closed("TUE"), closed("WED"), closed("THU"), closed("FRI"), closed("SAT"),
                closed("SUN"), closed("MON")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BUSINESS_HOURS"));
        writeHours(token, "{\"businessHours\":[]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BUSINESS_HOURS"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"22:00", "09:00"})
    void 오픈_시각이_마감_시각보다_빠르지_않으면_400이다(String closeTime) throws Exception {
        String token = adminToken();

        writeHours(token, body(open("MON", "22:00", closeTime), closed("TUE"), closed("WED"), closed("THU"),
                closed("FRI"), closed("SAT"), closed("SUN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BUSINESS_HOURS"));
    }

    @Test
    void 휴무_요일에_시각을_보내면_400_INVALID_BUSINESS_HOURS이다() throws Exception {
        String token = adminToken();

        writeHours(token, body(day("MON", true, "09:00", "22:00"), closed("TUE"), closed("WED"), closed("THU"),
                closed("FRI"), closed("SAT"), closed("SUN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BUSINESS_HOURS"));
    }

    @Test
    void 영업일에_시각이_없으면_400_INVALID_BUSINESS_HOURS이다() throws Exception {
        String token = adminToken();

        writeHours(token, body(day("MON", false, null, null), closed("TUE"), closed("WED"), closed("THU"),
                closed("FRI"), closed("SAT"), closed("SUN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BUSINESS_HOURS"));
        writeHours(token, body(day("MON", false, "09:00", null), closed("TUE"), closed("WED"), closed("THU"),
                closed("FRI"), closed("SAT"), closed("SUN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BUSINESS_HOURS"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"businessHours\":null}",
            "{\"businessHours\":\"MON\"}",
            "{\"businessHours\":[{\"dayOfWeek\":\"XXX\",\"closed\":true}]}",
            "{\"businessHours\":[{\"closed\":true}]}",
            "{\"businessHours\":[{\"dayOfWeek\":\"MON\"}]}",
            "{\"businessHours\":[{\"dayOfWeek\":\"MON\",\"closed\":false,\"openTime\":\"9:00\",\"closeTime\":\"22:00\"}]}",
            "{\"businessHours\":[{\"dayOfWeek\":\"MON\",\"closed\":false,\"openTime\":\"25:00\",\"closeTime\":\"26:00\"}]}",
            "not json"
    })
    void 형식이_잘못된_요청은_400_INVALID_INPUT이다(String body) throws Exception {
        String token = adminToken();

        writeHours(token, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 관리자가_아니면_403이고_잘못된_본문이어도_403이다() throws Exception {
        String token = instructorToken();

        writeHours(token, allOpen("09:00", "22:00"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        writeHours(token, "{}").andExpect(status().isForbidden());
    }

    @Test
    void 로그인하지_않으면_설정은_401이다() throws Exception {
        mockMvc.perform(put("/business-hours").contentType(MediaType.APPLICATION_JSON).content(allClosed()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 거절된_요청은_기존_설정을_바꾸지_않는다() throws Exception {
        String token = adminToken();
        writeHours(token, allOpen("09:00", "22:00")).andExpect(status().isOk());

        writeHours(token, body(open("MON", "22:00", "09:00"), closed("TUE"), closed("WED"), closed("THU"),
                closed("FRI"), closed("SAT"), closed("SUN"))).andExpect(status().isBadRequest());

        readHours(token).andExpect(jsonPath("$.businessHours[0].openTime").value("09:00"));
        Map<String, Object> monday = jdbc.queryForMap("SELECT closed FROM business_hours WHERE day_of_week = 'MON'");
        assertThat(monday.get("closed")).isEqualTo(false);
    }

    private ResultActions readHours(String token) throws Exception {
        return mockMvc.perform(get("/business-hours").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions writeHours(String token, String body) throws Exception {
        return mockMvc.perform(put("/business-hours").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String day(String dayOfWeek, boolean closed, String openTime, String closeTime) {
        return "{\"dayOfWeek\":\"" + dayOfWeek + "\",\"closed\":" + closed
                + (openTime == null ? "" : ",\"openTime\":\"" + openTime + "\"")
                + (closeTime == null ? "" : ",\"closeTime\":\"" + closeTime + "\"") + "}";
    }

    private static String open(String dayOfWeek, String openTime, String closeTime) {
        return day(dayOfWeek, false, openTime, closeTime);
    }

    private static String closed(String dayOfWeek) {
        return day(dayOfWeek, true, null, null);
    }

    private static String body(String... days) {
        return "{\"businessHours\":[" + String.join(",", days) + "]}";
    }

    private static String allOpen(String openTime, String closeTime) {
        return body(DAYS.stream().map(d -> open(d, openTime, closeTime)).toArray(String[]::new));
    }

    private static String allClosed() {
        return body(DAYS.stream().map(BusinessHourTest::closed).toArray(String[]::new));
    }
}
