package com.campus.secondhand.user;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class VerificationRateLimiter {
    private final ConcurrentHashMap<String, Deque<Instant>> windows = new ConcurrentHashMap<>();

    public void check(String email, VerificationPurpose purpose, String ip) {
        take("email:" + purpose + ":" + email, 5, Duration.ofHours(1));
        take("ip:" + ip, 20, Duration.ofHours(1));
        take("global", 500, Duration.ofHours(1));
    }

    private void take(String key, int limit, Duration window) {
        Deque<Instant> entries = windows.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (entries) {
            Instant cutoff = Instant.now().minus(window);
            while (!entries.isEmpty() && entries.peekFirst().isBefore(cutoff)) entries.removeFirst();
            if (entries.size() >= limit) throw new VerificationRateLimitException("验证码请求过于频繁，请稍后再试");
            entries.addLast(Instant.now());
        }
    }
}
