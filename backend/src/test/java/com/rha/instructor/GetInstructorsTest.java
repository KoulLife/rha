package com.rha.instructor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rha.ApiTestSupport;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

class GetInstructorsTest extends ApiTestSupport {

    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    private ResultActions list(String token, String... keyValues) throws Exception {
        var request = get("/instructors").header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        for (int i = 0; i < keyValues.length; i += 2) {
            request.param(keyValues[i], keyValues[i + 1]);
        }
        return mockMvc.perform(request);
    }

    @Test
    void 활성_강사만_이름_오름차순으로_id와_이름만_반환한다() throws Exception {
        insertMember("c@example.com", "최강사", "INSTRUCTOR", "ACTIVE", BASE);
        long a = insertMember("a@example.com", "김강사", "INSTRUCTOR", "ACTIVE", BASE);
        insertMember("b@example.com", "비활성", "INSTRUCTOR", "INACTIVE", BASE);
        insertMember("r@example.com", "퇴사", "INSTRUCTOR", "RESIGNED", BASE);
        insertMember("m@example.com", "가회원", "MEMBER", "ACTIVE", BASE);
        String token = adminToken();

        list(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(a))
                .andExpect(jsonPath("$.content[0].name").value("김강사"))
                .andExpect(jsonPath("$.content[0].email").doesNotExist())
                .andExpect(jsonPath("$.content[0].phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.content[1].name").value("최강사"))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(2));
    }

    @Test
    void 이름이_같으면_id_오름차순이다() throws Exception {
        long first = insertMember("a@example.com", "김강사", "INSTRUCTOR", "ACTIVE", BASE);
        long second = insertMember("b@example.com", "김강사", "INSTRUCTOR", "ACTIVE", BASE);
        String token = adminToken();

        list(token)
                .andExpect(jsonPath("$.content[0].id").value(first))
                .andExpect(jsonPath("$.content[1].id").value(second));
    }

    @Test
    void 회원과_강사_모두_조회할_수_있다() throws Exception {
        insertMember("a@example.com", "김강사", "INSTRUCTOR", "ACTIVE", BASE);

        list(memberToken()).andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void 페이지네이션이_동작한다() throws Exception {
        for (int i = 0; i < 3; i++) {
            insertMember("i" + i + "@example.com", "강사" + i, "INSTRUCTOR", "ACTIVE", BASE);
        }
        String token = adminToken();

        list(token, "page", "2", "size", "2")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("강사2"))
                .andExpect(jsonPath("$.pageInfo.page").value(2))
                .andExpect(jsonPath("$.pageInfo.totalPages").value(2));
    }

    @ParameterizedTest
    @CsvSource({"page,0", "size,0", "size,101", "page,abc"})
    void 잘못된_파라미터면_400이다(String key, String value) throws Exception {
        String token = adminToken();

        list(token, key, value)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 인증하지_않으면_401이다() throws Exception {
        mockMvc.perform(get("/instructors")).andExpect(status().isUnauthorized());
    }
}
