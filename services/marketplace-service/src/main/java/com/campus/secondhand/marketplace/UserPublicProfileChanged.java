package com.campus.secondhand.marketplace;

import java.time.LocalDateTime;

public record UserPublicProfileChanged(String eventId, long userId, long version, String username,
        String nickname, String region, Integer creditScore, LocalDateTime lastActiveAt,
        String status, String role, LocalDateTime createdAt, LocalDateTime occurredAt) { }
