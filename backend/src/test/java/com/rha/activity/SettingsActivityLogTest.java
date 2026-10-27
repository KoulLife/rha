package com.rha.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** 설정·마스터 데이터·회원 관리 API의 활동 이력 기록. */
class SettingsActivityLogTest extends ActivityLogTestSupport {

    String token;
    long adminId;

    @BeforeEach
    void prepare() throws Exception {
        token = adminToken();
        adminId = adminId();
        clearLogs();
    }

    private long idOf(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void assertAdminActor(Map<String, Object> log) {
        assertThat(((Number) log.get("actor_id")).longValue()).isEqualTo(adminId);
        assertThat(log.get("actor_role")).isEqualTo("ADMIN");
    }

    // ===== 수업 유형 =====

    @Test
    void 수업_유형_등록을_기록한다() throws Exception {
        long id = idOf(send(HttpMethod.POST, token, "/lesson-types",
                "{\"code\":\"YOGA\",\"name\":\"요가\",\"capacity\":10}").andExpect(status().isCreated()));

        Map<String, Object> log = onlyLog("LESSON_TYPE_CREATED");
        assertThat(log.get("category")).isEqualTo("LESSON_TYPE");
        assertAdminActor(log);
        assertThat(log.get("target_type")).isEqualTo("LESSON_TYPE");
        assertThat(((Number) log.get("target_id")).longValue()).isEqualTo(id);
        assertThat(log.get("subject_member_id")).isNull();
        assertThat((String) log.get("summary")).startsWith("수업 유형 '요가' 등록").contains("YOGA");
        assertThat(changesOf(log)).isEmpty();
    }

    @Test
    void 수업_유형_수정은_바뀐_필드만_기록한다() throws Exception {
        long id = idOf(send(HttpMethod.POST, token, "/lesson-types",
                "{\"code\":\"YOGA\",\"name\":\"요가\",\"capacity\":10}"));
        clearLogs();

        send(HttpMethod.PATCH, token, "/lesson-types/" + id, "{\"name\":\"하타 요가\",\"capacity\":10,\"active\":false}")
                .andExpect(status().isOk());

        Map<String, Object> log = onlyLog("LESSON_TYPE_UPDATED");
        assertThat(((Number) log.get("target_id")).longValue()).isEqualTo(id);
        assertThat(changesOf(log)).containsExactly(
                Map.of("field", "name", "before", "요가", "after", "하타 요가"),
                Map.of("field", "active", "before", "true", "after", "false"));
    }

    @Test
    void 거절된_요청은_기록하지_않는다() throws Exception {
        send(HttpMethod.POST, token, "/lesson-types", "{\"code\":\"YOGA\",\"name\":\"요가\",\"capacity\":10}");
        clearLogs();

        send(HttpMethod.POST, token, "/lesson-types", "{\"code\":\"YOGA\",\"name\":\"또 요가\",\"capacity\":10}")
                .andExpect(status().isConflict());
        send(HttpMethod.PATCH, token, "/lesson-types/999999", "{\"name\":\"없음\"}").andExpect(status().isNotFound());

        assertThat(logCount()).isZero();
    }

    // ===== 룸 =====

    @Test
    void 룸_등록_수정_삭제를_기록한다() throws Exception {
        long id = idOf(send(HttpMethod.POST, token, "/rooms", "{\"name\":\"A룸\",\"capacity\":8}")
                .andExpect(status().isCreated()));
        Map<String, Object> created = onlyLog("ROOM_CREATED");
        assertAdminActor(created);
        assertThat(created.get("category")).isEqualTo("ROOM");
        assertThat(created.get("target_type")).isEqualTo("ROOM");
        assertThat(((Number) created.get("target_id")).longValue()).isEqualTo(id);
        assertThat((String) created.get("summary")).contains("A룸");

        send(HttpMethod.PATCH, token, "/rooms/" + id, "{\"name\":\"B룸\",\"capacity\":12}").andExpect(status().isOk());
        Map<String, Object> updated = onlyLog("ROOM_UPDATED");
        assertThat(changesOf(updated)).containsExactly(
                Map.of("field", "name", "before", "A룸", "after", "B룸"),
                Map.of("field", "capacity", "before", "8", "after", "12"));

        send(HttpMethod.DELETE, token, "/rooms/" + id, null).andExpect(status().isNoContent());
        Map<String, Object> deleted = onlyLog("ROOM_DELETED");
        assertThat(((Number) deleted.get("target_id")).longValue()).isEqualTo(id);
        assertThat((String) deleted.get("summary")).contains("B룸");
    }

    // ===== 영업시간 =====

    @Test
    void 영업시간_설정은_바뀐_요일만_기록한다() throws Exception {
        send(HttpMethod.PUT, token, "/business-hours", hours("09:00", "22:00", "SAT")).andExpect(status().isOk());

        Map<String, Object> log = onlyLog("BUSINESS_HOURS_UPDATED");
        assertThat(log.get("category")).isEqualTo("BUSINESS_HOUR");
        assertAdminActor(log);
        assertThat(log.get("target_type")).isEqualTo("BUSINESS_HOURS");
        assertThat(log.get("target_id")).isNull();
        // 처음에는 모든 요일이 휴무이므로 SAT만 그대로 휴무이다.
        List<Map<String, Object>> changes = changesOf(log);
        assertThat(changes).hasSize(6);
        assertThat(changes.get(0)).isEqualTo(Map.of("field", "MON", "before", "휴무", "after", "09:00~22:00"));
        assertThat(changes).noneMatch(change -> "SAT".equals(change.get("field")));
    }

    private static String hours(String open, String close, String closedDay) {
        StringBuilder sb = new StringBuilder("{\"businessHours\":[");
        String[] days = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};
        for (int i = 0; i < days.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            if (days[i].equals(closedDay)) {
                sb.append("{\"dayOfWeek\":\"").append(days[i]).append("\",\"closed\":true}");
            } else {
                sb.append("{\"dayOfWeek\":\"").append(days[i]).append("\",\"closed\":false,\"openTime\":\"")
                        .append(open).append("\",\"closeTime\":\"").append(close).append("\"}");
            }
        }
        return sb.append("]}").toString();
    }

