package com.rha.reservation;

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

/** 예약 API 테스트의 데이터 준비 도우미. 날짜와 시각은 센터 현지(Asia/Seoul) 기준이다. */
abstract class ReservationTestSupport extends ApiTestSupport {

    static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");
    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    static LocalDate today() {
        return LocalDate.now(SEOUL);
    }

    static LocalDate days(int offset) {
        return today().plusDays(offset);
    }

    /** 오늘로부터 {@code days}일 뒤 현지 시각 {@code hour}시 정각. */
    static Instant at(int days, int hour) {
        return ZonedDateTime.of(days(days), LocalTime.of(hour, 0), SEOUL).toInstant();
    }

    long insertRoom() {
        return jdbc.queryForObject("""
                INSERT INTO rooms (name, capacity, created_at, updated_at) VALUES ('A룸', 8, now(), now())
                RETURNING id
                """, Long.class);
    }

    long insertLessonType(int capacity) {
        return jdbc.queryForObject("""
                INSERT INTO lesson_types (code, name, capacity, active, created_at, updated_at)
                VALUES ('GROUP', '단체', ?, true, now(), now())
                RETURNING id
                """, Long.class, capacity);
    }

    long insertInstructor(String email, String name) {
        return insertMember(email, name, "INSTRUCTOR", "ACTIVE", BASE);
    }

    long insertMemberNamed(String email, String name) {
        return insertMember(email, name, "MEMBER", "ACTIVE", BASE);
    }

    long insertLesson(long typeId, long instructorId, long roomId, Instant start, int capacity, String status) {
        return jdbc.queryForObject("""
                INSERT INTO lessons (title, lesson_type_id, instructor_id, room_id, start_at, end_at, capacity,
                                     status, created_at, updated_at)
                VALUES ('수업', ?, ?, ?, ?, ?, ?, ?, now(), now())
                RETURNING id
                """, Long.class, typeId, instructorId, roomId, Timestamp.from(start),
                Timestamp.from(start.plusSeconds(3600)), capacity, status);
    }

    long insertProduct() {
        return jdbc.queryForObject("""
                INSERT INTO pass_products (name, price, total_count, validity_days, created_at, updated_at)
                VALUES ('필라테스 10회권', 300000, 10, 30, now(), now())
                RETURNING id
                """, Long.class);
    }

    long insertPass(long productId, long memberId, long instructorId, long price, int total, int remaining,
            LocalDate start, LocalDate end, String status) {
        return jdbc.queryForObject("""
                INSERT INTO passes (pass_product_id, member_id, instructor_id, price, total_count, remaining_count,
                                    start_date, end_date, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                RETURNING id
                """, Long.class, productId, memberId, instructorId, price, total, remaining, Date.valueOf(start),
                Date.valueOf(end), status);
    }

    long insertPause(long passId, LocalDate start, LocalDate end, String status, long createdBy) {
        return jdbc.queryForObject("""
                INSERT INTO pass_pauses (pass_id, start_date, end_date, status, created_by, created_at)
                VALUES (?, ?, ?, ?, ?, now())
                RETURNING id
                """, Long.class, passId, Date.valueOf(start), Date.valueOf(end), status, createdBy);
    }

    void pay(long passId, long amount, long recordedBy) {
        jdbc.update("""
                INSERT INTO pass_payments (pass_id, method, amount, paid_at, recorded_by, created_at, updated_at)
                VALUES (?, 'CARD', ?, ?, ?, now(), now())
                """, passId, amount, Date.valueOf(today()), recordedBy);
    }

    long insertReservation(long lessonId, long memberId, long passId, String status) {
        return jdbc.queryForObject("""
                INSERT INTO reservations (lesson_id, member_id, pass_id, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, now(), now())
                RETURNING id
                """, Long.class, lessonId, memberId, passId, status);
    }

    void setPolicy(int cancelDeadlineHours, boolean allowUnpaid) {
        jdbc.update("UPDATE reservation_policies SET cancel_deadline_hours = ?, allow_unpaid_reservation = ?",
                cancelDeadlineHours, allowUnpaid);
    }

    String reservationStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM reservations WHERE id = ?", String.class, id);
    }

    int remaining(long passId) {
        return jdbc.queryForObject("SELECT remaining_count FROM passes WHERE id = ?", Integer.class, passId);
    }

    int reservationCount() {
        return jdbc.queryForObject("SELECT count(*) FROM reservations", Integer.class);
    }

    long idOf(String email) {
        return jdbc.queryForObject("SELECT id FROM members WHERE email = ?", Long.class, email);
    }

    ResultActions reserve(String token, long lessonId) throws Exception {
        return mockMvc.perform(post("/reservations").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"lessonId\":" + lessonId + "}"));
    }

    ResultActions cancelReservation(String token, long id, String json) throws Exception {
        var request = post("/reservations/" + id + "/cancellations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    ResultActions attendance(String token, long id, String json) throws Exception {
        return mockMvc.perform(put("/reservations/" + id + "/attendance")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    ResultActions detail(String token, long id) throws Exception {
        return mockMvc.perform(get("/reservations/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    ResultActions listAt(String path, String token, String... keyValues) throws Exception {
        var request = get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        for (int i = 0; i < keyValues.length; i += 2) {
            request.param(keyValues[i], keyValues[i + 1]);
        }
        return mockMvc.perform(request);
    }

    ResultActions list(String token, String... keyValues) throws Exception {
        return listAt("/reservations", token, keyValues);
    }

    ResultActions mine(String token, String... keyValues) throws Exception {
        return listAt("/members/me/reservations", token, keyValues);
    }

    ResultActions ofLesson(String token, long lessonId, String... keyValues) throws Exception {
        return listAt("/lessons/" + lessonId + "/reservations", token, keyValues);
    }
}
