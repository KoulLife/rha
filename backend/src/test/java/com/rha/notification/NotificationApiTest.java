package com.rha.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rha.ApiTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

class NotificationApiTest extends ApiTestSupport {

    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    long me;
    long other;
    String token;

    @BeforeEach
    void prepare() throws Exception {
        me = insertMember("me@example.com", "나", "MEMBER", "ACTIVE", BASE);
        other = insertMember("other@example.com", "남", "MEMBER", "ACTIVE", BASE);
        token = login("me@example.com");
    }

    private long insertNotification(long recipient, String type, Instant createdAt, boolean read) {
        return jdbc.queryForObject("""
                INSERT INTO notifications (recipient_id, type, title, message, related_type, related_id, read_at,
                                           created_at)
                VALUES (?, ?, '제목', '본문', 'LESSON', 15, ?, ?)
                RETURNING id
                """, Long.class, recipient, type, read ? Timestamp.from(createdAt.plusSeconds(60)) : null,
                Timestamp.from(createdAt));
    }

    private ResultActions list(String... keyValues) throws Exception {
        var request = get("/notifications").header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        for (int i = 0; i < keyValues.length; i += 2) {
            request.param(keyValues[i], keyValues[i + 1]);
        }
        return mockMvc.perform(request);
    }

    private ResultActions readOne(String who, long id) throws Exception {
        return mockMvc.perform(put("/notifications/" + id + "/read").header(HttpHeaders.AUTHORIZATION, "Bearer " + who));
    }

