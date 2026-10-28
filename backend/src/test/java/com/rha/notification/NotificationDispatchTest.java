package com.rha.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 수업 취소, 관리자의 예약 취소, 부재 신청·승인·반려가 알림을 만드는 동작. */
class NotificationDispatchTest extends ApiTestSupport {

    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    long roomId;
    long typeId;
    long instructorId;
    long productId;
    long memberA;
    long memberB;
    long passA;
    long passB;
    long lessonId;
    Instant lessonStart;
    String admin;

    @BeforeEach
    void prepare() throws Exception {
        roomId = jdbc.queryForObject("""
                INSERT INTO rooms (name, capacity, created_at, updated_at) VALUES ('A룸', 8, now(), now())
                RETURNING id
                """, Long.class);
        typeId = jdbc.queryForObject("""
                INSERT INTO lesson_types (code, name, capacity, active, created_at, updated_at)
                VALUES ('GROUP', '단체', 5, true, now(), now()) RETURNING id
                """, Long.class);
        instructorId = insertMember("i@example.com", "박강사", "INSTRUCTOR", "ACTIVE", BASE);
        productId = jdbc.queryForObject("""
                INSERT INTO pass_products (name, price, total_count, validity_days, created_at, updated_at)
                VALUES ('10회권', 1, 10, 30, now(), now()) RETURNING id
                """, Long.class);
        memberA = insertMember("a@example.com", "김회원", "MEMBER", "ACTIVE", BASE);
        memberB = insertMember("b@example.com", "이회원", "MEMBER", "ACTIVE", BASE);
        passA = insertPass(memberA);
        passB = insertPass(memberB);
        LocalDate lessonDate = LocalDate.now(SEOUL).plusDays(3);
        lessonStart = ZonedDateTime.of(lessonDate, LocalTime.of(10, 0), SEOUL).toInstant();
        lessonId = jdbc.queryForObject("""
                INSERT INTO lessons (title, lesson_type_id, instructor_id, room_id, start_at, end_at, capacity,
                                     status, created_at, updated_at)
                VALUES ('아침 필라테스', ?, ?, ?, ?, ?, 5, 'PUBLISHED', now(), now()) RETURNING id
                """, Long.class, typeId, instructorId, roomId, Timestamp.from(lessonStart),
                Timestamp.from(lessonStart.plusSeconds(3600)));
        admin = adminToken();
    }

    private long insertPass(long memberId) {
        return jdbc.queryForObject("""
                INSERT INTO passes (pass_product_id, member_id, instructor_id, price, total_count, remaining_count,
                                    start_date, end_date, status, created_at, updated_at)
                VALUES (?, ?, ?, 1, 10, 9, ?, ?, 'ACTIVE', now(), now()) RETURNING id
                """, Long.class, productId, memberId, instructorId, Date.valueOf(LocalDate.now(SEOUL).minusDays(5)),
                Date.valueOf(LocalDate.now(SEOUL).plusDays(30)));
    }

    private long reserve(long lesson, long member, long pass, String reservationStatus) {
        return jdbc.queryForObject("""
                INSERT INTO reservations (lesson_id, member_id, pass_id, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, now(), now()) RETURNING id
                """, Long.class, lesson, member, pass, reservationStatus);
    }

    private List<Map<String, Object>> notificationsOf(long recipient) {
        return jdbc.queryForList("SELECT * FROM notifications WHERE recipient_id = ? ORDER BY id", recipient);
    }

