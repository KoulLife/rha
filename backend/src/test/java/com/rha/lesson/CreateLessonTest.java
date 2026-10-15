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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

class CreateLessonTest extends LessonTestSupport {

    long roomId;
    long typeId;
    long instructorId;
    Instant start;
    Instant end;

    @BeforeEach
    void prepare() {
        openEveryDay();
        roomId = insertRoom("A룸");
        typeId = insertLessonType("GROUP_3", "단체 3:1", 3, true);
        instructorId = insertInstructor("i@example.com", "김강사");
        start = at(1, 10);
        end = at(1, 11);
    }

    private String json() {
        return body("아침 필라테스", typeId, instructorId, roomId, start, end);
    }

    private long idOf(String email) {
        return jdbc.queryForObject("SELECT id FROM members WHERE email = ?", Long.class, email);
    }

    // ===== 성공 =====

    @Test
    void 관리자는_수업을_개설할_수_있고_CREATED_상태로_생성된다() throws Exception {
        String token = adminToken();

        createLesson(token, json())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("아침 필라테스"))
                .andExpect(jsonPath("$.lessonType.id").value(typeId))
                .andExpect(jsonPath("$.lessonType.code").value("GROUP_3"))
                .andExpect(jsonPath("$.lessonType.name").value("단체 3:1"))
                .andExpect(jsonPath("$.instructor.id").value(instructorId))
                .andExpect(jsonPath("$.instructor.name").value("김강사"))
                .andExpect(jsonPath("$.room.id").value(roomId))
                .andExpect(jsonPath("$.room.name").value("A룸"))
                .andExpect(jsonPath("$.startAt").value(start.toString()))
                .andExpect(jsonPath("$.endAt").value(end.toString()))
                .andExpect(jsonPath("$.capacity").value(3))
                .andExpect(jsonPath("$.reservedCount").value(0))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.publishAt").doesNotExist())
                .andExpect(jsonPath("$.createdAt").isString());

