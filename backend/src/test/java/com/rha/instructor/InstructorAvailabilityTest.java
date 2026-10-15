package com.rha.instructor;

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

class InstructorAvailabilityTest extends ApiTestSupport {

    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    long instructorId;

    @BeforeEach
    void prepare() {
        instructorId = insertMember("i@example.com", "김강사", "INSTRUCTOR", "ACTIVE", BASE);
    }

    private ResultActions read(String token, long memberId) throws Exception {
        return mockMvc.perform(get("/members/" + memberId + "/availability")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions replace(String token, long memberId, String json) throws Exception {
        return mockMvc.perform(put("/members/" + memberId + "/availability")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String slot(String day, String start, String end) {
        return "{\"dayOfWeek\":\"" + day + "\",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\"}";
    }

    private int slotCount(long memberId) {
        return jdbc.queryForObject("SELECT count(*) FROM instructor_availabilities WHERE instructor_id = ?",
                Integer.class, memberId);
    }

    // ===== 조회 =====

    @Test
    void 설정하지_않았으면_빈_목록이다() throws Exception {
        String token = adminToken();

        read(token, instructorId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instructor.id").value(instructorId))
                .andExpect(jsonPath("$.instructor.name").value("김강사"))
                .andExpect(jsonPath("$.slots.length()").value(0));
    }

    @Test
    void 관리자와_본인은_요일_시작_시각_순으로_조회한다() throws Exception {
        jdbc.update("""
                INSERT INTO instructor_availabilities (instructor_id, day_of_week, start_time, end_time) VALUES
                (?, 'WED', '14:00', '18:00'), (?, 'MON', '14:00', '18:00'), (?, 'MON', '09:00', '12:00')
                """, instructorId, instructorId, instructorId);
        String admin = adminToken();
        String self = login("i@example.com");

        for (String token : new String[] {admin, self}) {
            read(token, instructorId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.slots.length()").value(3))
                    .andExpect(jsonPath("$.slots[0].dayOfWeek").value("MON"))
                    .andExpect(jsonPath("$.slots[0].startTime").value("09:00"))
                    .andExpect(jsonPath("$.slots[0].endTime").value("12:00"))
                    .andExpect(jsonPath("$.slots[1].startTime").value("14:00"))
                    .andExpect(jsonPath("$.slots[2].dayOfWeek").value("WED"));
        }
    }

    @Test
    void 다른_강사와_회원은_404이다() throws Exception {
        insertMember("i2@example.com", "박강사", "INSTRUCTOR", "ACTIVE", BASE);
        insertMember("m@example.com", "회원", "MEMBER", "ACTIVE", BASE);

        read(login("i2@example.com"), instructorId).andExpect(status().isNotFound());
        read(login("m@example.com"), instructorId).andExpect(status().isNotFound());
    }

    @Test
    void 대상이_강사가_아니거나_없으면_404이다() throws Exception {
        long memberId = insertMember("m@example.com", "회원", "MEMBER", "ACTIVE", BASE);
        String token = adminToken();

        read(token, memberId).andExpect(status().isNotFound());
        read(token, 999999).andExpect(status().isNotFound());
    }

    @Test
    void 조회도_인증하지_않으면_401이다() throws Exception {
        mockMvc.perform(get("/members/" + instructorId + "/availability")).andExpect(status().isUnauthorized());
    }

    // ===== 설정 =====

    @Test
    void 관리자는_요일별_여러_시간대를_설정할_수_있다() throws Exception {
        String token = adminToken();

        replace(token, instructorId, "{\"slots\":[" + slot("MON", "14:00", "18:00") + ","
                + slot("MON", "09:00", "12:00") + "," + slot("FRI", "10:00", "20:00") + "]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instructor.id").value(instructorId))
                .andExpect(jsonPath("$.slots.length()").value(3))
                .andExpect(jsonPath("$.slots[0].dayOfWeek").value("MON"))
                .andExpect(jsonPath("$.slots[0].startTime").value("09:00"))
                .andExpect(jsonPath("$.slots[2].dayOfWeek").value("FRI"));

        assertThat(slotCount(instructorId)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT min(start_time)::text FROM instructor_availabilities", String.class))
                .startsWith("09:00");
    }

    @Test
    void 설정하면_기존_시간대를_통째로_교체한다() throws Exception {
        String token = adminToken();
        replace(token, instructorId, "{\"slots\":[" + slot("MON", "09:00", "12:00") + ","
                + slot("TUE", "09:00", "12:00") + "]}").andExpect(status().isOk());

        replace(token, instructorId, "{\"slots\":[" + slot("SAT", "10:00", "11:00") + "]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots.length()").value(1))
                .andExpect(jsonPath("$.slots[0].dayOfWeek").value("SAT"));
        assertThat(slotCount(instructorId)).isEqualTo(1);
    }

    @Test
    void 빈_목록이면_모든_요일이_불가이다() throws Exception {
        String token = adminToken();
        replace(token, instructorId, "{\"slots\":[" + slot("MON", "09:00", "12:00") + "]}")
                .andExpect(status().isOk());

        replace(token, instructorId, "{\"slots\":[]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots.length()").value(0));
        assertThat(slotCount(instructorId)).isZero();
    }

    @Test
    void 다른_강사의_가능시간은_바뀌지_않는다() throws Exception {
        long other = insertMember("i2@example.com", "박강사", "INSTRUCTOR", "ACTIVE", BASE);
        jdbc.update("""
                INSERT INTO instructor_availabilities (instructor_id, day_of_week, start_time, end_time)
                VALUES (?, 'MON', '09:00', '12:00')
                """, other);
        String token = adminToken();

        replace(token, instructorId, "{\"slots\":[]}").andExpect(status().isOk());

        assertThat(slotCount(other)).isEqualTo(1);
    }

    @Test
    void 이어지는_시간대는_겹치지_않는다() throws Exception {
        String token = adminToken();

        replace(token, instructorId, "{\"slots\":[" + slot("MON", "09:00", "12:00") + ","
                + slot("MON", "12:00", "15:00") + "]}").andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "10:00,10:00", "12:00,10:00"
    })
    void 시작이_종료보다_늦거나_같으면_400_INVALID_AVAILABILITY이다(String range) throws Exception {
        String[] parts = range.split(",");
        String token = adminToken();

        replace(token, instructorId, "{\"slots\":[" + slot("MON", parts[0], parts[1]) + "]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AVAILABILITY"));
    }

    @Test
    void 같은_요일에서_겹치면_400_INVALID_AVAILABILITY이고_기존_설정은_유지된다() throws Exception {
        String token = adminToken();
        replace(token, instructorId, "{\"slots\":[" + slot("TUE", "09:00", "12:00") + "]}")
                .andExpect(status().isOk());

        replace(token, instructorId, "{\"slots\":[" + slot("MON", "09:00", "12:00") + ","
                + slot("MON", "11:00", "14:00") + "]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AVAILABILITY"));

        assertThat(jdbc.queryForObject("SELECT day_of_week FROM instructor_availabilities WHERE instructor_id = ?",
                String.class, instructorId)).isEqualTo("TUE");
    }

    @Test
    void 다른_요일의_같은_시간대는_겹침이_아니다() throws Exception {
        String token = adminToken();

        replace(token, instructorId, "{\"slots\":[" + slot("MON", "09:00", "12:00") + ","
                + slot("TUE", "09:00", "12:00") + "]}").andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"slots\":null}",
            "{\"slots\":[{\"dayOfWeek\":\"MON\",\"startTime\":\"9:00\",\"endTime\":\"12:00\"}]}",
            "{\"slots\":[{\"dayOfWeek\":\"MON\",\"startTime\":\"09:00\",\"endTime\":\"24:00\"}]}",
            "{\"slots\":[{\"dayOfWeek\":\"MON\",\"startTime\":\"09:60\",\"endTime\":\"12:00\"}]}",
            "{\"slots\":[{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"09:00\",\"endTime\":\"12:00\"}]}",
            "{\"slots\":[{\"dayOfWeek\":\"MON\",\"endTime\":\"12:00\"}]}",
            "{\"slots\":[null]}",
            "not json"
    })
    void 요청_형식이_올바르지_않으면_400_INVALID_INPUT이다(String json) throws Exception {
        String token = adminToken();

        replace(token, instructorId, json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 시간대가_70개를_넘으면_400이다() throws Exception {
        String token = adminToken();
        StringBuilder slots = new StringBuilder();
        for (int i = 0; i < 71; i++) {
            if (i > 0) {
                slots.append(',');
            }
            slots.append(slot("MON", "09:00", "10:00"));
        }

        replace(token, instructorId, "{\"slots\":[" + slots + "]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 대상이_강사가_아니면_404이다() throws Exception {
        long memberId = insertMember("m@example.com", "회원", "MEMBER", "ACTIVE", BASE);
        String token = adminToken();

        replace(token, memberId, "{\"slots\":[]}").andExpect(status().isNotFound());
        replace(token, 999999, "{\"slots\":[]}").andExpect(status().isNotFound());
    }

    @Test
    void 값_오류는_없는_회원보다_먼저_400이다() throws Exception {
        String token = adminToken();

        replace(token, 999999, "{\"slots\":[" + slot("MON", "12:00", "10:00") + "]}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void 관리자가_아니면_본인이어도_403이고_잘못된_본문이어도_403이다() throws Exception {
        String self = login("i@example.com");

        replace(self, instructorId, "{\"slots\":[]}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        replace(self, instructorId, "{}").andExpect(status().isForbidden());
    }

    @Test
    void 설정도_인증하지_않으면_401이다() throws Exception {
        mockMvc.perform(put("/members/" + instructorId + "/availability")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"slots\":[]}"))
                .andExpect(status().isUnauthorized());
    }
}
