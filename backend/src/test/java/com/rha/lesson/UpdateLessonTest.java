package com.rha.lesson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

class UpdateLessonTest extends LessonTestSupport {

    long roomId;
    long typeId;
    long instructorId;
    long lessonId;

    @BeforeEach
    void prepare() {
        openEveryDay();
        roomId = insertRoom("A룸");
        typeId = insertLessonType("GROUP_3", "단체 3:1", 3, true);
        instructorId = insertInstructor("i@example.com", "김강사");
        lessonId = insertLesson("원래 제목", typeId, instructorId, roomId, at(1, 10), at(1, 11), "PUBLISHED");
    }

    private long idOf(String email) {
        return jdbc.queryForObject("SELECT id FROM members WHERE email = ?", Long.class, email);
    }

    private Instant startAt() {
        return jdbc.queryForObject("SELECT start_at FROM lessons WHERE id = ?", Timestamp.class, lessonId)
                .toInstant();
    }

    // ===== 성공 =====

    @Test
    void 관리자는_제목을_수정할_수_있고_다른_값과_상태는_그대로다() throws Exception {
        String token = adminToken();

        updateLesson(token, lessonId, "{\"title\":\"새 제목\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(lessonId))
                .andExpect(jsonPath("$.title").value("새 제목"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.instructor.id").value(instructorId))
                .andExpect(jsonPath("$.room.id").value(roomId))
                .andExpect(jsonPath("$.startAt").value(at(1, 10).toString()))
                .andExpect(jsonPath("$.reservedCount").value(0));
    }

    @Test
    void 시간_강사_룸을_한_번에_수정할_수_있다() throws Exception {
        String token = adminToken();
        long otherRoom = insertRoom("B룸");
        long otherInstructor = insertInstructor("i2@example.com", "박강사");

        updateLesson(token, lessonId, "{\"instructorId\":" + otherInstructor + ",\"roomId\":" + otherRoom
                + ",\"startAt\":\"" + at(2, 14) + "\",\"endAt\":\"" + at(2, 15) + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instructor.name").value("박강사"))
                .andExpect(jsonPath("$.room.name").value("B룸"))
                .andExpect(jsonPath("$.startAt").value(at(2, 14).toString()))
                .andExpect(jsonPath("$.endAt").value(at(2, 15).toString()));
    }

    @Test
    void LESSON_UPDATE_권한이_있는_강사는_수정할_수_있다() throws Exception {
        String token = instructorToken();
        long callerId = idOf("t@example.com");
        grant(callerId, "LESSON_UPDATE:GROUP_3", callerId);

        updateLesson(token, lessonId, "{\"title\":\"강사가 수정\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("강사가 수정"));
    }

    @Test
    void 자기_자신과는_시간이_겹쳐도_충돌이_아니다() throws Exception {
        String token = adminToken();

        updateLesson(token, lessonId,
                "{\"startAt\":\"" + at(1, 10).plusSeconds(1800) + "\",\"endAt\":\"" + at(1, 11).plusSeconds(1800) + "\"}")
                .andExpect(status().isOk());
    }

    @Test
    void 시작_시각만_바꾸면_종료는_기존_값을_기준으로_한다() throws Exception {
        String token = adminToken();

        updateLesson(token, lessonId, "{\"startAt\":\"" + at(1, 9) + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startAt").value(at(1, 9).toString()))
                .andExpect(jsonPath("$.endAt").value(at(1, 11).toString()));
    }

    @Test
    void 시간이_바뀌지_않으면_영업시간과_충돌을_다시_검사하지_않는다() throws Exception {
        String token = adminToken();
        closeDay(dayOf(1));

        updateLesson(token, lessonId, "{\"title\":\"제목만\",\"startAt\":\"" + at(1, 10) + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("제목만"));
    }

    // ===== 권한 =====

    @Test
    void 권한이_없는_강사는_본인_담당이어도_403이다() throws Exception {
        String token = instructorToken();
        long callerId = idOf("t@example.com");
        long own = insertLesson("내 수업", typeId, callerId, roomId, at(2, 10), at(2, 11), "PUBLISHED");

        updateLesson(token, own, "{\"title\":\"x\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void 다른_유형의_권한만_있는_강사는_403이다() throws Exception {
        insertLessonType("PRIVATE_1", "개인", 1, true);
        String token = instructorToken();
        long callerId = idOf("t@example.com");
        grant(callerId, "LESSON_UPDATE:PRIVATE_1", callerId);

        updateLesson(token, lessonId, "{\"title\":\"x\"}").andExpect(status().isForbidden());
    }

    @Test
    void LESSON_CREATE_권한만으로는_수정할_수_없다() throws Exception {
        String token = instructorToken();
        long callerId = idOf("t@example.com");
        grant(callerId, "LESSON_CREATE:GROUP_3", callerId);

        updateLesson(token, lessonId, "{\"title\":\"x\"}").andExpect(status().isForbidden());
    }

    @Test
    void 회원은_403이다() throws Exception {
        String token = memberToken();

        updateLesson(token, lessonId, "{\"title\":\"x\"}").andExpect(status().isForbidden());
    }

    @Test
    void 권한이_없으면_잘못된_본문이어도_없는_수업이어도_403이다() throws Exception {
        String token = instructorToken();

        updateLesson(token, lessonId, "{}").andExpect(status().isForbidden());
        updateLesson(token, 999999, "{\"title\":\"x\"}").andExpect(status().isForbidden());
    }

    @Test
    void 인증하지_않으면_401이다() throws Exception {
        mockMvc.perform(patch("/lessons/" + lessonId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ===== 400, 404 =====

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"lessonTypeId\":2}",
            "{\"title\":\"\"}",
            "{\"startAt\":\"not-a-date\"}",
            "{\"instructorId\":\"x\"}",
            "not json"
    })
    void 요청_값이_올바르지_않으면_400_INVALID_INPUT이다(String json) throws Exception {
        String token = adminToken();

        updateLesson(token, lessonId, json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 수업_유형은_바꿀_수_없고_함께_보내도_무시된다() throws Exception {
        String token = adminToken();
        long otherType = insertLessonType("PRIVATE_1", "개인", 1, true);

        updateLesson(token, lessonId, "{\"title\":\"x\",\"lessonTypeId\":" + otherType + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lessonType.id").value(typeId));
    }

    @Test
    void 종료가_시작보다_늦지_않으면_400_INVALID_LESSON_TIME이다() throws Exception {
        String token = adminToken();

        updateLesson(token, lessonId, "{\"endAt\":\"" + at(1, 10) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LESSON_TIME"));
        updateLesson(token, lessonId, "{\"startAt\":\"" + at(1, 12) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LESSON_TIME"));
        assertThat(startAt()).isEqualTo(at(1, 10));
    }

    @Test
    void 시작을_과거로_바꾸면_400_INVALID_LESSON_TIME이다() throws Exception {
        String token = adminToken();

        updateLesson(token, lessonId,
                "{\"startAt\":\"" + at(-1, 10) + "\",\"endAt\":\"" + at(-1, 11) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LESSON_TIME"));
    }

    @Test
    void 담당_강사를_강사가_아닌_회원으로_바꾸면_400_INVALID_INSTRUCTOR이다() throws Exception {
        String token = adminToken();
        long memberId = insertMember("m1@example.com", "MEMBER");

        updateLesson(token, lessonId, "{\"instructorId\":" + memberId + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INSTRUCTOR"));
    }

    @Test
    void 공개_예약_시각보다_앞으로_시작을_옮기면_400_INVALID_PUBLISH_AT이다() throws Exception {
        String token = adminToken();
        long scheduled = insertLesson("예약", typeId, instructorId, roomId, at(3, 10), at(3, 11), "SCHEDULED");
        jdbc.update("UPDATE lessons SET publish_at = ? WHERE id = ?", Timestamp.from(at(2, 10)), scheduled);

        updateLesson(token, scheduled,
                "{\"startAt\":\"" + at(2, 9) + "\",\"endAt\":\"" + at(2, 10) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PUBLISH_AT"));
    }

    @Test
    void 없는_수업이면_404이다() throws Exception {
        String token = adminToken();

        updateLesson(token, 999999, "{\"title\":\"x\"}").andExpect(status().isNotFound());
    }

    @Test
    void 없는_룸이나_삭제된_룸으로_바꾸면_404이다() throws Exception {
        String token = adminToken();
        long deletedRoom = insertRoom("C룸");
        jdbc.update("UPDATE rooms SET deleted_at = now() WHERE id = ?", deletedRoom);

        updateLesson(token, lessonId, "{\"roomId\":999999}").andExpect(status().isNotFound());
        updateLesson(token, lessonId, "{\"roomId\":" + deletedRoom + "}").andExpect(status().isNotFound());
    }

    @Test
    void lessonId가_숫자가_아니면_400이다() throws Exception {
        String token = adminToken();

        mockMvc.perform(patch("/lessons/abc").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    // ===== 409 =====

    @Test
    void 취소된_수업은_409_LESSON_CANCELED이다() throws Exception {
        String token = adminToken();
        long canceled = insertLesson("취소", typeId, instructorId, roomId, at(2, 10), at(2, 11), "CANCELED");

        updateLesson(token, canceled, "{\"title\":\"x\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LESSON_CANCELED"));
    }

    @Test
    void 이미_시작한_수업은_409_LESSON_ALREADY_STARTED이다() throws Exception {
        String token = adminToken();
        long started = insertPastLesson("지난 수업", typeId, instructorId, roomId, "PUBLISHED");

        updateLesson(token, started, "{\"title\":\"x\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LESSON_ALREADY_STARTED"));
    }

    @Test
    void 취소되고_시작한_수업은_LESSON_CANCELED가_먼저다() throws Exception {
        String token = adminToken();
        long both = insertPastLesson("지난 취소", typeId, instructorId, roomId, "CANCELED");

        updateLesson(token, both, "{\"title\":\"x\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LESSON_CANCELED"));
    }

    @Test
    void 영업시간_밖으로_옮기면_409_OUTSIDE_BUSINESS_HOURS이다() throws Exception {
        String token = adminToken();

        updateLesson(token, lessonId, "{\"startAt\":\"" + at(1, 7) + "\",\"endAt\":\"" + at(1, 8) + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUTSIDE_BUSINESS_HOURS"));
        assertThat(startAt()).isEqualTo(at(1, 10));
    }

    @Test
    void 퇴사일이_있는_강사로_바꾸면_409_INSTRUCTOR_RESIGNING이다() throws Exception {
        String token = adminToken();
        long resigning = insertInstructor("i2@example.com", "박강사");
        jdbc.update("UPDATE members SET resignation_date = ? WHERE id = ?",
                Date.valueOf(LocalDate.now(SEOUL).plusDays(1)), resigning);

        updateLesson(token, lessonId, "{\"instructorId\":" + resigning + "}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTRUCTOR_RESIGNING"));
    }

    @Test
    void 강사를_바꿨는데_새_강사의_수업과_겹치면_409_INSTRUCTOR_TIME_CONFLICT이다() throws Exception {
        String token = adminToken();
        long otherInstructor = insertInstructor("i2@example.com", "박강사");
        long otherRoom = insertRoom("B룸");
        insertLesson("박강사 수업", typeId, otherInstructor, otherRoom, at(1, 10), at(1, 11), "CREATED");

        updateLesson(token, lessonId, "{\"instructorId\":" + otherInstructor + "}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTRUCTOR_TIME_CONFLICT"));
    }

    @Test
    void 룸을_바꿨는데_새_룸의_수업과_겹치면_409_ROOM_TIME_CONFLICT이다() throws Exception {
        String token = adminToken();
        long otherInstructor = insertInstructor("i2@example.com", "박강사");
        long otherRoom = insertRoom("B룸");
        insertLesson("B룸 수업", typeId, otherInstructor, otherRoom, at(1, 10), at(1, 11), "CREATED");

        updateLesson(token, lessonId, "{\"roomId\":" + otherRoom + "}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_TIME_CONFLICT"));
    }

    @Test
    void 취소된_수업과는_겹쳐도_된다() throws Exception {
        String token = adminToken();
        long otherRoom = insertRoom("B룸");
        insertLesson("취소됨", typeId, instructorId, otherRoom, at(2, 10), at(2, 11), "CANCELED");

        updateLesson(token, lessonId, "{\"startAt\":\"" + at(2, 10) + "\",\"endAt\":\"" + at(2, 11) + "\"}")
                .andExpect(status().isOk());
    }
}
