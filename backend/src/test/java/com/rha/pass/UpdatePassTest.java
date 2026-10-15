package com.rha.pass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

class UpdatePassTest extends PassTestSupport {

    long productId;
    long memberId;
    long instructorId;
    long passId;

    @BeforeEach
    void prepare() {
        productId = insertProduct();
        memberId = insertMemberNamed("mem@example.com", "김회원");
        instructorId = insertInstructor("i@example.com", "박강사");
        passId = insertPass(productId, memberId, instructorId, 300000, 10, 7, days(0), days(29), "ACTIVE");
    }

    @Test
    void 전달한_필드만_수정되고_나머지는_그대로이다() throws Exception {
        String token = adminToken();

        updatePass(token, passId, "{\"remainingCount\":3}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingCount").value(3))
                .andExpect(jsonPath("$.totalCount").value(10))
                .andExpect(jsonPath("$.endDate").value(days(29).toString()))
                .andExpect(jsonPath("$.instructor.id").value(instructorId));
        assertThat(remainingCountOf(passId)).isEqualTo(3);
    }

    @Test
    void 잔여_횟수는_0과_총_횟수까지_허용한다() throws Exception {
        String token = adminToken();

        updatePass(token, passId, "{\"remainingCount\":0}").andExpect(status().isOk());
        updatePass(token, passId, "{\"remainingCount\":10}").andExpect(status().isOk());
    }

    @Test
    void 만료일을_바꾸면_반영되고_시작일과_같아도_된다() throws Exception {
        String token = adminToken();

        updatePass(token, passId, "{\"endDate\":\"" + days(60) + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endDate").value(days(60).toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        updatePass(token, passId, "{\"endDate\":\"" + days(0) + "\"}").andExpect(status().isOk());
    }

    @Test
    void 만료일을_과거로_바꾸면_EXPIRED가_된다() throws Exception {
        String token = adminToken();
        long past = insertPass(productId, memberId, instructorId, 1, 10, 10, days(-20), days(-1), "EXPIRED");
        long active = insertPass(productId, memberId, instructorId, 1, 10, 10, days(-10), days(10), "ACTIVE");

        updatePass(token, active, "{\"endDate\":\"" + days(-1) + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"));
        assertThat(passStatus(past)).isEqualTo("EXPIRED");
    }

    @Test
    void 만료된_이용권의_만료일을_미래로_수정하면_ACTIVE로_돌아온다() throws Exception {
        String token = adminToken();
        long expired = insertPass(productId, memberId, instructorId, 1, 10, 10, days(-40), days(-10), "EXPIRED");

        updatePass(token, expired, "{\"endDate\":\"" + days(5) + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void 시작_전_이용권의_만료일을_수정해도_UPCOMING이다() throws Exception {
        String token = adminToken();
        long upcoming = insertPass(productId, memberId, instructorId, 1, 10, 10, days(5), days(34), "UPCOMING");

        updatePass(token, upcoming, "{\"endDate\":\"" + days(60) + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPCOMING"));
    }

    @Test
    void 정지_중인_이용권은_만료일을_수정해도_PAUSED이다() throws Exception {
        String token = adminToken();
        insertPause(passId, days(-1), days(3), "ACTIVE", adminId());
        jdbc.update("UPDATE passes SET status = 'PAUSED' WHERE id = ?", passId);

        updatePass(token, passId, "{\"endDate\":\"" + days(40) + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));
    }

    @Test
    void 담당_강사를_바꿀_수_있다() throws Exception {
        String token = adminToken();
        long other = insertInstructor("i2@example.com", "최강사");

        updatePass(token, passId, "{\"instructorId\":" + other + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instructor.id").value(other))
                .andExpect(jsonPath("$.instructor.name").value("최강사"));
    }

    @Test
    void 같은_강사로_보내면_퇴사일이_등록되어도_통과한다() throws Exception {
        String token = adminToken();
        jdbc.update("UPDATE members SET resignation_date = ? WHERE id = ?",
                Date.valueOf(days(60)), instructorId);

        updatePass(token, passId, "{\"instructorId\":" + instructorId + ",\"remainingCount\":5}")
                .andExpect(status().isOk());
    }

    @Test
    void 다른_이용권에는_영향이_없다() throws Exception {
        String token = adminToken();
        long other = insertActivePass(productId, memberId, instructorId);

        updatePass(token, passId, "{\"remainingCount\":1}").andExpect(status().isOk());

        assertThat(remainingCountOf(other)).isEqualTo(10);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"endDate\":\"not-a-date\"}",
            "{\"remainingCount\":\"x\"}",
            "{\"instructorId\":\"x\"}",
            "not json"
    })
    void 요청_형식이_올바르지_않으면_400_INVALID_INPUT이다(String json) throws Exception {
        String token = adminToken();

        updatePass(token, passId, json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"remainingCount\":-1}", "{\"remainingCount\":11}"})
    void 잔여_횟수가_범위를_벗어나면_400_INVALID_REMAINING_COUNT이다(String json) throws Exception {
        String token = adminToken();

        updatePass(token, passId, json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REMAINING_COUNT"));
        assertThat(remainingCountOf(passId)).isEqualTo(7);
    }

    @Test
    void 만료일이_시작일보다_빠르면_400_INVALID_END_DATE이다() throws Exception {
        String token = adminToken();

        updatePass(token, passId, "{\"endDate\":\"" + days(-1) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_END_DATE"));
        assertThat(endDateOf(passId)).isEqualTo(days(29));
    }

    @Test
    void 담당_강사가_강사가_아니거나_활성이_아니면_400_INVALID_INSTRUCTOR이다() throws Exception {
        String token = adminToken();
        long member = insertMember("o@example.com", "다른", "MEMBER", "ACTIVE", BASE);
        long inactive = insertMember("in@example.com", "비활성", "INSTRUCTOR", "INACTIVE", BASE);

        for (long id : new long[] {member, inactive}) {
            updatePass(token, passId, "{\"instructorId\":" + id + "}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INSTRUCTOR"));
        }
    }

    @Test
    void 없는_강사나_없는_이용권이면_404이다() throws Exception {
        String token = adminToken();

        updatePass(token, passId, "{\"instructorId\":999999}").andExpect(status().isNotFound());
        updatePass(token, 999999, "{\"remainingCount\":1}").andExpect(status().isNotFound());
    }

    @Test
    void 퇴사일이_등록된_강사로_바꾸면_409_INSTRUCTOR_RESIGNING이다() throws Exception {
        String token = adminToken();
        long resigning = insertInstructor("r@example.com", "퇴사예정");
        jdbc.update("UPDATE members SET resignation_date = ? WHERE id = ?", Date.valueOf(days(60)), resigning);

        updatePass(token, passId, "{\"instructorId\":" + resigning + "}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTRUCTOR_RESIGNING"));
    }

    @Test
    void 값_오류는_없는_이용권보다_먼저_400이다() throws Exception {
        String token = adminToken();

        updatePass(token, 999999, "{}").andExpect(status().isBadRequest());
    }

    @Test
    void 관리자가_아니면_403이고_없는_이용권이어도_403이다() throws Exception {
        for (String token : new String[] {memberToken(), instructorToken()}) {
            updatePass(token, passId, "{\"remainingCount\":1}")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
            updatePass(token, 999999, "{}").andExpect(status().isForbidden());
        }
        assertThat(remainingCountOf(passId)).isEqualTo(7);
    }

    @Test
    void 수정도_인증하지_않으면_401이다() throws Exception {
        mockMvc.perform(patch("/passes/" + passId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remainingCount\":1}"))
                .andExpect(status().isUnauthorized());
    }
}
