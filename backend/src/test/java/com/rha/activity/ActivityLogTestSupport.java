package com.rha.activity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.rha.ApiTestSupport;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 활동 이력 테스트의 데이터 준비와 조회 도우미. */
abstract class ActivityLogTestSupport extends ApiTestSupport {

    static final Instant T0 = Instant.parse("2026-10-05T00:00:00Z");

    long insertLog(Instant at, String category, String action, Long actorId, String actorRole, Long subjectId,
            String targetType, Long targetId, String summary, String changesJson) {
        return jdbc.queryForObject("""
                INSERT INTO activity_logs (occurred_at, category, action, actor_id, actor_role, subject_member_id,
                                           target_type, target_id, summary, changes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb) RETURNING id
                """, Long.class, Timestamp.from(at), category, action, actorId, actorRole, subjectId, targetType,
                targetId, summary, changesJson);
    }

    long insertLog(Instant at, String category, String action) {
        return insertLog(at, category, action, null, null, null, null, null, "요약", "[]");
    }

    /** 로그인으로 쌓인 이력을 비운다. 이력을 직접 넣어 조회를 검증하는 테스트가 토큰을 만든 뒤에 호출한다. */
    void clearLogs() {
        jdbc.update("DELETE FROM activity_logs");
    }

    List<Map<String, Object>> logs(String action) {
        return jdbc.queryForList("SELECT * FROM activity_logs WHERE action = ? ORDER BY id", action);
    }

    Map<String, Object> onlyLog(String action) {
        List<Map<String, Object>> found = logs(action);
        org.assertj.core.api.Assertions.assertThat(found).as("%s 이력", action).hasSize(1);
        return found.get(0);
    }

    int logCount() {
        return jdbc.queryForObject("SELECT count(*) FROM activity_logs", Integer.class);
    }

    ResultActions query(String token, String... keyValues) throws Exception {
        var request = get("/activity-logs");
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        for (int i = 0; i < keyValues.length; i += 2) {
            request.param(keyValues[i], keyValues[i + 1]);
        }
        return mockMvc.perform(request);
    }

    ResultActions catalog(String token) throws Exception {
        var request = get("/activity-log-actions");
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    /** 본문이 있으면 JSON으로 보낸다. */
    ResultActions send(HttpMethod method, String token, String path, String json) throws Exception {
        var builder = request(method, path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (json != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(builder);
    }

    /** 이력 한 건의 changes(JSONB)를 [{field, before, after}] 목록으로 읽는다. */
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> changesOf(Map<String, Object> log) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readValue(String.valueOf(log.get("changes")),
                List.class);
    }

    Long adminId() {
        return jdbc.queryForObject("SELECT id FROM members WHERE email = 'admin@example.com'", Long.class);
    }

    // ===== 업무 데이터 준비 (센터 현지 Asia/Seoul 기준) =====

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    static LocalDate days(int offset) {
        return LocalDate.now(SEOUL).plusDays(offset);
    }

    /** 오늘로부터 {@code days}일 뒤 현지 시각 {@code hour}시 정각의 UTC 시각. */
    static Instant at(int days, int hour) {
        return ZonedDateTime.of(days(days), LocalTime.of(hour, 0), SEOUL).toInstant();
    }

    void openEveryDay() {
        jdbc.update("UPDATE business_hours SET closed = false, open_time = '06:00', close_time = '23:00'");
    }

    long insertRoom(String name) {
        return jdbc.queryForObject("""
                INSERT INTO rooms (name, capacity, created_at, updated_at) VALUES (?, 8, now(), now())
                RETURNING id
                """, Long.class, name);
    }

    long insertType(String code, String name) {
        return jdbc.queryForObject("""
                INSERT INTO lesson_types (code, name, capacity, active, created_at, updated_at)
                VALUES (?, ?, 5, true, now(), now()) RETURNING id
                """, Long.class, code, name);
    }

    /** 모든 요일 00:00~23:59에 수업이 가능한 강사. */
    long insertAvailableInstructor(String email, String name) {
        long id = insertMember(email, name, "INSTRUCTOR", "ACTIVE", T0);
        for (String day : new String[] {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"}) {
            jdbc.update("""
                    INSERT INTO instructor_availabilities (instructor_id, day_of_week, start_time, end_time)
                    VALUES (?, ?, '00:00'::time, '23:59'::time)
                    """, id, day);
        }
        return id;
    }

    long insertLesson(String title, long typeId, long instructorId, long roomId, Instant start, String status) {
        return jdbc.queryForObject("""
                INSERT INTO lessons (title, lesson_type_id, instructor_id, room_id, start_at, end_at, capacity,
                                     status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 5, ?, now(), now()) RETURNING id
                """, Long.class, title, typeId, instructorId, roomId, Timestamp.from(start),
                Timestamp.from(start.plusSeconds(3600)), status);
    }

    long insertProduct() {
        return jdbc.queryForObject("""
                INSERT INTO pass_products (name, price, total_count, validity_days, created_at, updated_at)
                VALUES ('10회권', 300000, 10, 90, now(), now()) RETURNING id
                """, Long.class);
    }

    long insertPass(long productId, long memberId, long instructorId) {
        return jdbc.queryForObject("""
                INSERT INTO passes (pass_product_id, member_id, instructor_id, price, total_count, remaining_count,
                                    start_date, end_date, status, created_at, updated_at)
                VALUES (?, ?, ?, 300000, 10, 9, ?, ?, 'ACTIVE', now(), now()) RETURNING id
                """, Long.class, productId, memberId, instructorId, Date.valueOf(days(-30)), Date.valueOf(days(60)));
    }

    long insertReservation(long lessonId, long memberId, long passId, String status) {
        return jdbc.queryForObject("""
                INSERT INTO reservations (lesson_id, member_id, pass_id, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, now(), now()) RETURNING id
                """, Long.class, lessonId, memberId, passId, status);
    }
}
