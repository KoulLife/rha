package com.rha.lesson;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.rha.ApiTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 수업 API 테스트의 데이터 준비 도우미. 시각은 센터 현지(Asia/Seoul) 기준으로 만든다. */
abstract class LessonTestSupport extends ApiTestSupport {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /** 오늘로부터 {@code days}일 뒤 현지 시각 {@code hour}시 정각의 UTC 시각. */
    static Instant at(int days, int hour) {
        return ZonedDateTime.of(LocalDate.now(SEOUL).plusDays(days), java.time.LocalTime.of(hour, 0), SEOUL).toInstant();
    }

    /** 현지 날짜 기준 요일 영문 약어(MON~SUN). */
    static String dayOf(int days) {
        String name = LocalDate.now(SEOUL).plusDays(days).getDayOfWeek().name();
        return name.substring(0, 3);
    }

    /** 모든 요일을 09:00~22:00 영업으로 설정한다. */
    void openEveryDay() {
        jdbc.update("UPDATE business_hours SET closed = false, open_time = '09:00', close_time = '22:00'");
    }

    void closeDay(String dayOfWeek) {
        jdbc.update("UPDATE business_hours SET closed = true, open_time = NULL, close_time = NULL WHERE day_of_week = ?",
                dayOfWeek);
    }

    long insertRoom(String name) {
        return jdbc.queryForObject("""
                INSERT INTO rooms (name, capacity, created_at, updated_at)
                VALUES (?, 8, now(), now())
                RETURNING id
                """, Long.class, name);
    }

    long insertLessonType(String code, String name, int capacity, boolean active) {
        return jdbc.queryForObject("""
                INSERT INTO lesson_types (code, name, capacity, active, created_at, updated_at)
                VALUES (?, ?, ?, ?, now(), now())
                RETURNING id
                """, Long.class, code, name, capacity, active);
    }

    /** 모든 요일 00:00~23:59에 수업이 가능한 강사. 가능시간이 필요한 테스트는 따로 설정한다. */
    long insertInstructor(String email, String name) {
        long id = insertMember(email, name, "INSTRUCTOR", "ACTIVE", Instant.parse("2026-10-01T00:00:00Z"));
        for (String day : new String[] {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"}) {
            insertAvailability(id, day, "00:00", "23:59");
        }
        return id;
    }

    void insertAvailability(long instructorId, String day, String start, String end) {
        jdbc.update("""
                INSERT INTO instructor_availabilities (instructor_id, day_of_week, start_time, end_time)
                VALUES (?, ?, ?::time, ?::time)
                """, instructorId, day, start, end);
    }

    void clearAvailability(long instructorId) {
        jdbc.update("DELETE FROM instructor_availabilities WHERE instructor_id = ?", instructorId);
    }

    long insertLesson(String title, long typeId, long instructorId, long roomId, Instant start, Instant end,
            String status) {
        return jdbc.queryForObject("""
                INSERT INTO lessons (title, lesson_type_id, instructor_id, room_id, start_at, end_at, capacity,
                                     status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 3, ?, now(), now())
                RETURNING id
                """, Long.class, title, typeId, instructorId, roomId, Timestamp.from(start), Timestamp.from(end),
                status);
    }

    /** 이미 시작한 수업. CHECK 제약은 start < end만 요구하므로 과거 시각도 넣을 수 있다. */
    long insertPastLesson(String title, long typeId, long instructorId, long roomId, String status) {
        Instant start = Instant.now().minusSeconds(3600 * 5);
        return insertLesson(title, typeId, instructorId, roomId, start, start.plusSeconds(3600), status);
    }

    String lessonStatus(long lessonId) {
        return jdbc.queryForObject("SELECT status FROM lessons WHERE id = ?", String.class, lessonId);
    }

    int lessonCount() {
        return jdbc.queryForObject("SELECT count(*) FROM lessons", Integer.class);
    }

    String body(String title, long typeId, long instructorId, long roomId, Instant start, Instant end) {
        return "{\"title\":\"" + title + "\",\"lessonTypeId\":" + typeId + ",\"instructorId\":" + instructorId
                + ",\"roomId\":" + roomId + ",\"startAt\":\"" + start + "\",\"endAt\":\"" + end + "\"}";
    }

    ResultActions createLesson(String token, String json) throws Exception {
        return mockMvc.perform(post("/lessons")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    ResultActions updateLesson(String token, long lessonId, String json) throws Exception {
        return mockMvc.perform(patch("/lessons/" + lessonId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    ResultActions changeStatus(String token, long lessonId, String json) throws Exception {
        return mockMvc.perform(put("/lessons/" + lessonId + "/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    ResultActions cancel(String token, long lessonId, String json) throws Exception {
        var request = post("/lessons/" + lessonId + "/cancellations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    ResultActions list(String token, String... keyValues) throws Exception {
        var request = get("/lessons").header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        for (int i = 0; i < keyValues.length; i += 2) {
            request.param(keyValues[i], keyValues[i + 1]);
        }
        return mockMvc.perform(request);
    }

    ResultActions detail(String token, long lessonId) throws Exception {
        return mockMvc.perform(get("/lessons/" + lessonId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}
