package com.campus.secondhand.marketplace;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;

@Configuration
public class MarketplaceSecurityConfig {
    @Bean JwtDecoder jwtDecoder(MarketplaceProperties properties) {
        String secret = properties.internalJwtSecret();
        var decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer("campus-gateway")));
        return decoder;
    }
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean SecurityFilterChain security(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(
                        org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness",
                                "/api/items", "/api/search").permitAll()
                        .requestMatchers("/internal/items/**").permitAll()
                        .requestMatchers(
                                RegexRequestMatcher.regexMatcher(org.springframework.http.HttpMethod.GET,
                                        "^/api/items/[1-9]\\d*$"),
                                RegexRequestMatcher.regexMatcher(org.springframework.http.HttpMethod.GET,
                                        "^/api/messages/item/[1-9]\\d*$"),
                                RegexRequestMatcher.regexMatcher(org.springframework.http.HttpMethod.GET,
                                        "^/api/media/product-images/[1-9]\\d*/[0-9a-fA-F-]{36}\\.(jpg|png)$"))
                        .permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(jsonEntryPoint())
                        .accessDeniedHandler(jsonAccessDeniedHandler()))
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .build();
    }

    private AuthenticationEntryPoint jsonEntryPoint() {
        return (request, response, cause) -> writeError(response, 401, "UNAUTHENTICATED", "请先登录");
    }

    private AccessDeniedHandler jsonAccessDeniedHandler() {
        return (request, response, cause) -> writeError(response, 403, "FORBIDDEN", "没有权限执行此操作");
    }

    private void writeError(jakarta.servlet.http.HttpServletResponse response, int status,
                            String code, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"code\":\"" + code
                + "\",\"message\":\"" + message + "\"}");
    }
}
