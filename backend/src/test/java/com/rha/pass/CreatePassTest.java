package com.rha.pass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Date;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

class CreatePassTest extends PassTestSupport {

    long productId;
    long memberId;
    long instructorId;

    @BeforeEach
    void prepare() {
        productId = insertProduct("필라테스 10회권", 300000, 10, 30);
        memberId = insertMemberNamed("m1@example.com", "김회원");
        instructorId = insertInstructor("i@example.com", "박강사");
    }

    // ===== 성공 =====

    @Test
    void 오늘_시작으로_발급하면_ACTIVE이고_상품_값이_복사된다() throws Exception {
        String token = adminToken();

        createPass(token, passBody(productId, memberId, instructorId, today()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.passProduct.id").value(productId))
                .andExpect(jsonPath("$.passProduct.name").value("필라테스 10회권"))
                .andExpect(jsonPath("$.member.id").value(memberId))
                .andExpect(jsonPath("$.member.name").value("김회원"))
                .andExpect(jsonPath("$.instructor.id").value(instructorId))
                .andExpect(jsonPath("$.instructor.name").value("박강사"))
                .andExpect(jsonPath("$.price").value(300000))
                .andExpect(jsonPath("$.paidAmount").value(0))
                .andExpect(jsonPath("$.unpaidAmount").value(300000))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.totalCount").value(10))
                .andExpect(jsonPath("$.remainingCount").value(10))
                .andExpect(jsonPath("$.startDate").value(today().toString()))
                .andExpect(jsonPath("$.endDate").value(today().plusDays(29).toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").isString());
        assertThat(passCount()).isEqualTo(1);
    }

    @Test
    void 시작일이_미래이면_UPCOMING이다() throws Exception {
        String token = adminToken();

        createPass(token, passBody(productId, memberId, instructorId, days(5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UPCOMING"))
                .andExpect(jsonPath("$.endDate").value(days(5).plusDays(29).toString()));
    }

    @Test
    void 시작일이_과거여도_만료일이_지나지_않았으면_발급된다() throws Exception {
        String token = adminToken();

        createPass(token, passBody(productId, memberId, instructorId, days(-10)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.endDate").value(days(19).toString()));
    }

    @Test
    void 만료일_당일에도_발급된다() throws Exception {
        String token = adminToken();

        createPass(token, passBody(productId, memberId, instructorId, days(-29)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.endDate").value(today().toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void 금액을_지정하면_그_값이_쓰이고_미수금은_금액이다() throws Exception {
        String token = adminToken();

        createPass(token, "{\"passProductId\":" + productId + ",\"memberId\":" + memberId
                + ",\"instructorId\":" + instructorId + ",\"startDate\":\"" + today() + "\",\"price\":270000}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.price").value(270000))
                .andExpect(jsonPath("$.unpaidAmount").value(270000))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"));
    }

    @Test
    void 금액이_0이면_바로_PAID이다() throws Exception {
        String token = adminToken();

        createPass(token, "{\"passProductId\":" + productId + ",\"memberId\":" + memberId
                + ",\"instructorId\":" + instructorId + ",\"startDate\":\"" + today() + "\",\"price\":0}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.price").value(0))
                .andExpect(jsonPath("$.unpaidAmount").value(0))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));
    }

    @Test
    void 발급_후_상품이_바뀌어도_이용권의_횟수는_그대로이다() throws Exception {
        String token = adminToken();
        createPass(token, passBody(productId, memberId, instructorId, today())).andExpect(status().isCreated());

        jdbc.update("UPDATE pass_products SET total_count = 20, price = 1 WHERE id = ?", productId);

        assertThat(jdbc.queryForObject("SELECT total_count FROM passes", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT price FROM passes", Long.class)).isEqualTo(300000L);
    }

    // ===== 400 =====

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"memberId\":1,\"instructorId\":1,\"startDate\":\"2026-10-05\"}",
            "{\"passProductId\":1,\"instructorId\":1,\"startDate\":\"2026-10-05\"}",
            "{\"passProductId\":1,\"memberId\":1,\"startDate\":\"2026-10-05\"}",
            "{\"passProductId\":1,\"memberId\":1,\"instructorId\":1}",
            "{\"passProductId\":1,\"memberId\":1,\"instructorId\":1,\"startDate\":\"not-a-date\"}",
            "{\"passProductId\":1,\"memberId\":1,\"instructorId\":1,\"startDate\":\"2026-10-05\",\"price\":-1}",
            "{\"passProductId\":\"x\",\"memberId\":1,\"instructorId\":1,\"startDate\":\"2026-10-05\"}",
            "not json"
    })
    void 요청_값이_올바르지_않으면_400_INVALID_INPUT이다(String json) throws Exception {
        String token = adminToken();

        createPass(token, json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(passCount()).isZero();
    }

    @Test
    void 만료일이_이미_지났으면_400_INVALID_START_DATE이다() throws Exception {
        String token = adminToken();

        createPass(token, passBody(productId, memberId, instructorId, days(-30)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_START_DATE"));
        assertThat(passCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"INSTRUCTOR", "ADMIN"})
    void 회원이_일반_회원이_아니면_400_INVALID_MEMBER이다(String role) throws Exception {
        String token = adminToken();
        long other = insertMember("o@example.com", "다른", role, "ACTIVE", BASE);

        createPass(token, passBody(productId, other, instructorId, today()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MEMBER"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"INACTIVE", "WITHDRAWN"})
    void 회원이_활성이_아니면_400_INVALID_MEMBER이다(String memberStatus) throws Exception {
        String token = adminToken();
        long other = insertMember("o@example.com", "다른", "MEMBER", memberStatus, BASE);

        createPass(token, passBody(productId, other, instructorId, today()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MEMBER"));
    }

    @Test
    void 담당_강사가_강사가_아니거나_활성이_아니면_400_INVALID_INSTRUCTOR이다() throws Exception {
        String token = adminToken();
        long member = insertMember("o@example.com", "다른", "MEMBER", "ACTIVE", BASE);
        long inactive = insertMember("in@example.com", "비활성", "INSTRUCTOR", "INACTIVE", BASE);

        for (long id : new long[] {member, inactive}) {
            createPass(token, passBody(productId, memberId, id, today()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INSTRUCTOR"));
        }
    }

    @Test
    void 시작일_오류가_회원_오류보다_먼저다() throws Exception {
        String token = adminToken();
        long other = insertMember("o@example.com", "다른", "INSTRUCTOR", "ACTIVE", BASE);

        createPass(token, passBody(productId, other, instructorId, days(-40)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_START_DATE"));
    }

    // ===== 404, 409 =====

    @Test
    void 상품_회원_강사가_없으면_404이다() throws Exception {
        String token = adminToken();

        createPass(token, passBody(999999, memberId, instructorId, today())).andExpect(status().isNotFound());
        createPass(token, passBody(productId, 999999, instructorId, today())).andExpect(status().isNotFound());
        createPass(token, passBody(productId, memberId, 999999, today())).andExpect(status().isNotFound());
        assertThat(passCount()).isZero();
    }

    @Test
    void 퇴사일이_등록된_강사는_담당으로_지정할_수_없다() throws Exception {
        String token = adminToken();
        jdbc.update("UPDATE members SET resignation_date = ? WHERE id = ?", Date.valueOf(LocalDate.now().plusDays(90)),
                instructorId);

        createPass(token, passBody(productId, memberId, instructorId, today()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTRUCTOR_RESIGNING"));
        assertThat(passCount()).isZero();
    }

    // ===== 권한 =====

    @Test
    void 관리자가_아니면_403이고_잘못된_본문이어도_403이다() throws Exception {
        for (String token : new String[] {instructorToken(), memberToken()}) {
            createPass(token, passBody(productId, memberId, instructorId, today()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
            createPass(token, "{}").andExpect(status().isForbidden());
        }
        assertThat(passCount()).isZero();
    }

    @Test
    void 인증하지_않으면_401이다() throws Exception {
        mockMvc.perform(post("/passes").contentType(MediaType.APPLICATION_JSON)
                        .content(passBody(productId, memberId, instructorId, today())))
                .andExpect(status().isUnauthorized());
    }
}
