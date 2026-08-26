package com.campus.secondhand.unit;

import com.campus.secondhand.test.MutableTestClock;
import com.campus.secondhand.user.VerificationPurpose;
import com.campus.secondhand.user.VerificationRateLimitException;
import com.campus.secondhand.user.VerificationRateLimiter;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VerificationRateLimiterTests {
    @Test
    void emailCanRequestAtMostFiveCodesPerHour() {
        MutableTestClock clock = new MutableTestClock(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));
        VerificationRateLimiter limiter = new VerificationRateLimiter(clock);

        for (int i = 0; i < 5; i++) limiter.check("student@example.com", VerificationPurpose.REGISTER, "127.0.0.1");
        assertThrows(VerificationRateLimitException.class,
            () -> limiter.check("student@example.com", VerificationPurpose.REGISTER, "127.0.0.1"));
        clock.advance(Duration.ofHours(1).plusSeconds(1));
        assertDoesNotThrow(() -> limiter.check("student@example.com", VerificationPurpose.REGISTER, "127.0.0.1"));
    }
}
