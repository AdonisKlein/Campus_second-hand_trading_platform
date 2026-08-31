package com.campus.secondhand.governance;

import java.util.Optional;

interface AccountGovernancePort {
    Optional<AccountSnapshot> find(long userId);
    default AccountSnapshot requireActiveStudent(long userId) {
        return find(userId).filter(AccountSnapshot::activeStudent)
                .orElseThrow(() -> GovernanceException.forbidden("只有学生用户可以提交举报"));
    }
    default AccountSnapshot requireActiveAdmin(long userId) {
        return find(userId).filter(AccountSnapshot::activeAdmin)
                .orElseThrow(() -> GovernanceException.forbidden("无管理员权限"));
    }
    record AccountSnapshot(long id,String username,String nickname,String status,String role) {
        boolean activeStudent(){return "ACTIVE".equals(status)&&"STUDENT".equals(role);}
        boolean activeAdmin(){return "ACTIVE".equals(status)&&"ADMIN".equals(role);}
        String displayName(){return nickname==null||nickname.isBlank()?username:nickname;}
    }
}
