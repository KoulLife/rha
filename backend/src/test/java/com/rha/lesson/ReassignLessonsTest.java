package com.rha.lesson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 수업 인계(reassignLessons)와 수업이 퇴사 처리에 미치는 영향, 가능시간 검증을 확인한다. */
class ReassignLessonsTest extends LessonTestSupport {

    long roomId;
    long typeId;
    long fromId;
    long toId;

    @BeforeEach
    void prepare() {
        openEveryDay();
        roomId = insertRoom("A룸");
        typeId = insertLessonType("GROUP_3", "단체 3:1", 3, true);
        fromId = insertInstructor("from@example.com", "김강사");
        toId = insertInstructor("to@example.com", "박강사");
    }

    private ResultActions reassign(String token, long memberId, String json) throws Exception {
        return mockMvc.perform(post("/members/" + memberId + "/lesson-reassignments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String to(long id) {
        return "{\"toInstructorId\":" + id + "}";
    }

    private long instructorOf(long lessonId) {
        return jdbc.queryForObject("SELECT instructor_id FROM lessons WHERE id = ?", Long.class, lessonId);
    }

    // ===== reassignLessons =====

    @Test
    void 시작_전이고_취소되지_않은_수업을_모두_넘긴다() throws Exception {
        String token = adminToken();
        long a = insertLesson("a", typeId, fromId, roomId, at(1, 10), at(1, 11), "PUBLISHED");
        long b = insertLesson("b", typeId, fromId, roomId, at(2, 10), at(2, 11), "CREATED");
        long canceled = insertLesson("c", typeId, fromId, roomId, at(3, 10), at(3, 11), "CANCELED");
        long past = insertPastLesson("past", typeId, fromId, roomId, "PUBLISHED");
        long others = insertLesson("o", typeId, toId, roomId, at(4, 10), at(4, 11), "PUBLISHED");

        reassign(token, fromId, to(toId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reassignedCount").value(2));

        assertThat(instructorOf(a)).isEqualTo(toId);
        assertThat(instructorOf(b)).isEqualTo(toId);
        assertThat(instructorOf(canceled)).isEqualTo(fromId);
        assertThat(instructorOf(past)).isEqualTo(fromId);
        assertThat(instructorOf(others)).isEqualTo(toId);
    }

    @Test
    void from_이후에_시작하는_수업만_넘긴다() throws Exception {
        String token = adminToken();
        long early = insertLesson("early", typeId, fromId, roomId, at(1, 10), at(1, 11), "PUBLISHED");
        long late = insertLesson("late", typeId, fromId, roomId, at(5, 10), at(5, 11), "PUBLISHED");

        reassign(token, fromId, "{\"toInstructorId\":" + toId + ",\"from\":\"" + at(3, 0) + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reassignedCount").value(1));

        assertThat(instructorOf(early)).isEqualTo(fromId);
        assertThat(instructorOf(late)).isEqualTo(toId);
    }

    @Test
    void 넘길_수업이_없으면_0이다() throws Exception {
        String token = adminToken();

        reassign(token, fromId, to(toId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reassignedCount").value(0));
    }

    @Test
    void 하나라도_배정할_수_없으면_아무것도_바뀌지_않고_409_REASSIGNMENT_FAILED이다() throws Exception {
        String token = adminToken();
        long ok = insertLesson("ok", typeId, fromId, roomId, at(1, 10), at(1, 11), "PUBLISHED");
        long conflict = insertLesson("conflict", typeId, fromId, roomId, at(2, 10), at(2, 11), "PUBLISHED");
        long otherRoom = insertRoom("B룸");
        insertLesson("박강사 수업", typeId, toId, otherRoom, at(2, 10), at(2, 11), "PUBLISHED");

        reassign(token, fromId, to(toId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REASSIGNMENT_FAILED"))
                .andExpect(jsonPath("$.invalidParams.length()").value(1))
                .andExpect(jsonPath("$.invalidParams[0].field").value(String.valueOf(conflict)))
                .andExpect(jsonPath("$.invalidParams[0].reason").value("INSTRUCTOR_TIME_CONFLICT"));

        assertThat(instructorOf(ok)).isEqualTo(fromId);
        assertThat(instructorOf(conflict)).isEqualTo(fromId);
    }

    @Test
    void 실패한_수업은_모두_invalidParams에_담긴다() throws Exception {
        String token = adminToken();
        long first = insertLesson("a", typeId, fromId, roomId, at(1, 10), at(1, 11), "PUBLISHED");
        long second = insertLesson("b", typeId, fromId, roomId, at(2, 10), at(2, 11), "PUBLISHED");
        clearAvailability(toId);

        reassign(token, fromId, to(toId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REASSIGNMENT_FAILED"))
                .andExpect(jsonPath("$.invalidParams.length()").value(2))
                .andExpect(jsonPath("$.invalidParams[0].field").value(String.valueOf(first)))
                .andExpect(jsonPath("$.invalidParams[0].reason").value("INSTRUCTOR_UNAVAILABLE"))
                .andExpect(jsonPath("$.invalidParams[1].field").value(String.valueOf(second)));
    }

    @Test
    void 넘겨받을_강사의_퇴사일_이후_수업은_넘길_수_없다() throws Exception {
        String token = adminToken();
        insertLesson("a", typeId, fromId, roomId, at(5, 10), at(5, 11), "PUBLISHED");
        jdbc.update("UPDATE members SET resignation_date = ? WHERE id = ?",
                Date.valueOf(LocalDate.now(SEOUL).plusDays(3)), toId);

        reassign(token, fromId, to(toId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.invalidParams[0].reason").value("INSTRUCTOR_RESIGNING"));
    }

    @Test
    void 새_강사가_활성_강사가_아니거나_같은_강사면_400_INVALID_INSTRUCTOR이다() throws Exception {
        String token = adminToken();
        long member = insertMember("m@example.com", "MEMBER");
        long inactive = insertInstructor("in@example.com", "비활성");
        jdbc.update("UPDATE members SET status = 'INACTIVE' WHERE id = ?", inactive);

        for (long id : new long[] {member, inactive, fromId}) {
            reassign(token, fromId, to(id))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INSTRUCTOR"));
        }
    }

    @Test
    void 대상_강사나_새_강사가_없거나_대상이_강사가_아니면_404이다() throws Exception {
        String token = adminToken();
        long member = insertMember("m@example.com", "MEMBER");

        reassign(token, 999999, to(toId)).andExpect(status().isNotFound());
        reassign(token, member, to(toId)).andExpect(status().isNotFound());
        reassign(token, fromId, to(999999)).andExpect(status().isNotFound());
    }

    @Test
    void 요청_형식이_올바르지_않으면_400이다() throws Exception {
        String token = adminToken();

        reassign(token, fromId, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        reassign(token, fromId, "{\"toInstructorId\":" + toId + ",\"from\":\"not-a-date\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void 관리자만_할_수_있다() throws Exception {
        String token = instructorToken();

        reassign(token, fromId, to(toId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        reassign(token, fromId, "{}").andExpect(status().isForbidden());
    }

    @Test
    void 인증하지_않으면_401이다() throws Exception {
        mockMvc.perform(post("/members/" + fromId + "/lesson-reassignments")
                        .contentType(MediaType.APPLICATION_JSON).content(to(toId)))
                .andExpect(status().isUnauthorized());
    }

    // ===== 퇴사 처리와 수업 =====

    @Test
    void 시작_전_담당_수업이_남아_있으면_퇴사_처리할_수_없다() throws Exception {
        String token = adminToken();
        jdbc.update("UPDATE members SET resignation_date = ? WHERE id = ?",
                Date.valueOf(LocalDate.now(SEOUL)), fromId);
        long lessonId = insertLesson("a", typeId, fromId, roomId, at(1, 10), at(1, 11), "PUBLISHED");

        mockMvc.perform(post("/members/" + fromId + "/resignation/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTRUCTOR_HAS_UPCOMING_LESSONS"));

        reassign(token, fromId, to(toId)).andExpect(status().isOk());
        mockMvc.perform(post("/members/" + fromId + "/resignation/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(instructorOf(lessonId)).isEqualTo(toId);
    }

    // ===== 수업 가능시간 검증 (createLesson, updateLesson) =====

    @Test
    void 가능시간이_설정되지_않은_강사에게는_수업을_개설할_수_없다() throws Exception {
        String token = adminToken();
        clearAvailability(fromId);

        createLesson(token, body("a", typeId, fromId, roomId, at(1, 10), at(1, 11)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTRUCTOR_UNAVAILABLE"));
        assertThat(lessonCount()).isZero();
    }

    @Test
    void 수업_시간이_가능_시간대_하나_안에_모두_들어와야_한다() throws Exception {
        String token = adminToken();
        clearAvailability(fromId);
        String day = dayOf(1);
        insertAvailability(fromId, day, "09:00", "12:00");
        insertAvailability(fromId, day, "14:00", "18:00");

        createLesson(token, body("안", typeId, fromId, roomId, at(1, 10), at(1, 11)))
                .andExpect(status().isCreated());
        createLesson(token, body("경계", typeId, fromId, roomId, at(1, 14), at(1, 15)))
                .andExpect(status().isCreated());
        createLesson(token, body("걸침", typeId, fromId, roomId, at(1, 11), at(1, 13)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTRUCTOR_UNAVAILABLE"));
        createLesson(token, body("사이", typeId, fromId, roomId, at(1, 12), at(1, 14)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTRUCTOR_UNAVAILABLE"));
    }

    @Test
    void 다른_요일의_가능시간은_인정하지_않는다() throws Exception {
        String token = adminToken();
        clearAvailability(fromId);
        String other = dayOf(2);
        insertAvailability(fromId, other, "00:00", "23:59");

        createLesson(token, body("a", typeId, fromId, roomId, at(1, 10), at(1, 11)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTRUCTOR_UNAVAILABLE"));
    }

    @Test
    void 영업시간_위반이_가능시간보다_먼저다() throws Exception {
        String token = adminToken();
        clearAvailability(fromId);
        closeDay(dayOf(1));

        createLesson(token, body("a", typeId, fromId, roomId, at(1, 10), at(1, 11)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUTSIDE_BUSINESS_HOURS"));
    }

    @Test
    void 가능시간이_바뀌어도_개설된_수업은_그대로이고_시간을_바꿀_때만_다시_검증한다() throws Exception {
        String token = adminToken();
        long lessonId = insertLesson("a", typeId, fromId, roomId, at(1, 10), at(1, 11), "PUBLISHED");
        clearAvailability(fromId);

        updateLesson(token, lessonId, "{\"title\":\"제목만\"}").andExpect(status().isOk());
        updateLesson(token, lessonId, "{\"startAt\":\"" + at(1, 12) + "\",\"endAt\":\"" + at(1, 13) + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTRUCTOR_UNAVAILABLE"));
    }

    @Test
    void 강사를_바꿀_때_새_강사의_가능시간을_검증한다() throws Exception {
        String token = adminToken();
        long lessonId = insertLesson("a", typeId, fromId, roomId, at(1, 10), at(1, 11), "PUBLISHED");
        clearAvailability(toId);

        updateLesson(token, lessonId, "{\"instructorId\":" + toId + "}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTRUCTOR_UNAVAILABLE"));
    }

    @Test
    void 수업_생성_응답은_Instant이다() throws Exception {
        String token = adminToken();
        Instant start = at(1, 10);

        createLesson(token, body("a", typeId, fromId, roomId, start, at(1, 11)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startAt").value(start.toString()));
    }
}
