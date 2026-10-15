package com.rha.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.rha.pass.PassService;
import java.sql.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.ResultActions;

/** 이용권, 결제, 예약, 출결, 정산 API의 활동 이력 기록. */
class PassAndSettlementActivityLogTest extends ActivityLogTestSupport {

    @Autowired
    PassService passService;

    String token;
    long adminId;
    long roomId;
    long typeId;
    long instructorId;
    long memberId;
    long productId;

    @BeforeEach
    void prepare() throws Exception {
        token = adminToken();
        adminId = adminId();
        roomId = insertRoom("A룸");
        typeId = insertType("GROUP", "단체");
        instructorId = insertAvailableInstructor("i@example.com", "박강사");
        memberId = insertMember("a@example.com", "이회원", "MEMBER", "ACTIVE", T0);
        productId = insertProduct();
        clearLogs();
    }

    private static Map<String, Object> change(String field, String before, String after) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("field", field);
        map.put("before", before);
        map.put("after", after);
        return map;
    }

    private long idOf(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static long num(Map<String, Object> log, String column) {
        return ((Number) log.get(column)).longValue();
    }

    // ===== 이용권 =====

    @Test
    void 이용권_발급을_기록한다() throws Exception {
        long id = idOf(send(HttpMethod.POST, token, "/passes", "{\"passProductId\":" + productId + ",\"memberId\":"
                + memberId + ",\"instructorId\":" + instructorId + ",\"startDate\":\"" + days(0) + "\"}")
                .andExpect(status().isCreated()));

        Map<String, Object> log = onlyLog("PASS_ISSUED");
        assertThat(log.get("category")).isEqualTo("PASS");
        assertThat(num(log, "actor_id")).isEqualTo(adminId);
        assertThat(log.get("actor_role")).isEqualTo("ADMIN");
        assertThat(log.get("target_type")).isEqualTo("PASS");
        assertThat(num(log, "target_id")).isEqualTo(id);
        assertThat(num(log, "subject_member_id")).isEqualTo(memberId);
        assertThat((String) log.get("summary")).contains("이회원").contains("10회권").contains("발급");
    }

    @Test
    void 이용권_수정은_바뀐_필드만_기록한다() throws Exception {
        long passId = insertPass(productId, memberId, instructorId);

        send(HttpMethod.PATCH, token, "/passes/" + passId, "{\"remainingCount\":5}").andExpect(status().isOk());

        Map<String, Object> log = onlyLog("PASS_UPDATED");
        assertThat(num(log, "target_id")).isEqualTo(passId);
        assertThat(num(log, "subject_member_id")).isEqualTo(memberId);
        assertThat(changesOf(log)).containsExactly(change("remainingCount", "9", "5"));
    }

    @Test
    void 일시정지_등록과_해제를_기록한다() throws Exception {
        long passId = insertPass(productId, memberId, instructorId);
        String endBefore = days(60).toString();

        long pauseId = idOf(send(HttpMethod.POST, token, "/passes/" + passId + "/pauses",
                "{\"startDate\":\"" + days(10) + "\",\"endDate\":\"" + days(14) + "\",\"reason\":\"여행\"}")
                .andExpect(status().isCreated()));

        Map<String, Object> paused = onlyLog("PASS_PAUSED");
        assertThat(num(paused, "actor_id")).isEqualTo(adminId);
        assertThat(paused.get("target_type")).isEqualTo("PASS_PAUSE");
        assertThat(num(paused, "target_id")).isEqualTo(pauseId);
        assertThat(num(paused, "subject_member_id")).isEqualTo(memberId);
        assertThat(changesOf(paused)).containsExactly(change("endDate", endBefore, days(65).toString()));

        send(HttpMethod.POST, token, "/passes/" + passId + "/pauses/" + pauseId + "/terminations", null)
                .andExpect(status().isOk());

        Map<String, Object> terminated = onlyLog("PASS_PAUSE_TERMINATED");
        assertThat(terminated.get("target_type")).isEqualTo("PASS_PAUSE");
        assertThat(num(terminated, "target_id")).isEqualTo(pauseId);
        assertThat(changesOf(terminated)).containsExactly(change("endDate", days(65).toString(), endBefore));
    }

    @Test
    void 이용권_만료는_행위자_없이_한_번만_기록한다() throws Exception {
        long passId = jdbc.queryForObject("""
                INSERT INTO passes (pass_product_id, member_id, instructor_id, price, total_count, remaining_count,
                                    start_date, end_date, status, created_at, updated_at)
                VALUES (?, ?, ?, 1000, 10, 3, ?, ?, 'ACTIVE', now(), now()) RETURNING id
                """, Long.class, productId, memberId, instructorId, Date.valueOf(days(-40)),
                Date.valueOf(days(-1)));

        passService.refreshStatuses(days(0));
        passService.refreshStatuses(days(0));

        Map<String, Object> log = onlyLog("PASS_EXPIRED");
        assertThat(log.get("actor_id")).isNull();
        assertThat(log.get("target_type")).isEqualTo("PASS");
        assertThat(num(log, "target_id")).isEqualTo(passId);
        assertThat(num(log, "subject_member_id")).isEqualTo(memberId);
        assertThat(changesOf(log)).containsExactly(change("status", "ACTIVE", "EXPIRED"));
    }

    // ===== 결제 =====

    private String paymentBody(long amount) {
        return "{\"method\":\"CARD\",\"amount\":" + amount + ",\"paidAt\":\"" + days(0) + "\"}";
    }

    @Test
    void 결제_기록_정정_삭제를_기록한다() throws Exception {
        long passId = insertPass(productId, memberId, instructorId);

        long paymentId = idOf(send(HttpMethod.POST, token, "/passes/" + passId + "/payments", paymentBody(100000))
                .andExpect(status().isCreated()));
        Map<String, Object> recorded = onlyLog("PAYMENT_RECORDED");
        assertThat(recorded.get("category")).isEqualTo("PAYMENT");
        assertThat(num(recorded, "actor_id")).isEqualTo(adminId);
        assertThat(recorded.get("target_type")).isEqualTo("PASS_PAYMENT");
        assertThat(num(recorded, "target_id")).isEqualTo(paymentId);
        assertThat(num(recorded, "subject_member_id")).isEqualTo(memberId);
        assertThat((String) recorded.get("summary")).contains("100,000원").contains("이용권 #" + passId);

        send(HttpMethod.PATCH, token, "/passes/" + passId + "/payments/" + paymentId,
                "{\"amount\":80000,\"memo\":\"정정\"}").andExpect(status().isOk());
        Map<String, Object> updated = onlyLog("PAYMENT_UPDATED");
        assertThat(num(updated, "target_id")).isEqualTo(paymentId);
        assertThat(changesOf(updated)).containsExactly(
                change("amount", "100000", "80000"),
                change("memo", null, "정정"));

        send(HttpMethod.DELETE, token, "/passes/" + passId + "/payments/" + paymentId, null)
                .andExpect(status().isNoContent());
        Map<String, Object> deleted = onlyLog("PAYMENT_DELETED");
        assertThat(num(deleted, "target_id")).isEqualTo(paymentId);
        assertThat((String) deleted.get("summary")).contains("80,000원");
        assertThat(changesOf(deleted)).containsExactly(change("amount", "80000", null));
    }

    @Test
    void 환불_체크와_해제를_기록한다() throws Exception {
        long passId = insertPass(productId, memberId, instructorId);
        long paymentId = idOf(send(HttpMethod.POST, token, "/passes/" + passId + "/payments", paymentBody(100000)));
        clearLogs();

        send(HttpMethod.PUT, token, "/passes/" + passId + "/payments/" + paymentId + "/refund",
                "{\"refunded\":true,\"memo\":\"고객 요청\"}").andExpect(status().isOk());
        Map<String, Object> checked = onlyLog("PAYMENT_REFUND_CHECKED");
        assertThat(num(checked, "subject_member_id")).isEqualTo(memberId);
        assertThat(changesOf(checked)).containsExactly(
                change("refunded", "false", "true"),
                change("refundedAt", null, days(0).toString()),
                change("refundMemo", null, "고객 요청"));

        send(HttpMethod.PUT, token, "/passes/" + passId + "/payments/" + paymentId + "/refund",
                "{\"refunded\":false}").andExpect(status().isOk());
        Map<String, Object> unchecked = onlyLog("PAYMENT_REFUND_UNCHECKED");
        assertThat(changesOf(unchecked)).containsExactly(
                change("refunded", "true", "false"),
                change("refundedAt", days(0).toString(), null),
                change("refundMemo", "고객 요청", null));
    }

    @Test
    void 거절된_결제_정정은_기록하지_않는다() throws Exception {
        long passId = insertPass(productId, memberId, instructorId);
        long paymentId = idOf(send(HttpMethod.POST, token, "/passes/" + passId + "/payments", paymentBody(100000)));
        send(HttpMethod.PUT, token, "/passes/" + passId + "/payments/" + paymentId + "/refund",
                "{\"refunded\":true}").andExpect(status().isOk());
        clearLogs();

        send(HttpMethod.PATCH, token, "/passes/" + passId + "/payments/" + paymentId, "{\"amount\":1000}")
                .andExpect(status().isConflict());
        send(HttpMethod.DELETE, token, "/passes/" + passId + "/payments/" + paymentId, null)
                .andExpect(status().isConflict());

        assertThat(logCount()).isZero();
    }

    // ===== 예약·출결 =====

    @Test
    void 예약_생성과_회원_취소를_기록한다() throws Exception {
        long passId = insertPass(productId, memberId, instructorId);
        long lessonId = insertLesson("아침 필라테스", typeId, instructorId, roomId, at(5, 10), "PUBLISHED");
        String memberToken = login("a@example.com");
        clearLogs();

        long id = idOf(send(HttpMethod.POST, memberToken, "/reservations", "{\"lessonId\":" + lessonId + "}")
                .andExpect(status().isCreated()));
        Map<String, Object> created = onlyLog("RESERVATION_CREATED");
        assertThat(created.get("category")).isEqualTo("RESERVATION");
        assertThat(num(created, "actor_id")).isEqualTo(memberId);
        assertThat(created.get("actor_role")).isEqualTo("MEMBER");
        assertThat(created.get("target_type")).isEqualTo("RESERVATION");
        assertThat(num(created, "target_id")).isEqualTo(id);
        assertThat(num(created, "subject_member_id")).isEqualTo(memberId);
        assertThat((String) created.get("summary")).contains("아침 필라테스").contains("이용권 #" + passId);

        send(HttpMethod.POST, memberToken, "/reservations/" + id + "/cancellations", "{\"reason\":\"일정 변경\"}")
                .andExpect(status().isOk());
        Map<String, Object> canceled = onlyLog("RESERVATION_CANCELED");
        assertThat(num(canceled, "actor_id")).isEqualTo(memberId);
        assertThat(num(canceled, "target_id")).isEqualTo(id);
        assertThat(changesOf(canceled)).containsExactly(
                change("status", "RESERVED", "CANCELED"),
                change("reason", null, "일정 변경"));
    }

    @Test
    void 관리자의_예약_취소는_관리자를_행위자로_기록한다() throws Exception {
        long passId = insertPass(productId, memberId, instructorId);
        long lessonId = insertLesson("수업", typeId, instructorId, roomId, at(5, 10), "PUBLISHED");
        long reservationId = insertReservation(lessonId, memberId, passId, "RESERVED");

        send(HttpMethod.POST, token, "/reservations/" + reservationId + "/cancellations", null)
                .andExpect(status().isOk());

        Map<String, Object> canceled = onlyLog("RESERVATION_CANCELED");
        assertThat(num(canceled, "actor_id")).isEqualTo(adminId);
        assertThat(canceled.get("actor_role")).isEqualTo("ADMIN");
        assertThat(num(canceled, "subject_member_id")).isEqualTo(memberId);
    }

    @Test
    void 출결_기록과_정정을_기록한다() throws Exception {
        long passId = insertPass(productId, memberId, instructorId);
        long lessonId = insertLesson("수업", typeId, instructorId, roomId, at(-1, 10), "PUBLISHED");
        long reservationId = insertReservation(lessonId, memberId, passId, "RESERVED");

        send(HttpMethod.PUT, token, "/reservations/" + reservationId + "/attendance", "{\"status\":\"ATTENDED\"}")
                .andExpect(status().isOk());
        send(HttpMethod.PUT, token, "/reservations/" + reservationId + "/attendance", "{\"status\":\"NO_SHOW\"}")
                .andExpect(status().isOk());

        var logs = logs("ATTENDANCE_RECORDED");
        assertThat(logs).hasSize(2);
        Map<String, Object> first = logs.get(0);
        assertThat(first.get("category")).isEqualTo("ATTENDANCE");
        assertThat(num(first, "actor_id")).isEqualTo(adminId);
        assertThat(first.get("target_type")).isEqualTo("RESERVATION");
        assertThat(num(first, "target_id")).isEqualTo(reservationId);
        assertThat(num(first, "subject_member_id")).isEqualTo(memberId);
        assertThat(changesOf(first)).containsExactly(change("status", "RESERVED", "ATTENDED"));
        assertThat(changesOf(logs.get(1))).containsExactly(change("status", "ATTENDED", "NO_SHOW"));
    }

    // ===== 정산 =====

    private long settleableLesson() {
        jdbc.update("""
                INSERT INTO pay_rates (instructor_id, lesson_type_id, unit_price, updated_at)
                VALUES (?, ?, 30000, now())
                """, instructorId, typeId);
        long passId = insertPass(productId, memberId, instructorId);
        long lessonId = insertLesson("수업", typeId, instructorId, roomId, at(-3, 10), "PUBLISHED");
        insertReservation(lessonId, memberId, passId, "ATTENDED");
        return lessonId;
    }

    private String settlementBody() {
        return "{\"instructorId\":" + instructorId + ",\"periodStart\":\"" + days(-5) + "\",\"periodEnd\":\""
                + days(-1) + "\"}";
    }

    @Test
    void 정산_계산을_기록한다() throws Exception {
        settleableLesson();

        long id = idOf(send(HttpMethod.POST, token, "/settlements", settlementBody())
                .andExpect(status().isCreated()));

        Map<String, Object> log = onlyLog("SETTLEMENT_CREATED");
        assertThat(log.get("category")).isEqualTo("SETTLEMENT");
        assertThat(num(log, "actor_id")).isEqualTo(adminId);
        assertThat(log.get("target_type")).isEqualTo("SETTLEMENT");
        assertThat(num(log, "target_id")).isEqualTo(id);
        assertThat(num(log, "subject_member_id")).isEqualTo(instructorId);
        assertThat((String) log.get("summary")).contains("박강사").contains("1건").contains("30,000원");
    }

    @Test
    void 정산_검토_요청_확정_지급을_기록한다() throws Exception {
        settleableLesson();
        long id = idOf(send(HttpMethod.POST, token, "/settlements", settlementBody()));
        clearLogs();

        send(HttpMethod.POST, token, "/settlements/" + id + "/review-requests", null).andExpect(status().isOk());
        assertThat(changesOf(onlyLog("SETTLEMENT_REVIEW_REQUESTED"))).containsExactly(
                change("status", "CALCULATED", "REVIEW_REQUESTED"));

        send(HttpMethod.POST, token, "/settlements/" + id + "/confirmations", null).andExpect(status().isOk());
        assertThat(changesOf(onlyLog("SETTLEMENT_CONFIRMED"))).containsExactly(
                change("status", "REVIEW_REQUESTED", "CONFIRMED"));

        send(HttpMethod.POST, token, "/settlements/" + id + "/payments",
                "{\"paidAt\":\"" + days(0) + "\",\"memo\":\"계좌 이체\"}").andExpect(status().isOk());
        Map<String, Object> paid = onlyLog("SETTLEMENT_PAID");
        assertThat(num(paid, "target_id")).isEqualTo(id);
        assertThat(num(paid, "subject_member_id")).isEqualTo(instructorId);
        assertThat(changesOf(paid)).containsExactly(
                change("status", "CONFIRMED", "PAID"),
                change("paidAt", null, days(0).toString()));
    }

    @Test
    void 정산_폐기를_기록하고_폐기_뒤에도_대상_ID가_남는다() throws Exception {
        settleableLesson();
        long id = idOf(send(HttpMethod.POST, token, "/settlements", settlementBody()));
        clearLogs();

        send(HttpMethod.DELETE, token, "/settlements/" + id, null).andExpect(status().isNoContent());

        Map<String, Object> log = onlyLog("SETTLEMENT_DISCARDED");
        assertThat(num(log, "actor_id")).isEqualTo(adminId);
        assertThat(log.get("target_type")).isEqualTo("SETTLEMENT");
        assertThat(num(log, "target_id")).isEqualTo(id);
        assertThat(num(log, "subject_member_id")).isEqualTo(instructorId);
        assertThat(changesOf(log)).containsExactly(change("status", "CALCULATED", null));
    }
}
