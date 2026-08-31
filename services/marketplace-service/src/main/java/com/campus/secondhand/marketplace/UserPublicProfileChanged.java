package com.campus.secondhand.marketplace;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserPublicProfileChanged(String eventId, String correlationId, long userId, long version, String username,
        String nickname, String region, Integer creditScore, LocalDateTime lastActiveAt,
        String status, String role, LocalDateTime createdAt, LocalDateTime occurredAt) { }
