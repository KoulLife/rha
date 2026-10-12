package com.rha;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RhaApplicationTests extends IntegrationTestSupport {

    @Autowired
    DataSource dataSource;

    @Test
    void 컨텍스트가_로드되고_DB에_연결된다() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.isValid(1)).isTrue();
        }
    }
}
