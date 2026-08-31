package com.campus.secondhand.trading;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CurrentActorService {
    public CurrentActor require(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw TradingException.forbidden("请先登录");
        }
        try {
            Number version = jwt.getClaim("auth_version");
            return new CurrentActor(Long.parseLong(jwt.getSubject()), jwt.getClaimAsString("role"),
                    version == null ? 0 : version.intValue());
        } catch (RuntimeException error) {
            throw TradingException.forbidden("登录身份无效");
        }
    }
}
