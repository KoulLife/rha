package com.rha.common.config;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;
import org.springframework.context.annotation.Configuration;

/**
 * JVM 기본 시간대를 UTC로 고정한다. Hibernate의 JDBC 시간대(`hibernate.jdbc.time_zone`)가 UTC인데
 * JVM 시간대가 다르면 TIME 컬럼(영업시간)의 값이 시간대 차이만큼 밀려 저장·조회된다.
 * 센터 현지 시각(Asia/Seoul)은 필요한 곳에서 명시적으로 변환한다.
 */
@Configuration
public class TimeZoneConfig {

    @PostConstruct
    void useUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
}
