package com.campus.secondhand.marketplace;
import java.util.Optional;
import java.time.LocalDateTime;
public interface AccountPublicPort {
    Optional<PublicAccount> findPublic(long userId);
    record PublicAccount(long id, String username, String nickname, String region,
                         Integer creditScore, String status, String role, LocalDateTime lastActiveAt) {}
}