    private int notificationCount() {
        return jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class);
    }

    private String lessonTime() {
        ZonedDateTime local = lessonStart.atZone(SEOUL);
        return local.getMonthValue() + "월 " + local.getDayOfMonth() + "일 10:00";
    }

    private ResultActions send(String path, String token, String json) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    // ===== 수업 취소 =====

    @Test
    void 수업을_취소하면_예약한_회원마다_알림이_생긴다() throws Exception {
        reserve(lessonId, memberA, passA, "RESERVED");
        reserve(lessonId, memberB, passB, "RESERVED");

        send("/lessons/" + lessonId + "/cancellations", admin, "{\"reason\":\"강사 사정\"}")
                .andExpect(status().isOk());

        for (long member : new long[] {memberA, memberB}) {
            List<Map<String, Object>> rows = notificationsOf(member);
            assertThat(rows).hasSize(1);
            Map<String, Object> row = rows.get(0);
            assertThat(row.get("type")).isEqualTo("LESSON_CANCELED");
            assertThat(row.get("title")).isEqualTo("수업이 취소되었습니다");
            assertThat((String) row.get("message")).contains("아침 필라테스").contains(lessonTime())
                    .contains("이용권 횟수가 복구");
            assertThat(row.get("related_type")).isEqualTo("LESSON");
            assertThat(row.get("related_id")).isEqualTo(lessonId);
            assertThat(row.get("read_at")).isNull();
        }
    }

    @Test
    void 예약이_없거나_취소된_예약만_있으면_알림이_없다() throws Exception {
        reserve(lessonId, memberA, passA, "CANCELED");

        send("/lessons/" + lessonId + "/cancellations", admin, null).andExpect(status().isOk());

        assertThat(notificationCount()).isZero();
    }

    @Test
    void 다른_수업의_예약_회원에게는_알림이_가지_않는다() throws Exception {
        long other = jdbc.queryForObject("""
                INSERT INTO lessons (title, lesson_type_id, instructor_id, room_id, start_at, end_at, capacity,
                                     status, created_at, updated_at)
                VALUES ('다른 수업', ?, ?, ?, ?, ?, 5, 'PUBLISHED', now(), now()) RETURNING id
                """, Long.class, typeId, instructorId, roomId, Timestamp.from(lessonStart.plusSeconds(86400)),
                Timestamp.from(lessonStart.plusSeconds(86400 + 3600)));
        reserve(other, memberB, passB, "RESERVED");
        reserve(lessonId, memberA, passA, "RESERVED");

        send("/lessons/" + lessonId + "/cancellations", admin, null).andExpect(status().isOk());

        assertThat(notificationsOf(memberA)).hasSize(1);
        assertThat(notificationsOf(memberB)).isEmpty();
    }

    // ===== 관리자의 예약 취소 =====

    @Test
    void 관리자가_예약을_취소하면_회원에게_알림이_생긴다() throws Exception {
        long reservationId = reserve(lessonId, memberA, passA, "RESERVED");

        send("/reservations/" + reservationId + "/cancellations", admin, null).andExpect(status().isOk());

        List<Map<String, Object>> rows = notificationsOf(memberA);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("type")).isEqualTo("RESERVATION_CANCELED");
        assertThat(rows.get(0).get("title")).isEqualTo("예약이 취소되었습니다");
        assertThat((String) rows.get(0).get("message")).contains("아침 필라테스").contains(lessonTime());
        assertThat(rows.get(0).get("related_type")).isEqualTo("RESERVATION");
        assertThat(rows.get(0).get("related_id")).isEqualTo(reservationId);
    }

    @Test
    void 회원이_스스로_취소하면_활동_중인_모든_관리자에게_알림이_생긴다() throws Exception {
        long secondAdmin = insertMember("admin2@example.com", "두번째관리자", "ADMIN", "ACTIVE", BASE);
        long inactiveAdmin = insertMember("admin3@example.com", "비활성관리자", "ADMIN", "INACTIVE", BASE);
        long adminId = jdbc.queryForObject("SELECT id FROM members WHERE email = 'admin@example.com'", Long.class);
        long reservationId = reserve(lessonId, memberA, passA, "RESERVED");

        send("/reservations/" + reservationId + "/cancellations", login("a@example.com"), null)
                .andExpect(status().isOk());

        for (long recipient : new long[] {adminId, secondAdmin}) {
            List<Map<String, Object>> rows = notificationsOf(recipient);
            assertThat(rows).hasSize(1);
            Map<String, Object> row = rows.get(0);
            assertThat(row.get("type")).isEqualTo("RESERVATION_CANCELED_BY_MEMBER");
            assertThat(row.get("title")).isEqualTo("회원이 예약을 취소했습니다");
            assertThat((String) row.get("message")).contains("김회원").contains("아침 필라테스")
                    .contains(lessonTime());
            assertThat(row.get("related_type")).isEqualTo("LESSON");
            assertThat(row.get("related_id")).isEqualTo(lessonId);
            assertThat(row.get("read_at")).isNull();
        }
        assertThat(notificationsOf(inactiveAdmin)).isEmpty();
        assertThat(notificationsOf(memberA)).isEmpty();
        assertThat(notificationsOf(instructorId)).isEmpty();
    }

    @Test
    void 회원의_취소가_거절되면_관리자_알림도_생기지_않는다() throws Exception {
        long reservationId = reserve(lessonId, memberA, passA, "CANCELED");

        send("/reservations/" + reservationId + "/cancellations", login("a@example.com"), null)
                .andExpect(status().isConflict());

        assertThat(notificationCount()).isZero();
    }

    // ===== 부재 =====

    @Test
    void 부재를_신청하면_활동_중인_모든_관리자에게_알림이_생긴다() throws Exception {
        long secondAdmin = insertMember("admin2@example.com", "두번째관리자", "ADMIN", "ACTIVE", BASE);
        long inactiveAdmin = insertMember("admin3@example.com", "비활성관리자", "ADMIN", "INACTIVE", BASE);
        long adminId = jdbc.queryForObject("SELECT id FROM members WHERE email = 'admin@example.com'", Long.class);
        Instant start = Instant.now().plusSeconds(86400 * 5);

        String json = "{\"startAt\":\"" + start.truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
                + "\",\"endAt\":\"" + start.plusSeconds(86400).truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
                + "\",\"reason\":\"휴가\"}";
        send("/absences", login("i@example.com"), json).andExpect(status().isCreated());

        for (long recipient : new long[] {adminId, secondAdmin}) {
            List<Map<String, Object>> rows = notificationsOf(recipient);
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).get("type")).isEqualTo("ABSENCE_REQUESTED");
            assertThat(rows.get(0).get("title")).isEqualTo("부재 신청이 접수되었습니다");
            assertThat((String) rows.get(0).get("message")).contains("박강사");
            assertThat(rows.get(0).get("related_type")).isEqualTo("ABSENCE");
        }
        assertThat(notificationsOf(inactiveAdmin)).isEmpty();
        assertThat(notificationsOf(instructorId)).isEmpty();
    }

    private long insertAbsence(String absenceStatus) {
        Instant start = Instant.now().plusSeconds(86400 * 20);
        return jdbc.queryForObject("""
                INSERT INTO absences (instructor_id, start_at, end_at, status, created_at)
                VALUES (?, ?, ?, ?, now()) RETURNING id
                """, Long.class, instructorId, Timestamp.from(start), Timestamp.from(start.plusSeconds(86400)),
                absenceStatus);
    }

    @Test
    void 부재를_승인하면_신청한_강사에게_알림이_생긴다() throws Exception {
        long absenceId = insertAbsence("REQUESTED");

        send("/absences/" + absenceId + "/approvals", admin, null).andExpect(status().isOk());

        List<Map<String, Object>> rows = notificationsOf(instructorId);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("type")).isEqualTo("ABSENCE_APPROVED");
        assertThat(rows.get(0).get("title")).isEqualTo("부재가 승인되었습니다");
        assertThat(rows.get(0).get("related_type")).isEqualTo("ABSENCE");
        assertThat(rows.get(0).get("related_id")).isEqualTo(absenceId);
    }

    @Test
    void 부재를_반려하면_사유와_함께_강사에게_알림이_생긴다() throws Exception {
        long absenceId = insertAbsence("REQUESTED");

        send("/absences/" + absenceId + "/rejections", admin, "{\"reason\":\"수업이 많습니다\"}")
                .andExpect(status().isOk());

        List<Map<String, Object>> rows = notificationsOf(instructorId);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("type")).isEqualTo("ABSENCE_REJECTED");
        assertThat(rows.get(0).get("title")).isEqualTo("부재가 반려되었습니다");
        assertThat((String) rows.get(0).get("message")).contains("수업이 많습니다");
    }

    @Test
    void 사유_없이_반려해도_알림이_생긴다() throws Exception {
        long absenceId = insertAbsence("REQUESTED");

        send("/absences/" + absenceId + "/rejections", admin, null).andExpect(status().isOk());

        assertThat(notificationsOf(instructorId)).hasSize(1);
    }

    @Test
    void 처리가_실패하면_알림도_생기지_않는다() throws Exception {
        reserve(lessonId, memberA, passA, "RESERVED");
        Instant start = lessonStart.minusSeconds(3600);
        long absenceId = jdbc.queryForObject("""
                INSERT INTO absences (instructor_id, start_at, end_at, status, created_at)
                VALUES (?, ?, ?, 'REQUESTED', now()) RETURNING id
                """, Long.class, instructorId, Timestamp.from(start), Timestamp.from(start.plusSeconds(86400)));

        send("/absences/" + absenceId + "/approvals", admin, null).andExpect(status().isConflict());
        send("/absences/" + absenceId + "/approvals", login("i@example.com"), null)
                .andExpect(status().isForbidden());

        assertThat(notificationCount()).isZero();
    }
}
