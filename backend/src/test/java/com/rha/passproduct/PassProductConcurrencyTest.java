package com.rha.passproduct;

import static org.assertj.core.api.Assertions.assertThat;

import com.rha.ConcurrencyTestSupport;
import com.rha.reservation.ReservationCreateRequest;
import com.rha.reservation.ReservationService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 상품 수정을 발급된 이용권에 반영할 때 동시에 들어온 예약·수정의 횟수 변경이 사라지거나 중복되지 않는지. */
class PassProductConcurrencyTest extends ConcurrencyTestSupport {

    @Autowired
    PassProductService passProductService;

    @Autowired
    ReservationService reservationService;

    @Test
    void 상품_수정_반영과_예약이_동시에_들어와도_예약_차감이_사라지지_않는다() throws Exception {
        long admin = insertAdmin();
        long instructor = insertInstructor("t@example.com");
        long lesson = insertLesson(insertLessonType(), instructor, insertRoom("A룸"), at(3, 10), "PUBLISHED");
        long product = insertProduct(10);
        List<long[]> members = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            long member = insertActiveMember("m" + i + "@example.com");
            members.add(new long[] {member, insertPass(product, member, instructor, 10, 10)});
        }

        List<Callable<?>> tasks = new ArrayList<>();
        tasks.add(() -> passProductService.updatePassProduct(admin, product, totalCount(12)));
        for (long[] member : members) {
            tasks.add(() -> reservationService.createReservation(member[0], new ReservationCreateRequest(lesson)));
        }
        List<Outcome> outcomes = runConcurrently(tasks);

        assertThat(successCount(outcomes)).isEqualTo(tasks.size());
        for (long[] member : members) {
            assertThat(remaining(member[1])).as("이용권 #" + member[1]).isEqualTo(11);
            assertThat(totalCountOf(member[1])).isEqualTo(12);
        }
    }

    @Test
    void 같은_상품을_동시에_수정해도_횟수_변경이_한_번만_반영된다() throws Exception {
        long admin = insertAdmin();
        long instructor = insertInstructor("t@example.com");
        long product = insertProduct(10);
        List<Long> passes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            passes.add(insertPass(product, insertActiveMember("m" + i + "@example.com"), instructor, 10, 10));
        }

        List<Outcome> outcomes = runConcurrently(List.of(
                () -> passProductService.updatePassProduct(admin, product, totalCount(12)),
                () -> passProductService.updatePassProduct(admin, product, totalCount(12))));

        assertThat(successCount(outcomes)).isEqualTo(2);
        for (long pass : passes) {
            assertThat(totalCountOf(pass)).isEqualTo(12);
            assertThat(remaining(pass)).isEqualTo(12);
        }
    }

    private static PassProductUpdateRequest totalCount(int totalCount) {
        PassProductUpdateRequest request = new PassProductUpdateRequest();
        request.setTotalCount(totalCount);
        request.setApplyToIssued(true);
        return request;
    }

    private int totalCountOf(long passId) {
        return jdbc.queryForObject("SELECT total_count FROM passes WHERE id = ?", Integer.class, passId);
    }
}
