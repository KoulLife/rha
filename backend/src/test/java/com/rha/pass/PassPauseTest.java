package com.rha.pass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class PassPauseTest extends PassTestSupport {

    long productId;
    long memberId;
    long instructorId;
    long passId;
    String adminToken;
    long adminId;

    @BeforeEach
    void prepare() throws Exception {
        productId = insertProduct();
        memberId = insertMemberNamed("mem@example.com", "김회원");
        instructorId = insertInstructor("i@example.com", "박강사");
        // 5일 전에 시작해 24일 뒤까지 쓰는 이용권
        passId = insertPass(productId, memberId, instructorId, 300000, 10, 10, days(-5), days(24), "ACTIVE");
        adminToken = adminToken();
        adminId = adminId();
    }

    private static String pauseBody(LocalDate start, LocalDate end) {
        return "{\"startDate\":\"" + start + "\",\"endDate\":\"" + end + "\",\"reason\":\"개인 사정\"}";
    }

    // ===== createPassPause =====

    @Test
    void 미래_시작이면_SCHEDULED로_등록되고_만료일이_연장된다() throws Exception {
        createPause(adminToken, passId, pauseBody(days(3), days(5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.startDate").value(days(3).toString()))
                .andExpect(jsonPath("$.endDate").value(days(5).toString()))
                .andExpect(jsonPath("$.days").value(3))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.reason").value("개인 사정"))
                .andExpect(jsonPath("$.terminatedAt").doesNotExist())
                .andExpect(jsonPath("$.createdAt").isString());

        assertThat(endDateOf(passId)).isEqualTo(days(27));
        assertThat(passStatus(passId)).isEqualTo("ACTIVE");
    }

    @Test
    void 오늘_시작이면_ACTIVE로_등록되고_이용권이_PAUSED가_된다() throws Exception {
        createPause(adminToken, passId, pauseBody(days(0), days(1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.days").value(2));

        assertThat(endDateOf(passId)).isEqualTo(days(26));
        assertThat(passStatus(passId)).isEqualTo("PAUSED");
    }

    @Test
    void 하루_정지도_등록된다() throws Exception {
        createPause(adminToken, passId, pauseBody(days(2), days(2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.days").value(1));
        assertThat(endDateOf(passId)).isEqualTo(days(25));
    }

    @Test
    void 사유는_생략할_수_있다() throws Exception {
        createPause(adminToken, passId, "{\"startDate\":\"" + days(2) + "\",\"endDate\":\"" + days(3) + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reason").doesNotExist());
    }

    @Test
    void 이용권의_마지막_날까지_정지할_수_있다() throws Exception {
        createPause(adminToken, passId, pauseBody(days(24), days(26))).andExpect(status().isCreated());
        assertThat(endDateOf(passId)).isEqualTo(days(27));
    }

    @Test
    void 이어지는_정지는_겹치지_않고_취소된_정지와는_겹쳐도_된다() throws Exception {
        createPause(adminToken, passId, pauseBody(days(3), days(5))).andExpect(status().isCreated());
        createPause(adminToken, passId, pauseBody(days(6), days(7))).andExpect(status().isCreated());
        insertPause(passId, days(10), days(12), "CANCELED", adminId);

        createPause(adminToken, passId, pauseBody(days(10), days(11))).andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SCHEDULED", "ACTIVE", "ENDED"})
    void 취소되지_않은_정지와_겹치면_409_PAUSE_PERIOD_OVERLAP이다(String existing) throws Exception {
        insertPause(passId, days(3), days(6), existing, adminId);

        createPause(adminToken, passId, pauseBody(days(5), days(8)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAUSE_PERIOD_OVERLAP"));
        assertThat(endDateOf(passId)).isEqualTo(days(24));
    }

    @Test
    void 시작일이_과거이면_400_INVALID_PAUSE_PERIOD이다() throws Exception {
        createPause(adminToken, passId, pauseBody(days(-1), days(2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAUSE_PERIOD"));
    }

    @Test
    void 종료일이_시작일보다_빠르면_400_INVALID_PAUSE_PERIOD이다() throws Exception {
        createPause(adminToken, passId, pauseBody(days(5), days(4)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAUSE_PERIOD"));
    }

    @Test
    void 시작일이_이용권_기간_밖이면_400_INVALID_PAUSE_PERIOD이다() throws Exception {
        long upcoming = insertPass(productId, memberId, instructorId, 1, 10, 10, days(10), days(39), "UPCOMING");

        createPause(adminToken, upcoming, pauseBody(days(5), days(6)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAUSE_PERIOD"));
        createPause(adminToken, passId, pauseBody(days(25), days(26)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAUSE_PERIOD"));
    }

    @Test
    void 만료된_이용권이면_409_PASS_EXPIRED이다() throws Exception {
        long expired = insertPass(productId, memberId, instructorId, 1, 10, 10, days(-40), days(-10), "EXPIRED");

        createPause(adminToken, expired, pauseBody(days(1), days(2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PASS_EXPIRED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"startDate\":\"2099-01-01\"}",
            "{\"endDate\":\"2099-01-02\"}",
            "{\"startDate\":\"not-a-date\",\"endDate\":\"2099-01-02\"}",
            "not json"
    })
    void 요청_형식이_올바르지_않으면_400_INVALID_INPUT이다(String json) throws Exception {
        createPause(adminToken, passId, json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 사유가_200자를_넘으면_400이다() throws Exception {
        createPause(adminToken, passId, "{\"startDate\":\"" + days(2) + "\",\"endDate\":\"" + days(3)
                + "\",\"reason\":\"" + "가".repeat(201) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 없는_이용권이면_404이다() throws Exception {
        createPause(adminToken, 999999, pauseBody(days(2), days(3))).andExpect(status().isNotFound());
    }

    @Test
    void 정지_등록은_관리자만_할_수_있다() throws Exception {
        for (String token : new String[] {memberToken(), instructorToken()}) {
            createPause(token, passId, pauseBody(days(2), days(3)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
            createPause(token, 999999, "{}").andExpect(status().isForbidden());
        }
        assertThat(endDateOf(passId)).isEqualTo(days(24));
        mockMvc.perform(post("/passes/" + passId + "/pauses").contentType(MediaType.APPLICATION_JSON)
                        .content(pauseBody(days(2), days(3))))
                .andExpect(status().isUnauthorized());
    }

    // ===== getPassPauses =====

    @Test
    void 정지_이력은_시작일_내림차순이고_취소된_정지도_포함한다() throws Exception {
        long first = insertPause(passId, days(2), days(3), "ENDED", adminId);
        long third = insertPause(passId, days(20), days(21), "SCHEDULED", adminId);
        long second = insertPause(passId, days(10), days(11), "CANCELED", adminId);

        listAt("/passes/" + passId + "/pauses", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].id").value(third))
                .andExpect(jsonPath("$.content[1].id").value(second))
                .andExpect(jsonPath("$.content[1].status").value("CANCELED"))
                .andExpect(jsonPath("$.content[1].days").value(0))
                .andExpect(jsonPath("$.content[2].id").value(first))
                .andExpect(jsonPath("$.content[2].days").value(2))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(3));
    }

    @Test
    void 다른_이용권의_정지는_나오지_않고_페이지네이션이_동작한다() throws Exception {
        long other = insertActivePass(productId, memberId, instructorId);
        insertPause(other, days(2), days(3), "ENDED", adminId);
        for (int i = 0; i < 3; i++) {
            insertPause(passId, days(2 + i * 3), days(3 + i * 3), "ENDED", adminId);
        }

        listAt("/passes/" + passId + "/pauses", adminToken, "page", "2", "size", "2")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(3));
    }

    @Test
    void 이력은_없는_이용권이면_404이고_관리자만_볼_수_있다() throws Exception {
        listAt("/passes/999999/pauses", adminToken).andExpect(status().isNotFound());
        listAt("/passes/" + passId + "/pauses", memberToken()).andExpect(status().isForbidden());
        mockMvc.perform(get("/passes/" + passId + "/pauses")).andExpect(status().isUnauthorized());
    }

    // ===== terminatePassPause =====

    @Test
    void 시작_전_정지를_해제하면_취소되고_만료일이_원래대로_돌아간다() throws Exception {
        long pause = insertPause(passId, days(3), days(5), "SCHEDULED", adminId);
        jdbc.update("UPDATE passes SET end_date = ? WHERE id = ?", java.sql.Date.valueOf(days(27)), passId);

        terminate(adminToken, passId, pause)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pause))
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.days").value(0))
                .andExpect(jsonPath("$.terminatedAt").isString());

        assertThat(endDateOf(passId)).isEqualTo(days(24));
        assertThat(pauseStatus(pause)).isEqualTo("CANCELED");
    }

    @Test
    void 진행_중_정지를_해제하면_오늘부터_풀리고_사용하지_않은_일수만큼_줄어든다() throws Exception {
        long pause = insertPause(passId, days(-2), days(4), "ACTIVE", adminId);
        jdbc.update("UPDATE passes SET status = 'PAUSED' WHERE id = ?", passId);

        terminate(adminToken, passId, pause)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"))
                .andExpect(jsonPath("$.endDate").value(days(-1).toString()))
                .andExpect(jsonPath("$.days").value(2))
                .andExpect(jsonPath("$.terminatedAt").isString());

        // 7일 정지 중 2일만 썼으므로 5일이 줄어든다
        assertThat(endDateOf(passId)).isEqualTo(days(19));
        assertThat(passStatus(passId)).isEqualTo("ACTIVE");
    }

    @Test
    void 시작일_당일에_해제하면_취소와_같이_처리된다() throws Exception {
        long pause = insertPause(passId, days(0), days(2), "ACTIVE", adminId);
        jdbc.update("UPDATE passes SET status = 'PAUSED', end_date = ? WHERE id = ?",
                java.sql.Date.valueOf(days(27)), passId);

        terminate(adminToken, passId, pause)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.days").value(0));

        assertThat(endDateOf(passId)).isEqualTo(days(24));
        assertThat(passStatus(passId)).isEqualTo("ACTIVE");
    }

    @Test
    void 해제로_만료일이_과거가_되면_이용권은_EXPIRED가_된다() throws Exception {
        long expiring = insertPass(productId, memberId, instructorId, 1, 10, 10, days(-20), days(10), "PAUSED");
        // 24일 정지 중 어제까지 13일을 썼으므로 11일이 줄어 만료일이 어제가 된다
        long pause = insertPause(expiring, days(-13), days(10), "ACTIVE", adminId);

        terminate(adminToken, expiring, pause).andExpect(status().isOk());

        assertThat(endDateOf(expiring)).isEqualTo(days(-1));
        assertThat(passStatus(expiring)).isEqualTo("EXPIRED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ENDED", "CANCELED"})
    void 이미_끝났거나_취소된_정지는_409_INVALID_PAUSE_STATUS이다(String current) throws Exception {
        long pause = insertPause(passId, days(-4), days(-2), current, adminId);

        terminate(adminToken, passId, pause)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_PAUSE_STATUS"));
    }

    @Test
    void 다른_이용권의_정지이거나_없으면_404이다() throws Exception {
        long other = insertActivePass(productId, memberId, instructorId);
        long pause = insertPause(other, days(3), days(4), "SCHEDULED", adminId);

        terminate(adminToken, passId, pause).andExpect(status().isNotFound());
        terminate(adminToken, passId, 999999).andExpect(status().isNotFound());
        terminate(adminToken, 999999, pause).andExpect(status().isNotFound());
        assertThat(pauseStatus(pause)).isEqualTo("SCHEDULED");
    }

    @Test
    void 해제는_관리자만_할_수_있다() throws Exception {
        long pause = insertPause(passId, days(3), days(4), "SCHEDULED", adminId);

        terminate(memberToken(), passId, pause)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mockMvc.perform(post("/passes/" + passId + "/pauses/" + pause + "/terminations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer x"))
                .andExpect(status().isUnauthorized());
        assertThat(pauseStatus(pause)).isEqualTo("SCHEDULED");
    }
}
