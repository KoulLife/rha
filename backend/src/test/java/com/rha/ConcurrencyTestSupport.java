package com.rha;

import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 같은 데이터에 동시에 들어오는 요청을 재현하는 테스트의 도우미. 서비스를 여러 스레드에서 한꺼번에 출발시킨다.
 * 날짜와 시각은 센터 현지(Asia/Seoul) 기준이다.
 */
public abstract class ConcurrencyTestSupport extends ApiTestSupport {

    protected static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    protected static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    /** 작업 하나의 결과. 성공했으면 {@code error}가 null이다. */
    protected record Outcome(Object value, Throwable error) {

        public boolean succeeded() {
            return error == null;
        }

        public ErrorCode errorCode() {
            return error instanceof ApiException apiException ? apiException.errorCode() : null;
        }
    }

    /** 모든 작업을 같은 순간에 출발시키고, 끝난 순서와 무관하게 넘긴 순서대로 결과를 돌려준다. */
    protected List<Outcome> runConcurrently(List<Callable<?>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (Callable<?> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return new Outcome(task.call(), null);
                    } catch (Throwable e) {
                        return new Outcome(null, e);
                    }
                }));
            }
            ready.await();
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    protected static long successCount(List<Outcome> outcomes) {
        return outcomes.stream().filter(Outcome::succeeded).count();
    }

    /** 실패한 작업은 모두 예상한 업무 오류(ApiException)여야 한다. 처리되지 않은 예외(500)가 섞이면 실패다. */
    protected static List<ErrorCode> failureCodes(List<Outcome> outcomes) {
        List<ErrorCode> codes = new ArrayList<>();
        for (Outcome outcome : outcomes) {
            if (!outcome.succeeded()) {
                if (outcome.errorCode() == null) {
                    throw new AssertionError("업무 오류가 아닌 예외가 발생했다", outcome.error());
                }
                codes.add(outcome.errorCode());
            }
        }
        return codes;
    }

    // ===== 시각 =====

    protected static LocalDate days(int offset) {
        return LocalDate.now(SEOUL).plusDays(offset);
    }

    /** 오늘로부터 {@code days}일 뒤 현지 시각 {@code hour}시 정각. */
    protected static Instant at(int days, int hour) {
        return ZonedDateTime.of(days(days), LocalTime.of(hour, 0), SEOUL).toInstant();
    }

    // ===== 데이터 준비 =====

    protected long insertAdmin() {
        return insertMember("admin@example.com", "관리자", "ADMIN", "ACTIVE", BASE);
    }

    protected long insertActiveMember(String email) {
        return insertMember(email, "회원", "MEMBER", "ACTIVE", BASE);
    }

    /** 모든 요일 00:00~23:59에 수업이 가능한 강사. */
    protected long insertInstructor(String email) {
        long id = insertMember(email, "강사", "INSTRUCTOR", "ACTIVE", BASE);
        for (String day : new String[] {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"}) {
            jdbc.update("""
                    INSERT INTO instructor_availabilities (instructor_id, day_of_week, start_time, end_time)
                    VALUES (?, ?, '00:00'::time, '23:59'::time)
                    """, id, day);
        }
        return id;
    }

    protected void openEveryDay() {
        jdbc.update("UPDATE business_hours SET closed = false, open_time = '00:00', close_time = '23:59'");
    }

    protected long insertRoom(String name) {
        return jdbc.queryForObject("""
                INSERT INTO rooms (name, capacity, created_at, updated_at) VALUES (?, 30, now(), now())
                RETURNING id
                """, Long.class, name);
    }

    protected long insertLessonType() {
        return jdbc.queryForObject("""
                INSERT INTO lesson_types (code, name, capacity, active, created_at, updated_at)
                VALUES ('GROUP', '단체', 30, true, now(), now())
                RETURNING id
                """, Long.class);
    }

    protected long insertLesson(long typeId, long instructorId, long roomId, Instant start, String status) {
        return jdbc.queryForObject("""
                INSERT INTO lessons (title, lesson_type_id, instructor_id, room_id, start_at, end_at, capacity,
                                     status, created_at, updated_at)
                VALUES ('수업', ?, ?, ?, ?, ?, 30, ?, now(), now())
                RETURNING id
                """, Long.class, typeId, instructorId, roomId, Timestamp.from(start),
                Timestamp.from(start.plusSeconds(3600)), status);
    }

    protected long insertProduct(int totalCount) {
        return jdbc.queryForObject("""
                INSERT INTO pass_products (name, price, total_count, validity_days, created_at, updated_at)
                VALUES ('10회권', 300000, ?, 60, now(), now())
                RETURNING id
                """, Long.class, totalCount);
    }

    /** 30일 전부터 30일 뒤까지 쓸 수 있는 ACTIVE 이용권. */
    protected long insertPass(long productId, long memberId, long instructorId, int total, int remaining) {
        return jdbc.queryForObject("""
                INSERT INTO passes (pass_product_id, member_id, instructor_id, price, total_count, remaining_count,
                                    start_date, end_date, status, created_at, updated_at)
                VALUES (?, ?, ?, 300000, ?, ?, ?, ?, 'ACTIVE', now(), now())
                RETURNING id
                """, Long.class, productId, memberId, instructorId, total, remaining, Date.valueOf(days(-30)),
                Date.valueOf(days(29)));
    }

    protected long insertReservation(long lessonId, long memberId, long passId, String status) {
        return jdbc.queryForObject("""
                INSERT INTO reservations (lesson_id, member_id, pass_id, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, now(), now())
                RETURNING id
                """, Long.class, lessonId, memberId, passId, status);
    }

    protected long insertAbsence(long instructorId, Instant start, Instant end, String status) {
        return jdbc.queryForObject("""
                INSERT INTO absences (instructor_id, start_at, end_at, status, created_at)
                VALUES (?, ?, ?, ?, now())
                RETURNING id
                """, Long.class, instructorId, Timestamp.from(start), Timestamp.from(end), status);
    }

    // ===== 조회 =====

    protected String lessonStatus(long lessonId) {
        return jdbc.queryForObject("SELECT status FROM lessons WHERE id = ?", String.class, lessonId);
    }

    protected int remaining(long passId) {
        return jdbc.queryForObject("SELECT remaining_count FROM passes WHERE id = ?", Integer.class, passId);
    }

    protected int reservedCountOf(long lessonId) {
        return jdbc.queryForObject("SELECT count(*) FROM reservations WHERE lesson_id = ? AND status = 'RESERVED'",
                Integer.class, lessonId);
    }

    protected int activityCount(String action) {
        return jdbc.queryForObject("SELECT count(*) FROM activity_logs WHERE action = ?", Integer.class, action);
    }
}
