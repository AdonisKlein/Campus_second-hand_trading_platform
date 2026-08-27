package com.campus.secondhand.gateway;

import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

@Component
public class InternalJwt {
    private final JwtEncoder encoder;

    public InternalJwt(GatewayProperties properties) {
        this.encoder = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(
                properties.internalJwtSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "HmacSHA256")).build();
    }

    public String issue(AuthenticatedAccount account) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("campus-gateway")
                .subject(String.valueOf(account.userId()))
                .claim("email", account.email())
                .claim("username", account.username())
                .claim("nickname", account.nickname())
                .claim("role", account.role())
                .claim("auth_version", account.authVersion())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
