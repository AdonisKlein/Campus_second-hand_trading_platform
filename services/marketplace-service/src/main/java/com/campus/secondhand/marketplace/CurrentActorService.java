package com.campus.secondhand.marketplace;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CurrentActorService {
    public CurrentActor require(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt))
            throw new org.springframework.security.access.AccessDeniedException("需要内部身份令牌");
        return new CurrentActor(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString("role"),
                jwt.getClaimAsString("email"), ((Number) jwt.getClaims().getOrDefault("auth_version", 0)).longValue());
    }
}
