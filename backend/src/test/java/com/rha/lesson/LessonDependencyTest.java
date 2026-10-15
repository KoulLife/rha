package com.rha.lesson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 수업이 생기면서 룸 삭제, 강사 역할·상태 변경이 받게 된 제약과 공개 예약 자동 공개를 검증한다. */
class LessonDependencyTest extends LessonTestSupport {

    @Autowired
    LessonService lessonService;

    long roomId;
    long typeId;
    long instructorId;

    @BeforeEach
    void prepare() {
        roomId = insertRoom("A룸");
        typeId = insertLessonType("GROUP_3", "단체 3:1", 3, true);
        instructorId = insertInstructor("i@example.com", "김강사");
    }

    private ResultActions deleteRoom(String token, long id) throws Exception {
        return mockMvc.perform(delete("/rooms/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions changeRole(String token, long memberId, String role) throws Exception {
        return mockMvc.perform(patch("/members/" + memberId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"" + role + "\"}"));
    }

    private ResultActions changeMemberStatus(String token, long memberId, String memberStatus) throws Exception {
        return mockMvc.perform(put("/members/" + memberId + "/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + memberStatus + "\"}"));
    }

    // ===== deleteRoom: ROOM_IN_USE =====

    @Test
    void 시작_전이고_취소되지_않은_수업이_있는_룸은_409_ROOM_IN_USE이다() throws Exception {
        String token = adminToken();
        insertLesson("예정", typeId, instructorId, roomId, at(1, 10), at(1, 11), "CREATED");

        deleteRoom(token, roomId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_IN_USE"));
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM rooms WHERE id = ?", Timestamp.class, roomId))
                .isNull();
    }

    @Test
    void 취소된_수업만_있는_룸은_삭제할_수_있다() throws Exception {
        String token = adminToken();
        insertLesson("취소", typeId, instructorId, roomId, at(1, 10), at(1, 11), "CANCELED");

        deleteRoom(token, roomId).andExpect(status().isNoContent());
    }

    @Test
    void 이미_시작한_수업만_있는_룸은_삭제할_수_있다() throws Exception {
        String token = adminToken();
        insertPastLesson("지난 수업", typeId, instructorId, roomId, "PUBLISHED");

        deleteRoom(token, roomId).andExpect(status().isNoContent());
    }

    @Test
    void 다른_룸의_수업은_삭제를_막지_않는다() throws Exception {
        String token = adminToken();
        long otherRoom = insertRoom("B룸");
        insertLesson("예정", typeId, instructorId, otherRoom, at(1, 10), at(1, 11), "PUBLISHED");

        deleteRoom(token, roomId).andExpect(status().isNoContent());
    }

    // ===== updateMember: 강사 역할 변경 =====

    @Test
    void 시작_전_담당_수업이_있는_강사의_역할은_바꿀_수_없다() throws Exception {
        String token = adminToken();
        insertLesson("예정", typeId, instructorId, roomId, at(1, 10), at(1, 11), "PUBLISHED");
        grant(instructorId, "MEMBER_READ", instructorId);

        changeRole(token, instructorId, "MEMBER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTRUCTOR_HAS_UPCOMING_LESSONS"));

        assertThat(jdbc.queryForObject("SELECT role FROM members WHERE id = ?", String.class, instructorId))
                .isEqualTo("INSTRUCTOR");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_permissions WHERE member_id = ?",
                Integer.class, instructorId)).isEqualTo(1);
    }

    @Test
    void 취소되었거나_이미_시작한_수업만_있으면_역할을_바꿀_수_있다() throws Exception {
        String token = adminToken();
        insertLesson("취소", typeId, instructorId, roomId, at(1, 10), at(1, 11), "CANCELED");
        insertPastLesson("지난 수업", typeId, instructorId, roomId, "PUBLISHED");

        changeRole(token, instructorId, "MEMBER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"));
    }

    @Test
    void 역할이_바뀌지_않는_수정은_담당_수업이_있어도_가능하다() throws Exception {
        String token = adminToken();
        insertLesson("예정", typeId, instructorId, roomId, at(1, 10), at(1, 11), "PUBLISHED");

        mockMvc.perform(patch("/members/" + instructorId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"새이름\",\"role\":\"INSTRUCTOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("새이름"));
    }

    // ===== changeMemberStatus: 강사 비활성화 =====

    @Test
    void 시작_전_담당_수업이_있는_강사는_비활성화할_수_없다() throws Exception {
        String token = adminToken();
        insertLesson("예정", typeId, instructorId, roomId, at(1, 10), at(1, 11), "CREATED");

        changeMemberStatus(token, instructorId, "INACTIVE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTRUCTOR_HAS_UPCOMING_LESSONS"));

        assertThat(jdbc.queryForObject("SELECT status FROM members WHERE id = ?", String.class, instructorId))
                .isEqualTo("ACTIVE");
    }

    @Test
    void 수업을_취소하면_강사를_비활성화할_수_있다() throws Exception {
        String token = adminToken();
        long lessonId = insertLesson("예정", typeId, instructorId, roomId, at(1, 10), at(1, 11), "PUBLISHED");

        cancel(token, lessonId, null).andExpect(status().isOk());

        changeMemberStatus(token, instructorId, "INACTIVE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void 이미_시작한_수업만_있는_강사는_비활성화할_수_있다() throws Exception {
        String token = adminToken();
        insertPastLesson("지난 수업", typeId, instructorId, roomId, "PUBLISHED");

        changeMemberStatus(token, instructorId, "INACTIVE").andExpect(status().isOk());
    }

    // ===== 공개 예약 자동 공개 =====

    @Test
    void 공개_예약_시각이_지난_수업만_PUBLISHED로_바뀌고_예약_시각이_지워진다() throws Exception {
        Instant now = Instant.parse("2030-01-01T00:00:00Z");
        long due = insertLesson("도래", typeId, instructorId, roomId, at(5, 10), at(5, 11), "SCHEDULED");
        long exact = insertLesson("정각", typeId, instructorId, roomId, at(5, 12), at(5, 13), "SCHEDULED");
        long later = insertLesson("아직", typeId, instructorId, roomId, at(5, 14), at(5, 15), "SCHEDULED");
        long created = insertLesson("개설", typeId, instructorId, roomId, at(5, 16), at(5, 17), "CREATED");
        jdbc.update("UPDATE lessons SET publish_at = ? WHERE id = ?", Timestamp.from(now.minusSeconds(60)), due);
        jdbc.update("UPDATE lessons SET publish_at = ? WHERE id = ?", Timestamp.from(now), exact);
        jdbc.update("UPDATE lessons SET publish_at = ? WHERE id = ?", Timestamp.from(now.plusSeconds(60)), later);

        int published = lessonService.publishDueLessons(now);

        assertThat(published).isEqualTo(2);
        assertThat(lessonStatus(due)).isEqualTo("PUBLISHED");
        assertThat(lessonStatus(exact)).isEqualTo("PUBLISHED");
        assertThat(lessonStatus(later)).isEqualTo("SCHEDULED");
        assertThat(lessonStatus(created)).isEqualTo("CREATED");
        assertThat(jdbc.queryForObject("SELECT publish_at FROM lessons WHERE id = ?", Timestamp.class, due))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT publish_at FROM lessons WHERE id = ?", Timestamp.class, later))
                .isNotNull();
    }

    @Test
    void 공개할_수업이_없으면_0을_반환한다() {
        assertThat(lessonService.publishDueLessons(Instant.now())).isZero();
    }
}
