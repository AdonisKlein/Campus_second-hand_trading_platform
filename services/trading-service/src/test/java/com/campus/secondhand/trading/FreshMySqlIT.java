package com.campus.secondhand.trading;

import static org.assertj.core.api.Assertions.assertThat;

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

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class FreshMySqlIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("trading_it").withUsername("trading").withPassword("trading-pass");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired JdbcTemplate jdbc;

    @Test
    void freshDatabaseRunsAllOwnedMigrations() {
        assertThat(ownedTableCount("trade_orders", "chat_conversations", "chat_messages", "chat_blocks",
                "outbox_events", "inbox_events")).isEqualTo(6);
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success = 1", Integer.class))
                .isEqualTo(1);
    }

    private int ownedTableCount(String... names) {
        String placeholders = String.join(",", java.util.Collections.nCopies(names.length, "?"));
        return jdbc.queryForObject("select count(*) from information_schema.tables where table_schema=database() and table_name in (" + placeholders + ")", Integer.class, (Object[]) names);
    }
}
