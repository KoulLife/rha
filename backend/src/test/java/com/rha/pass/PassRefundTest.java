package com.rha.pass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class PassRefundTest extends PassTestSupport {

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
        // 5일 전에 시작해 24일 뒤까지 쓰는 이용권. 한 번 예약해 잔여 9회이다.
        passId = insertPass(productId, memberId, instructorId, 300000, 10, 9, days(-5), days(24), "ACTIVE");
        adminToken = adminToken();
        adminId = adminId();
        pay(passId, 200000, days(-5), false);
    }

    private ResultActions refund(String token, long id, String json) throws Exception {
        return mockMvc.perform(post("/passes/" + id + "/refunds").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void pay(long id, long amount, LocalDate paidAt, boolean refunded) {
        jdbc.update("""
                INSERT INTO pass_payments (pass_id, method, amount, paid_at, refunded, recorded_by, created_at,
                                           updated_at)
                VALUES (?, 'CARD', ?, ?, ?, ?, now(), now())
                """, id, amount, Date.valueOf(paidAt), refunded, adminId());
    }

    private int lessonCount;

    private long insertLesson(Instant start, String status) {
        lessonCount++;
        long roomId = jdbc.queryForObject("""
                INSERT INTO rooms (name, capacity, created_at, updated_at) VALUES (?, 8, now(), now())
                RETURNING id
                """, Long.class, "룸" + lessonCount);
        long typeId = jdbc.queryForObject("""
                INSERT INTO lesson_types (code, name, capacity, active, created_at, updated_at)
                VALUES (?, '단체', 5, true, now(), now())
                RETURNING id
                """, Long.class, "TYPE" + lessonCount);
        return jdbc.queryForObject("""
                INSERT INTO lessons (title, lesson_type_id, instructor_id, room_id, start_at, end_at, capacity,
                                     status, created_at, updated_at)
                VALUES ('아침 필라테스', ?, ?, ?, ?, ?, 5, ?, now(), now())
                RETURNING id
                """, Long.class, typeId, instructorId, roomId, Timestamp.from(start),
                Timestamp.from(start.plusSeconds(3600)), status);
    }

    private long insertReservation(long lessonId, String status) {
        return jdbc.queryForObject("""
                INSERT INTO reservations (lesson_id, member_id, pass_id, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, now(), now())
                RETURNING id
                """, Long.class, lessonId, memberId, passId, status);
    }

    private Map<String, Object> reservation(long id) {
        return jdbc.queryForMap("SELECT status, canceled_by FROM reservations WHERE id = ?", id);
    }

    // ===== 환불 처리 =====

    @Test
    void 부분_환불하면_REFUNDED가_되고_미수금은_0_결제_상태는_PAID이다() throws Exception {
        refund(adminToken, passId, "{\"amount\":150000,\"memo\":\"고객 요청\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.refundedAmount").value(150000))
                .andExpect(jsonPath("$.refundedAt").value(today().toString()))
                .andExpect(jsonPath("$.refundMemo").value("고객 요청"))
                .andExpect(jsonPath("$.paidAmount").value(200000))
                .andExpect(jsonPath("$.unpaidAmount").value(0))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));

        assertThat(passStatus(passId)).isEqualTo("REFUNDED");
    }

    @Test
    void 환불하지_않은_이용권은_환불_금액이_0이다() throws Exception {
        detail(adminToken, passId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundedAmount").value(0))
                .andExpect(jsonPath("$.refundedAt").doesNotExist())
                .andExpect(jsonPath("$.unpaidAmount").value(100000))
                .andExpect(jsonPath("$.paymentStatus").value("PARTIAL"));
    }

    @Test
    void 진행_중과_예정된_일시정지를_해제하고_연장한_만료일을_되돌린다() throws Exception {
        createPause(adminToken, passId, "{\"startDate\":\"" + days(0) + "\",\"endDate\":\"" + days(1) + "\"}")
                .andExpect(status().isCreated());
        createPause(adminToken, passId, "{\"startDate\":\"" + days(3) + "\",\"endDate\":\"" + days(5) + "\"}")
                .andExpect(status().isCreated());
        assertThat(endDateOf(passId)).isEqualTo(days(29));
        assertThat(passStatus(passId)).isEqualTo("PAUSED");

        refund(adminToken, passId, "{\"amount\":200000}").andExpect(status().isOk());

        assertThat(endDateOf(passId)).isEqualTo(days(24));
        assertThat(passStatus(passId)).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForList("SELECT status FROM pass_pauses WHERE pass_id = ? ORDER BY id", String.class,
                passId)).containsExactly("CANCELED", "CANCELED");
    }

    @Test
    void 시작_전_수업의_예약은_취소되고_지난_수업의_예약은_그대로_둔다() throws Exception {
        long upcoming = insertReservation(insertLesson(Instant.now().plusSeconds(2 * 86400), "PUBLISHED"),
                "RESERVED");
        long past = insertReservation(insertLesson(Instant.now().minusSeconds(2 * 86400), "PUBLISHED"),
                "ATTENDED");

        refund(adminToken, passId, "{\"amount\":200000}").andExpect(status().isOk());

        assertThat(reservation(upcoming)).containsEntry("status", "CANCELED").containsEntry("canceled_by", "ADMIN");
        assertThat(reservation(past)).containsEntry("status", "ATTENDED");
        assertThat(remainingCountOf(passId)).isEqualTo(10);
    }

    @Test
    void 환불된_이용권은_만료일이_지나도_EXPIRED로_바뀌지_않는다() throws Exception {
        refund(adminToken, passId, "{\"amount\":200000}").andExpect(status().isOk());
        jdbc.update("UPDATE passes SET end_date = ? WHERE id = ?", Date.valueOf(days(-1)), passId);

        detail(adminToken, passId).andExpect(jsonPath("$.status").value("REFUNDED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM activity_logs WHERE action = 'PASS_EXPIRED'",
                Integer.class)).isZero();
    }

    @Test
    void 환불된_이용권으로는_예약할_수_없다() throws Exception {
        long lessonId = insertLesson(Instant.now().plusSeconds(86400), "PUBLISHED");
        refund(adminToken, passId, "{\"amount\":200000}").andExpect(status().isOk());

        mockMvc.perform(post("/reservations").header(HttpHeaders.AUTHORIZATION, "Bearer " + login("mem@example.com"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lessonId\":" + lessonId + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_AVAILABLE_PASS"));
    }

    @Test
    void 환불된_이용권은_수정_일시정지_결제_기록_재환불이_409_PASS_REFUNDED이다() throws Exception {
        refund(adminToken, passId, "{\"amount\":200000}").andExpect(status().isOk());

        updatePass(adminToken, passId, "{\"remainingCount\":5}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PASS_REFUNDED"));
        createPause(adminToken, passId, "{\"startDate\":\"" + days(1) + "\",\"endDate\":\"" + days(2) + "\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PASS_REFUNDED"));
        mockMvc.perform(post("/passes/" + passId + "/payments").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"CARD\",\"amount\":10000,\"paidAt\":\"" + today() + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PASS_REFUNDED"));
        refund(adminToken, passId, "{\"amount\":1}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PASS_REFUNDED"));
    }

    @Test
    void 결제_상태_필터에서_환불된_이용권은_PAID에만_포함된다() throws Exception {
        refund(adminToken, passId, "{\"amount\":200000}").andExpect(status().isOk());

        list(adminToken, "paymentStatus", "PARTIAL").andExpect(jsonPath("$.content.length()").value(0));
        list(adminToken, "paymentStatus", "PAID").andExpect(jsonPath("$.content[0].id").value(passId));
        list(adminToken, "status", "REFUNDED").andExpect(jsonPath("$.content[0].id").value(passId));
    }

    @Test
    void 환불을_활동_이력에_건수와_함께_남긴다() throws Exception {
        insertReservation(insertLesson(Instant.now().plusSeconds(86400), "PUBLISHED"), "RESERVED");

        refund(adminToken, passId, "{\"amount\":150000}").andExpect(status().isOk());

        List<Map<String, Object>> logs = jdbc.queryForList(
                "SELECT * FROM activity_logs WHERE action = 'PASS_REFUNDED'");
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0)).containsEntry("category", "PASS").containsEntry("target_type", "PASS");
        assertThat(((Number) logs.get(0).get("subject_member_id")).longValue()).isEqualTo(memberId);
        assertThat((String) logs.get(0).get("summary"))
                .isEqualTo("이용권 #" + passId + " 환불 (150,000원, 예약 1건 자동 취소)");
    }

    // ===== 거절 =====

    @Test
    void 환불할_수_있는_금액을_넘으면_400_INVALID_REFUND_AMOUNT이다() throws Exception {
        // 결제 기록 단위로 환불 체크된 기록(폐지 예정)은 이미 돌려준 것으로 보고 빼므로 환불 가능 금액은 200,000원이다.
        pay(passId, 50000, days(-1), true);

        refund(adminToken, passId, "{\"amount\":200001}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REFUND_AMOUNT"));
        assertThat(passStatus(passId)).isEqualTo("ACTIVE");
    }

    @Test
    void 결제_기록이_없으면_환불할_수_없다() throws Exception {
        long unpaid = insertPass(productId, memberId, instructorId, 300000, 10, 10, days(0), days(29), "ACTIVE");

        refund(adminToken, unpaid, "{\"amount\":1}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REFUND_AMOUNT"));
    }

    @Test
    void 환불_금액이_0이하이거나_없으면_400_INVALID_INPUT이다() throws Exception {
        refund(adminToken, passId, "{\"amount\":0}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        refund(adminToken, passId, "{}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 환불일이_미래이거나_첫_결제일보다_빠르면_400_INVALID_PAYMENT_DATE이다() throws Exception {
        refund(adminToken, passId, "{\"amount\":1000,\"refundedAt\":\"" + days(1) + "\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAYMENT_DATE"));
        refund(adminToken, passId, "{\"amount\":1000,\"refundedAt\":\"" + days(-6) + "\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAYMENT_DATE"));

        refund(adminToken, passId, "{\"amount\":1000,\"refundedAt\":\"" + days(-5) + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.refundedAt").value(days(-5).toString()));
    }

    @Test
    void 관리자가_아니면_403_없는_이용권이면_404이다() throws Exception {
        refund(login("mem@example.com"), passId, "{\"amount\":1000}").andExpect(status().isForbidden());
        refund(adminToken, passId + 999, "{\"amount\":1000}").andExpect(status().isNotFound());
    }
}
