package com.rha.lesson;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 수업 수정의 확인 순서: 담당 강사 400(INVALID_INSTRUCTOR)이 예약 확인 409(LESSON_HAS_RESERVATIONS)보다 먼저다. */
class UpdateLessonOrderTest extends LessonTestSupport {

    @Test
    void 예약이_있어도_담당_강사로_지정할_수_없는_회원이면_INVALID_INSTRUCTOR이다() throws Exception {
        openEveryDay();
        long room = insertRoom("A룸");
        long type = insertLessonType("GROUP_3", "단체 3:1", 3, true);
        long instructor = insertInstructor("i@example.com", "김강사");
        long lesson = insertLesson("수업", type, instructor, room, at(1, 10), at(1, 11), "PUBLISHED");
        long member = insertMember("m@example.com", "MEMBER");
        long product = jdbc.queryForObject("""
                INSERT INTO pass_products (name, price, total_count, validity_days, created_at, updated_at)
                VALUES ('상품', 1, 10, 30, now(), now()) RETURNING id
                """, Long.class);
        long pass = jdbc.queryForObject("""
                INSERT INTO passes (pass_product_id, member_id, instructor_id, price, total_count, remaining_count,
                                    start_date, end_date, status, created_at, updated_at)
                VALUES (?, ?, ?, 1, 10, 9, current_date - 1, current_date + 30, 'ACTIVE', now(), now())
                RETURNING id
                """, Long.class, product, member, instructor);
        jdbc.update("""
                INSERT INTO reservations (lesson_id, member_id, pass_id, status, created_at, updated_at)
                VALUES (?, ?, ?, 'RESERVED', now(), now())
                """, lesson, member, pass);
        Instant newStart = at(2, 10);

        updateLesson(adminToken(), lesson, "{\"startAt\":\"" + newStart + "\",\"endAt\":\""
                + newStart.plusSeconds(3600) + "\",\"instructorId\":" + member + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INSTRUCTOR"));
    }
}
