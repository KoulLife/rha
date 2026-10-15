package com.rha.settlement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.rha.ApiTestSupport;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 정산 API 테스트의 데이터 준비 도우미. 날짜와 시각은 센터 현지(Asia/Seoul) 기준이다. */
abstract class SettlementTestSupport extends ApiTestSupport {

    static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");
    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    static LocalDate today() {
        return LocalDate.now(SEOUL);
    }

    static LocalDate days(int offset) {
        return today().plusDays(offset);
    }

    static Instant at(int days, int hour) {
        return ZonedDateTime.of(days(days), LocalTime.of(hour, 0), SEOUL).toInstant();
    }

    long insertRoom() {
        return jdbc.queryForObject("""
                INSERT INTO rooms (name, capacity, created_at, updated_at) VALUES ('A룸', 8, now(), now())
                RETURNING id
                """, Long.class);
    }

    long insertType(String code, String name) {
        return jdbc.queryForObject("""
                INSERT INTO lesson_types (code, name, capacity, active, created_at, updated_at)
                VALUES (?, ?, 5, true, now(), now()) RETURNING id
                """, Long.class, code, name);
    }

    long insertInstructor(String email, String name) {
        return insertMember(email, name, "INSTRUCTOR", "ACTIVE", BASE);
    }

    void setRate(long instructorId, long typeId, long price) {
        jdbc.update("""
                INSERT INTO pay_rates (instructor_id, lesson_type_id, unit_price, updated_at)
                VALUES (?, ?, ?, now())
                ON CONFLICT (instructor_id, lesson_type_id) DO UPDATE SET unit_price = EXCLUDED.unit_price
                """, instructorId, typeId, price);
    }

    long insertLesson(long typeId, long instructorId, long roomId, Instant start, String status) {
        return jdbc.queryForObject("""
                INSERT INTO lessons (title, lesson_type_id, instructor_id, room_id, start_at, end_at, capacity,
                                     status, created_at, updated_at)
                VALUES ('수업', ?, ?, ?, ?, ?, 5, ?, now(), now()) RETURNING id
                """, Long.class, typeId, instructorId, roomId, Timestamp.from(start),
                Timestamp.from(start.plusSeconds(3600)), status);
    }

    long insertPass(long productId, long memberId, long instructorId) {
        return jdbc.queryForObject("""
                INSERT INTO passes (pass_product_id, member_id, instructor_id, price, total_count, remaining_count,
                                    start_date, end_date, status, created_at, updated_at)
                VALUES (?, ?, ?, 1, 100, 50, ?, ?, 'ACTIVE', now(), now()) RETURNING id
                """, Long.class, productId, memberId, instructorId, Date.valueOf(days(-60)), Date.valueOf(days(60)));
    }

    long insertProduct() {
        return jdbc.queryForObject("""
                INSERT INTO pass_products (name, price, total_count, validity_days, created_at, updated_at)
                VALUES ('100회권', 1, 100, 90, now(), now()) RETURNING id
                """, Long.class);
    }

    long insertReservation(long lessonId, long memberId, long passId, String status) {
        return jdbc.queryForObject("""
                INSERT INTO reservations (lesson_id, member_id, pass_id, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, now(), now()) RETURNING id
                """, Long.class, lessonId, memberId, passId, status);
    }

    long insertSettlement(long instructorId, LocalDate start, LocalDate end, String status, long total,
            Instant createdAt) {
        return jdbc.queryForObject("""
                INSERT INTO settlements (instructor_id, period_start, period_end, status, total_amount,
                                         settled_lesson_count, excluded_lesson_count, created_at)
                VALUES (?, ?, ?, ?, ?, 0, 0, ?) RETURNING id
                """, Long.class, instructorId, Date.valueOf(start), Date.valueOf(end), status, total,
                Timestamp.from(createdAt));
    }

    void insertItem(long settlementId, long lessonId) {
        jdbc.update("""
                INSERT INTO settlement_items (settlement_id, lesson_id, attended_count, no_show_count, unit_price,
                                              amount, settled)
                VALUES (?, ?, 1, 0, 1000, 1000, true)
                """, settlementId, lessonId);
    }

    String settlementStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM settlements WHERE id = ?", String.class, id);
    }

    int settlementCount() {
        return jdbc.queryForObject("SELECT count(*) FROM settlements", Integer.class);
    }

    int itemCount() {
        return jdbc.queryForObject("SELECT count(*) FROM settlement_items", Integer.class);
    }

    static String body(long instructorId, LocalDate start, LocalDate end) {
        return "{\"instructorId\":" + instructorId + ",\"periodStart\":\"" + start + "\",\"periodEnd\":\"" + end
                + "\"}";
    }

    ResultActions calculate(String token, String json) throws Exception {
        return mockMvc.perform(post("/settlements").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    ResultActions detail(String token, long id) throws Exception {
        return mockMvc.perform(get("/settlements/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    ResultActions discard(String token, long id) throws Exception {
        return mockMvc.perform(delete("/settlements/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    ResultActions action(String token, long id, String path, String json) throws Exception {
        var request = post("/settlements/" + id + "/" + path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    ResultActions list(String token, String... keyValues) throws Exception {
        var request = get("/settlements").header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        for (int i = 0; i < keyValues.length; i += 2) {
            request.param(keyValues[i], keyValues[i + 1]);
        }
        return mockMvc.perform(request);
    }

    ResultActions attendance(String token, long reservationId, String json) throws Exception {
        return mockMvc.perform(put("/reservations/" + reservationId + "/attendance")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
