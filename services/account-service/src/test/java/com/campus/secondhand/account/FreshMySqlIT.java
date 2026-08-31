package com.campus.secondhand.account;

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
            .withDatabaseName("account_it").withUsername("account").withPassword("account-pass");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired JdbcTemplate jdbc;

    @Test
    void freshDatabaseRunsAllOwnedMigrations() {
        assertThat(ownedTableCount("users", "email_verification", "account_event_inbox", "account_event_outbox"))
                .isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success = 1", Integer.class))
                .isEqualTo(3);
    }

    private int ownedTableCount(String... names) {
        String placeholders = String.join(",", java.util.Collections.nCopies(names.length, "?"));
        return jdbc.queryForObject("select count(*) from information_schema.tables where table_schema=database() and table_name in (" + placeholders + ")", Integer.class, (Object[]) names);
    }
}
