package com.rha.lesson;

import static org.assertj.core.api.Assertions.assertThat;

import com.rha.ConcurrencyTestSupport;
import com.rha.activity.ActivityTime;
import com.rha.reservation.ReservationCreateRequest;
import com.rha.reservation.ReservationService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 수업 변경이 자동 공개·예약과 동시에 일어날 때 수업 상태와 예약 규칙이 어긋나지 않는지. */
class LessonConcurrencyTest extends ConcurrencyTestSupport {

    @Autowired
    LessonService lessonService;

    @Autowired
    ReservationService reservationService;

    @Test
    void 자동_공개와_수업_취소가_동시에_일어나도_취소된_수업이_되살아나지_않는다() throws Exception {
        long admin = insertAdmin();
        long instructor = insertInstructor("t@example.com");
        long type = insertLessonType();
        long room = insertRoom("A룸");
        List<Long> lessons = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            long lesson = insertLesson(type, instructor, room, at(3, 0).plusSeconds(3600L * i), "SCHEDULED");
            jdbc.update("UPDATE lessons SET publish_at = ? WHERE id = ?",
                    Timestamp.from(Instant.now().minusSeconds(60)), lesson);
            lessons.add(lesson);
        }

        List<Callable<?>> tasks = new ArrayList<>();
        tasks.add(() -> lessonService.publishDueLessons(Instant.now()));
        for (long lesson : lessons) {
            tasks.add(() -> lessonService.cancelLesson(admin, lesson, null));
        }
        List<Outcome> outcomes = runConcurrently(tasks);

        assertThat(successCount(outcomes)).isEqualTo(tasks.size());
        for (long lesson : lessons) {
            assertThat(lessonStatus(lesson)).isEqualTo("CANCELED");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM lessons WHERE canceled_at IS NULL", Integer.class))
                .isZero();
    }

    @Test
    void 수업_시간_변경과_예약이_동시에_들어와도_예약이_있는_수업의_시간이_바뀌지_않는다() throws Exception {
        openEveryDay();
        long admin = insertAdmin();
        long instructor = insertInstructor("t@example.com");
        long type = insertLessonType();
        long room = insertRoom("A룸");
        long product = insertProduct(10);

        for (int round = 0; round < 8; round++) {
            long member = insertActiveMember("m" + round + "@example.com");
            insertPass(product, member, instructor, 10, 10);
            Instant start = at(2 + round, 10);
            long lesson = insertLesson(type, instructor, room, start, "PUBLISHED");
            Instant newStart = start.plusSeconds(7200);

            List<Outcome> outcomes = runConcurrently(List.of(
                    () -> lessonService.updateLesson(admin, lesson, new LessonUpdateRequest(null, null, null,
                            newStart.toString(), newStart.plusSeconds(3600).toString())),
                    () -> reservationService.createReservation(member, new ReservationCreateRequest(lesson))));
            failureCodes(outcomes);

            // 둘 다 성공해도 되는 것은 시간을 바꾼 뒤에 예약한 경우뿐이다. 예약은 바뀐 시각의 수업을 예약했어야 한다.
            if (outcomes.get(0).succeeded() && outcomes.get(1).succeeded()) {
                assertThat(reservationSummaryOf(member)).as("바뀌기 전 시각에 예약된 수업의 시간이 바뀌었다")
                        .contains(ActivityTime.format(newStart));
            }
        }
    }

    private String reservationSummaryOf(long memberId) {
        return jdbc.queryForObject(
                "SELECT summary FROM activity_logs WHERE action = 'RESERVATION_CREATED' AND actor_id = ?",
                String.class, memberId);
    }
}
