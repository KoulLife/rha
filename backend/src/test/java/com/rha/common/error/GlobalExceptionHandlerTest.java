package com.rha.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// 예외 처리만 검증한다. 인증은 MyProfileTest 등 통합 테스트에서 다룬다.
@WebMvcTest(ErrorTestController.class)
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void ApiException은_에러코드의_상태와_code_timestamp를_담은_problem_json으로_응답한다() throws Exception {
        mockMvc.perform(get("/test-errors/api-exception"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.detail").value("이미 가입된 이메일입니다."))
                .andExpect(jsonPath("$.instance").value("/test-errors/api-exception"))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void 요청_검증에_실패하면_INVALID_INPUT과_invalidParams를_응답한다() throws Exception {
        mockMvc.perform(post("/test-errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.invalidParams.length()").value(2))
                .andExpect(jsonPath("$.invalidParams[?(@.field == 'name')].reason").isNotEmpty())
                .andExpect(jsonPath("$.invalidParams[?(@.field == 'email')].reason").isNotEmpty());
    }

    @Test
    void 요청_본문을_읽을_수_없으면_INVALID_INPUT을_응답한다() throws Exception {
        mockMvc.perform(post("/test-errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.timestamp").isString());
    }
}
