package com.rha.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rha.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 스펙에 없는 값 제한이 걸리지 않는지, 정산 목록의 기간 오류가 invalidParams를 담는지. */
class RequestRuleTest extends ApiTestSupport {

    private ResultActions postJson(String path, String token, String json) throws Exception {
        return mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void 비밀번호가_길어도_로그인은_400이_아니라_401이다() throws Exception {
        insertMember("u@example.com", "MEMBER");

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"u@example.com\",\"password\":\"" + "a".repeat(100) + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void 룸_이름은_앞뒤_공백을_제거한_길이로_검사한다() throws Exception {
        String token = adminToken();
        String fifty = "가".repeat(50);

        postJson("/rooms", token, "{\"name\":\"  " + fifty + "  \",\"capacity\":5}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(fifty));
        postJson("/rooms", token, "{\"name\":\"" + fifty + "나\",\"capacity\":5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 수업_유형_이름은_앞뒤_공백을_제거한_길이로_검사한다() throws Exception {
        String token = adminToken();
        String fifty = "나".repeat(50);

        postJson("/lesson-types", token, "{\"code\":\"TYPE_A\",\"name\":\"  " + fifty + " \",\"capacity\":3}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(fifty));
        postJson("/lesson-types", token, "{\"code\":\"TYPE_B\",\"name\":\"   \",\"capacity\":3}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void 정산_목록의_기간이_거꾸로면_invalidParams에_periodTo를_담는다() throws Exception {
        String token = adminToken();

        mockMvc.perform(get("/settlements").param("periodFrom", "2026-10-10").param("periodTo", "2026-10-01")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.invalidParams[0].field").value("periodTo"));
    }
}
