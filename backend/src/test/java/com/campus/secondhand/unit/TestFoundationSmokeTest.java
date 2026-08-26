package com.campus.secondhand.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.campus.secondhand.item.Item;
import com.campus.secondhand.test.MutableTestClock;
import com.campus.secondhand.test.TestDataFactory;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TestFoundationSmokeTest {
    @Test
    void factoryCreatesStableDomainFixture() {
        Item item = TestDataFactory.item(7, 3, "Algorithms", BigDecimal.valueOf(12.50));
        assertEquals(7L, item.getId());
        assertEquals(3L, item.getSellerId());
        assertEquals(BigDecimal.valueOf(12.50), item.getPrice());
    }

    @Test
    void mutableClockAdvancesDeterministically() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        MutableTestClock clock = MutableTestClock.at(start);
        assertSame(start, clock.instant());
        clock.advance(Duration.ofHours(24));
        assertEquals(start.plus(Duration.ofDays(1)), clock.instant());
    }
}
