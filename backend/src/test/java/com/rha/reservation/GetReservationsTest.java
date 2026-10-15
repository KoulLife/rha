package com.rha.reservation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GetReservationsTest extends ReservationTestSupport {

    long roomId;
    long typeId;
    long instructorId;
    long productId;
    long memberA;
    long memberB;
    long passA;
    long passB;
    long lessonEarly;
    long lessonLate;

    @BeforeEach
    void prepare() {
        roomId = insertRoom();
        typeId = insertLessonType(5);
        instructorId = insertInstructor("i@example.com", "박강사");
        productId = insertProduct();
        memberA = insertMemberNamed("a@example.com", "김회원");
        memberB = insertMemberNamed("b@example.com", "이회원");
        passA = insertPass(productId, memberA, instructorId, 1, 10, 10, days(-10), days(30), "ACTIVE");
        passB = insertPass(productId, memberB, instructorId, 1, 10, 10, days(-10), days(30), "ACTIVE");
        lessonEarly = insertLesson(typeId, instructorId, roomId, at(2, 10), 5, "PUBLISHED");
        lessonLate = insertLesson(typeId, instructorId, roomId, at(5, 10), 5, "PUBLISHED");
    }

    // ===== getReservations =====

    @Test
    void 관리자는_수업_시작_시각_내림차순으로_조회하고_같으면_id_내림차순이다() throws Exception {
        long first = insertReservation(lessonEarly, memberA, passA, "RESERVED");
        long second = insertReservation(lessonEarly, memberB, passB, "RESERVED");
        long late = insertReservation(lessonLate, memberA, passA, "RESERVED");
        String token = adminToken();

        list(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].id").value(late))
                .andExpect(jsonPath("$.content[1].id").value(second))
                .andExpect(jsonPath("$.content[2].id").value(first))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(3));
        list(token, "order", "ASC")
                .andExpect(jsonPath("$.content[0].id").value(first))
                .andExpect(jsonPath("$.content[1].id").value(second))
                .andExpect(jsonPath("$.content[2].id").value(late));
    }

    @Test
    void MEMBER_READ_권한이_있는_강사도_조회할_수_있다() throws Exception {
        insertReservation(lessonEarly, memberA, passA, "RESERVED");
        String token = instructorToken();
        grant(idOf("t@example.com"), "MEMBER_READ", idOf("t@example.com"));

        list(token).andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void 권한이_없는_강사와_회원은_403이다() throws Exception {
        list(instructorToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        list(memberToken()).andExpect(status().isForbidden());
        mockMvc.perform(get("/reservations")).andExpect(status().isUnauthorized());
    }

    @Test
    void 회원_수업_상태_기간으로_필터한다() throws Exception {
        long a = insertReservation(lessonEarly, memberA, passA, "RESERVED");
        long b = insertReservation(lessonEarly, memberB, passB, "CANCELED");
        long c = insertReservation(lessonLate, memberA, passA, "ATTENDED");
        String token = adminToken();

        list(token, "memberId", String.valueOf(memberB))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(b));
        list(token, "lessonId", String.valueOf(lessonLate))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(c));
        list(token, "status", "RESERVED")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(a));
        list(token, "lessonFrom", at(3, 0).toString())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(c));
        list(token, "lessonTo", at(3, 0).toString())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void 페이지네이션이_동작한다() throws Exception {
        for (int i = 0; i < 3; i++) {
            long member = insertMemberNamed("p" + i + "@example.com", "회원" + i);
            long pass = insertPass(productId, member, instructorId, 1, 10, 10, days(-1), days(30), "ACTIVE");
            insertReservation(lessonEarly, member, pass, "RESERVED");
        }
        String token = adminToken();

        list(token, "page", "2", "size", "2")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.pageInfo.page").value(2))
                .andExpect(jsonPath("$.pageInfo.totalPages").value(2));
    }

    @ParameterizedTest
    @CsvSource({"page,0", "size,101", "status,UNKNOWN", "order,UP", "lessonFrom,not-a-date", "memberId,abc"})
    void 잘못된_파라미터는_400이다(String key, String value) throws Exception {
        list(adminToken(), key, value)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void lessonTo가_lessonFrom보다_늦지_않으면_400이다() throws Exception {
        list(adminToken(), "lessonFrom", at(3, 0).toString(), "lessonTo", at(3, 0).toString())
                .andExpect(status().isBadRequest());
    }

    // ===== getReservation =====

    @Test
    void 본인_관리자_담당_강사_MEMBER_READ_강사는_상세를_볼_수_있다() throws Exception {
        long id = insertReservation(lessonEarly, memberA, passA, "RESERVED");
        String admin = adminToken();
        String owner = login("a@example.com");
        String lessonInstructor = login("i@example.com");
        String reader = instructorToken();
        grant(idOf("t@example.com"), "MEMBER_READ", idOf("t@example.com"));

        for (String token : new String[] {admin, owner, lessonInstructor, reader}) {
            detail(token, id)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.member.id").value(memberA))
                    .andExpect(jsonPath("$.lesson.id").value(lessonEarly))
                    .andExpect(jsonPath("$.pass.id").value(passA));
        }
    }

    @Test
    void 볼_수_없는_예약은_404이다() throws Exception {
        long id = insertReservation(lessonEarly, memberA, passA, "RESERVED");
        insertInstructor("other@example.com", "다른강사");

        String admin = adminToken();
        detail(login("b@example.com"), id).andExpect(status().isNotFound());
        detail(login("other@example.com"), id).andExpect(status().isNotFound());
        detail(admin, 999999).andExpect(status().isNotFound());
        mockMvc.perform(get("/reservations/abc").header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 상세도_인증하지_않으면_401이다() throws Exception {
        mockMvc.perform(get("/reservations/1")).andExpect(status().isUnauthorized());
    }

    // ===== getLessonReservations =====

    @Test
    void 수업_예약자는_예약_시각_오름차순이고_상태로_필터한다() throws Exception {
        long first = insertReservation(lessonEarly, memberA, passA, "RESERVED");
        long second = insertReservation(lessonEarly, memberB, passB, "CANCELED");
        insertReservation(lessonLate, memberA, passA, "RESERVED");
        String admin = adminToken();

        ofLesson(admin, lessonEarly)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(first))
                .andExpect(jsonPath("$.content[1].id").value(second));
        ofLesson(login("i@example.com"), lessonEarly, "status", "CANCELED")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(second));
    }

    @Test
    void 담당이_아닌_강사와_회원은_403이고_없는_수업은_404이다() throws Exception {
        insertInstructor("other@example.com", "다른강사");

        ofLesson(login("other@example.com"), lessonEarly)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        ofLesson(login("a@example.com"), lessonEarly).andExpect(status().isForbidden());
        ofLesson(adminToken(), 999999).andExpect(status().isNotFound());
        ofLesson(login("other@example.com"), 999999).andExpect(status().isForbidden());
    }

    @Test
    void 수업_예약자_목록의_잘못된_파라미터는_400이다() throws Exception {
        String admin = adminToken();
        ofLesson(admin, lessonEarly, "status", "UNKNOWN").andExpect(status().isBadRequest());
        ofLesson(admin, lessonEarly, "size", "0").andExpect(status().isBadRequest());
    }

    // ===== getMyReservations =====

    @Test
    void 내_예약만_보고_취소된_수업의_예약도_포함한다() throws Exception {
        long canceledLesson = insertLesson(typeId, instructorId, roomId, at(7, 10), 5, "CANCELED");
        long early = insertReservation(lessonEarly, memberA, passA, "RESERVED");
        long late = insertReservation(canceledLesson, memberA, passA, "CANCELED");
        insertReservation(lessonEarly, memberB, passB, "RESERVED");
        String token = login("a@example.com");

        mine(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(late))
                .andExpect(jsonPath("$.content[1].id").value(early))
                .andExpect(jsonPath("$.content[0].lesson.status").value("CANCELED"));
        mine(token, "order", "ASC").andExpect(jsonPath("$.content[0].id").value(early));
        mine(token, "status", "CANCELED")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(late));
        mine(token, "lessonFrom", at(3, 0).toString()).andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void 일반_회원이_아니면_내_예약은_빈_목록이다() throws Exception {
        insertReservation(lessonEarly, memberA, passA, "RESERVED");

        mine(adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(0));
    }

    @Test
    void 내_예약의_잘못된_파라미터는_400이고_인증이_없으면_401이다() throws Exception {
        String member = memberToken();
        mine(member, "order", "UP").andExpect(status().isBadRequest());
        mine(member, "page", "0").andExpect(status().isBadRequest());
        mockMvc.perform(get("/members/me/reservations")).andExpect(status().isUnauthorized());
    }
}
