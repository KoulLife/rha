package com.rha.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rha.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
@Import(TestMailerConfig.class)
class ConfirmEmailVerificationTest extends IntegrationTestSupport {

    private static final String EMAIL = "user@example.com";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RecordingEmailVerificationMailer mailer;

    @BeforeEach
    void setUp() {
        mailer.clear();
    }

    @Test
    void 올바른_코드면_200과_이메일_인증_토큰을_응답한다() throws Exception {
        String code = requestCode(EMAIL);

        confirm(EMAIL, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerificationToken").value(org.hamcrest.Matchers.startsWith("evt_")))
                .andExpect(jsonPath("$.expiresIn").value(1800));
    }

    @Test
    void 토큰은_해시로_저장되고_인증_시각과_토큰_만료_시각이_기록된다() throws Exception {
        String code = requestCode(EMAIL);
        Instant before = Instant.now();

        String body = confirm(EMAIL, code).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT verification_token_hash, verified_at, token_expires_at FROM email_verifications WHERE email = ?",
                EMAIL);
        assertThat((String) row.get("verification_token_hash")).isNotBlank().doesNotContain("evt_");
        assertThat(body).doesNotContain((String) row.get("verification_token_hash"));
        assertThat(row.get("verified_at")).isNotNull();
        Instant tokenExpiresAt = ((Timestamp) row.get("token_expires_at")).toInstant();
        assertThat(tokenExpiresAt).isBetween(before.plus(29, ChronoUnit.MINUTES), Instant.now().plus(31, ChronoUnit.MINUTES));
    }

    @Test
    void 코드가_일치하지_않으면_400_INVALID_VERIFICATION_CODE를_응답한다() throws Exception {
        String code = requestCode(EMAIL);
        String wrong = code.equals("000000") ? "111111" : "000000";

        confirm(EMAIL, wrong)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));
    }

    @Test
    void 코드가_만료되면_400_INVALID_VERIFICATION_CODE를_응답한다() throws Exception {
        String code = requestCode(EMAIL);
        // DB 컨테이너와 호스트의 시계 차이에 영향받지 않도록 충분히 과거로 설정한다.
        jdbc.update("UPDATE email_verifications SET code_expires_at = now() - interval '1 day' WHERE email = ?",
                EMAIL);

        confirm(EMAIL, code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));
    }

    @Test
    void 인증을_요청한_적이_없는_이메일이면_400_INVALID_VERIFICATION_CODE를_응답한다() throws Exception {
        confirm(EMAIL, "123456")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));
    }

    @Test
    void 이미_확인한_코드는_다시_사용할_수_없다() throws Exception {
        String code = requestCode(EMAIL);
        confirm(EMAIL, code).andExpect(status().isOk());

        confirm(EMAIL, code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));
    }

    @Test
    void 코드를_재요청하면_이전_코드로는_확인할_수_없다() throws Exception {
        String firstCode = requestCode(EMAIL);
        requestCode(EMAIL);

        confirm(EMAIL, firstCode)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));
    }

    @Test
    void 같은_코드로_4번_틀린_뒤_올바른_코드는_통과한다() throws Exception {
        String code = requestCode(EMAIL);
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 4; i++) {
            confirm(EMAIL, wrong).andExpect(status().isBadRequest());
        }

        confirm(EMAIL, code).andExpect(status().isOk());
    }

    @Test
    void 같은_코드로_5번_틀리면_올바른_코드도_거절하고_재요청해야_한다() throws Exception {
        String code = requestCode(EMAIL);
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            confirm(EMAIL, wrong)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));
        }

        confirm(EMAIL, code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));

        String fresh = requestCode(EMAIL);
        confirm(EMAIL, fresh).andExpect(status().isOk());
    }

    @Test
    void 코드가_6자리_숫자가_아니면_400_INVALID_INPUT을_응답한다() throws Exception {
        confirm(EMAIL, "12345")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.invalidParams[0].field").value("code"));
    }

    private String requestCode(String email) throws Exception {
        mockMvc.perform(post("/auth/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isNoContent());
        return mailer.sent().get(mailer.sent().size() - 1).code();
    }

    private ResultActions confirm(String email, String code) throws Exception {
        return mockMvc.perform(post("/auth/email-verifications/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}"));
    }
}