        assertThat(lessonCount()).isEqualTo(1);
    }

    @Test
    void 해당_유형의_LESSON_CREATE_권한이_있는_강사는_다른_강사를_담당으로_개설할_수_있다() throws Exception {
        String token = instructorToken();
        long callerId = idOf("t@example.com");
        grant(callerId, "LESSON_CREATE:GROUP_3", callerId);

        createLesson(token, json())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.instructor.id").value(instructorId));
    }

    @Test
    void 정원은_개설_시점의_유형_정원이_복사되어_이후_유형이_바뀌어도_유지된다() throws Exception {
        String token = adminToken();

        createLesson(token, json()).andExpect(status().isCreated());
        jdbc.update("UPDATE lesson_types SET capacity = 10 WHERE id = ?", typeId);

        assertThat(jdbc.queryForObject("SELECT capacity FROM lessons", Integer.class)).isEqualTo(3);
    }

    @Test
    void 영업시간_경계에_맞닿은_수업도_개설된다() throws Exception {
        String token = adminToken();

        createLesson(token, body("개장", typeId, instructorId, roomId, at(1, 9), at(1, 10)))
                .andExpect(status().isCreated());
        createLesson(token, body("폐장", typeId, instructorId, roomId, at(1, 21), at(1, 22)))
                .andExpect(status().isCreated());
    }

    @Test
    void 퇴사일_전날의_수업은_개설할_수_있다() throws Exception {
        String token = adminToken();
        jdbc.update("UPDATE members SET resignation_date = ? WHERE id = ?",
                Date.valueOf(LocalDate.now(SEOUL).plusDays(2)), instructorId);

        createLesson(token, json()).andExpect(status().isCreated());
    }

    @Test
    void 취소된_수업과는_강사와_룸이_같아도_시간이_겹칠_수_있다() throws Exception {
        String token = adminToken();
        insertLesson("취소됨", typeId, instructorId, roomId, start, end, "CANCELED");

        createLesson(token, json()).andExpect(status().isCreated());
    }

    @Test
    void 바로_이어지는_수업은_충돌이_아니다() throws Exception {
        String token = adminToken();
        insertLesson("앞 수업", typeId, instructorId, roomId, at(1, 9), start, "PUBLISHED");
        insertLesson("뒷 수업", typeId, instructorId, roomId, end, at(1, 12), "PUBLISHED");

        createLesson(token, json()).andExpect(status().isCreated());
    }

    @Test
    void 영업시간은_현지_시각_기준이다() throws Exception {
        String token = adminToken();
        // 현지 09:00은 UTC로는 전날 00:00이다. UTC 시각만 보면 영업 전이지만 현지로는 영업 중이다.
        createLesson(token, body("a", typeId, instructorId, roomId, at(1, 9), at(1, 10)))
                .andExpect(status().isCreated());
    }

    // ===== 권한 =====

    @Test
    void 권한이_없는_강사는_403_PERMISSION_DENIED이다() throws Exception {
        String token = instructorToken();

        createLesson(token, json())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        assertThat(lessonCount()).isZero();
    }

    @Test
    void 다른_유형의_권한만_있는_강사는_403이다() throws Exception {
        insertLessonType("PRIVATE_1", "개인", 1, true);
        String token = instructorToken();
        long callerId = idOf("t@example.com");
        grant(callerId, "LESSON_CREATE:PRIVATE_1", callerId);

        createLesson(token, json())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void LESSON_UPDATE_권한만으로는_개설할_수_없다() throws Exception {
        String token = instructorToken();
        long callerId = idOf("t@example.com");
        grant(callerId, "LESSON_UPDATE:GROUP_3", callerId);

        createLesson(token, json()).andExpect(status().isForbidden());
    }

    @Test
    void 회원은_403이다() throws Exception {
        String token = memberToken();

        createLesson(token, json()).andExpect(status().isForbidden());
    }

    @Test
    void 비활성화된_관리자의_기존_토큰은_403이다() throws Exception {
        String token = adminToken();
        jdbc.update("UPDATE members SET status = 'INACTIVE' WHERE email = 'admin@example.com'");

        createLesson(token, json()).andExpect(status().isForbidden());
    }

    @Test
    void 권한이_없으면_잘못된_본문이어도_400이_아니라_403이다() throws Exception {
        String token = instructorToken();

        createLesson(token, "{}").andExpect(status().isForbidden());
    }

    @Test
    void 권한이_없는_강사에게는_없는_수업_유형이어도_403이다() throws Exception {
        String token = instructorToken();

        createLesson(token, body("x", 999999, instructorId, roomId, start, end))
                .andExpect(status().isForbidden());
    }

    @Test
    void 인증하지_않으면_401이다() throws Exception {
        mockMvc.perform(post("/lessons").contentType(MediaType.APPLICATION_JSON).content(json()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // ===== 400 =====

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"title\":\"\",\"lessonTypeId\":1,\"instructorId\":1,\"roomId\":1,\"startAt\":\"2099-01-01T01:00:00Z\",\"endAt\":\"2099-01-01T02:00:00Z\"}",
            "{\"lessonTypeId\":1,\"instructorId\":1,\"roomId\":1,\"startAt\":\"2099-01-01T01:00:00Z\",\"endAt\":\"2099-01-01T02:00:00Z\"}",
            "{\"title\":\"a\",\"instructorId\":1,\"roomId\":1,\"startAt\":\"2099-01-01T01:00:00Z\",\"endAt\":\"2099-01-01T02:00:00Z\"}",
            "{\"title\":\"a\",\"lessonTypeId\":1,\"roomId\":1,\"startAt\":\"2099-01-01T01:00:00Z\",\"endAt\":\"2099-01-01T02:00:00Z\"}",
            "{\"title\":\"a\",\"lessonTypeId\":1,\"instructorId\":1,\"startAt\":\"2099-01-01T01:00:00Z\",\"endAt\":\"2099-01-01T02:00:00Z\"}",
            "{\"title\":\"a\",\"lessonTypeId\":1,\"instructorId\":1,\"roomId\":1,\"endAt\":\"2099-01-01T02:00:00Z\"}",
            "{\"title\":\"a\",\"lessonTypeId\":1,\"instructorId\":1,\"roomId\":1,\"startAt\":\"2099-01-01T01:00:00Z\"}",
            "{\"title\":\"a\",\"lessonTypeId\":1,\"instructorId\":1,\"roomId\":1,\"startAt\":\"not-a-date\",\"endAt\":\"2099-01-01T02:00:00Z\"}",
            "{\"title\":\"a\",\"lessonTypeId\":\"x\",\"instructorId\":1,\"roomId\":1,\"startAt\":\"2099-01-01T01:00:00Z\",\"endAt\":\"2099-01-01T02:00:00Z\"}",
            "not json"
    })
    void 요청_값이_올바르지_않으면_400_INVALID_INPUT이다(String json) throws Exception {
        String token = adminToken();

        createLesson(token, json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(lessonCount()).isZero();
    }

    @Test
    void 제목이_100자를_넘으면_400이다() throws Exception {
        String token = adminToken();

        createLesson(token, body("가".repeat(101), typeId, instructorId, roomId, start, end))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 종료가_시작보다_늦지_않으면_400_INVALID_LESSON_TIME이다() throws Exception {
        String token = adminToken();

        createLesson(token, body("a", typeId, instructorId, roomId, start, start))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LESSON_TIME"));
        createLesson(token, body("a", typeId, instructorId, roomId, end, start))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LESSON_TIME"));
    }

    @Test
    void 시작이_과거이면_400_INVALID_LESSON_TIME이다() throws Exception {
        String token = adminToken();

        createLesson(token, body("a", typeId, instructorId, roomId, at(-1, 10), at(-1, 11)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LESSON_TIME"));
    }

    @Test
    void 시간_오류는_없는_유형보다_먼저_400이다() throws Exception {
        String token = adminToken();

        createLesson(token, body("a", 999999, instructorId, roomId, end, start))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LESSON_TIME"));
    }

    @Test
    void 비활성_수업_유형이면_400_LESSON_TYPE_INACTIVE이다() throws Exception {
        String token = adminToken();
        jdbc.update("UPDATE lesson_types SET active = false WHERE id = ?", typeId);

        createLesson(token, json())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LESSON_TYPE_INACTIVE"));
    }

    @Test
    void 담당_강사가_강사가_아니면_400_INVALID_INSTRUCTOR이다() throws Exception {
        String token = adminToken();
        long memberId = insertMember("m1@example.com", "MEMBER");

        createLesson(token, body("a", typeId, memberId, roomId, start, end))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INSTRUCTOR"));
    }

    @Test
    void 담당_강사가_활성이_아니면_400_INVALID_INSTRUCTOR이다() throws Exception {
        String token = adminToken();
        jdbc.update("UPDATE members SET status = 'INACTIVE' WHERE id = ?", instructorId);

        createLesson(token, json())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INSTRUCTOR"));
    }

    @Test
    void 없는_강사_ID면_400_INVALID_INSTRUCTOR이다() throws Exception {
        String token = adminToken();

        createLesson(token, body("a", typeId, 999999, roomId, start, end))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INSTRUCTOR"));
    }

    // ===== 404 =====

    @Test
    void 없는_수업_유형이면_404이다() throws Exception {
        String token = adminToken();

        createLesson(token, body("a", 999999, instructorId, roomId, start, end))
                .andExpect(status().isNotFound());
    }

    @Test
    void 없는_룸이면_404이다() throws Exception {
        String token = adminToken();

        createLesson(token, body("a", typeId, instructorId, 999999, start, end))
                .andExpect(status().isNotFound());
    }

    @Test
    void 삭제된_룸이면_404이다() throws Exception {
        String token = adminToken();
        jdbc.update("UPDATE rooms SET deleted_at = now() WHERE id = ?", roomId);

        createLesson(token, json()).andExpect(status().isNotFound());
    }

    // ===== 409 =====

    @Test
    void 휴무일이면_409_OUTSIDE_BUSINESS_HOURS이다() throws Exception {
        String token = adminToken();
        closeDay(dayOf(1));

        createLesson(token, json())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUTSIDE_BUSINESS_HOURS"));
        assertThat(lessonCount()).isZero();
    }

    @Test
    void 영업시간이_설정되지_않았으면_모든_요일이_휴무라_409이다() throws Exception {
        String token = adminToken();
        jdbc.update("UPDATE business_hours SET closed = true, open_time = NULL, close_time = NULL");

        createLesson(token, json())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUTSIDE_BUSINESS_HOURS"));
    }

    @Test
    void 오픈_전에_시작하면_409이다() throws Exception {
        String token = adminToken();

        createLesson(token, body("a", typeId, instructorId, roomId, at(1, 8), at(1, 10)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUTSIDE_BUSINESS_HOURS"));
    }

    @Test
    void 마감_후에_끝나면_409이다() throws Exception {
        String token = adminToken();

        createLesson(token, body("a", typeId, instructorId, roomId, at(1, 21), at(1, 23)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUTSIDE_BUSINESS_HOURS"));
    }

    @Test
    void 자정을_넘기는_수업은_409이다() throws Exception {
        String token = adminToken();
        jdbc.update("UPDATE business_hours SET open_time = '00:00', close_time = '23:59' WHERE closed = false");

        createLesson(token, body("a", typeId, instructorId, roomId, at(1, 23), at(2, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUTSIDE_BUSINESS_HOURS"));
    }

    @Test
    void 퇴사일_당일의_수업은_409_INSTRUCTOR_RESIGNING이다() throws Exception {
        String token = adminToken();
        jdbc.update("UPDATE members SET resignation_date = ? WHERE id = ?",
                Date.valueOf(LocalDate.now(SEOUL).plusDays(1)), instructorId);

        createLesson(token, json())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTRUCTOR_RESIGNING"));
    }

    @Test
    void 퇴사일_이후의_수업은_409_INSTRUCTOR_RESIGNING이다() throws Exception {
        String token = adminToken();
        jdbc.update("UPDATE members SET resignation_date = ? WHERE id = ?",
                Date.valueOf(LocalDate.now(SEOUL)), instructorId);

        createLesson(token, json())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTRUCTOR_RESIGNING"));
    }

    @Test
    void 담당_강사의_다른_수업과_겹치면_409_INSTRUCTOR_TIME_CONFLICT이다() throws Exception {
        String token = adminToken();
        long otherRoom = insertRoom("B룸");
        insertLesson("기존", typeId, instructorId, otherRoom, at(1, 10), at(1, 12), "PUBLISHED");

        createLesson(token, body("a", typeId, instructorId, roomId, at(1, 11), at(1, 13)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTRUCTOR_TIME_CONFLICT"));
        assertThat(lessonCount()).isEqualTo(1);
    }

    @Test
    void 수업_유형이_달라도_담당_강사의_시간이_겹치면_409이다() throws Exception {
        String token = adminToken();
        long privateType = insertLessonType("PRIVATE_1", "개인", 1, true);
        long otherRoom = insertRoom("B룸");
        insertLesson("개인 수업", privateType, instructorId, otherRoom, start, end, "CREATED");

        createLesson(token, json())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTRUCTOR_TIME_CONFLICT"));
    }

    @Test
    void 같은_룸의_다른_수업과_겹치면_409_ROOM_TIME_CONFLICT이다() throws Exception {
        String token = adminToken();
        long otherInstructor = insertInstructor("i2@example.com", "박강사");
        insertLesson("기존", typeId, otherInstructor, roomId, at(1, 10), at(1, 12), "CREATED");

        createLesson(token, body("a", typeId, instructorId, roomId, at(1, 9), at(1, 11)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_TIME_CONFLICT"));
    }

    @Test
    void 강사와_룸이_모두_겹치면_강사_충돌이_먼저다() throws Exception {
        String token = adminToken();
        insertLesson("기존", typeId, instructorId, roomId, start, end, "CREATED");

        createLesson(token, json())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTRUCTOR_TIME_CONFLICT"));
    }

    @Test
    void 영업시간_위반이_시간_충돌보다_먼저다() throws Exception {
        String token = adminToken();
        insertLesson("기존", typeId, instructorId, roomId, at(1, 6), at(1, 7), "CREATED");

        createLesson(token, body("a", typeId, instructorId, roomId, at(1, 6), at(1, 7)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUTSIDE_BUSINESS_HOURS"));
    }
}
