package com.rha.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rha.ApiTestSupport;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 마스킹한 값이 같아도 실제 값이 바뀌면 활동 이력에 남고, 퇴사일은 상태·역할 변경 규칙대로 처리된다. */
class MemberChangeRecordTest extends ApiTestSupport {

    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    private ResultActions send(MockHttpServletRequestBuilder builder, String token, String json) throws Exception {
        return mockMvc.perform(builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private LocalDate resignationDateOf(long memberId) {
        Date date = jdbc.queryForObject("SELECT resignation_date FROM members WHERE id = ?", Date.class, memberId);
        return date == null ? null : date.toLocalDate();
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Seoul"));
    }

    @Test
    void 내_정보에서_전화번호_가운데_자리만_바뀌어도_변경이_기록된다() throws Exception {
        String token = memberToken(); // 전화번호 01012345678
        send(patch("/members/me"), token, "{\"phoneNumber\":\"01099995678\"}").andExpect(status().isOk());

        String changes = jdbc.queryForObject(
                "SELECT changes::text FROM activity_logs WHERE action = 'MEMBER_PROFILE_UPDATED'", String.class);
        assertThat(changes).contains("phoneNumber").doesNotContain("01099995678");
    }

    @Test
    void 관리자가_전화번호_가운데_자리만_바꿔도_변경이_기록된다() throws Exception {
        String token = adminToken();
        long target = insertMember("m@example.com", "MEMBER");
        send(patch("/members/" + target), token, "{\"phoneNumber\":\"01099995678\"}").andExpect(status().isOk());

        String changes = jdbc.queryForObject(
                "SELECT changes::text FROM activity_logs WHERE action = 'MEMBER_UPDATED'", String.class);
        assertThat(changes).contains("phoneNumber").doesNotContain("01099995678");
    }

    @Test
    void 비활성_강사를_활성화해도_등록된_퇴사일은_지워지지_않는다() throws Exception {
        String token = adminToken();
        long instructor = insertMember("i@example.com", "김강사", "INSTRUCTOR", "INACTIVE", BASE);
        jdbc.update("UPDATE members SET resignation_date = ? WHERE id = ?", Date.valueOf(today().plusDays(1)),
                instructor);

        send(put("/members/" + instructor + "/status"), token, "{\"status\":\"ACTIVE\"}")
                .andExpect(status().isOk());

        assertThat(resignationDateOf(instructor)).isEqualTo(today().plusDays(1));
    }

    @Test
    void 퇴사한_강사를_활성화하면_퇴사일이_지워진다() throws Exception {
        String token = adminToken();
        long instructor = insertMember("i@example.com", "김강사", "INSTRUCTOR", "RESIGNED", BASE);
        jdbc.update("UPDATE members SET resignation_date = ?, resigned_at = now() WHERE id = ?",
                Date.valueOf(today()), instructor);

        send(put("/members/" + instructor + "/status"), token, "{\"status\":\"ACTIVE\"}")
                .andExpect(status().isOk());

        assertThat(resignationDateOf(instructor)).isNull();
    }

    @Test
    void 강사의_역할을_바꾸면_퇴사일이_지워진다() throws Exception {
        String token = adminToken();
        long instructor = insertMember("i@example.com", "INSTRUCTOR");
        jdbc.update("UPDATE members SET resignation_date = ? WHERE id = ?", Date.valueOf(today().plusDays(1)),
                instructor);

        send(patch("/members/" + instructor), token, "{\"role\":\"MEMBER\"}").andExpect(status().isOk());

        assertThat(resignationDateOf(instructor)).isNull();
    }
}
