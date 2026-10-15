package com.rha.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** GET /activity-logs, GET /activity-log-actions. */
class ActivityLogQueryTest extends ActivityLogTestSupport {

    String token;
    long adminId;
    long instructorId;
    long memberId;

    @BeforeEach
    void prepare() throws Exception {
        token = adminToken();
        adminId = jdbc.queryForObject("SELECT id FROM members WHERE email = 'admin@example.com'", Long.class);
        instructorId = insertMember("i@example.com", "김강사", "INSTRUCTOR", "ACTIVE", T0);
        memberId = insertMember("a@example.com", "이회원", "MEMBER", "ACTIVE", T0);
        clearLogs();
    }

    // ===== 접근 제어 =====

    @Test
    void 인증_없이는_401이다() throws Exception {
        query(null).andExpect(status().isUnauthorized());
        catalog(null).andExpect(status().isUnauthorized());
    }

    @Test
    void 관리자가_아니면_403이다() throws Exception {
        String memberToken = login("a@example.com");
        String instructorToken = login("i@example.com");

        query(memberToken).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        query(instructorToken).andExpect(status().isForbidden());
        catalog(memberToken).andExpect(status().isForbidden());
        catalog(instructorToken).andExpect(status().isForbidden());
    }

    @Test
    void 비활성_관리자는_403이다() throws Exception {
        jdbc.update("UPDATE members SET status = 'INACTIVE' WHERE id = ?", adminId);

        query(token).andExpect(status().isForbidden());
    }

    @Test
    void 관리자_확인이_필터_형식_확인보다_먼저다() throws Exception {
        query(login("a@example.com"), "order", "UP").andExpect(status().isForbidden());
    }

    // ===== 목록 =====

    @Test
    void 이력이_없으면_빈_목록이다() throws Exception {
        query(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.pageInfo.page").value(1))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(0));
    }

    @Test
    void 응답에_행위자와_영향받은_회원과_변경_내역이_담긴다() throws Exception {
        long id = insertLog(T0, "LESSON", "LESSON_UPDATED", instructorId, "INSTRUCTOR", memberId, "LESSON", 15L,
                "김강사가 수업을 수정했다.", "[{\"field\":\"startAt\",\"before\":\"A\",\"after\":\"B\"}]");

        query(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].occurredAt").value("2026-10-05T00:00:00Z"))
                .andExpect(jsonPath("$.content[0].category").value("LESSON"))
                .andExpect(jsonPath("$.content[0].action").value("LESSON_UPDATED"))
                .andExpect(jsonPath("$.content[0].actionName").value("수업 수정"))
                .andExpect(jsonPath("$.content[0].actor.id").value(instructorId))
                .andExpect(jsonPath("$.content[0].actor.name").value("김강사"))
                .andExpect(jsonPath("$.content[0].actor.role").value("INSTRUCTOR"))
                .andExpect(jsonPath("$.content[0].subjectMember.id").value(memberId))
                .andExpect(jsonPath("$.content[0].subjectMember.name").value("이회원"))
                .andExpect(jsonPath("$.content[0].targetType").value("LESSON"))
                .andExpect(jsonPath("$.content[0].targetId").value(15))
                .andExpect(jsonPath("$.content[0].summary").value("김강사가 수업을 수정했다."))
                .andExpect(jsonPath("$.content[0].changes[0].field").value("startAt"))
                .andExpect(jsonPath("$.content[0].changes[0].before").value("A"))
                .andExpect(jsonPath("$.content[0].changes[0].after").value("B"));
    }

    @Test
    void 행위자와_대상이_없는_이력은_null이고_변경_내역은_빈_배열이다() throws Exception {
        insertLog(T0, "LESSON", "LESSON_PUBLISHED");

        query(token)
                .andExpect(jsonPath("$.content[0].actor").value(nullValue()))
                .andExpect(jsonPath("$.content[0].subjectMember").value(nullValue()))
                .andExpect(jsonPath("$.content[0].targetType").value(nullValue()))
                .andExpect(jsonPath("$.content[0].targetId").value(nullValue()))
                .andExpect(jsonPath("$.content[0].changes", hasSize(0)));
    }

    @Test
    void 행위자의_역할은_행동_당시_역할이다() throws Exception {
        insertLog(T0, "MEMBER", "MEMBER_UPDATED", memberId, "INSTRUCTOR", null, null, null, "요약", "[]");

        query(token).andExpect(jsonPath("$.content[0].actor.role").value("INSTRUCTOR"));
    }

    @Test
    void 알_수_없는_행위_코드도_코드_그대로_표시한다() throws Exception {
        insertLog(T0, "FUTURE", "FUTURE_THING");

        query(token)
                .andExpect(jsonPath("$.content[0].action").value("FUTURE_THING"))
                .andExpect(jsonPath("$.content[0].actionName").value("FUTURE_THING"));
    }

    // ===== 정렬과 페이징 =====

    @Test
    void 기본은_최신순이고_시각이_같으면_id_내림차순이다() throws Exception {
        long a = insertLog(T0, "LESSON", "LESSON_CREATED");
        long b = insertLog(T0.plusSeconds(60), "LESSON", "LESSON_CREATED");
        long c = insertLog(T0.plusSeconds(60), "LESSON", "LESSON_CREATED");

        String body = query(token).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        List<Integer> ids = JsonPath.read(body, "$.content[*].id");
        assertThat(ids).containsExactly((int) c, (int) b, (int) a);
    }

    @Test
    void order가_ASC이면_오래된_순이고_시각이_같으면_id_오름차순이다() throws Exception {
        long a = insertLog(T0, "LESSON", "LESSON_CREATED");
        long b = insertLog(T0.plusSeconds(60), "LESSON", "LESSON_CREATED");
        long c = insertLog(T0.plusSeconds(60), "LESSON", "LESSON_CREATED");

        String body = query(token, "order", "ASC").andReturn().getResponse().getContentAsString();

        List<Integer> ids = JsonPath.read(body, "$.content[*].id");
        assertThat(ids).containsExactly((int) a, (int) b, (int) c);
    }

    @Test
    void 페이지는_1부터_시작하고_pageInfo를_반환한다() throws Exception {
        for (int i = 0; i < 5; i++) {
            insertLog(T0.plusSeconds(i), "LESSON", "LESSON_CREATED");
        }

        query(token, "page", "2", "size", "2")
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.pageInfo.page").value(2))
                .andExpect(jsonPath("$.pageInfo.size").value(2))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(5))
                .andExpect(jsonPath("$.pageInfo.totalPages").value(3));
    }

