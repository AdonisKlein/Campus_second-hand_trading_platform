package com.campus.secondhand.trading;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AccountPort {
    Optional<AccountSnapshot> find(long userId);

    default AccountSnapshot requireActiveStudent(long userId) {
        AccountSnapshot account = find(userId).orElseThrow(() -> TradingException.forbidden("学生账号不可用"));
        if (!"ACTIVE".equals(account.status()) || !"STUDENT".equals(account.role())) {
            throw TradingException.forbidden("学生账号不可用");
        }
        return account;
    }

    record AccountSnapshot(long id, String username, String nickname, String campusRegion,
                           Integer creditScore, String status, String role, LocalDateTime lastActiveAt) {
        public String displayName() {
            return nickname == null || nickname.isBlank() ? username : nickname;
        }
    }
}