    private int unreadOf(long recipient) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE recipient_id = ? AND read_at IS NULL", Integer.class,
                recipient);
    }

    // ===== getMyNotifications =====

    @Test
    void 본인_알림만_최신순이고_같으면_id_내림차순이다() throws Exception {
        long old = insertNotification(me, "LESSON_CANCELED", BASE, false);
        Instant same = BASE.plusSeconds(3600);
        long first = insertNotification(me, "ABSENCE_APPROVED", same, false);
        long second = insertNotification(me, "ABSENCE_REJECTED", same, true);
        insertNotification(other, "LESSON_CANCELED", BASE.plusSeconds(7200), false);

        list()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].id").value(second))
                .andExpect(jsonPath("$.content[1].id").value(first))
                .andExpect(jsonPath("$.content[2].id").value(old))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(3));
    }

    @Test
    void 알림_필드가_담긴다() throws Exception {
        long unread = insertNotification(me, "LESSON_CANCELED", BASE, false);
        long read = insertNotification(me, "RESERVATION_CANCELED", BASE.plusSeconds(10), true);

        list()
                .andExpect(jsonPath("$.content[1].id").value(unread))
                .andExpect(jsonPath("$.content[1].type").value("LESSON_CANCELED"))
                .andExpect(jsonPath("$.content[1].title").value("제목"))
                .andExpect(jsonPath("$.content[1].message").value("본문"))
                .andExpect(jsonPath("$.content[1].relatedType").value("LESSON"))
                .andExpect(jsonPath("$.content[1].relatedId").value(15))
                .andExpect(jsonPath("$.content[1].read").value(false))
                .andExpect(jsonPath("$.content[1].readAt").doesNotExist())
                .andExpect(jsonPath("$.content[1].createdAt").isString())
                .andExpect(jsonPath("$.content[0].id").value(read))
                .andExpect(jsonPath("$.content[0].read").value(true))
                .andExpect(jsonPath("$.content[0].readAt").isString());
    }

    @Test
    void unread가_true이면_읽지_않은_알림만_false이거나_생략하면_전체이다() throws Exception {
        long unread = insertNotification(me, "LESSON_CANCELED", BASE, false);
        insertNotification(me, "ABSENCE_APPROVED", BASE.plusSeconds(10), true);

        list("unread", "true")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(unread));
        list("unread", "false").andExpect(jsonPath("$.content.length()").value(2));
        list().andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void 페이지네이션이_동작한다() throws Exception {
        for (int i = 0; i < 5; i++) {
            insertNotification(me, "LESSON_CANCELED", BASE.plusSeconds(i), false);
        }

        list("page", "2", "size", "2")
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.pageInfo.page").value(2))
                .andExpect(jsonPath("$.pageInfo.totalPages").value(3));
    }

    @ParameterizedTest
    @CsvSource({"page,0", "size,0", "size,101", "unread,maybe", "page,abc"})
    void 잘못된_파라미터는_400이다(String key, String value) throws Exception {
        list(key, value)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 인증하지_않으면_401이다() throws Exception {
        mockMvc.perform(get("/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/notifications/unread-count")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/notifications/read")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/notifications/1/read")).andExpect(status().isUnauthorized());
    }

    // ===== getMyUnreadNotificationCount =====

    @Test
    void 읽지_않은_알림_수는_본인_것만_센다() throws Exception {
        insertNotification(me, "LESSON_CANCELED", BASE, false);
        insertNotification(me, "ABSENCE_APPROVED", BASE.plusSeconds(1), false);
        insertNotification(me, "ABSENCE_REJECTED", BASE.plusSeconds(2), true);
        insertNotification(other, "LESSON_CANCELED", BASE.plusSeconds(3), false);

        mockMvc.perform(get("/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2));
    }

    @Test
    void 알림이_없으면_0이다() throws Exception {
        mockMvc.perform(get("/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    // ===== readAllNotifications =====

    @Test
    void 전체_읽음_처리하면_본인의_읽지_않은_알림이_모두_읽음이_된다() throws Exception {
        long mine1 = insertNotification(me, "LESSON_CANCELED", BASE, false);
        insertNotification(me, "ABSENCE_APPROVED", BASE.plusSeconds(1), false);
        long othersUnread = insertNotification(other, "LESSON_CANCELED", BASE.plusSeconds(2), false);

        mockMvc.perform(put("/notifications/read").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(unreadOf(me)).isZero();
        assertThat(unreadOf(other)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT read_at FROM notifications WHERE id = ?", Timestamp.class, mine1))
                .isNotNull();
        assertThat(jdbc.queryForObject("SELECT read_at FROM notifications WHERE id = ?", Timestamp.class,
                othersUnread)).isNull();
    }

    @Test
    void 이미_모두_읽었거나_알림이_없어도_204이고_읽은_시각은_유지된다() throws Exception {
        long id = insertNotification(me, "LESSON_CANCELED", BASE, true);
        Timestamp before = jdbc.queryForObject("SELECT read_at FROM notifications WHERE id = ?", Timestamp.class,
                id);

        mockMvc.perform(put("/notifications/read").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/notifications/read").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT read_at FROM notifications WHERE id = ?", Timestamp.class, id))
                .isEqualTo(before);
    }

    // ===== readNotification =====

    @Test
    void 알림_하나를_읽음_처리한다() throws Exception {
        long id = insertNotification(me, "LESSON_CANCELED", BASE, false);
        long untouched = insertNotification(me, "ABSENCE_APPROVED", BASE.plusSeconds(1), false);

        readOne(token, id).andExpect(status().isNoContent()).andExpect(content().string(""));

        assertThat(jdbc.queryForObject("SELECT read_at FROM notifications WHERE id = ?", Timestamp.class, id))
                .isNotNull();
        assertThat(jdbc.queryForObject("SELECT read_at FROM notifications WHERE id = ?", Timestamp.class,
                untouched)).isNull();
    }

    @Test
    void 이미_읽은_알림도_204이고_읽은_시각은_바뀌지_않는다() throws Exception {
        long id = insertNotification(me, "LESSON_CANCELED", BASE, true);
        Timestamp before = jdbc.queryForObject("SELECT read_at FROM notifications WHERE id = ?", Timestamp.class,
                id);

        readOne(token, id).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT read_at FROM notifications WHERE id = ?", Timestamp.class, id))
                .isEqualTo(before);
    }

    @Test
    void 다른_사용자의_알림이거나_없으면_404이다() throws Exception {
        long othersId = insertNotification(other, "LESSON_CANCELED", BASE, false);

        readOne(token, othersId).andExpect(status().isNotFound());
        readOne(token, 999999).andExpect(status().isNotFound());
        assertThat(unreadOf(other)).isEqualTo(1);
        mockMvc.perform(put("/notifications/abc/read").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }
}
