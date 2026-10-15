package com.rha.absence;

import static org.assertj.core.api.Assertions.assertThat;

import com.rha.ConcurrencyTestSupport;
import com.rha.common.error.ErrorCode;
import com.rha.lesson.LessonCreateRequest;
import com.rha.lesson.LessonService;
import com.rha.reservation.ReservationCreateRequest;
import com.rha.reservation.ReservationService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 부재 처리가 다른 처리·수업 개설·예약과 동시에 들어올 때 부재와 수업 상태가 어긋나지 않는지. */
class AbsenceConcurrencyTest extends ConcurrencyTestSupport {

    @Autowired
    AbsenceService absenceService;

    @Autowired
    LessonService lessonService;

    @Autowired
    ReservationService reservationService;

    @Test
    void 같은_부재를_동시에_승인해도_한_번만_승인된다() throws Exception {
        long admin = insertAdmin();
        long instructor = insertInstructor("t@example.com");
        long absence = insertAbsence(instructor, at(3, 9), at(3, 18), "REQUESTED");

        List<Callable<?>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> absenceService.approveAbsence(admin, absence));
        }
        List<Outcome> outcomes = runConcurrently(tasks);

        assertThat(successCount(outcomes)).isEqualTo(1);
        assertThat(failureCodes(outcomes)).containsOnly(ErrorCode.INVALID_ABSENCE_STATUS);
        assertThat(activityCount("ABSENCE_APPROVED")).isEqualTo(1);
    }

    @Test
    void 부재_승인과_신청_취소가_동시에_들어와도_하나만_처리되고_수업_상태가_결과와_맞는다() throws Exception {
        long admin = insertAdmin();
        long instructor = insertInstructor("t@example.com");
        long type = insertLessonType();
        long room = insertRoom("A룸");

        for (int round = 0; round < 8; round++) {
            long lesson = insertLesson(type, instructor, room, at(3 + round, 10), "CREATED");
            long absence = insertAbsence(instructor, at(3 + round, 9), at(3 + round, 18), "REQUESTED");

            List<Outcome> outcomes = runConcurrently(List.of(
                    () -> absenceService.approveAbsence(admin, absence),
                    () -> absenceService.cancelAbsence(instructor, absence)));

            assertThat(successCount(outcomes)).isEqualTo(1);
            assertThat(failureCodes(outcomes)).isSubsetOf(ErrorCode.INVALID_ABSENCE_STATUS,
                    ErrorCode.PERMISSION_DENIED);
            String status = absenceStatus(absence);
            assertThat(lessonStatus(lesson)).isEqualTo(status.equals("APPROVED") ? "CANCELED" : "CREATED");
        }
    }

    @Test
    void 부재_승인과_수업_개설이_동시에_들어와도_승인된_부재_기간에_수업이_남지_않는다() throws Exception {
        openEveryDay();
        long admin = insertAdmin();
        long instructor = insertInstructor("t@example.com");
        long type = insertLessonType();
        long room = insertRoom("A룸");

        for (int round = 0; round < 8; round++) {
            Instant start = at(3 + round, 10);
            long absence = insertAbsence(instructor, at(3 + round, 9), at(3 + round, 18), "REQUESTED");

            List<Outcome> outcomes = runConcurrently(List.of(
                    () -> absenceService.approveAbsence(admin, absence),
                    () -> lessonService.createLesson(admin, new LessonCreateRequest("수업", type, instructor, room,
                            start.toString(), start.plusSeconds(3600).toString()))));
            failureCodes(outcomes);

            if (absenceStatus(absence).equals("APPROVED")) {
                assertThat(activeLessonCountBetween(instructor, at(3 + round, 9), at(3 + round, 18)))
                        .as("승인된 부재 기간에 수업이 남았다").isZero();
            }
        }
    }

    @Test
    void 부재_승인과_예약이_동시에_들어와도_예약이_남은_수업이_자동_취소되지_않는다() throws Exception {
        long admin = insertAdmin();
        long instructor = insertInstructor("t@example.com");
        long type = insertLessonType();
        long room = insertRoom("A룸");
        long product = insertProduct(10);

        for (int round = 0; round < 8; round++) {
            long member = insertActiveMember("m" + round + "@example.com");
            insertPass(product, member, instructor, 10, 10);
            long lesson = insertLesson(type, instructor, room, at(3 + round, 10), "PUBLISHED");
            long absence = insertAbsence(instructor, at(3 + round, 9), at(3 + round, 18), "REQUESTED");

            List<Outcome> outcomes = runConcurrently(List.of(
                    () -> absenceService.approveAbsence(admin, absence),
                    () -> reservationService.createReservation(member, new ReservationCreateRequest(lesson))));
            failureCodes(outcomes);

            assertThat(lessonStatus(lesson).equals("CANCELED") && reservedCountOf(lesson) > 0)
                    .as("자동 취소된 수업에 예약이 남았다").isFalse();
        }
    }

    private String absenceStatus(long absenceId) {
        return jdbc.queryForObject("SELECT status FROM absences WHERE id = ?", String.class, absenceId);
    }

    private int activeLessonCountBetween(long instructorId, Instant start, Instant end) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM lessons
                WHERE instructor_id = ? AND status <> 'CANCELED' AND start_at < ? AND end_at > ?
                """, Integer.class, instructorId, java.sql.Timestamp.from(end), java.sql.Timestamp.from(start));
    }
}
