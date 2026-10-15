package com.rha.passproduct;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rha.ApiTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class PassProductTest extends ApiTestSupport {

    private static final String VALID =
            "{\"name\":\"필라테스 10회권\",\"price\":300000,\"totalCount\":10,\"validityDays\":30,"
                    + "\"description\":\"기간 내 10회 이용 가능\"}";

    long insertProduct(String name, long price, int count, int days, String description, Instant createdAt) {
        return jdbc.queryForObject("""
                INSERT INTO pass_products (name, price, total_count, validity_days, description, created_at,
                                           updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, name, price, count, days, description, Timestamp.from(createdAt),
                Timestamp.from(createdAt));
    }

    long insertProduct(String name) {
        return insertProduct(name, 100000, 10, 30, null, Instant.parse("2026-10-01T00:00:00Z"));
    }

    private int productCount() {
        return jdbc.queryForObject("SELECT count(*) FROM pass_products", Integer.class);
    }

    private ResultActions create(String token, String json) throws Exception {
        return mockMvc.perform(post("/pass-products").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions update(String token, long id, String json) throws Exception {
        return mockMvc.perform(patch("/pass-products/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions remove(String token, long id) throws Exception {
        return mockMvc.perform(delete("/pass-products/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions detail(String token, long id) throws Exception {
        return mockMvc.perform(get("/pass-products/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions list(String token, String... keyValues) throws Exception {
        var request = get("/pass-products").header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        for (int i = 0; i < keyValues.length; i += 2) {
            request.param(keyValues[i], keyValues[i + 1]);
        }
        return mockMvc.perform(request);
    }

    // ===== createPassProduct =====

    @Test
    void 관리자는_상품을_등록할_수_있다() throws Exception {
        String token = adminToken();

        create(token, VALID)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("필라테스 10회권"))
                .andExpect(jsonPath("$.price").value(300000))
                .andExpect(jsonPath("$.totalCount").value(10))
                .andExpect(jsonPath("$.validityDays").value(30))
                .andExpect(jsonPath("$.description").value("기간 내 10회 이용 가능"))
                .andExpect(jsonPath("$.createdAt").isString());
        assertThat(productCount()).isEqualTo(1);
    }

    @Test
    void 설명은_생략할_수_있고_가격_0과_경계값도_허용한다() throws Exception {
        String token = adminToken();

        create(token, "{\"name\":\"체험권\",\"price\":0,\"totalCount\":1,\"validityDays\":1}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").doesNotExist());
        create(token, "{\"name\":\"" + "가".repeat(100) + "\",\"price\":1,\"totalCount\":1000,"
                + "\"validityDays\":3650,\"description\":\"" + "나".repeat(500) + "\"}")
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"price\":1,\"totalCount\":1,\"validityDays\":1}",
            "{\"name\":\"\",\"price\":1,\"totalCount\":1,\"validityDays\":1}",
            "{\"name\":\"a\",\"totalCount\":1,\"validityDays\":1}",
            "{\"name\":\"a\",\"price\":-1,\"totalCount\":1,\"validityDays\":1}",
            "{\"name\":\"a\",\"price\":1,\"validityDays\":1}",
            "{\"name\":\"a\",\"price\":1,\"totalCount\":0,\"validityDays\":1}",
            "{\"name\":\"a\",\"price\":1,\"totalCount\":1001,\"validityDays\":1}",
            "{\"name\":\"a\",\"price\":1,\"totalCount\":1}",
            "{\"name\":\"a\",\"price\":1,\"totalCount\":1,\"validityDays\":0}",
            "{\"name\":\"a\",\"price\":1,\"totalCount\":1,\"validityDays\":3651}",
            "{\"name\":\"a\",\"price\":\"x\",\"totalCount\":1,\"validityDays\":1}",
            "not json"
    })
    void 요청_값이_올바르지_않으면_400_INVALID_INPUT이다(String json) throws Exception {
        String token = adminToken();

        create(token, json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(productCount()).isZero();
    }

    @Test
    void 이름이_100자를_넘거나_설명이_500자를_넘으면_400이다() throws Exception {
        String token = adminToken();

        create(token, "{\"name\":\"" + "가".repeat(101) + "\",\"price\":1,\"totalCount\":1,\"validityDays\":1}")
                .andExpect(status().isBadRequest());
        create(token, "{\"name\":\"a\",\"price\":1,\"totalCount\":1,\"validityDays\":1,\"description\":\""
                + "가".repeat(501) + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void 등록은_관리자만_할_수_있고_잘못된_본문이어도_403이다() throws Exception {
        for (String token : new String[] {instructorToken(), memberToken()}) {
            create(token, VALID)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
            create(token, "{}").andExpect(status().isForbidden());
        }
        assertThat(productCount()).isZero();
    }

    @Test
    void 등록도_인증하지_않으면_401이다() throws Exception {
        mockMvc.perform(post("/pass-products").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnauthorized());
    }

    // ===== getPassProducts =====

    @Test
    void 등록_최신순이고_같으면_id_내림차순이다() throws Exception {
        long old = insertProduct("오래된", 1, 1, 1, null, Instant.parse("2026-09-01T00:00:00Z"));
        Instant same = Instant.parse("2026-10-01T00:00:00Z");
        long first = insertProduct("같은1", 1, 1, 1, null, same);
        long second = insertProduct("같은2", 1, 1, 1, null, same);
        String token = adminToken();

        list(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].id").value(second))
                .andExpect(jsonPath("$.content[1].id").value(first))
                .andExpect(jsonPath("$.content[2].id").value(old))
                .andExpect(jsonPath("$.pageInfo.page").value(1))
                .andExpect(jsonPath("$.pageInfo.size").value(20))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(3));
    }

    @Test
    void 키워드는_이름에_대소문자_구분_없이_부분_일치한다() throws Exception {
        insertProduct("Pilates 10회권");
        insertProduct("요가 5회권");
        String token = adminToken();

        list(token, "keyword", "PILATES")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Pilates 10회권"));
        list(token, "keyword", "회권").andExpect(jsonPath("$.content.length()").value(2));
        list(token, "keyword", "  요가  ").andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void 공백뿐인_키워드는_필터를_적용하지_않고_와일드카드는_문자로_취급한다() throws Exception {
        insertProduct("10% 할인권");
        insertProduct("일반권");
        String token = adminToken();

        list(token, "keyword", "   ").andExpect(jsonPath("$.content.length()").value(2));
        list(token, "keyword", "%").andExpect(jsonPath("$.content.length()").value(1));
        list(token, "keyword", "_").andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void 목록_페이지네이션이_동작한다() throws Exception {
        for (int i = 0; i < 5; i++) {
            insertProduct("상품" + i, 1, 1, 1, null, Instant.parse("2026-10-01T00:00:00Z").plusSeconds(i));
        }
        String token = adminToken();

        list(token, "page", "2", "size", "2")
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("상품2"))
                .andExpect(jsonPath("$.pageInfo.totalPages").value(3));
    }

    @ParameterizedTest
    @CsvSource({"page,0", "size,0", "size,101", "page,abc"})
    void 목록의_잘못된_파라미터는_400이다(String key, String value) throws Exception {
        String token = adminToken();

        list(token, key, value)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 키워드가_100자를_넘으면_400이다() throws Exception {
        String token = adminToken();

        list(token, "keyword", "가".repeat(101)).andExpect(status().isBadRequest());
    }

    @Test
    void 목록은_관리자만_볼_수_있다() throws Exception {
        list(instructorToken()).andExpect(status().isForbidden());
        list(memberToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mockMvc.perform(get("/pass-products")).andExpect(status().isUnauthorized());
    }

    // ===== getPassProduct =====

    @Test
    void 상품_상세를_볼_수_있다() throws Exception {
        long id = insertProduct("필라테스", 300000, 10, 30, "설명", Instant.parse("2026-10-01T00:00:00Z"));
        String token = adminToken();

        detail(token, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("필라테스"))
                .andExpect(jsonPath("$.price").value(300000))
                .andExpect(jsonPath("$.totalCount").value(10))
                .andExpect(jsonPath("$.validityDays").value(30))
                .andExpect(jsonPath("$.description").value("설명"));
    }

    @Test
    void 없는_상품이면_404이고_숫자가_아니면_400이다() throws Exception {
        String token = adminToken();

        detail(token, 999999).andExpect(status().isNotFound());
        mockMvc.perform(get("/pass-products/abc").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 상세는_관리자만_볼_수_있다() throws Exception {
        long id = insertProduct("a");

        detail(memberToken(), id).andExpect(status().isForbidden());
        detail(instructorToken(), 999999).andExpect(status().isForbidden());
    }

    // ===== updatePassProduct =====

    @Test
    void 전달한_필드만_수정되고_나머지는_그대로이다() throws Exception {
        long id = insertProduct("옛 이름", 100000, 10, 30, "옛 설명", Instant.parse("2026-10-01T00:00:00Z"));
        String token = adminToken();

        update(token, id, "{\"name\":\"새 이름\",\"price\":200000}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passProduct.id").value(id))
                .andExpect(jsonPath("$.passProduct.name").value("새 이름"))
                .andExpect(jsonPath("$.passProduct.price").value(200000))
                .andExpect(jsonPath("$.passProduct.totalCount").value(10))
                .andExpect(jsonPath("$.passProduct.validityDays").value(30))
                .andExpect(jsonPath("$.passProduct.description").value("옛 설명"))
                .andExpect(jsonPath("$.affectedPassCount").value(0));
    }

    @Test
    void 횟수와_유효기간도_수정할_수_있다() throws Exception {
        long id = insertProduct("a");
        String token = adminToken();

        update(token, id, "{\"totalCount\":12,\"validityDays\":60}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passProduct.totalCount").value(12))
                .andExpect(jsonPath("$.passProduct.validityDays").value(60));
        assertThat(jdbc.queryForObject("SELECT total_count FROM pass_products WHERE id = ?", Integer.class, id))
                .isEqualTo(12);
    }

    @Test
    void description에_null을_보내면_설명이_지워진다() throws Exception {
        long id = insertProduct("a", 1, 1, 1, "설명", Instant.parse("2026-10-01T00:00:00Z"));
        String token = adminToken();

        update(token, id, "{\"description\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passProduct.description").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT description FROM pass_products WHERE id = ?", String.class, id))
                .isNull();
    }

    @Test
    void applyToIssued를_보내도_발급된_이용권이_없으면_영향받은_수는_0이다() throws Exception {
        long id = insertProduct("a");
        String token = adminToken();

        update(token, id, "{\"totalCount\":20,\"applyToIssued\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passProduct.totalCount").value(20))
                .andExpect(jsonPath("$.affectedPassCount").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"applyToIssued\":true}",
            "{\"name\":\"\"}",
            "{\"price\":-1}",
            "{\"totalCount\":0}",
            "{\"totalCount\":1001}",
            "{\"validityDays\":0}",
            "{\"validityDays\":3651}",
            "{\"name\":1,\"price\":\"x\"}",
            "not json"
    })
    void 수정_요청_값이_올바르지_않으면_400_INVALID_INPUT이다(String json) throws Exception {
        long id = insertProduct("a");
        String token = adminToken();

        update(token, id, json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 없는_상품이면_404이고_값_오류는_404보다_먼저다() throws Exception {
        String token = adminToken();

        update(token, 999999, "{\"name\":\"x\"}").andExpect(status().isNotFound());
        update(token, 999999, "{}").andExpect(status().isBadRequest());
    }

    @Test
    void 수정은_관리자만_할_수_있다() throws Exception {
        long id = insertProduct("a");

        update(memberToken(), id, "{\"name\":\"x\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        update(instructorToken(), 999999, "{}").andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT name FROM pass_products WHERE id = ?", String.class, id))
                .isEqualTo("a");
    }

    // ===== deletePassProduct =====

    @Test
    void 상품을_삭제하면_204이고_행이_지워진다() throws Exception {
        long id = insertProduct("a");
        long other = insertProduct("b");
        String token = adminToken();

        remove(token, id).andExpect(status().isNoContent()).andExpect(content().string(""));

        assertThat(productCount()).isEqualTo(1);
        detail(token, id).andExpect(status().isNotFound());
        detail(token, other).andExpect(status().isOk());
    }

    @Test
    void 없는_상품_삭제는_404이다() throws Exception {
        String token = adminToken();
        long id = insertProduct("a");
        remove(token, id).andExpect(status().isNoContent());

        remove(token, id).andExpect(status().isNotFound());
    }

    @Test
    void 삭제는_관리자만_할_수_있다() throws Exception {
        long id = insertProduct("a");

        remove(memberToken(), id).andExpect(status().isForbidden());
        remove(instructorToken(), 999999).andExpect(status().isForbidden());
        assertThat(productCount()).isEqualTo(1);
        mockMvc.perform(delete("/pass-products/" + id)).andExpect(status().isUnauthorized());
    }
}
