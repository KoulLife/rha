package com.rha.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rha.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

/** 권한 확인(403)과 요청 형식 오류(400)의 순서, 공통 설정. */
class ErrorOrderTest extends ApiTestSupport {

    @Autowired
    ApplicationContext context;

    @Test
    void 관리자가_아니면_회원_등록의_본문_값이_틀려도_403이다() throws Exception {
        String token = memberToken();

        mockMvc.perform(post("/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void 관리자가_회원_등록의_본문_값이_틀리면_400이다() throws Exception {
        String token = adminToken();

        mockMvc.perform(post("/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.invalidParams").isNotEmpty());
    }

    @Test
    void 관리자가_아니면_활동_이력의_page_형식이_틀려도_403이다() throws Exception {
        String token = memberToken();

        mockMvc.perform(get("/activity-logs").param("page", "abc").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void 관리자의_활동_이력_page_형식이_틀리면_400이다() throws Exception {
        String token = adminToken();

        mockMvc.perform(get("/activity-logs").param("page", "abc").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.invalidParams[0].field").value("page"));
    }

    @Test
    void 관리자의_활동_이력_size가_범위를_벗어나면_400이다() throws Exception {
        String token = adminToken();

        mockMvc.perform(get("/activity-logs").param("size", "0").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidParams[0].field").value("size"));
    }

    @Test
    void 실제_서버에서도_없는_주소는_code와_timestamp를_담는다() throws Exception {
        String token = adminToken();

        mockMvc.perform(get("/nope").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void 자동_공개를_꺼도_스케줄링은_켜져_있다() {
        assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).isNotEmpty();
    }
}