    // ===== 예약 정책 =====

    @Test
    void 예약_정책_설정을_기록한다() throws Exception {
        send(HttpMethod.PUT, token, "/reservation-policy",
                "{\"cancelDeadlineHours\":6,\"allowUnpaidReservation\":false}").andExpect(status().isOk());

        Map<String, Object> log = onlyLog("RESERVATION_POLICY_UPDATED");
        assertThat(log.get("category")).isEqualTo("POLICY");
        assertAdminActor(log);
        assertThat(log.get("target_type")).isEqualTo("RESERVATION_POLICY");
        assertThat(changesOf(log)).containsExactly(
                Map.of("field", "cancelDeadlineHours", "before", "24", "after", "6"),
                Map.of("field", "allowUnpaidReservation", "before", "true", "after", "false"));
    }

    // ===== 이용권 상품 =====

    @Test
    void 이용권_상품_등록_수정_삭제를_기록한다() throws Exception {
        long id = idOf(send(HttpMethod.POST, token, "/pass-products",
                "{\"name\":\"10회권\",\"price\":300000,\"totalCount\":10,\"validityDays\":30}")
                .andExpect(status().isCreated()));
        Map<String, Object> created = onlyLog("PASS_PRODUCT_CREATED");
        assertThat(created.get("category")).isEqualTo("PASS_PRODUCT");
        assertThat(created.get("target_type")).isEqualTo("PASS_PRODUCT");
        assertThat(((Number) created.get("target_id")).longValue()).isEqualTo(id);
        assertThat((String) created.get("summary")).contains("10회권");

        send(HttpMethod.PATCH, token, "/pass-products/" + id, "{\"price\":250000,\"totalCount\":10}")
                .andExpect(status().isOk());
        Map<String, Object> updated = onlyLog("PASS_PRODUCT_UPDATED");
        assertThat(changesOf(updated)).containsExactly(
                Map.of("field", "price", "before", "300000", "after", "250000"));

        send(HttpMethod.DELETE, token, "/pass-products/" + id, null).andExpect(status().isNoContent());
        assertThat(((Number) onlyLog("PASS_PRODUCT_DELETED").get("target_id")).longValue()).isEqualTo(id);
    }

    // ===== 수당 단가 =====

    @Test
    void 수당_단가_설정은_유형별_변경을_기록하고_강사를_영향받은_회원으로_남긴다() throws Exception {
        long instructorId = insertMember("i@example.com", "박강사", "INSTRUCTOR", "ACTIVE", T0);
        long typeId = jdbc.queryForObject("""
                INSERT INTO lesson_types (code, name, capacity, active, created_at, updated_at)
                VALUES ('GROUP', '단체', 5, true, now(), now()) RETURNING id
                """, Long.class);

        send(HttpMethod.PUT, token, "/members/" + instructorId + "/pay-rates",
                "{\"payRates\":[{\"lessonTypeId\":" + typeId + ",\"unitPrice\":30000}]}").andExpect(status().isOk());
        send(HttpMethod.PUT, token, "/members/" + instructorId + "/pay-rates",
                "{\"payRates\":[{\"lessonTypeId\":" + typeId + ",\"unitPrice\":35000}]}").andExpect(status().isOk());

        List<Map<String, Object>> logs = logs("PAY_RATES_UPDATED");
        assertThat(logs).hasSize(2);
        Map<String, Object> first = logs.get(0);
        assertThat(first.get("category")).isEqualTo("PAY_RATE");
        assertAdminActor(first);
        assertThat(first.get("target_type")).isEqualTo("MEMBER");
        assertThat(((Number) first.get("target_id")).longValue()).isEqualTo(instructorId);
        assertThat(((Number) first.get("subject_member_id")).longValue()).isEqualTo(instructorId);
        assertThat(changesOf(first)).containsExactly(
                changeMap("GROUP", null, "30000"));
        assertThat(changesOf(logs.get(1))).containsExactly(changeMap("GROUP", "30000", "35000"));
    }

