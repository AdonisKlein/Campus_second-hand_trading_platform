package com.campus.secondhand.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class VerificationLifecycleTest {
    @Autowired
    private VerificationService verification;
    @Autowired
    private AccountService accounts;
    @Autowired
    private UserRepository users;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM email_verification");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void fifthWrongAttemptPermanentlyConsumesChallenge() {
        verification.prepare("attempts@example.com", "REGISTER", "123456");

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> accounts.register(
                    "attempts@example.com", "attempt-user", null, "abc123", "000000"))
                    .isInstanceOf(InvalidVerificationCodeException.class);
        }

        assertThat(jdbc.queryForObject(
                "SELECT attempts FROM email_verification WHERE email=? AND purpose='REGISTER'",
                Integer.class, "attempts@example.com")).isEqualTo(5);
        assertThat(jdbc.queryForObject(
                "SELECT used FROM email_verification WHERE email=? AND purpose='REGISTER'",
                Boolean.class, "attempts@example.com")).isTrue();
        assertThatThrownBy(() -> accounts.register(
                "attempts@example.com", "attempt-user", null, "abc123", "123456"))
                .isInstanceOf(InvalidVerificationCodeException.class);
    }

    @Test
    void registerCodeCannotResetPasswordAndCanOnlyBeConsumedOnce() {
        verification.prepare("purpose@example.com", "REGISTER", "123456");

        assertThatThrownBy(() -> accounts.reset("purpose@example.com", "123456", "next123"))
                .isInstanceOf(InvalidVerificationCodeException.class);

        User created = accounts.register("purpose@example.com", "purpose-user", "同学", "abc123", "123456");
        assertThat(created.getId()).isNotNull();
        assertThatThrownBy(() -> accounts.register(
                "purpose@example.com", "another-user", null, "abc123", "123456"))
                .isInstanceOf(InvalidVerificationCodeException.class);
    }

    @Test
    void failedUserInsertRollsBackSuccessfulCodeConsumption() {
        User existing = new User();
        existing.setUsername("duplicate-name");
        existing.setEmail("existing@example.com");
        existing.setPasswordHash("not-used");
        users.saveAndFlush(existing);
        verification.prepare("retry@example.com", "REGISTER", "123456");

        assertThatThrownBy(() -> accounts.register(
                "retry@example.com", "duplicate-name", null, "abc123", "123456"))
                .isInstanceOf(DataIntegrityViolationException.class);

        User retried = accounts.register("retry@example.com", "available-name", null, "abc123", "123456");
        assertThat(retried.getId()).isNotNull();
    }
}
