package com.rha.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

class CreateReservationTest extends ReservationTestSupport {

    long roomId;
    long typeId;
    long instructorId;
    long productId;
    long memberId;
    long lessonId;
    String token;
    Instant lessonStart;

    @BeforeEach
    void prepare() {
        roomId = insertRoom();
        typeId = insertLessonType(3);
        instructorId = insertInstructor("i@example.com", "박강사");
        productId = insertProduct();
        memberId = insertMemberNamed("mem@example.com", "김회원");
        lessonStart = at(3, 10);
        lessonId = insertLesson(typeId, instructorId, roomId, lessonStart, 3, "PUBLISHED");
        token = loginAs("mem@example.com");
    }

    private String loginAs(String email) {
        try {
            return login(email);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private long activePass(long price, int remaining, int startOffset, int endOffset) {
        return insertPass(productId, memberId, instructorId, price, 10, remaining, days(startOffset),
                days(endOffset), "ACTIVE");
    }

    // ===== 성공 =====

    @Test
    void 예약하면_확정되고_이용권_횟수가_1회_차감된다() throws Exception {
        long passId = activePass(300000, 10, 0, 29);

        reserve(token, lessonId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.lesson.id").value(lessonId))
                .andExpect(jsonPath("$.lesson.title").value("수업"))
                .andExpect(jsonPath("$.lesson.instructor.name").value("박강사"))
                .andExpect(jsonPath("$.lesson.room.name").value("A룸"))
                .andExpect(jsonPath("$.lesson.startAt").value(lessonStart.toString()))
                .andExpect(jsonPath("$.lesson.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.member.id").value(memberId))
                .andExpect(jsonPath("$.member.name").value("김회원"))
                .andExpect(jsonPath("$.pass.id").value(passId))
                .andExpect(jsonPath("$.pass.passProduct.name").value("필라테스 10회권"))
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.attendanceConfirmedAt").doesNotExist())
                .andExpect(jsonPath("$.canceledBy").doesNotExist())
                .andExpect(jsonPath("$.createdAt").isString());

        assertThat(remaining(passId)).isEqualTo(9);
    }

    @Test
    void 예약하면_수업의_reservedCount가_늘어난다() throws Exception {
        activePass(300000, 10, 0, 29);

        reserve(token, lessonId).andExpect(status().isCreated());

        mockMvc.perform(get("/lessons/" + lessonId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.reservedCount").value(1));
    }

    // ===== 이용권 자동 선택 =====

    @Test
    void 만료일이_가장_이른_이용권을_쓰고_같으면_먼저_발급된_것을_쓴다() throws Exception {
        activePass(1, 10, 0, 40);
        long soonFirst = activePass(1, 10, 0, 10);
        long soonSecond = activePass(1, 10, 0, 10);

        reserve(token, lessonId).andExpect(jsonPath("$.pass.id").value(soonFirst));

        assertThat(remaining(soonFirst)).isEqualTo(9);
        assertThat(remaining(soonSecond)).isEqualTo(10);
    }

    @Test
    void 잔여_횟수가_없는_이용권은_건너뛴다() throws Exception {
        activePass(1, 0, 0, 10);
        long next = activePass(1, 5, 0, 40);

        reserve(token, lessonId).andExpect(jsonPath("$.pass.id").value(next));
    }

    @Test
    void 수업일이_이용권_기간_밖이면_건너뛴다() throws Exception {
        activePass(1, 10, 0, 2);
        activePass(1, 10, 4, 40);
        long fits = activePass(1, 10, 3, 3);

        reserve(token, lessonId).andExpect(jsonPath("$.pass.id").value(fits));
    }

    @Test
    void 시작_전_이용권도_수업일이_시작일_이후이면_쓸_수_있다() throws Exception {
        long upcoming = insertPass(productId, memberId, instructorId, 1, 10, 10, days(1), days(30), "UPCOMING");

        reserve(token, lessonId).andExpect(jsonPath("$.pass.id").value(upcoming));
    }

    @Test
    void 수업일이_일시정지_기간에_속하면_그_이용권은_건너뛴다() throws Exception {
        long paused = activePass(1, 10, 0, 10);
        insertPause(paused, days(2), days(4), "SCHEDULED", idOf("i@example.com"));
        long other = activePass(1, 10, 0, 40);

        reserve(token, lessonId).andExpect(jsonPath("$.pass.id").value(other));
    }

    @Test
    void 수업일을_포함하지_않는_정지와_취소된_정지는_영향이_없다() throws Exception {
        long passId = activePass(1, 10, 0, 10);
        insertPause(passId, days(5), days(6), "SCHEDULED", instructorId);
        insertPause(passId, days(2), days(4), "CANCELED", instructorId);

        reserve(token, lessonId).andExpect(jsonPath("$.pass.id").value(passId));
    }

    @Test
    void 미수_예약이_허용되지_않으면_완납한_이용권만_쓴다() throws Exception {
        setPolicy(24, false);
        long unpaidSoon = activePass(100000, 10, 0, 10);
        long partial = activePass(100000, 10, 0, 20);
        long paid = activePass(100000, 10, 0, 30);
        pay(partial, 50000, instructorId);
        pay(paid, 100000, instructorId);

        reserve(token, lessonId).andExpect(jsonPath("$.pass.id").value(paid));

        assertThat(remaining(unpaidSoon)).isEqualTo(10);
    }

    @Test
    void 금액이_0인_이용권은_완납으로_본다() throws Exception {
        setPolicy(24, false);
        long free = activePass(0, 10, 0, 10);

        reserve(token, lessonId).andExpect(jsonPath("$.pass.id").value(free));
    }

    @Test
    void 미수_예약이_허용되면_미결제_이용권으로도_예약한다() throws Exception {
        long unpaid = activePass(100000, 10, 0, 10);

        reserve(token, lessonId).andExpect(jsonPath("$.pass.id").value(unpaid));
    }

    @Test
    void 미수_예약이_불허이고_완납한_이용권이_없으면_409_PASS_UNPAID이다() throws Exception {
        setPolicy(24, false);
        long unpaid = activePass(100000, 10, 0, 10);

        reserve(token, lessonId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PASS_UNPAID"));
        assertThat(remaining(unpaid)).isEqualTo(10);
    }

    @Test
    void 사용할_이용권이_없으면_409_NO_AVAILABLE_PASS이다() throws Exception {
        reserve(token, lessonId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_AVAILABLE_PASS"));
        activePass(1, 0, 0, 10);
        reserve(token, lessonId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_AVAILABLE_PASS"));
        assertThat(reservationCount()).isZero();
    }

    @Test
    void 다른_회원의_이용권은_쓰지_않는다() throws Exception {
        long other = insertMemberNamed("other@example.com", "다른회원");
        insertPass(productId, other, instructorId, 1, 10, 10, days(0), days(30), "ACTIVE");

        reserve(token, lessonId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_AVAILABLE_PASS"));
    }

    // ===== 수업 상태·정원 =====

    @ParameterizedTest
    @ValueSource(strings = {"CREATED", "SCHEDULED", "UNPUBLISHED", "CANCELED"})
    void 공개되지_않았거나_취소된_수업은_409_LESSON_NOT_RESERVABLE이다(String lessonStatus) throws Exception {
        activePass(1, 10, 0, 29);
        long lesson = insertLesson(typeId, instructorId, roomId, at(4, 10), 3, lessonStatus);

        reserve(token, lesson)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LESSON_NOT_RESERVABLE"));
    }

    @Test
    void 이미_시작한_수업은_409_LESSON_NOT_RESERVABLE이다() throws Exception {
        activePass(1, 10, -5, 29);
        long started = insertLesson(typeId, instructorId, roomId, Instant.now().minusSeconds(3600), 3,
                "PUBLISHED");

        reserve(token, started)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LESSON_NOT_RESERVABLE"));
    }

    @Test
    void 없는_수업이면_404이다() throws Exception {
        reserve(token, 999999).andExpect(status().isNotFound());
    }

    @Test
    void 이미_예약_중이면_409_ALREADY_RESERVED이고_취소한_수업은_다시_예약할_수_있다() throws Exception {
        long passId = activePass(1, 10, 0, 29);
        long previous = insertReservation(lessonId, memberId, passId, "CANCELED");

        reserve(token, lessonId).andExpect(status().isCreated());
        reserve(token, lessonId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_RESERVED"));
        assertThat(reservationStatus(previous)).isEqualTo("CANCELED");
        assertThat(remaining(passId)).isEqualTo(9);
    }

    @Test
    void 정원이_차면_409_LESSON_FULL이고_취소된_예약은_세지_않는다() throws Exception {
        activePass(1, 10, 0, 29);
        long a = insertMemberNamed("a@example.com", "가");
        long b = insertMemberNamed("b@example.com", "나");
        long c = insertMemberNamed("c@example.com", "다");
        long d = insertMemberNamed("d@example.com", "라");
        long passA = insertPass(productId, a, instructorId, 1, 10, 10, days(0), days(29), "ACTIVE");
        insertReservation(lessonId, a, passA, "ATTENDED");
        insertReservation(lessonId, b, passA, "NO_SHOW");
        insertReservation(lessonId, c, passA, "RESERVED");
        insertReservation(lessonId, d, passA, "CANCELED");

        reserve(token, lessonId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LESSON_FULL"));

        jdbc.update("UPDATE reservations SET status = 'CANCELED' WHERE member_id = ?", c);
        reserve(token, lessonId).andExpect(status().isCreated());
    }

    @Test
    void 확인_순서는_수업_상태_중복_정원_이용권_순이다() throws Exception {
        // 이용권이 없어도 비공개 수업이면 LESSON_NOT_RESERVABLE이 먼저다
        long hidden = insertLesson(typeId, instructorId, roomId, at(4, 10), 3, "CREATED");
        reserve(token, hidden).andExpect(jsonPath("$.code").value("LESSON_NOT_RESERVABLE"));

        // 정원이 차고 이용권도 없으면 LESSON_FULL이 먼저다
        long full = insertLesson(typeId, instructorId, roomId, at(5, 10), 1, "PUBLISHED");
        long other = insertMemberNamed("o@example.com", "오");
        long otherPass = insertPass(productId, other, instructorId, 1, 10, 10, days(0), days(29), "ACTIVE");
        insertReservation(full, other, otherPass, "RESERVED");
        reserve(token, full).andExpect(jsonPath("$.code").value("LESSON_FULL"));
    }

    // ===== 권한·형식 =====

    @Test
    void 일반_회원이_아니면_403이다() throws Exception {
        activePass(1, 10, 0, 29);

        for (String other : new String[] {adminToken(), instructorToken()}) {
            reserve(other, lessonId)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        }
        assertThat(reservationCount()).isZero();
    }

    @Test
    void 비활성_회원의_기존_토큰은_403이다() throws Exception {
        activePass(1, 10, 0, 29);
        jdbc.update("UPDATE members SET status = 'INACTIVE' WHERE id = ?", memberId);

        reserve(token, lessonId).andExpect(status().isForbidden());
    }

    @Test
    void lessonId가_없거나_잘못되면_400이다() throws Exception {
        for (String json : new String[] {"{}", "{\"lessonId\":\"x\"}", "not json"}) {
            mockMvc.perform(post("/reservations").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
    }

    @Test
    void 일반_회원이_아니면_잘못된_본문이어도_403이다() throws Exception {
        mockMvc.perform(post("/reservations").header("Authorization", "Bearer " + instructorToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 인증하지_않으면_401이다() throws Exception {
        mockMvc.perform(post("/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lessonId\":" + lessonId + "}"))
                .andExpect(status().isUnauthorized());
    }
}
