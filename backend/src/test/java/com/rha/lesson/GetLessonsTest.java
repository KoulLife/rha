package com.rha.lesson;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GetLessonsTest extends LessonTestSupport {

    long roomId;
    long typeId;
    long instructorId;

    @BeforeEach
    void prepare() {
        roomId = insertRoom("A룸");
        typeId = insertLessonType("GROUP_3", "단체 3:1", 3, true);
        instructorId = insertInstructor("i@example.com", "김강사");
    }

    private long idOf(String email) {
        return jdbc.queryForObject("SELECT id FROM members WHERE email = ?", Long.class, email);
    }

    // ===== 목록: 노출 범위 =====

    @Test
    void 관리자는_모든_상태의_수업을_볼_수_있다() throws Exception {
        String token = adminToken();
        insertLesson("a", typeId, instructorId, roomId, at(1, 9), at(1, 10), "CREATED");
        insertLesson("b", typeId, instructorId, roomId, at(1, 10), at(1, 11), "SCHEDULED");
        insertLesson("c", typeId, instructorId, roomId, at(1, 11), at(1, 12), "PUBLISHED");
        insertLesson("d", typeId, instructorId, roomId, at(1, 12), at(1, 13), "UNPUBLISHED");
        insertLesson("e", typeId, instructorId, roomId, at(1, 13), at(1, 14), "CANCELED");

        list(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(5));
    }

    @Test
    void 회원은_공개된_수업만_볼_수_있다() throws Exception {
        String token = memberToken();
        insertLesson("a", typeId, instructorId, roomId, at(1, 9), at(1, 10), "CREATED");
        insertLesson("b", typeId, instructorId, roomId, at(1, 10), at(1, 11), "SCHEDULED");
        insertLesson("c", typeId, instructorId, roomId, at(1, 11), at(1, 12), "PUBLISHED");
        insertLesson("d", typeId, instructorId, roomId, at(1, 12), at(1, 13), "CANCELED");

        list(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("c"));
    }

    @Test
    void 회원이_다른_상태로_필터해도_결과에_나오지_않는다() throws Exception {
        String token = memberToken();
        insertLesson("a", typeId, instructorId, roomId, at(1, 9), at(1, 10), "CREATED");
        insertLesson("c", typeId, instructorId, roomId, at(1, 11), at(1, 12), "PUBLISHED");

        list(token, "status", "CREATED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void 강사는_공개된_수업과_본인_담당_수업과_권한이_있는_유형의_수업을_볼_수_있다() throws Exception {
        String token = instructorToken();
        long callerId = idOf("t@example.com");
        long otherInstructor = insertInstructor("i2@example.com", "박강사");
        long privateType = insertLessonType("PRIVATE_1", "개인", 1, true);
        long yogaType = insertLessonType("YOGA", "요가", 10, true);
        grant(callerId, "LESSON_UPDATE:PRIVATE_1", callerId);

        insertLesson("공개", typeId, otherInstructor, roomId, at(1, 9), at(1, 10), "PUBLISHED");
        insertLesson("내 수업", typeId, callerId, roomId, at(1, 10), at(1, 11), "CREATED");
        insertLesson("내 취소", typeId, callerId, roomId, at(1, 11), at(1, 12), "CANCELED");
        insertLesson("권한 유형", privateType, otherInstructor, roomId, at(1, 12), at(1, 13), "UNPUBLISHED");
        insertLesson("남의 비공개", typeId, otherInstructor, roomId, at(1, 13), at(1, 14), "CREATED");
        insertLesson("권한 없는 유형", yogaType, otherInstructor, roomId, at(1, 14), at(1, 15), "SCHEDULED");

        list(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(4))
                .andExpect(jsonPath("$.content[0].title").value("공개"))
                .andExpect(jsonPath("$.content[1].title").value("내 수업"))
                .andExpect(jsonPath("$.content[2].title").value("내 취소"))
                .andExpect(jsonPath("$.content[3].title").value("권한 유형"));
    }

    @Test
    void LESSON_CREATE_권한이_있는_유형의_수업도_볼_수_있다() throws Exception {
        String token = instructorToken();
        long callerId = idOf("t@example.com");
        long otherInstructor = insertInstructor("i2@example.com", "박강사");
        grant(callerId, "LESSON_CREATE:GROUP_3", callerId);
        insertLesson("권한 유형", typeId, otherInstructor, roomId, at(1, 9), at(1, 10), "CREATED");

        list(token)
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    // ===== 목록: 응답 형태, 정렬, 필터 =====

    @Test
    void 응답에_수업_정보와_pageInfo가_들어_있다() throws Exception {
        String token = adminToken();
        long lessonId = insertLesson("아침", typeId, instructorId, roomId, at(1, 9), at(1, 10), "PUBLISHED");

        list(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(lessonId))
                .andExpect(jsonPath("$.content[0].title").value("아침"))
                .andExpect(jsonPath("$.content[0].lessonType.code").value("GROUP_3"))
                .andExpect(jsonPath("$.content[0].instructor.name").value("김강사"))
                .andExpect(jsonPath("$.content[0].room.name").value("A룸"))
                .andExpect(jsonPath("$.content[0].capacity").value(3))
                .andExpect(jsonPath("$.content[0].reservedCount").value(0))
                .andExpect(jsonPath("$.content[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.pageInfo.page").value(1))
                .andExpect(jsonPath("$.pageInfo.size").value(20))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(1))
                .andExpect(jsonPath("$.pageInfo.totalPages").value(1));
    }

    @Test
    void 시작_시각_오름차순이_기본이고_같으면_id_오름차순이다() throws Exception {
        String token = adminToken();
        long otherRoom = insertRoom("B룸");
        long late = insertLesson("늦은", typeId, instructorId, roomId, at(1, 15), at(1, 16), "PUBLISHED");
        long first = insertLesson("같은시각1", typeId, instructorId, roomId, at(1, 9), at(1, 10), "PUBLISHED");
        long second = insertLesson("같은시각2", typeId, insertInstructor("i2@example.com", "박강사"), otherRoom,
                at(1, 9), at(1, 10), "PUBLISHED");

        list(token)
                .andExpect(jsonPath("$.content[0].id").value(first))
                .andExpect(jsonPath("$.content[1].id").value(second))
                .andExpect(jsonPath("$.content[2].id").value(late));
    }

    @Test
    void order가_DESC이면_시작_시각_내림차순이다() throws Exception {
        String token = adminToken();
        long early = insertLesson("이른", typeId, instructorId, roomId, at(1, 9), at(1, 10), "PUBLISHED");
        long late = insertLesson("늦은", typeId, instructorId, roomId, at(1, 15), at(1, 16), "PUBLISHED");

        list(token, "order", "DESC")
                .andExpect(jsonPath("$.content[0].id").value(late))
                .andExpect(jsonPath("$.content[1].id").value(early));
    }

    @Test
    void from은_포함하고_to는_포함하지_않는다() throws Exception {
        String token = adminToken();
        insertLesson("a", typeId, instructorId, roomId, at(1, 9), at(1, 10), "PUBLISHED");
        insertLesson("b", typeId, instructorId, roomId, at(1, 10), at(1, 11), "PUBLISHED");
        insertLesson("c", typeId, instructorId, roomId, at(1, 11), at(1, 12), "PUBLISHED");

        list(token, "from", at(1, 10).toString(), "to", at(1, 11).toString())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("b"));
    }

    @Test
    void from만_주면_이후_수업을_조회한다() throws Exception {
        String token = adminToken();
        insertLesson("a", typeId, instructorId, roomId, at(1, 9), at(1, 10), "PUBLISHED");
        insertLesson("b", typeId, instructorId, roomId, at(1, 10), at(1, 11), "PUBLISHED");

        list(token, "from", at(1, 10).toString())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("b"));
    }

    @Test
    void 유형_강사_룸_상태로_필터한다() throws Exception {
        String token = adminToken();
        long otherType = insertLessonType("PRIVATE_1", "개인", 1, true);
        long otherInstructor = insertInstructor("i2@example.com", "박강사");
        long otherRoom = insertRoom("B룸");
        insertLesson("기준", typeId, instructorId, roomId, at(1, 9), at(1, 10), "PUBLISHED");
        insertLesson("다른유형", otherType, instructorId, roomId, at(1, 10), at(1, 11), "PUBLISHED");
        insertLesson("다른강사", typeId, otherInstructor, roomId, at(1, 11), at(1, 12), "PUBLISHED");
        insertLesson("다른룸", typeId, instructorId, otherRoom, at(1, 12), at(1, 13), "PUBLISHED");
        insertLesson("다른상태", typeId, instructorId, roomId, at(1, 13), at(1, 14), "CREATED");

        list(token, "lessonTypeId", String.valueOf(otherType))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("다른유형"));
        list(token, "instructorId", String.valueOf(otherInstructor))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("다른강사"));
        list(token, "roomId", String.valueOf(otherRoom))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("다른룸"));
        list(token, "status", "CREATED")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("다른상태"));
    }

    @Test
    void 삭제된_룸의_수업도_룸_이름과_함께_조회된다() throws Exception {
        String token = adminToken();
        insertLesson("a", typeId, instructorId, roomId, at(1, 9), at(1, 10), "PUBLISHED");
        jdbc.update("UPDATE rooms SET deleted_at = now() WHERE id = ?", roomId);

        list(token)
                .andExpect(jsonPath("$.content[0].room.name").value("A룸"));
    }

    @Test
    void 페이지네이션이_동작한다() throws Exception {
        String token = adminToken();
        for (int i = 0; i < 5; i++) {
            insertLesson("수업" + i, typeId, instructorId, roomId, at(1, 9 + i), at(1, 10 + i), "PUBLISHED");
        }

        list(token, "page", "2", "size", "2")
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].title").value("수업2"))
                .andExpect(jsonPath("$.pageInfo.page").value(2))
                .andExpect(jsonPath("$.pageInfo.size").value(2))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(5))
                .andExpect(jsonPath("$.pageInfo.totalPages").value(3));
    }

    // ===== 목록: 400, 401 =====

    @ParameterizedTest
    @CsvSource({
            "page,0", "size,0", "size,101", "order,UP", "status,UNKNOWN", "from,not-a-date", "to,not-a-date",
            "lessonTypeId,abc"
    })
    void 잘못된_쿼리_파라미터면_400_INVALID_INPUT이다(String key, String value) throws Exception {
        String token = adminToken();

        list(token, key, value)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void to가_from보다_늦지_않으면_400이다() throws Exception {
        String token = adminToken();

        list(token, "from", at(1, 10).toString(), "to", at(1, 10).toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        list(token, "from", at(1, 10).toString(), "to", at(1, 9).toString())
                .andExpect(status().isBadRequest());
    }

    @Test
    void 인증하지_않으면_401이다() throws Exception {
        mockMvc.perform(get("/lessons"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // ===== 상세 =====

    @Test
    void 관리자는_어떤_상태의_수업이든_상세를_볼_수_있다() throws Exception {
        String token = adminToken();
        long lessonId = insertLesson("a", typeId, instructorId, roomId, at(1, 9), at(1, 10), "CREATED");

        detail(token, lessonId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(lessonId))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.lessonType.name").value("단체 3:1"))
                .andExpect(jsonPath("$.instructor.id").value(instructorId))
                .andExpect(jsonPath("$.room.id").value(roomId));
    }

    @Test
    void 회원은_공개된_수업의_상세를_볼_수_있다() throws Exception {
        String token = memberToken();
        long lessonId = insertLesson("a", typeId, instructorId, roomId, at(1, 9), at(1, 10), "PUBLISHED");

        detail(token, lessonId).andExpect(status().isOk());
    }

    @Test
    void 회원이_볼_수_없는_수업의_상세는_404이다() throws Exception {
        String token = memberToken();
        long lessonId = insertLesson("a", typeId, instructorId, roomId, at(1, 9), at(1, 10), "CREATED");

        detail(token, lessonId).andExpect(status().isNotFound());
    }

    @Test
    void 강사는_본인_담당의_비공개_수업_상세를_볼_수_있다() throws Exception {
        String token = instructorToken();
        long callerId = idOf("t@example.com");
        long lessonId = insertLesson("a", typeId, callerId, roomId, at(1, 9), at(1, 10), "CREATED");

        detail(token, lessonId).andExpect(status().isOk());
    }

    @Test
    void 강사가_볼_수_없는_수업의_상세는_404이다() throws Exception {
        String token = instructorToken();
        long lessonId = insertLesson("a", typeId, instructorId, roomId, at(1, 9), at(1, 10), "CREATED");

        detail(token, lessonId).andExpect(status().isNotFound());
    }

    @Test
    void 없는_수업이면_404이다() throws Exception {
        String token = adminToken();

        detail(token, 999999).andExpect(status().isNotFound());
    }

    @Test
    void lessonId가_숫자가_아니면_400이다() throws Exception {
        String token = adminToken();

        mockMvc.perform(get("/lessons/abc").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 상세도_인증하지_않으면_401이다() throws Exception {
        mockMvc.perform(get("/lessons/1")).andExpect(status().isUnauthorized());
    }
}
