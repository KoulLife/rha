package com.rha.instructor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rha.ApiTestSupport;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class ResignationTest extends ApiTestSupport {

    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    long instructorId;

    @BeforeEach
    void prepare() {
        instructorId = insertMember("i@example.com", "김강사", "INSTRUCTOR", "ACTIVE", BASE);
    }

    private static LocalDate today() {
        return LocalDate.now(SEOUL);
    }

    private ResultActions register(String token, long memberId, String json) throws Exception {
        return mockMvc.perform(put("/members/" + memberId + "/resignation")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions cancel(String token, long memberId) throws Exception {
        return mockMvc.perform(delete("/members/" + memberId + "/resignation")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions complete(String token, long memberId) throws Exception {
        return mockMvc.perform(post("/members/" + memberId + "/resignation/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static String date(LocalDate date) {
        return "{\"resignationDate\":\"" + date + "\"}";
    }

    private void setResignationDate(long memberId, LocalDate date) {
        jdbc.update("UPDATE members SET resignation_date = ? WHERE id = ?", Date.valueOf(date), memberId);
    }

    private Date resignationDateOf(long memberId) {
        return jdbc.queryForObject("SELECT resignation_date FROM members WHERE id = ?", Date.class, memberId);
    }

    // ===== registerResignation =====

    @Test
    void 퇴사일을_등록하면_회원에_반영되고_상태는_그대로다() throws Exception {
        String token = adminToken();
        LocalDate target = today().plusDays(30);

        register(token, instructorId, date(target))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(instructorId))
                .andExpect(jsonPath("$.resignationDate").value(target.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(resignationDateOf(instructorId).toLocalDate()).isEqualTo(target);
    }

    @Test
    void 오늘로도_등록할_수_있고_다시_등록하면_바뀐다() throws Exception {
        String token = adminToken();

        register(token, instructorId, date(today())).andExpect(status().isOk());
        register(token, instructorId, date(today().plusDays(7)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resignationDate").value(today().plusDays(7).toString()));
    }

    @Test
    void 퇴사일이_과거이면_400_INVALID_RESIGNATION_DATE이다() throws Exception {
        String token = adminToken();

        register(token, instructorId, date(today().minusDays(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESIGNATION_DATE"));
        assertThat(resignationDateOf(instructorId)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"resignationDate\":null}", "{\"resignationDate\":\"2026-13-01\"}",
            "{\"resignationDate\":\"내일\"}", "not json"})
    void 요청_형식이_올바르지_않으면_400_INVALID_INPUT이다(String json) throws Exception {
        String token = adminToken();

        register(token, instructorId, json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 강사가_아니거나_활성이_아니면_400_INVALID_INSTRUCTOR이다() throws Exception {
        String token = adminToken();
        long member = insertMember("m@example.com", "회원", "MEMBER", "ACTIVE", BASE);
        long inactive = insertMember("i2@example.com", "비활성", "INSTRUCTOR", "INACTIVE", BASE);

        for (long id : new long[] {member, inactive}) {
            register(token, id, date(today().plusDays(3)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INSTRUCTOR"));
        }
    }

    @Test
    void 이미_퇴사한_강사는_409_MEMBER_RESIGNED이다() throws Exception {
        String token = adminToken();
        long resigned = insertMember("r@example.com", "퇴사", "INSTRUCTOR", "RESIGNED", BASE);

        register(token, resigned, date(today().plusDays(3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_RESIGNED"));
    }

    @Test
    void 없는_회원이면_404이다() throws Exception {
        String token = adminToken();

        register(token, 999999, date(today().plusDays(3))).andExpect(status().isNotFound());
    }

    @Test
    void 퇴사일_등록은_관리자만_할_수_있다() throws Exception {
        String token = instructorToken();

        register(token, instructorId, date(today().plusDays(3)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        register(token, instructorId, "{}").andExpect(status().isForbidden());
    }

    @Test
    void 퇴사일_등록도_인증하지_않으면_401이다() throws Exception {
        mockMvc.perform(put("/members/" + instructorId + "/resignation")
                        .contentType(MediaType.APPLICATION_JSON).content(date(today())))
                .andExpect(status().isUnauthorized());
    }

    // ===== cancelResignation =====

    @Test
    void 퇴사일_등록을_취소하면_204이고_퇴사일이_지워진다() throws Exception {
        String token = adminToken();
        setResignationDate(instructorId, today().plusDays(10));

        cancel(token, instructorId).andExpect(status().isNoContent());

        assertThat(resignationDateOf(instructorId)).isNull();
    }

    @Test
    void 등록된_퇴사일이_없으면_409_RESIGNATION_NOT_REGISTERED이다() throws Exception {
        String token = adminToken();

        cancel(token, instructorId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESIGNATION_NOT_REGISTERED"));
    }

    @Test
    void 이미_퇴사한_강사의_퇴사일은_취소할_수_없다() throws Exception {
        String token = adminToken();
        long resigned = insertMember("r@example.com", "퇴사", "INSTRUCTOR", "RESIGNED", BASE);
        setResignationDate(resigned, today().minusDays(1));

        cancel(token, resigned)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_RESIGNED"));
        assertThat(resignationDateOf(resigned)).isNotNull();
    }

    @Test
    void 퇴사일_취소는_강사가_아니거나_없으면_404이다() throws Exception {
        String token = adminToken();
        long member = insertMember("m@example.com", "회원", "MEMBER", "ACTIVE", BASE);

        cancel(token, member).andExpect(status().isNotFound());
        cancel(token, 999999).andExpect(status().isNotFound());
    }

    @Test
    void 퇴사일_취소는_관리자만_할_수_있다() throws Exception {
        String token = instructorToken();
        setResignationDate(instructorId, today().plusDays(10));

        cancel(token, instructorId).andExpect(status().isForbidden());
        assertThat(resignationDateOf(instructorId)).isNotNull();
    }

    // ===== completeResignation =====

    @Test
    void 퇴사일이_오면_퇴사_처리되어_로그인이_막히고_토큰과_권한이_회수된다() throws Exception {
        String token = adminToken();
        setResignationDate(instructorId, today());
        grant(instructorId, "MEMBER_READ", instructorId);
        login("i@example.com");
        login("i@example.com");

        complete(token, instructorId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(instructorId))
                .andExpect(jsonPath("$.status").value("RESIGNED"))
                .andExpect(jsonPath("$.resignationDate").value(today().toString()))
                .andExpect(jsonPath("$.permissions.length()").value(0));

        assertThat(jdbc.queryForObject("SELECT status FROM members WHERE id = ?", String.class, instructorId))
                .isEqualTo("RESIGNED");
        assertThat(jdbc.queryForObject("SELECT resigned_at FROM members WHERE id = ?", Timestamp.class,
                instructorId)).isNotNull();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE member_id = ? AND revoked_at IS NULL", Integer.class,
                instructorId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_permissions WHERE member_id = ?",
                Integer.class, instructorId)).isZero();
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"i@example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void 지난_퇴사일도_퇴사_처리할_수_있다() throws Exception {
        String token = adminToken();
        setResignationDate(instructorId, today().minusDays(5));

        complete(token, instructorId).andExpect(status().isOk());
    }

    @Test
    void 퇴사_처리한_강사를_ACTIVE로_복구하면_퇴사일과_퇴사_시각이_지워진다() throws Exception {
        String token = adminToken();
        setResignationDate(instructorId, today());
        complete(token, instructorId).andExpect(status().isOk());

        mockMvc.perform(put("/members/" + instructorId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());

        assertThat(resignationDateOf(instructorId)).isNull();
        assertThat(jdbc.queryForObject("SELECT resigned_at FROM members WHERE id = ?", Timestamp.class,
                instructorId)).isNull();
    }

    @Test
    void 퇴사일이_없으면_409_RESIGNATION_NOT_REGISTERED이다() throws Exception {
        String token = adminToken();

        complete(token, instructorId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESIGNATION_NOT_REGISTERED"));
    }

    @Test
    void 퇴사일이_아직_오지_않았으면_409_RESIGNATION_DATE_NOT_REACHED이다() throws Exception {
        String token = adminToken();
        setResignationDate(instructorId, today().plusDays(1));

        complete(token, instructorId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESIGNATION_DATE_NOT_REACHED"));
        assertThat(jdbc.queryForObject("SELECT status FROM members WHERE id = ?", String.class, instructorId))
                .isEqualTo("ACTIVE");
    }

    @Test
    void 이미_퇴사한_강사는_퇴사_처리할_수_없고_409_MEMBER_RESIGNED이다() throws Exception {
        String token = adminToken();
        long resigned = insertMember("r@example.com", "퇴사", "INSTRUCTOR", "RESIGNED", BASE);
        setResignationDate(resigned, today().minusDays(1));

        complete(token, resigned)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_RESIGNED"));
    }

    @Test
    void 퇴사_처리는_강사가_아니거나_없으면_404이다() throws Exception {
        String token = adminToken();
        long member = insertMember("m@example.com", "회원", "MEMBER", "ACTIVE", BASE);

        complete(token, member).andExpect(status().isNotFound());
        complete(token, 999999).andExpect(status().isNotFound());
    }

    @Test
    void 퇴사_처리는_관리자만_할_수_있다() throws Exception {
        String token = instructorToken();
        setResignationDate(instructorId, today());

        complete(token, instructorId).andExpect(status().isForbidden());
    }

    @Test
    void 퇴사_처리도_인증하지_않으면_401이다() throws Exception {
        mockMvc.perform(post("/members/" + instructorId + "/resignation/completions"))
                .andExpect(status().isUnauthorized());
    }
}
