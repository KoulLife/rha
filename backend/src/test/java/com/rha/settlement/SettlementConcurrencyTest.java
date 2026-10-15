package com.rha.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.rha.ConcurrencyTestSupport;
import com.rha.common.error.ErrorCode;
import com.rha.reservation.AttendanceRequest;
import com.rha.reservation.ReservationService;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 정산 계산·상태 변경이 동시에 들어올 때 정산이 겹치거나 사라지거나 두 번 처리되지 않는지. */
class SettlementConcurrencyTest extends ConcurrencyTestSupport {

    @Autowired
    SettlementService settlementService;

    @Autowired
    ReservationService reservationService;

    @Test
    void 같은_기간의_정산을_동시에_계산해도_하나만_만들어진다() throws Exception {
        long admin = insertAdmin();
        long instructor = insertInstructor("t@example.com");

        List<Outcome> outcomes = runConcurrently(calculations(admin, instructor, 5));

        assertThat(successCount(outcomes)).isEqualTo(1);
        assertThat(failureCodes(outcomes)).containsOnly(ErrorCode.SETTLEMENT_PERIOD_OVERLAP);
        assertThat(settlementCount()).isEqualTo(1);
    }

    @Test
    void 수업이_있는_기간의_정산을_동시에_계산해도_서버_오류_없이_하나만_만들어진다() throws Exception {
        long admin = insertAdmin();
        long instructor = insertInstructor("t@example.com");
        long type = insertLessonType();
        setRate(instructor, type, 30000);
        long room = insertRoom("A룸");
        long member = insertActiveMember("m@example.com");
        long pass = insertPass(insertProduct(10), member, instructor, 10, 5);
        for (int i = 0; i < 3; i++) {
            insertReservation(insertLesson(type, instructor, room, at(-5 + i, 10), "PUBLISHED"), member, pass,
                    "ATTENDED");
        }

        List<Outcome> outcomes = runConcurrently(calculations(admin, instructor, 5));

        assertThat(successCount(outcomes)).isEqualTo(1);
        assertThat(failureCodes(outcomes)).containsOnly(ErrorCode.SETTLEMENT_PERIOD_OVERLAP);
        assertThat(settlementCount()).isEqualTo(1);
    }

    @Test
    void 같은_정산을_동시에_확정해도_한_번만_확정된다() throws Exception {
        long admin = insertAdmin();
        long settlement = insertSettlement(insertInstructor("t@example.com"), "REVIEW_REQUESTED");

        List<Callable<?>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> settlementService.confirm(admin, settlement));
        }
        List<Outcome> outcomes = runConcurrently(tasks);

        assertThat(successCount(outcomes)).isEqualTo(1);
        assertThat(failureCodes(outcomes)).containsOnly(ErrorCode.INVALID_SETTLEMENT_STATUS);
        assertThat(activityCount("SETTLEMENT_CONFIRMED")).isEqualTo(1);
    }

    @Test
    void 확정과_폐기가_동시에_들어와도_확정된_정산이_삭제되지_않는다() throws Exception {
        long admin = insertAdmin();
        long instructor = insertInstructor("t@example.com");

        for (int round = 0; round < 8; round++) {
            long settlement = insertSettlement(instructor, "REVIEW_REQUESTED");

            List<Outcome> outcomes = runConcurrently(List.of(
                    () -> settlementService.confirm(admin, settlement),
                    () -> {
                        settlementService.discard(admin, settlement);
                        return null;
                    }));

            assertThat(successCount(outcomes)).isEqualTo(1);
            assertThat(failureCodes(outcomes)).isSubsetOf(ErrorCode.INVALID_SETTLEMENT_STATUS,
                    ErrorCode.NOT_FOUND);
            if (outcomes.get(0).succeeded()) {
                assertThat(statusOf(settlement)).isEqualTo("CONFIRMED");
            } else {
                assertThat(exists(settlement)).isFalse();
            }
        }
    }

    @Test
    void 정산_계산과_출결_정정이_동시에_들어와도_정산된_출결과_실제_출결이_같다() throws Exception {
        long admin = insertAdmin();
        long type = insertLessonType();
        long room = insertRoom("A룸");
        long product = insertProduct(10);

        for (int round = 0; round < 8; round++) {
            long instructor = insertInstructor("t" + round + "@example.com");
            setRate(instructor, type, 30000);
            long member = insertActiveMember("m" + round + "@example.com");
            long pass = insertPass(product, member, instructor, 10, 5);
            long lesson = insertLesson(type, instructor, room, at(-3, 10), "PUBLISHED");
            long reservation = insertReservation(lesson, member, pass, "ATTENDED");

            List<Outcome> outcomes = runConcurrently(List.of(
                    () -> settlementService.createSettlement(admin, request(instructor)),
                    () -> reservationService.confirmAttendance(admin, reservation,
                            new AttendanceRequest("NO_SHOW"))));
            failureCodes(outcomes);

            String status = jdbc.queryForObject("SELECT status FROM reservations WHERE id = ?", String.class,
                    reservation);
            Integer noShowInItem = jdbc.queryForObject(
                    "SELECT coalesce(max(no_show_count), -1) FROM settlement_items WHERE lesson_id = ?",
                    Integer.class, lesson);
            if (noShowInItem >= 0) {
                assertThat(noShowInItem).as("정산된 노쇼 수와 실제 출결이 다르다")
                        .isEqualTo(status.equals("NO_SHOW") ? 1 : 0);
            }
        }
    }

    private List<Callable<?>> calculations(long admin, long instructor, int count) {
        List<Callable<?>> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tasks.add(() -> settlementService.createSettlement(admin, request(instructor)));
        }
        return tasks;
    }

    private static SettlementCreateRequest request(long instructor) {
        return new SettlementCreateRequest(instructor, days(-10).toString(), days(-1).toString());
    }

    private void setRate(long instructorId, long typeId, long price) {
        jdbc.update("INSERT INTO pay_rates (instructor_id, lesson_type_id, unit_price, updated_at) VALUES (?, ?, ?, now())",
                instructorId, typeId, price);
    }

    private long insertSettlement(long instructorId, String status) {
        return jdbc.queryForObject("""
                INSERT INTO settlements (instructor_id, period_start, period_end, status, total_amount,
                                         settled_lesson_count, excluded_lesson_count, created_at)
                VALUES (?, ?, ?, ?, 0, 0, 0, now()) RETURNING id
                """, Long.class, instructorId, Date.valueOf(days(-10)), Date.valueOf(days(-1)), status);
    }

    private int settlementCount() {
        return jdbc.queryForObject("SELECT count(*) FROM settlements", Integer.class);
    }

    private String statusOf(long settlementId) {
        return jdbc.queryForObject("SELECT status FROM settlements WHERE id = ?", String.class, settlementId);
    }

    private boolean exists(long settlementId) {
        return jdbc.queryForObject("SELECT count(*) FROM settlements WHERE id = ?", Integer.class, settlementId) > 0;
    }
}
