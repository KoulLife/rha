package com.rha.reservationpolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rha.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class ReservationPolicyTest extends ApiTestSupport {

    private ResultActions read(String token) throws Exception {
        return mockMvc.perform(get("/reservation-policy").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions replace(String token, String json) throws Exception {
        return mockMvc.perform(put("/reservation-policy").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private int storedDeadline() {
        return jdbc.queryForObject("SELECT cancel_deadline_hours FROM reservation_policies", Integer.class);
    }

    @Test
    void 설정한_적이_없으면_취소_마감_24시간_미수_예약_허용이다() throws Exception {
        for (String token : new String[] {adminToken(), instructorToken(), memberToken()}) {
            read(token)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cancelDeadlineHours").value(24))
                    .andExpect(jsonPath("$.allowUnpaidReservation").value(true));
        }
    }

    @Test
    void 관리자가_설정하면_저장되고_모든_사용자가_바뀐_값을_본다() throws Exception {
        String admin = adminToken();

        replace(admin, "{\"cancelDeadlineHours\":6,\"allowUnpaidReservation\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelDeadlineHours").value(6))
                .andExpect(jsonPath("$.allowUnpaidReservation").value(false));

        assertThat(storedDeadline()).isEqualTo(6);
        read(memberToken())
                .andExpect(jsonPath("$.cancelDeadlineHours").value(6))
                .andExpect(jsonPath("$.allowUnpaidReservation").value(false));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation_policies", Integer.class)).isEqualTo(1);
    }

    @Test
    void 경계값_0과_720을_허용하고_같은_값으로_다시_설정해도_된다() throws Exception {
        String admin = adminToken();

        replace(admin, "{\"cancelDeadlineHours\":0,\"allowUnpaidReservation\":true}").andExpect(status().isOk());
        replace(admin, "{\"cancelDeadlineHours\":720,\"allowUnpaidReservation\":true}")
                .andExpect(status().isOk());
        replace(admin, "{\"cancelDeadlineHours\":720,\"allowUnpaidReservation\":true}")
                .andExpect(status().isOk());
        assertThat(storedDeadline()).isEqualTo(720);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"cancelDeadlineHours\":24}",
            "{\"allowUnpaidReservation\":true}",
            "{\"cancelDeadlineHours\":-1,\"allowUnpaidReservation\":true}",
            "{\"cancelDeadlineHours\":721,\"allowUnpaidReservation\":true}",
            "{\"cancelDeadlineHours\":\"x\",\"allowUnpaidReservation\":true}",
            "{\"cancelDeadlineHours\":24,\"allowUnpaidReservation\":\"yes\"}",
            "{\"cancelDeadlineHours\":24,\"allowUnpaidReservation\":null}",
            "not json"
    })
    void 요청_값이_올바르지_않으면_400이고_기존_값은_유지된다(String json) throws Exception {
        String admin = adminToken();

        replace(admin, json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(storedDeadline()).isEqualTo(24);
    }

    @Test
    void 설정은_관리자만_할_수_있고_잘못된_본문이어도_403이다() throws Exception {
        for (String token : new String[] {instructorToken(), memberToken()}) {
            replace(token, "{\"cancelDeadlineHours\":6,\"allowUnpaidReservation\":false}")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
            replace(token, "{}").andExpect(status().isForbidden());
        }
        assertThat(storedDeadline()).isEqualTo(24);
    }

    @Test
    void 인증하지_않으면_401이다() throws Exception {
        mockMvc.perform(get("/reservation-policy")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/reservation-policy").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancelDeadlineHours\":6,\"allowUnpaidReservation\":false}"))
                .andExpect(status().isUnauthorized());
    }
}
