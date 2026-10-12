package com.rha;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 컨테이너는 테스트 JVM당 한 번만 띄워 모든 통합 테스트가 공유한다.
 * (클래스마다 내리면 캐시된 Spring 컨텍스트가 죽은 컨테이너를 바라보게 된다. 종료는 Ryuk이 정리한다.)
 */
@SpringBootTest
public abstract class IntegrationTestSupport {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }
}
