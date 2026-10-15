package com.rha;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 컨테이너는 테스트 JVM당 한 번만 띄워 모든 통합 테스트가 공유한다.
 * (클래스마다 내리면 캐시된 Spring 컨텍스트가 죽은 컨테이너를 바라보게 된다. 종료는 Ryuk이 정리한다.)
 */
@SpringBootTest(properties = {"rha.lesson.auto-publish.enabled=false", "rha.pass.status-refresh.enabled=false"})
public abstract class IntegrationTestSupport {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    private JdbcTemplate cleanerJdbc;

    /**
     * 컨테이너를 모든 테스트 클래스가 공유하므로, 앞선 클래스가 남긴 데이터가 외래키로 정리를 막지 않도록
     * 매 테스트 전에 모든 테이블을 외래키 순서(자식 → 부모)대로 비운다. 테이블이 늘면 여기에 추가한다.
     */
    @BeforeEach
    void cleanDatabase() {
        cleanerJdbc.update("DELETE FROM reservations");
        cleanerJdbc.update("DELETE FROM pass_payments");
        cleanerJdbc.update("DELETE FROM pass_pauses");
        cleanerJdbc.update("DELETE FROM passes");
        cleanerJdbc.update("DELETE FROM lessons");
        cleanerJdbc.update("DELETE FROM pass_products");
        cleanerJdbc.update("UPDATE reservation_policies SET cancel_deadline_hours = 24, allow_unpaid_reservation = true");
        cleanerJdbc.update("DELETE FROM absences");
        cleanerJdbc.update("DELETE FROM instructor_availabilities");
        cleanerJdbc.update("DELETE FROM member_permissions");
        cleanerJdbc.update("DELETE FROM password_reset_tokens");
        cleanerJdbc.update("DELETE FROM refresh_tokens");
        cleanerJdbc.update("DELETE FROM email_verifications");
        cleanerJdbc.update("DELETE FROM members");
        cleanerJdbc.update("DELETE FROM rooms");
        cleanerJdbc.update("DELETE FROM lesson_types");
        cleanerJdbc.update("UPDATE business_hours SET closed = true, open_time = NULL, close_time = NULL");
    }
}
