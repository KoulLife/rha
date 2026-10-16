package com.rha.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rha.ApiTestSupport;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 활성 상태가 아닌 회원이 남은 Access Token으로 본인 정보를 바꾸거나 탈퇴하지 못한다. */
class InactiveMemberTokenTest extends ApiTestSupport {

    private String tokenOfMemberWithStatus(String status) throws Exception {
        long id = insertMember("x@example.com", "엑스", "MEMBER", "ACTIVE", Instant.parse("2026-10-01T00:00:00Z"));
        String token = login("x@example.com");
        jdbc.update("UPDATE members SET status = ? WHERE id = ?", status, id);
        return token;
    }

    private ResultActions send(MockHttpServletRequestBuilder builder, String token, String json) throws Exception {
        return mockMvc.perform(builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @ParameterizedTest
    @ValueSource(strings = {"INACTIVE", "WITHDRAWN"})
    void 활성이_아닌_회원의_본인_정보_수정은_401이다(String status) throws Exception {
        String token = tokenOfMemberWithStatus(status);

        send(patch("/members/me"), token, "{\"name\":\"새이름\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertThat(jdbc.queryForObject("SELECT name FROM members WHERE email = 'x@example.com'", String.class))
                .isEqualTo("엑스");
    }

    @ParameterizedTest
    @ValueSource(strings = {"INACTIVE", "WITHDRAWN"})
    void 활성이_아닌_회원의_비밀번호_변경은_401이다(String status) throws Exception {
        String token = tokenOfMemberWithStatus(status);

        send(put("/members/me/password"), token,
                "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"newpass456!\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"INACTIVE", "WITHDRAWN"})
    void 활성이_아닌_회원의_탈퇴_요청은_401이고_상태가_바뀌지_않는다(String status) throws Exception {
        String token = tokenOfMemberWithStatus(status);

        send(delete("/members/me"), token, "")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertThat(jdbc.queryForObject("SELECT status FROM members WHERE email = 'x@example.com'", String.class))
                .isEqualTo(status);
    }

    @ParameterizedTest
    @ValueSource(strings = {"INACTIVE", "WITHDRAWN"})
    void 활성이_아닌_회원도_본인_정보_조회는_가능하다(String status) throws Exception {
        String token = tokenOfMemberWithStatus(status);

        mockMvc.perform(get("/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(status));
    }
}
