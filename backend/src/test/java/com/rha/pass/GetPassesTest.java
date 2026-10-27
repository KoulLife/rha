package com.rha.pass;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GetPassesTest extends PassTestSupport {

    long productId;
    long memberA;
    long memberB;
    long instructorId;

    @BeforeEach
    void prepare() {
        productId = insertProduct();
        memberA = insertMemberNamed("a@example.com", "김회원");
        memberB = insertMemberNamed("b@example.com", "이회원");
        instructorId = insertInstructor("i@example.com", "박강사");
    }

    private void createdAt(long passId, Instant at) {
        jdbc.update("UPDATE passes SET created_at = ? WHERE id = ?", Timestamp.from(at), passId);
    }

    // ===== getPasses =====

    @Test
    void 기본은_등록일_내림차순이고_같으면_id_내림차순이다() throws Exception {
        long old = insertActivePass(productId, memberA, instructorId);
        createdAt(old, Instant.parse("2026-09-01T00:00:00Z"));
        Instant same = Instant.parse("2026-10-01T00:00:00Z");
        long first = insertActivePass(productId, memberA, instructorId);
        long second = insertActivePass(productId, memberB, instructorId);
        createdAt(first, same);
        createdAt(second, same);
        String token = adminToken();

        list(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].id").value(second))
                .andExpect(jsonPath("$.content[1].id").value(first))
                .andExpect(jsonPath("$.content[2].id").value(old))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(3));
    }

    @Test
    void 응답에_이용권_정보가_담긴다() throws Exception {
        long id = insertPass(productId, memberA, instructorId, 300000, 10, 7, days(0), days(29), "ACTIVE");
        String token = adminToken();

        list(token)
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].passProduct.id").value(productId))
                .andExpect(jsonPath("$.content[0].member.name").value("김회원"))
                .andExpect(jsonPath("$.content[0].instructor.name").value("박강사"))
                .andExpect(jsonPath("$.content[0].remainingCount").value(7))
                .andExpect(jsonPath("$.content[0].paidAmount").value(0))
                .andExpect(jsonPath("$.content[0].paymentStatus").value("UNPAID"));
    }

    @Test
    void 회원_강사_상품_상태로_필터한다() throws Exception {
        long otherProduct = insertProduct("요가", 1, 5, 10);
        long otherInstructor = insertInstructor("i2@example.com", "최강사");
        long base = insertActivePass(productId, memberA, instructorId);
        long byMember = insertActivePass(productId, memberB, instructorId);
        long byInstructor = insertActivePass(productId, memberA, otherInstructor);
        long byProduct = insertActivePass(otherProduct, memberA, instructorId);
        long expired = insertPass(productId, memberA, instructorId, 1, 10, 10, days(-40), days(-10), "EXPIRED");
        String token = adminToken();

        list(token, "memberId", String.valueOf(memberB))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(byMember));
        list(token, "instructorId", String.valueOf(otherInstructor))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(byInstructor));
        list(token, "passProductId", String.valueOf(otherProduct))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(byProduct));
        list(token, "status", "EXPIRED")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(expired));
        list(token, "status", "ACTIVE").andExpect(jsonPath("$.content.length()").value(4));
        list(token, "memberId", String.valueOf(memberA), "status", "ACTIVE")
                .andExpect(jsonPath("$.content.length()").value(3));
        org.assertj.core.api.Assertions.assertThat(base).isPositive();
    }

    @Test
    void 회원_이름_키워드로_검색한다() throws Exception {
        insertActivePass(productId, memberA, instructorId);
        insertActivePass(productId, memberB, instructorId);
        String token = adminToken();

        list(token, "keyword", "김회")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].member.name").value("김회원"));
        list(token, "keyword", "회원").andExpect(jsonPath("$.content.length()").value(2));
        list(token, "keyword", "없는이름").andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void 결제_상태로_필터한다() throws Exception {
        long unpaid = insertActivePass(productId, memberA, instructorId);
        long free = insertPass(productId, memberB, instructorId, 0, 10, 10, days(0), days(29), "ACTIVE");
        String token = adminToken();

        list(token, "paymentStatus", "UNPAID")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(unpaid));
        list(token, "paymentStatus", "PAID")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(free))
                .andExpect(jsonPath("$.content[0].paymentStatus").value("PAID"));
        list(token, "paymentStatus", "PARTIAL").andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void 등록일_범위는_from_이상_to_미만이다() throws Exception {
        long a = insertActivePass(productId, memberA, instructorId);
        long b = insertActivePass(productId, memberA, instructorId);
        long c = insertActivePass(productId, memberA, instructorId);
        createdAt(a, Instant.parse("2026-10-01T00:00:00Z"));
        createdAt(b, Instant.parse("2026-10-02T00:00:00Z"));
        createdAt(c, Instant.parse("2026-10-03T00:00:00Z"));
        String token = adminToken();

        list(token, "createdFrom", "2026-10-02T00:00:00Z", "createdTo", "2026-10-03T00:00:00Z")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(b));
    }

    @Test
    void 정렬_기준과_방향을_지정할_수_있다() throws Exception {
        long few = insertPass(productId, memberA, instructorId, 1, 5, 1, days(0), days(50), "ACTIVE");
        long many = insertPass(productId, memberA, instructorId, 1, 20, 15, days(0), days(10), "ACTIVE");
        String token = adminToken();

        list(token, "sortBy", "totalCount", "order", "ASC")
                .andExpect(jsonPath("$.content[0].id").value(few));
        list(token, "sortBy", "totalCount")
                .andExpect(jsonPath("$.content[0].id").value(many));
        list(token, "sortBy", "remainingCount", "order", "ASC")
                .andExpect(jsonPath("$.content[0].id").value(few));
        list(token, "sortBy", "endDate", "order", "ASC")
                .andExpect(jsonPath("$.content[0].id").value(many));
        list(token, "sortBy", "endDate", "order", "DESC")
                .andExpect(jsonPath("$.content[0].id").value(few));
    }

    @Test
    void 정렬값이_같으면_id가_정렬_방향을_따른다() throws Exception {
        long first = insertActivePass(productId, memberA, instructorId);
        long second = insertActivePass(productId, memberA, instructorId);
        String token = adminToken();

        list(token, "sortBy", "totalCount", "order", "ASC")
                .andExpect(jsonPath("$.content[0].id").value(first))
                .andExpect(jsonPath("$.content[1].id").value(second));
        list(token, "sortBy", "totalCount", "order", "DESC")
                .andExpect(jsonPath("$.content[0].id").value(second));
    }

    @Test
    void 페이지네이션이_동작한다() throws Exception {
        for (int i = 0; i < 5; i++) {
            insertActivePass(productId, memberA, instructorId);
        }
        String token = adminToken();

        list(token, "page", "2", "size", "2")
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.pageInfo.page").value(2))
                .andExpect(jsonPath("$.pageInfo.totalPages").value(3));
    }

    @ParameterizedTest
    @CsvSource({"page,0", "size,0", "size,101", "status,UNKNOWN", "paymentStatus,X", "sortBy,name", "order,UP",
            "createdFrom,not-a-date", "memberId,abc"})
    void 잘못된_파라미터는_400이다(String key, String value) throws Exception {
        String token = adminToken();

        list(token, key, value)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void createdTo가_createdFrom보다_늦지_않으면_400이다() throws Exception {
        String token = adminToken();

        list(token, "createdFrom", "2026-10-02T00:00:00Z", "createdTo", "2026-10-02T00:00:00Z")
                .andExpect(status().isBadRequest());
    }

    @Test
    void 목록은_관리자만_볼_수_있다() throws Exception {
        list(memberToken()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        list(instructorToken()).andExpect(status().isForbidden());
        mockMvc.perform(get("/passes")).andExpect(status().isUnauthorized());
    }

    // ===== getPass =====

    @Test
    void 상세를_볼_수_있다() throws Exception {
        long id = insertActivePass(productId, memberA, instructorId);
        String token = adminToken();

        detail(token, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.member.id").value(memberA))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void 없는_이용권이면_404이고_숫자가_아니면_400이다() throws Exception {
        String token = adminToken();

        detail(token, 999999).andExpect(status().isNotFound());
        mockMvc.perform(get("/passes/abc").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 상세는_관리자만_볼_수_있다() throws Exception {
        long id = insertActivePass(productId, memberA, instructorId);

        detail(login("a@example.com"), id).andExpect(status().isForbidden());
        detail(instructorToken(), 999999).andExpect(status().isForbidden());
    }

    // ===== getMyPasses =====

    @Test
    void 내_이용권만_만료일_오름차순으로_보고_같으면_id_오름차순이다() throws Exception {
        long late = insertPass(productId, memberA, instructorId, 1, 10, 10, days(0), days(40), "ACTIVE");
        long soonFirst = insertPass(productId, memberA, instructorId, 1, 10, 10, days(0), days(5), "ACTIVE");
        long soonSecond = insertPass(productId, memberA, instructorId, 1, 10, 10, days(0), days(5), "ACTIVE");
        insertActivePass(productId, memberB, instructorId);
        String token = login("a@example.com");

        myPasses(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].id").value(soonFirst))
                .andExpect(jsonPath("$.content[1].id").value(soonSecond))
                .andExpect(jsonPath("$.content[2].id").value(late))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(3));
    }

    @Test
    void 내_이용권은_상태로_필터한다() throws Exception {
        insertActivePass(productId, memberA, instructorId);
        long upcoming = insertPass(productId, memberA, instructorId, 1, 10, 10, days(3), days(32), "UPCOMING");
        String token = login("a@example.com");

        myPasses(token, "status", "UPCOMING")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(upcoming));
    }

    @Test
    void 내_이용권은_상태를_지정하지_않으면_환불된_이용권을_빼고_REFUNDED를_지정해야_보인다() throws Exception {
        long active = insertActivePass(productId, memberA, instructorId);
        long refunded = insertPass(productId, memberA, instructorId, 1, 10, 10, days(0), days(29), "REFUNDED");
        String token = login("a@example.com");

        myPasses(token)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(active))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(1));
        myPasses(token, "status", "REFUNDED")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(refunded));
    }

    @Test
    void 일반_회원이_아니면_빈_목록이다() throws Exception {
        insertActivePass(productId, memberA, instructorId);

        for (String token : new String[] {adminToken(), login("i@example.com")}) {
            myPasses(token)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0))
                    .andExpect(jsonPath("$.pageInfo.totalElements").value(0));
        }
    }

    @ParameterizedTest
    @CsvSource({"page,0", "size,101", "status,UNKNOWN"})
    void 내_이용권의_잘못된_파라미터는_400이다(String key, String value) throws Exception {
        myPasses(memberToken(), key, value)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 내_이용권도_인증하지_않으면_401이다() throws Exception {
        mockMvc.perform(get("/members/me/passes")).andExpect(status().isUnauthorized());
    }
}
