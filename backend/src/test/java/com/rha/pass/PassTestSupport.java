package com.rha.pass;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.rha.ApiTestSupport;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 이용권 API 테스트의 데이터 준비 도우미. 날짜는 센터 현지(Asia/Seoul) 기준이다. */
abstract class PassTestSupport extends ApiTestSupport {

    static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");
    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    static LocalDate today() {
        return LocalDate.now(SEOUL);
    }

    static LocalDate days(int offset) {
        return today().plusDays(offset);
    }

    long insertProduct(String name, long price, int totalCount, int validityDays) {
        return jdbc.queryForObject("""
                INSERT INTO pass_products (name, price, total_count, validity_days, created_at, updated_at)
                VALUES (?, ?, ?, ?, now(), now())
                RETURNING id
                """, Long.class, name, price, totalCount, validityDays);
    }

    long insertProduct() {
        return insertProduct("필라테스 10회권", 300000, 10, 30);
    }

    long insertInstructor(String email, String name) {
        return insertMember(email, name, "INSTRUCTOR", "ACTIVE", BASE);
    }

    long insertMemberNamed(String email, String name) {
        return insertMember(email, name, "MEMBER", "ACTIVE", BASE);
    }

    long insertPass(long productId, long memberId, long instructorId, long price, int total, int remaining,
            LocalDate start, LocalDate end, String status) {
        return jdbc.queryForObject("""
                INSERT INTO passes (pass_product_id, member_id, instructor_id, price, total_count, remaining_count,
                                    start_date, end_date, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                RETURNING id
                """, Long.class, productId, memberId, instructorId, price, total, remaining, Date.valueOf(start),
                Date.valueOf(end), status);
    }

    /** 오늘 시작해 30일 뒤까지 쓰는 10회 이용권. */
    long insertActivePass(long productId, long memberId, long instructorId) {
        return insertPass(productId, memberId, instructorId, 300000, 10, 10, today(), days(29), "ACTIVE");
    }

    long insertPause(long passId, LocalDate start, LocalDate end, String status, long createdBy) {
        return jdbc.queryForObject("""
                INSERT INTO pass_pauses (pass_id, start_date, end_date, status, created_by, created_at)
                VALUES (?, ?, ?, ?, ?, now())
                RETURNING id
                """, Long.class, passId, Date.valueOf(start), Date.valueOf(end), status, createdBy);
    }

    String passStatus(long passId) {
        return jdbc.queryForObject("SELECT status FROM passes WHERE id = ?", String.class, passId);
    }

    LocalDate endDateOf(long passId) {
        return jdbc.queryForObject("SELECT end_date FROM passes WHERE id = ?", Date.class, passId).toLocalDate();
    }

    int totalCountOf(long passId) {
        return jdbc.queryForObject("SELECT total_count FROM passes WHERE id = ?", Integer.class, passId);
    }

    int remainingCountOf(long passId) {
        return jdbc.queryForObject("SELECT remaining_count FROM passes WHERE id = ?", Integer.class, passId);
    }

    String pauseStatus(long pauseId) {
        return jdbc.queryForObject("SELECT status FROM pass_pauses WHERE id = ?", String.class, pauseId);
    }

    int passCount() {
        return jdbc.queryForObject("SELECT count(*) FROM passes", Integer.class);
    }

    long adminId() {
        return jdbc.queryForObject("SELECT id FROM members WHERE email = 'admin@example.com'", Long.class);
    }

    ResultActions createPass(String token, String json) throws Exception {
        return mockMvc.perform(post("/passes").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    ResultActions updatePass(String token, long passId, String json) throws Exception {
        return mockMvc.perform(patch("/passes/" + passId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    ResultActions detail(String token, long passId) throws Exception {
        return mockMvc.perform(get("/passes/" + passId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    ResultActions list(String token, String... keyValues) throws Exception {
        return listAt("/passes", token, keyValues);
    }

    ResultActions myPasses(String token, String... keyValues) throws Exception {
        return listAt("/members/me/passes", token, keyValues);
    }

    ResultActions listAt(String path, String token, String... keyValues) throws Exception {
        var request = get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        for (int i = 0; i < keyValues.length; i += 2) {
            request.param(keyValues[i], keyValues[i + 1]);
        }
        return mockMvc.perform(request);
    }

    ResultActions createPause(String token, long passId, String json) throws Exception {
        return mockMvc.perform(post("/passes/" + passId + "/pauses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    ResultActions terminate(String token, long passId, long pauseId) throws Exception {
        return mockMvc.perform(post("/passes/" + passId + "/pauses/" + pauseId + "/terminations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    static String passBody(long productId, long memberId, long instructorId, LocalDate start) {
        return "{\"passProductId\":" + productId + ",\"memberId\":" + memberId + ",\"instructorId\":"
                + instructorId + ",\"startDate\":\"" + start + "\"}";
    }
}
