package com.campus.secondhand.governance;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
class CurrentActorService {
    CurrentActor require(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw GovernanceException.forbidden("请先登录");
        }
        try {
            return new CurrentActor(Long.parseLong(jwt.getSubject()), jwt.getClaimAsString("role"));
        } catch (RuntimeException error) {
            throw GovernanceException.forbidden("登录身份无效");
        }
    }
}
