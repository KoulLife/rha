package com.rha.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.rha.ConcurrencyTestSupport;
import com.rha.common.error.ErrorCode;
import com.rha.lesson.LessonService;
import com.rha.pass.PassPauseCreateRequest;
import com.rha.pass.PassService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 예약·취소가 다른 요청과 동시에 들어올 때 횟수와 상태가 어긋나지 않는지. */
class ReservationConcurrencyTest extends ConcurrencyTestSupport {

    @Autowired
    ReservationService reservationService;

    @Autowired
    LessonService lessonService;

    @Autowired
    PassService passService;

    @Test
    void 같은_예약을_동시에_취소해도_한_번만_취소되고_횟수는_한_번만_복구된다() throws Exception {
        long instructor = insertInstructor("t@example.com");
        long lesson = insertLesson(insertLessonType(), instructor, insertRoom("A룸"), at(3, 10), "PUBLISHED");
        long member = insertActiveMember("m@example.com");
        long pass = insertPass(insertProduct(10), member, instructor, 10, 5);
        long reservation = insertReservation(lesson, member, pass, "RESERVED");

        List<Callable<?>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> reservationService.cancelReservation(member, reservation, null));
        }
        List<Outcome> outcomes = runConcurrently(tasks);

        assertThat(successCount(outcomes)).isEqualTo(1);
        assertThat(failureCodes(outcomes)).containsOnly(ErrorCode.INVALID_RESERVATION_STATUS);
        assertThat(remaining(pass)).isEqualTo(6);
        assertThat(activityCount("RESERVATION_CANCELED")).isEqualTo(1);
    }

    @Test
    void 수업_취소와_예약이_동시에_들어와도_취소된_수업에_예약이_남지_않는다() throws Exception {
        long admin = insertAdmin();
        long instructor = insertInstructor("t@example.com");
        long type = insertLessonType();
        long room = insertRoom("A룸");
        long product = insertProduct(10);
        List<long[]> members = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            long member = insertActiveMember("m" + i + "@example.com");
            members.add(new long[] {member, insertPass(product, member, instructor, 10, 10)});
        }

        for (int round = 0; round < 5; round++) {
            long lesson = insertLesson(type, instructor, room, at(3 + round, 10), "PUBLISHED");
            List<Callable<?>> tasks = new ArrayList<>();
            tasks.add(() -> lessonService.cancelLesson(admin, lesson, null));
            for (long[] member : members) {
                tasks.add(() -> reservationService.createReservation(member[0],
                        new ReservationCreateRequest(lesson)));
            }
            List<Outcome> outcomes = runConcurrently(tasks);

            assertThat(outcomes.get(0).succeeded()).isTrue();
            assertThat(failureCodes(outcomes)).containsOnly(ErrorCode.LESSON_NOT_RESERVABLE);
            assertThat(reservedCountOf(lesson)).isZero();
        }
        for (long[] member : members) {
            assertThat(remaining(member[1])).isEqualTo(10);
        }
    }

    @Test
    void 예약과_일시정지_등록이_동시에_들어와도_정지_기간에_예약이_생기지_않고_연장도_사라지지_않는다() throws Exception {
        long admin = insertAdmin();
        long instructor = insertInstructor("t@example.com");
        long lesson = insertLesson(insertLessonType(), instructor, insertRoom("A룸"), at(3, 10), "PUBLISHED");
        long product = insertProduct(10);

        for (int round = 0; round < 8; round++) {
            long member = insertActiveMember("m" + round + "@example.com");
            long pass = insertPass(product, member, instructor, 10, 10);
            LocalDate endDate = endDateOf(pass);

            List<Outcome> outcomes = runConcurrently(List.of(
                    () -> reservationService.createReservation(member, new ReservationCreateRequest(lesson)),
                    () -> passService.createPassPause(admin, pass,
                            new PassPauseCreateRequest(days(2).toString(), days(4).toString(), null))));
            failureCodes(outcomes);

            boolean reserved = outcomes.get(0).succeeded();
            boolean paused = outcomes.get(1).succeeded();
            assertThat(reserved && paused).as("정지 기간의 수업이 예약되었다").isFalse();
            assertThat(endDateOf(pass)).isEqualTo(paused ? endDate.plusDays(3) : endDate);
            assertThat(remaining(pass)).isEqualTo(reserved ? 9 : 10);
        }
    }

    private LocalDate endDateOf(long passId) {
        return jdbc.queryForObject("SELECT end_date FROM passes WHERE id = ?", LocalDate.class, passId);
    }
}