    // ===== 필터 =====

    @Test
    void 카테고리로_거른다() throws Exception {
        insertLog(T0, "LESSON", "LESSON_CREATED");
        insertLog(T0, "PASS", "PASS_ISSUED");

        query(token, "category", "PASS")
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].action").value("PASS_ISSUED"));
    }

    @Test
    void 행위_코드로_거른다() throws Exception {
        insertLog(T0, "LESSON", "LESSON_CREATED");
        insertLog(T0, "LESSON", "LESSON_CANCELED");

        query(token, "action", "LESSON_CANCELED")
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].action").value("LESSON_CANCELED"));
    }

    @Test
    void 행위자_ID로_거른다() throws Exception {
        insertLog(T0, "LESSON", "LESSON_CREATED", instructorId, "INSTRUCTOR", null, null, null, "요약", "[]");
        insertLog(T0, "LESSON", "LESSON_CREATED", adminId, "ADMIN", null, null, null, "요약", "[]");

        query(token, "actorId", String.valueOf(instructorId))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].actor.id").value(instructorId));
    }

    @Test
    void 행위자_역할로_거른다() throws Exception {
        insertLog(T0, "LESSON", "LESSON_CREATED", instructorId, "INSTRUCTOR", null, null, null, "요약", "[]");
        insertLog(T0, "LESSON", "LESSON_CREATED", adminId, "ADMIN", null, null, null, "요약", "[]");

        query(token, "actorRole", "ADMIN")
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].actor.role").value("ADMIN"));
    }

    @Test
    void 영향받은_회원으로_거른다() throws Exception {
        insertLog(T0, "PASS", "PASS_ISSUED", adminId, "ADMIN", memberId, "PASS", 1L, "요약", "[]");
        insertLog(T0, "PASS", "PASS_ISSUED", adminId, "ADMIN", null, "PASS", 2L, "요약", "[]");

        query(token, "subjectMemberId", String.valueOf(memberId))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].targetId").value(1));
    }

    @Test
    void 대상_종류와_ID로_거른다() throws Exception {
        insertLog(T0, "PASS", "PASS_ISSUED", adminId, "ADMIN", null, "PASS", 7L, "요약", "[]");
        insertLog(T0, "PASS", "PASS_UPDATED", adminId, "ADMIN", null, "PASS", 7L, "요약", "[]");
        insertLog(T0, "PASS", "PASS_ISSUED", adminId, "ADMIN", null, "PASS", 8L, "요약", "[]");
        insertLog(T0, "LESSON", "LESSON_CREATED", adminId, "ADMIN", null, "LESSON", 7L, "요약", "[]");

        query(token, "targetType", "PASS", "targetId", "7").andExpect(jsonPath("$.content", hasSize(2)));
        query(token, "targetType", "PASS").andExpect(jsonPath("$.content", hasSize(3)));
    }

    @Test
    void 기간으로_거르며_시작은_포함하고_끝은_포함하지_않는다() throws Exception {
        insertLog(T0, "LESSON", "LESSON_CREATED");
        insertLog(T0.plusSeconds(3600), "LESSON", "LESSON_UPDATED");
        insertLog(T0.plusSeconds(7200), "LESSON", "LESSON_CANCELED");

        query(token, "from", "2026-10-05T00:00:00Z", "to", "2026-10-05T02:00:00Z", "order", "ASC")
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].action").value("LESSON_CREATED"))
                .andExpect(jsonPath("$.content[1].action").value("LESSON_UPDATED"));
    }

    @Test
    void 여러_필터를_함께_적용한다() throws Exception {
        insertLog(T0, "LESSON", "LESSON_CANCELED", instructorId, "INSTRUCTOR", null, "LESSON", 1L, "요약", "[]");
        insertLog(T0, "LESSON", "LESSON_CANCELED", adminId, "ADMIN", null, "LESSON", 1L, "요약", "[]");
        insertLog(T0, "LESSON", "LESSON_UPDATED", instructorId, "INSTRUCTOR", null, "LESSON", 1L, "요약", "[]");

        query(token, "category", "LESSON", "action", "LESSON_CANCELED", "actorId", String.valueOf(instructorId))
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void 알_수_없는_카테고리나_행위_코드는_400이_아니라_빈_결과이다() throws Exception {
        insertLog(T0, "LESSON", "LESSON_CREATED");

        query(token, "category", "NOPE").andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));
        query(token, "action", "NOPE").andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));
        query(token, "targetType", "NOPE").andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));
    }

    // ===== 400 =====

    @Test
    void from이_to보다_늦거나_같으면_400이다() throws Exception {
        query(token, "from", "2026-10-06T00:00:00Z", "to", "2026-10-05T00:00:00Z")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.invalidParams[0].field").value("from"));
        query(token, "from", "2026-10-05T00:00:00Z", "to", "2026-10-05T00:00:00Z")
                .andExpect(status().isBadRequest());
    }

    @Test
    void targetId만_있고_targetType이_없으면_400이다() throws Exception {
        query(token, "targetId", "7")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidParams[0].field").value("targetType"));
    }

    @Test
    void 형식이_맞지_않는_필터는_400이다() throws Exception {
        query(token, "actorRole", "BOSS").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidParams[*].field", hasItem("actorRole")));
        query(token, "order", "UP").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidParams[*].field", hasItem("order")));
        query(token, "from", "어제").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidParams[*].field", hasItem("from")));
        query(token, "to", "2026-10-05").andExpect(status().isBadRequest());
        query(token, "actorId", "abc").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidParams[*].field", hasItem("actorId")));
        query(token, "subjectMemberId", "x").andExpect(status().isBadRequest());
        query(token, "targetType", "PASS", "targetId", "x").andExpect(status().isBadRequest());
        query(token, "page", "0").andExpect(status().isBadRequest());
        query(token, "size", "101").andExpect(status().isBadRequest());
    }

    // ===== 카탈로그 =====

    @Test
    void 카탈로그는_카테고리별_행위_코드를_순서대로_반환한다() throws Exception {
        String body = catalog(token).andExpect(status().isOk())
                .andExpect(jsonPath("$.categories", hasSize(17)))
                .andExpect(jsonPath("$.categories[0].code").value("AUTH"))
                .andExpect(jsonPath("$.categories[0].name").value("인증"))
                .andExpect(jsonPath("$.categories[0].actions[0].code").value("SIGN_UP"))
                .andExpect(jsonPath("$.categories[0].actions[0].name").value("회원가입"))
                .andReturn().getResponse().getContentAsString();

        List<String> categories = JsonPath.read(body, "$.categories[*].code");
        assertThat(categories).containsExactly("AUTH", "MEMBER", "PERMISSION", "LESSON", "RESERVATION",
                "ATTENDANCE", "PASS", "PAYMENT", "PASS_PRODUCT", "LESSON_TYPE", "ROOM", "BUSINESS_HOUR", "POLICY",
                "PAY_RATE", "SETTLEMENT", "INSTRUCTOR", "ABSENCE");
        List<String> lessonActions = JsonPath.read(body, "$.categories[?(@.code=='LESSON')].actions[*].code");
        assertThat(lessonActions).containsExactly("LESSON_CREATED", "LESSON_UPDATED", "LESSON_STATUS_CHANGED",
                "LESSON_CANCELED", "LESSON_PUBLISHED");
        List<String> allActions = JsonPath.read(body, "$.categories[*].actions[*].code");
        assertThat(allActions).hasSize(55).doesNotHaveDuplicates();
    }

    @Test
    void 카탈로그의_행위_이름은_이력의_actionName과_같다() throws Exception {
        insertLog(Instant.now(), "LESSON", "LESSON_CANCELED");

        query(token).andExpect(jsonPath("$.content[0].actionName").value("수업 취소"));
        catalog(token).andExpect(jsonPath("$.categories[?(@.code=='LESSON')].actions[?(@.code=='LESSON_CANCELED')].name")
                .value(hasItem("수업 취소")));
    }
}
