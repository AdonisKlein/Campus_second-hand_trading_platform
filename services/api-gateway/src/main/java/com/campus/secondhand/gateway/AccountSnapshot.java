package com.campus.secondhand.gateway;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

record LoginCredentials(@NotBlank @Email String email, @NotBlank String password) {}

record AuthenticatedAccount(Long userId, String email, String username, String nickname, String phone,
                            String role, String status, String campusRegion, Integer creditScore,
                            LocalDateTime lastActiveAt, long authVersion) {
    boolean active() {
        return "ACTIVE".equals(status);
    }
}

record AccountSecurityState(Long userId, String status, String role, long authVersion) {
    boolean matches(AuthenticatedAccount account) {
        return userId.equals(account.userId())
                && "ACTIVE".equals(status)
                && role.equals(account.role())
                && authVersion == account.authVersion();
    }
}
