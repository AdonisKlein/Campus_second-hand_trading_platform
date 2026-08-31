package com.campus.secondhand.account;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class ConfigurationValidator {
    private final String serviceToken;
    private final String jwtSecret;
    private final String pepper;

    ConfigurationValidator(@Value("${app.security.internal-service-token}") String serviceToken,
                           @Value("${app.security.internal-jwt-secret}") String jwtSecret,
                           @Value("${app.verification.pepper}") String pepper) {
        this.serviceToken = serviceToken;
        this.jwtSecret = jwtSecret;
        this.pepper = pepper;
    }

    @PostConstruct
    void validate() {
        if (bytes(serviceToken) < 32 || bytes(jwtSecret) < 32 || bytes(pepper) < 32) {
            throw new IllegalStateException("内部密钥和验证码 Pepper 至少需要32字节");
        }
    }

    private int bytes(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }
}
