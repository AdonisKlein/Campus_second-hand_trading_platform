package com.campus.secondhand;

import java.time.Instant;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class MySqlFreshDeploymentTests {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
        .withDatabaseName("campus_secondhand")
        .withUsername("campus")
        .withPassword("test-only-password");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
        properties.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;

    @Test
    void freshSchemaValidatesAndJdbcSessionRowsCascade() {
        flyway.validate();
        assertEquals(1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class));

        long now = Instant.now().toEpochMilli();
        jdbc.update("INSERT INTO SPRING_SESSION "
                + "(PRIMARY_ID, SESSION_ID, CREATION_TIME, LAST_ACCESS_TIME, MAX_INACTIVE_INTERVAL, EXPIRY_TIME) "
                + "VALUES (?, ?, ?, ?, ?, ?)",
            "00000000-0000-0000-0000-000000000001",
            "00000000-0000-0000-0000-000000000002", now, now, 1800, now + 1_800_000);
        jdbc.update("INSERT INTO SPRING_SESSION_ATTRIBUTES "
                + "(SESSION_PRIMARY_ID, ATTRIBUTE_NAME, ATTRIBUTE_BYTES) VALUES (?, ?, ?)",
            "00000000-0000-0000-0000-000000000001", "smoke", new byte[] {1, 2, 3});
        jdbc.update("DELETE FROM SPRING_SESSION WHERE PRIMARY_ID = ?",
            "00000000-0000-0000-0000-000000000001");
        assertEquals(0, jdbc.queryForObject(
            "SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES WHERE ATTRIBUTE_NAME = 'smoke'", Integer.class));
    }
}
