package com.campus.secondhand.test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** A deterministic clock that tests can move forward without sleeping. */
public final class MutableTestClock extends Clock {
    private final AtomicReference<Instant> current;
    private final ZoneId zone;

    public MutableTestClock(Instant initial, ZoneId zone) {
        this.current = new AtomicReference<>(Objects.requireNonNull(initial));
        this.zone = Objects.requireNonNull(zone);
    }

    public static MutableTestClock at(Instant initial) {
        return new MutableTestClock(initial, ZoneId.of("Asia/Shanghai"));
    }

    public void advance(Duration duration) {
        current.updateAndGet(value -> value.plus(Objects.requireNonNull(duration)));
    }

    public void set(Instant instant) {
        current.set(Objects.requireNonNull(instant));
    }

    @Override public ZoneId getZone() { return zone; }

    @Override public Clock withZone(ZoneId zone) {
        return new MutableTestClock(current.get(), zone);
    }

    @Override public Instant instant() { return current.get(); }
}
