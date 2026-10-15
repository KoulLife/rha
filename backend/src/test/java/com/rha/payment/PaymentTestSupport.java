package com.rha.payment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.rha.ApiTestSupport;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 결제 기록 API 테스트의 데이터 준비 도우미. 날짜는 센터 현지(Asia/Seoul) 기준이다. */
abstract class PaymentTestSupport extends ApiTestSupport {

    static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");
    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    static LocalDate today() {
        return LocalDate.now(SEOUL);
    }

    static LocalDate days(int offset) {
        return today().plusDays(offset);
    }

    long insertProduct() {
        return jdbc.queryForObject("""
                INSERT INTO pass_products (name, price, total_count, validity_days, created_at, updated_at)
                VALUES ('필라테스 10회권', 300000, 10, 30, now(), now())
                RETURNING id
                """, Long.class);
    }

    long insertInstructor() {
        return insertMember("i@example.com", "박강사", "INSTRUCTOR", "ACTIVE", BASE);
    }

    long insertPass(long productId, long memberId, long instructorId, long price, LocalDate start, LocalDate end,
            String status) {
        return jdbc.queryForObject("""
                INSERT INTO passes (pass_product_id, member_id, instructor_id, price, total_count, remaining_count,
                                    start_date, end_date, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 10, 10, ?, ?, ?, now(), now())
                RETURNING id
                """, Long.class, productId, memberId, instructorId, price, Date.valueOf(start), Date.valueOf(end),
                status);
    }

    long insertPayment(long passId, String method, long amount, LocalDate paidAt, long recordedBy) {
        return jdbc.queryForObject("""
                INSERT INTO pass_payments (pass_id, method, amount, paid_at, recorded_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, now(), now())
                RETURNING id
                """, Long.class, passId, method, amount, Date.valueOf(paidAt), recordedBy);
    }

    void markRefunded(long paymentId, LocalDate refundedAt, String memo) {
        jdbc.update("UPDATE pass_payments SET refunded = true, refunded_at = ?, refund_memo = ? WHERE id = ?",
                Date.valueOf(refundedAt), memo, paymentId);
    }

    int paymentCount() {
        return jdbc.queryForObject("SELECT count(*) FROM pass_payments", Integer.class);
    }

    long adminId() {
        return jdbc.queryForObject("SELECT id FROM members WHERE email = 'admin@example.com'", Long.class);
    }

    static String paymentBody(String method, long amount, LocalDate paidAt) {
        return "{\"method\":\"" + method + "\",\"amount\":" + amount + ",\"paidAt\":\"" + paidAt + "\"}";
    }

    ResultActions record(String token, long passId, String json) throws Exception {
        return mockMvc.perform(post("/passes/" + passId + "/payments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    ResultActions correct(String token, long passId, long paymentId, String json) throws Exception {
        return mockMvc.perform(patch("/passes/" + passId + "/payments/" + paymentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    ResultActions remove(String token, long passId, long paymentId) throws Exception {
        return mockMvc.perform(delete("/passes/" + passId + "/payments/" + paymentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    ResultActions refund(String token, long passId, long paymentId, String json) throws Exception {
        return mockMvc.perform(put("/passes/" + passId + "/payments/" + paymentId + "/refund")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    ResultActions passPayments(String token, long passId, String... keyValues) throws Exception {
        return listAt("/passes/" + passId + "/payments", token, keyValues);
    }

    ResultActions allPayments(String token, String... keyValues) throws Exception {
        return listAt("/payments", token, keyValues);
    }

    ResultActions passDetail(String token, long passId) throws Exception {
        return mockMvc.perform(get("/passes/" + passId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    ResultActions listAt(String path, String token, String... keyValues) throws Exception {
        var request = get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        for (int i = 0; i < keyValues.length; i += 2) {
            request.param(keyValues[i], keyValues[i + 1]);
        }
        return mockMvc.perform(request);
    }
}
