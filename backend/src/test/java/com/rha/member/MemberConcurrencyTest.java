package com.rha.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.rha.ConcurrencyTestSupport;
import com.rha.auth.AccountWithdrawalService;
import com.rha.lesson.LessonCreateRequest;
import com.rha.lesson.LessonService;
import com.rha.reservation.ReservationCreateRequest;
import com.rha.reservation.ReservationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 회원 상태·역할 변경이 예약·수업 개설과 동시에 들어올 때 "시작 전 예약·수업 없음" 규칙이 깨지지 않는지. */
class MemberConcurrencyTest extends ConcurrencyTestSupport {

    @Autowired
    MemberAdminService memberAdminService;

    @Autowired
    AccountWithdrawalService accountWithdrawalService;

    @Autowired
    ReservationService reservationService;

    @Autowired
    LessonService lessonService;

    @Test
    void 비활성화와_예약이_동시에_들어와도_비활성_회원에게_예약이_남지_않는다() throws Exception {
        long admin = insertAdmin();
        long instructor = insertInstructor("t@example.com");
        long lesson = insertLesson(insertLessonType(), instructor, insertRoom("A룸"), at(3, 10), "PUBLISHED");
        long product = insertProduct(10);

        for (int round = 0; round < 8; round++) {
            long member = insertActiveMember("m" + round + "@example.com");
            insertPass(product, member, instructor, 10, 10);

            List<Outcome> outcomes = runConcurrently(List.of(
                    () -> memberAdminService.changeMemberStatus(admin, member,
                            new MemberStatusChangeRequest("INACTIVE")),
                    () -> reservationService.createReservation(member, new ReservationCreateRequest(lesson))));
            failureCodes(outcomes);

            assertThat(memberStatus(member).equals("INACTIVE") && reservedCountOfMember(member) > 0)
                    .as("비활성 회원에게 예약이 남았다").isFalse();
        }
    }

    @Test
    void 탈퇴와_예약이_동시에_들어와도_탈퇴한_회원에게_예약이_남지_않는다() throws Exception {
        long instructor = insertInstructor("t@example.com");
        long lesson = insertLesson(insertLessonType(), instructor, insertRoom("A룸"), at(3, 10), "PUBLISHED");
        long product = insertProduct(10);

        for (int round = 0; round < 8; round++) {
            long member = insertActiveMember("m" + round + "@example.com");
            insertPass(product, member, instructor, 10, 10);

            List<Outcome> outcomes = runConcurrently(List.of(
                    () -> {
                        accountWithdrawalService.withdraw(member);
                        return null;
                    },
                    () -> reservationService.createReservation(member, new ReservationCreateRequest(lesson))));
            failureCodes(outcomes);

            assertThat(memberStatus(member).equals("WITHDRAWN") && reservedCountOfMember(member) > 0)
                    .as("탈퇴한 회원에게 예약이 남았다").isFalse();
        }
    }

    @Test
    void 강사_역할_변경과_수업_개설이_동시에_들어와도_강사가_아닌_회원이_수업을_맡지_않는다() throws Exception {
        openEveryDay();
        long admin = insertAdmin();
        long type = insertLessonType();
        long room = insertRoom("A룸");

        for (int round = 0; round < 8; round++) {
            long instructor = insertInstructor("t" + round + "@example.com");
            Instant start = at(3 + round, 10);
            MemberAdminUpdateRequest toMember = new MemberAdminUpdateRequest();
            toMember.setRole("MEMBER");

            List<Outcome> outcomes = runConcurrently(List.of(
                    () -> memberAdminService.updateMember(admin, instructor, toMember),
                    () -> lessonService.createLesson(admin, new LessonCreateRequest("수업", type, instructor, room,
                            start.toString(), start.plusSeconds(3600).toString()))));
            failureCodes(outcomes);

            String role = jdbc.queryForObject("SELECT role FROM members WHERE id = ?", String.class, instructor);
            int lessons = jdbc.queryForObject(
                    "SELECT count(*) FROM lessons WHERE instructor_id = ? AND status <> 'CANCELED'", Integer.class,
                    instructor);
            assertThat(role.equals("MEMBER") && lessons > 0).as("강사가 아닌 회원이 수업을 맡았다").isFalse();
        }
    }

    private String memberStatus(long memberId) {
        return jdbc.queryForObject("SELECT status FROM members WHERE id = ?", String.class, memberId);
    }

    private int reservedCountOfMember(long memberId) {
        return jdbc.queryForObject("SELECT count(*) FROM reservations WHERE member_id = ? AND status = 'RESERVED'",
                Integer.class, memberId);
    }
}
