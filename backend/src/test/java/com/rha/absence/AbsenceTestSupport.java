package com.rha.absence;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.rha.ApiTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 부재 API 테스트의 데이터 준비 도우미. */
abstract class AbsenceTestSupport extends ApiTestSupport {

    static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    static Instant daysFromNow(int days) {
        return Instant.now().plusSeconds(86400L * days).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    }

    long insertInstructor(String email, String name) {
        return insertMember(email, name, "INSTRUCTOR", "ACTIVE", BASE);
    }

    long insertAbsence(long instructorId, Instant start, Instant end, String status) {
        return jdbc.queryForObject("""
                INSERT INTO absences (instructor_id, start_at, end_at, reason, status, created_at)
                VALUES (?, ?, ?, '휴가', ?, now())
                RETURNING id
                """, Long.class, instructorId, Timestamp.from(start), Timestamp.from(end), status);
    }

    String absenceStatus(long absenceId) {
        return jdbc.queryForObject("SELECT status FROM absences WHERE id = ?", String.class, absenceId);
    }

    int absenceCount() {
        return jdbc.queryForObject("SELECT count(*) FROM absences", Integer.class);
    }

    long insertRoom() {
        return jdbc.queryForObject("""
                INSERT INTO rooms (name, capacity, created_at, updated_at) VALUES ('A룸', 8, now(), now())
                RETURNING id
                """, Long.class);
    }

    long insertLessonType() {
        return jdbc.queryForObject("""
                INSERT INTO lesson_types (code, name, capacity, active, created_at, updated_at)
                VALUES ('GROUP_3', '단체', 3, true, now(), now())
                RETURNING id
                """, Long.class);
    }

    long insertLesson(long typeId, long instructorId, long roomId, Instant start, Instant end, String status) {
        return jdbc.queryForObject("""
                INSERT INTO lessons (title, lesson_type_id, instructor_id, room_id, start_at, end_at, capacity,
                                     status, created_at, updated_at)
                VALUES ('수업', ?, ?, ?, ?, ?, 3, ?, now(), now())
                RETURNING id
                """, Long.class, typeId, instructorId, roomId, Timestamp.from(start), Timestamp.from(end), status);
    }

    ResultActions createAbsence(String token, String json) throws Exception {
        return mockMvc.perform(post("/absences").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    ResultActions list(String token, String... keyValues) throws Exception {
        var request = get("/absences").header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        for (int i = 0; i < keyValues.length; i += 2) {
            request.param(keyValues[i], keyValues[i + 1]);
        }
        return mockMvc.perform(request);
    }

    ResultActions detail(String token, long id) throws Exception {
        return mockMvc.perform(get("/absences/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    ResultActions action(String token, long id, String path, String json) throws Exception {
        var request = post("/absences/" + id + "/" + path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    static String period(Instant start, Instant end) {
        return "{\"startAt\":\"" + start + "\",\"endAt\":\"" + end + "\",\"reason\":\"휴가\"}";
    }
}
