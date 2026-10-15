package com.rha.pass;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 날짜에 따라 서버가 이용권과 일시정지 상태를 전환하는 동작. */
class PassStatusRefreshTest extends PassTestSupport {

    @Autowired
    PassService passService;

    long productId;
    long memberId;
    long instructorId;
    long adminId;

    @BeforeEach
    void prepare() {
        productId = insertProduct();
        memberId = insertMemberNamed("mem@example.com", "김회원");
        instructorId = insertInstructor("i@example.com", "박강사");
        adminId = insertMember("admin@example.com", "관리자", "ADMIN", "ACTIVE", BASE);
    }

    private long pass(String status, int startOffset, int endOffset) {
        return insertPass(productId, memberId, instructorId, 1, 10, 10, days(startOffset), days(endOffset), status);
    }

    @Test
    void 시작일이_되면_UPCOMING이_ACTIVE가_되고_아직이면_그대로이다() {
        long started = pass("UPCOMING", 0, 29);
        long later = pass("UPCOMING", 1, 30);

        passService.refreshStatuses(today());

        assertThat(passStatus(started)).isEqualTo("ACTIVE");
        assertThat(passStatus(later)).isEqualTo("UPCOMING");
    }

    @Test
    void 만료일이_지나면_EXPIRED가_되고_만료일_당일에는_그대로이다() {
        long expired = pass("ACTIVE", -30, -1);
        long lastDay = pass("ACTIVE", -29, 0);
        long upcomingButPast = pass("UPCOMING", -30, -1);

        passService.refreshStatuses(today());

        assertThat(passStatus(expired)).isEqualTo("EXPIRED");
        assertThat(passStatus(lastDay)).isEqualTo("ACTIVE");
        assertThat(passStatus(upcomingButPast)).isEqualTo("EXPIRED");
    }

    @Test
    void 정지가_시작되면_정지는_ACTIVE_이용권은_PAUSED가_된다() {
        long passId = pass("ACTIVE", -5, 24);
        long pause = insertPause(passId, days(0), days(2), "SCHEDULED", adminId);
        long futurePass = pass("ACTIVE", -5, 24);
        long futurePause = insertPause(futurePass, days(1), days(2), "SCHEDULED", adminId);

        passService.refreshStatuses(today());

        assertThat(pauseStatus(pause)).isEqualTo("ACTIVE");
        assertThat(passStatus(passId)).isEqualTo("PAUSED");
        assertThat(pauseStatus(futurePause)).isEqualTo("SCHEDULED");
        assertThat(passStatus(futurePass)).isEqualTo("ACTIVE");
    }

    @Test
    void 정지가_끝나면_정지는_ENDED_이용권은_ACTIVE로_돌아온다() {
        long passId = pass("PAUSED", -5, 24);
        long pause = insertPause(passId, days(-3), days(-1), "ACTIVE", adminId);

        passService.refreshStatuses(today());

        assertThat(pauseStatus(pause)).isEqualTo("ENDED");
        assertThat(passStatus(passId)).isEqualTo("ACTIVE");
    }

    @Test
    void 정지_종료일_당일에는_정지가_유지된다() {
        long passId = pass("PAUSED", -5, 24);
        long pause = insertPause(passId, days(-3), days(0), "ACTIVE", adminId);

        passService.refreshStatuses(today());

        assertThat(pauseStatus(pause)).isEqualTo("ACTIVE");
        assertThat(passStatus(passId)).isEqualTo("PAUSED");
    }

    @Test
    void 시작도_하기_전에_기간이_지난_정지는_ENDED가_된다() {
        long passId = pass("ACTIVE", -10, 24);
        long pause = insertPause(passId, days(-5), days(-3), "SCHEDULED", adminId);

        passService.refreshStatuses(today());

        assertThat(pauseStatus(pause)).isEqualTo("ENDED");
        assertThat(passStatus(passId)).isEqualTo("ACTIVE");
    }

    @Test
    void 취소된_정지는_이용권에_영향을_주지_않는다() {
        long passId = pass("ACTIVE", -5, 24);
        long pause = insertPause(passId, days(-1), days(3), "CANCELED", adminId);

        passService.refreshStatuses(today());

        assertThat(pauseStatus(pause)).isEqualTo("CANCELED");
        assertThat(passStatus(passId)).isEqualTo("ACTIVE");
    }

    @Test
    void 정지가_끝났는데_만료일도_지났으면_EXPIRED가_된다() {
        long passId = pass("PAUSED", -20, -1);
        long pause = insertPause(passId, days(-5), days(-2), "ACTIVE", adminId);

        passService.refreshStatuses(today());

        assertThat(pauseStatus(pause)).isEqualTo("ENDED");
        assertThat(passStatus(passId)).isEqualTo("EXPIRED");
    }

    @Test
    void 이미_만료로_기록된_이용권은_정지가_있어도_되살아나지_않는다() {
        long passId = pass("EXPIRED", -20, -1);

        passService.refreshStatuses(today());

        assertThat(passStatus(passId)).isEqualTo("EXPIRED");
    }

    @Test
    void 조회하면_최신_상태가_반영된다() throws Exception {
        String token = login("admin@example.com");
        long expired = pass("ACTIVE", -30, -1);

        detail(token, expired)
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.status").value("EXPIRED"));
        assertThat(passStatus(expired)).isEqualTo("EXPIRED");
    }
}
