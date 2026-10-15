package com.rha.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.rha.lesson.LessonService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.ResultActions;

/** 수업, 강사(가능시간·퇴사·인계), 부재 API의 활동 이력 기록. */
class LessonActivityLogTest extends ActivityLogTestSupport {

    @Autowired
    LessonService lessonService;

    String token;
    long adminId;
    long roomId;
    long typeId;
    long instructorId;

    @BeforeEach
    void prepare() throws Exception {
        token = adminToken();
        adminId = adminId();
        openEveryDay();
        roomId = insertRoom("A룸");
        typeId = insertType("GROUP", "단체");
        instructorId = insertAvailableInstructor("i@example.com", "박강사");
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

    private String lessonBody(String title, Instant start) {
        return "{\"title\":\"" + title + "\",\"lessonTypeId\":" + typeId + ",\"instructorId\":" + instructorId
                + ",\"roomId\":" + roomId + ",\"startAt\":\"" + start + "\",\"endAt\":\""
                + start.plusSeconds(3600) + "\"}";
    }

    // ===== 수업 =====

    @Test
    void 수업_개설을_기록한다() throws Exception {
        long id = idOf(send(HttpMethod.POST, token, "/lessons", lessonBody("아침 필라테스", at(3, 10)))
                .andExpect(status().isCreated()));

        Map<String, Object> log = onlyLog("LESSON_CREATED");
        assertThat(log.get("category")).isEqualTo("LESSON");
        assertThat(((Number) log.get("actor_id")).longValue()).isEqualTo(adminId);
        assertThat(log.get("actor_role")).isEqualTo("ADMIN");
        assertThat(log.get("target_type")).isEqualTo("LESSON");
        assertThat(((Number) log.get("target_id")).longValue()).isEqualTo(id);
        assertThat(log.get("subject_member_id")).isNull();
        assertThat((String) log.get("summary")).startsWith("관리자가 ").contains("아침 필라테스").contains("개설");
    }

    @Test
    void 수업_수정은_바뀐_필드만_기록한다() throws Exception {
        long id = insertLesson("아침 필라테스", typeId, instructorId, roomId, at(3, 10), "CREATED");
        long otherRoom = insertRoom("B룸");

        send(HttpMethod.PATCH, token, "/lessons/" + id,
                "{\"title\":\"저녁 필라테스\",\"roomId\":" + otherRoom + ",\"startAt\":\"" + at(3, 18)
                        + "\",\"endAt\":\"" + at(3, 19) + "\"}").andExpect(status().isOk());

        Map<String, Object> log = onlyLog("LESSON_UPDATED");
        assertThat(((Number) log.get("target_id")).longValue()).isEqualTo(id);
        assertThat(changesOf(log)).containsExactly(
                change("title", "아침 필라테스", "저녁 필라테스"),
                change("startAt", at(3, 10).toString(), at(3, 18).toString()),
                change("endAt", at(3, 11).toString(), at(3, 19).toString()),
                change("roomId", String.valueOf(roomId), String.valueOf(otherRoom)));
    }

    @Test
    void 수업_공개_상태_변경을_기록한다() throws Exception {
        long id = insertLesson("수업", typeId, instructorId, roomId, at(3, 10), "CREATED");

        send(HttpMethod.PUT, token, "/lessons/" + id + "/status", "{\"status\":\"PUBLISHED\"}")
                .andExpect(status().isOk());

        Map<String, Object> log = onlyLog("LESSON_STATUS_CHANGED");
        assertThat(((Number) log.get("target_id")).longValue()).isEqualTo(id);
        assertThat(changesOf(log)).containsExactly(change("status", "CREATED", "PUBLISHED"));
    }

    @Test
    void 수업_취소를_기록하고_자동_취소된_예약_수를_요약에_남긴다() throws Exception {
        long memberId = insertMember("a@example.com", "이회원", "MEMBER", "ACTIVE", T0);
        long passId = insertPass(insertProduct(), memberId, instructorId);
        long id = insertLesson("수업", typeId, instructorId, roomId, at(3, 10), "PUBLISHED");
        insertReservation(id, memberId, passId, "RESERVED");

        send(HttpMethod.POST, token, "/lessons/" + id + "/cancellations", "{\"reason\":\"강사 사정\"}")
                .andExpect(status().isOk());

        Map<String, Object> log = onlyLog("LESSON_CANCELED");
        assertThat(((Number) log.get("target_id")).longValue()).isEqualTo(id);
        assertThat((String) log.get("summary")).contains("예약 1건");
        assertThat(changesOf(log)).containsExactly(
                change("status", "PUBLISHED", "CANCELED"),
                change("reason", null, "강사 사정"));
        // 자동 취소된 예약은 개별 이력을 남기지 않는다.
        assertThat(logs("RESERVATION_CANCELED")).isEmpty();
    }

    @Test
    void 거절된_수업_변경은_기록하지_않는다() throws Exception {
        long id = insertLesson("수업", typeId, instructorId, roomId, at(3, 10), "CANCELED");

        send(HttpMethod.PUT, token, "/lessons/" + id + "/status", "{\"status\":\"PUBLISHED\"}")
                .andExpect(status().isConflict());

        assertThat(logCount()).isZero();
    }

    @Test
    void 공개_예약_시각이_지나_자동_공개되면_행위자_없이_기록한다() throws Exception {
        long id = insertLesson("수업", typeId, instructorId, roomId, at(3, 10), "SCHEDULED");
        jdbc.update("UPDATE lessons SET publish_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(60)), id);

        int published = lessonService.publishDueLessons(Instant.now());

        assertThat(published).isEqualTo(1);
        Map<String, Object> log = onlyLog("LESSON_PUBLISHED");
        assertThat(log.get("actor_id")).isNull();
        assertThat(log.get("actor_role")).isNull();
        assertThat(log.get("target_type")).isEqualTo("LESSON");
        assertThat(((Number) log.get("target_id")).longValue()).isEqualTo(id);
        assertThat((String) log.get("summary")).contains("자동 공개");
        assertThat(changesOf(log)).containsExactly(change("status", "SCHEDULED", "PUBLISHED"));
    }

    // ===== 강사 =====

    @Test
    void 수업_인계를_기록한다() throws Exception {
        long toId = insertAvailableInstructor("to@example.com", "최강사");
        insertLesson("수업1", typeId, instructorId, roomId, at(3, 10), "PUBLISHED");
        insertLesson("수업2", typeId, instructorId, roomId, at(4, 10), "PUBLISHED");

        send(HttpMethod.POST, token, "/members/" + instructorId + "/lesson-reassignments",
                "{\"toInstructorId\":" + toId + "}").andExpect(status().isOk());

        Map<String, Object> log = onlyLog("LESSONS_REASSIGNED");
        assertThat(log.get("category")).isEqualTo("INSTRUCTOR");
        assertThat(((Number) log.get("actor_id")).longValue()).isEqualTo(adminId);
        assertThat(log.get("target_type")).isEqualTo("MEMBER");
        assertThat(((Number) log.get("target_id")).longValue()).isEqualTo(instructorId);
        assertThat(((Number) log.get("subject_member_id")).longValue()).isEqualTo(instructorId);
        assertThat((String) log.get("summary")).contains("박강사").contains("최강사").contains("2건");
        assertThat(changesOf(log)).containsExactly(
                change("instructorId", String.valueOf(instructorId), String.valueOf(toId)));
    }

    @Test
    void 수업_가능시간_설정은_바뀐_요일만_기록한다() throws Exception {
        long id = insertMember("n@example.com", "신강사", "INSTRUCTOR", "ACTIVE", T0);

        send(HttpMethod.PUT, token, "/members/" + id + "/availability",
                "{\"slots\":[{\"dayOfWeek\":\"MON\",\"startTime\":\"09:00\",\"endTime\":\"12:00\"},"
                        + "{\"dayOfWeek\":\"MON\",\"startTime\":\"14:00\",\"endTime\":\"18:00\"}]}")
                .andExpect(status().isOk());

        Map<String, Object> log = onlyLog("AVAILABILITY_UPDATED");
        assertThat(((Number) log.get("actor_id")).longValue()).isEqualTo(adminId);
        assertThat(log.get("target_type")).isEqualTo("MEMBER");
        assertThat(((Number) log.get("subject_member_id")).longValue()).isEqualTo(id);
        assertThat(changesOf(log)).containsExactly(
                change("MON", "없음", "09:00~12:00, 14:00~18:00"));
    }

    @Test
    void 퇴사일_등록_취소_처리를_기록한다() throws Exception {
        long id = insertMember("n@example.com", "신강사", "INSTRUCTOR", "ACTIVE", T0);
        String date = days(5).toString();

        send(HttpMethod.PUT, token, "/members/" + id + "/resignation", "{\"resignationDate\":\"" + date + "\"}")
                .andExpect(status().isOk());
        Map<String, Object> registered = onlyLog("RESIGNATION_REGISTERED");
        assertThat(((Number) registered.get("subject_member_id")).longValue()).isEqualTo(id);
        assertThat(changesOf(registered)).containsExactly(change("resignationDate", null, date));

        send(HttpMethod.DELETE, token, "/members/" + id + "/resignation", null).andExpect(status().isNoContent());
        assertThat(changesOf(onlyLog("RESIGNATION_CANCELED"))).containsExactly(change("resignationDate", date, null));

        jdbc.update("UPDATE members SET resignation_date = ? WHERE id = ?",
                java.sql.Date.valueOf(days(0)), id);
        send(HttpMethod.POST, token, "/members/" + id + "/resignation/completions", null)
                .andExpect(status().isOk());
        Map<String, Object> completed = onlyLog("RESIGNATION_COMPLETED");
        assertThat(((Number) completed.get("subject_member_id")).longValue()).isEqualTo(id);
        assertThat(changesOf(completed)).containsExactly(change("status", "ACTIVE", "RESIGNED"));
    }

    // ===== 부재 =====

    private long requestAbsence(String instructorToken, int day) throws Exception {
        return idOf(send(HttpMethod.POST, instructorToken, "/absences",
                "{\"startAt\":\"" + at(day, 9) + "\",\"endAt\":\"" + at(day, 13) + "\",\"reason\":\"개인 사정\"}")
                .andExpect(status().isCreated()));
    }

    @Test
    void 부재_신청_승인_반려_취소를_기록한다() throws Exception {
        insertMember("t@example.com", "강사", "INSTRUCTOR", "ACTIVE", T0);
        long absentId = jdbc.queryForObject("SELECT id FROM members WHERE email = 't@example.com'", Long.class);
        String instructorToken = login("t@example.com");
        clearLogs();

        long first = requestAbsence(instructorToken, 5);
        Map<String, Object> requested = onlyLog("ABSENCE_REQUESTED");
        assertThat(requested.get("category")).isEqualTo("ABSENCE");
        assertThat(((Number) requested.get("actor_id")).longValue()).isEqualTo(absentId);
        assertThat(requested.get("actor_role")).isEqualTo("INSTRUCTOR");
        assertThat(((Number) requested.get("subject_member_id")).longValue()).isEqualTo(absentId);
        assertThat(requested.get("target_type")).isEqualTo("ABSENCE");
        assertThat(((Number) requested.get("target_id")).longValue()).isEqualTo(first);

        send(HttpMethod.POST, token, "/absences/" + first + "/approvals", null).andExpect(status().isOk());
        Map<String, Object> approved = onlyLog("ABSENCE_APPROVED");
        assertThat(((Number) approved.get("actor_id")).longValue()).isEqualTo(adminId);
        assertThat(((Number) approved.get("subject_member_id")).longValue()).isEqualTo(absentId);
        assertThat(((Number) approved.get("target_id")).longValue()).isEqualTo(first);

        long second = requestAbsence(instructorToken, 8);
        send(HttpMethod.POST, token, "/absences/" + second + "/rejections", "{\"reason\":\"일정 겹침\"}")
                .andExpect(status().isOk());
        Map<String, Object> rejected = onlyLog("ABSENCE_REJECTED");
        assertThat(((Number) rejected.get("target_id")).longValue()).isEqualTo(second);
        assertThat((String) rejected.get("summary")).contains("일정 겹침");

        long third = requestAbsence(instructorToken, 11);
        send(HttpMethod.POST, instructorToken, "/absences/" + third + "/cancellations", null)
                .andExpect(status().isOk());
        Map<String, Object> canceled = onlyLog("ABSENCE_CANCELED");
        assertThat(((Number) canceled.get("actor_id")).longValue()).isEqualTo(absentId);
        assertThat(((Number) canceled.get("target_id")).longValue()).isEqualTo(third);
    }

    @Test
    void 부재_승인으로_자동_취소된_수업을_수업_이력으로_기록한다() throws Exception {
        insertMember("t@example.com", "강사", "INSTRUCTOR", "ACTIVE", T0);
        long absentId = jdbc.queryForObject("SELECT id FROM members WHERE email = 't@example.com'", Long.class);
        String instructorToken = login("t@example.com");
        long lessonId = insertLesson("수업", typeId, absentId, roomId, at(5, 10), "PUBLISHED");
        long absenceId = requestAbsence(instructorToken, 5);
        clearLogs();

        send(HttpMethod.POST, token, "/absences/" + absenceId + "/approvals", null).andExpect(status().isOk());

        Map<String, Object> canceled = onlyLog("LESSON_CANCELED");
        assertThat(((Number) canceled.get("actor_id")).longValue()).isEqualTo(adminId);
        assertThat(((Number) canceled.get("target_id")).longValue()).isEqualTo(lessonId);
        assertThat((String) canceled.get("summary")).contains("부재 승인");
        assertThat(changesOf(canceled)).containsExactly(
                change("status", "PUBLISHED", "CANCELED"),
                change("reason", null, "강사 부재 승인으로 자동 취소"));
        assertThat(logs("ABSENCE_APPROVED")).hasSize(1);
    }

    @Test
    void 퇴사_처리로_자동_취소된_부재를_기록한다() throws Exception {
        insertMember("t@example.com", "강사", "INSTRUCTOR", "ACTIVE", T0);
        long id = jdbc.queryForObject("SELECT id FROM members WHERE email = 't@example.com'", Long.class);
        String instructorToken = login("t@example.com");
        long absenceId = requestAbsence(instructorToken, 5);
        jdbc.update("UPDATE members SET resignation_date = ? WHERE id = ?", java.sql.Date.valueOf(days(0)), id);
        clearLogs();

        send(HttpMethod.POST, token, "/members/" + id + "/resignation/completions", null)
                .andExpect(status().isOk());

        Map<String, Object> canceled = onlyLog("ABSENCE_CANCELED");
        assertThat(((Number) canceled.get("actor_id")).longValue()).isEqualTo(adminId);
        assertThat(((Number) canceled.get("target_id")).longValue()).isEqualTo(absenceId);
        assertThat(((Number) canceled.get("subject_member_id")).longValue()).isEqualTo(id);
        assertThat(logs("RESIGNATION_COMPLETED")).hasSize(1);
    }
}