    private static Map<String, Object> changeMap(String field, String before, String after) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("field", field);
        map.put("before", before);
        map.put("after", after);
        return map;
    }

    // ===== 권한 =====

    @Test
    void 권한_설정은_전후_권한_목록을_기록한다() throws Exception {
        long instructorId = insertMember("i@example.com", "박강사", "INSTRUCTOR", "ACTIVE", T0);

        send(HttpMethod.PUT, token, "/members/" + instructorId + "/permissions",
                "{\"permissions\":[\"MEMBER_READ\"]}").andExpect(status().isOk());

        Map<String, Object> log = onlyLog("PERMISSIONS_UPDATED");
        assertThat(log.get("category")).isEqualTo("PERMISSION");
        assertAdminActor(log);
        assertThat(log.get("target_type")).isEqualTo("MEMBER");
        assertThat(((Number) log.get("subject_member_id")).longValue()).isEqualTo(instructorId);
        assertThat(changesOf(log)).containsExactly(changeMap("permissions", "", "MEMBER_READ"));
    }

    // ===== 회원 =====

    @Test
    void 강사_등록을_기록한다() throws Exception {
        String json = "{\"email\":\"new@example.com\",\"name\":\"김강사\",\"phoneNumber\":\"01012345678\","
                + "\"birthDate\":\"1990-01-31\",\"role\":\"INSTRUCTOR\"}";

        long id = idOf(send(HttpMethod.POST, token, "/members", json).andExpect(status().isCreated()));

        Map<String, Object> log = onlyLog("MEMBER_CREATED");
        assertThat(log.get("category")).isEqualTo("MEMBER");
        assertAdminActor(log);
        assertThat(log.get("target_type")).isEqualTo("MEMBER");
        assertThat(((Number) log.get("target_id")).longValue()).isEqualTo(id);
        assertThat(((Number) log.get("subject_member_id")).longValue()).isEqualTo(id);
        assertThat((String) log.get("summary")).contains("김강사");
        assertThat((String) log.get("summary")).doesNotContain("01012345678").doesNotContain("new@example.com");
    }

    @Test
    void 내_정보_수정은_개인정보를_마스킹해_기록한다() throws Exception {
        insertMember("m@example.com", "이회원", "MEMBER", "ACTIVE", T0);
        long memberId = jdbc.queryForObject("SELECT id FROM members WHERE email = 'm@example.com'", Long.class);
        String memberToken = login("m@example.com");
        clearLogs();

        send(HttpMethod.PATCH, memberToken, "/members/me",
                "{\"name\":\"이새이름\",\"phoneNumber\":\"01099998888\"}").andExpect(status().isOk());

        Map<String, Object> log = onlyLog("MEMBER_PROFILE_UPDATED");
        assertThat(log.get("category")).isEqualTo("MEMBER");
        assertThat(((Number) log.get("actor_id")).longValue()).isEqualTo(memberId);
        assertThat(log.get("actor_role")).isEqualTo("MEMBER");
        assertThat(((Number) log.get("subject_member_id")).longValue()).isEqualTo(memberId);
        assertThat(changesOf(log)).containsExactly(
                changeMap("name", "이회원", "이새이름"),
                changeMap("phoneNumber", "010****5678", "010****8888"));
    }

    @Test
    void 회원_정보_수정은_바뀐_필드를_기록한다() throws Exception {
        long memberId = insertMember("a@example.com", "이회원", "MEMBER", "ACTIVE", T0);

        send(HttpMethod.PATCH, token, "/members/" + memberId, "{\"name\":\"이수정\",\"role\":\"INSTRUCTOR\"}")
                .andExpect(status().isOk());

        Map<String, Object> log = onlyLog("MEMBER_UPDATED");
        assertAdminActor(log);
        assertThat(((Number) log.get("subject_member_id")).longValue()).isEqualTo(memberId);
        assertThat(((Number) log.get("target_id")).longValue()).isEqualTo(memberId);
        assertThat(changesOf(log)).containsExactly(
                changeMap("name", "이회원", "이수정"),
                changeMap("role", "MEMBER", "INSTRUCTOR"));
    }

    @Test
    void 회원_상태_변경을_기록한다() throws Exception {
        long memberId = insertMember("a@example.com", "이회원", "MEMBER", "ACTIVE", T0);

        send(HttpMethod.PUT, token, "/members/" + memberId + "/status", "{\"status\":\"INACTIVE\"}")
                .andExpect(status().isOk());

        Map<String, Object> log = onlyLog("MEMBER_STATUS_CHANGED");
        assertAdminActor(log);
        assertThat(((Number) log.get("subject_member_id")).longValue()).isEqualTo(memberId);
        assertThat(changesOf(log)).containsExactly(changeMap("status", "ACTIVE", "INACTIVE"));
    }

    @Test
    void 이미_같은_상태로_바꾸는_요청은_기록하지_않는다() throws Exception {
        long memberId = insertMember("a@example.com", "이회원", "MEMBER", "ACTIVE", T0);

        send(HttpMethod.PUT, token, "/members/" + memberId + "/status", "{\"status\":\"ACTIVE\"}")
                .andExpect(status().isOk());

        assertThat(logs("MEMBER_STATUS_CHANGED")).isEmpty();
    }
}
