package com.campus.secondhand.security;

import com.campus.secondhand.common.ApiResponse;
import com.campus.secondhand.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmailIgnoreCase(email)
            .map(user -> User.withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .roles(user.getRole())
                .disabled(!"ACTIVE".equals(user.getStatus()))
                .build())
            .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("invalid credentials"));
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookiePath("/");
        http
            .cors(cors -> {})
            .csrf(config -> config.csrfTokenRepository(csrf))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation(fixation -> fixation.migrateSession()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/items/mine").authenticated()
                .requestMatchers(HttpMethod.GET, "/items/**", "/messages/item/**", "/auth/csrf",
                    "/actuator/health", "/actuator/health/liveness").permitAll()
                .requestMatchers("/auth/login", "/auth/register", "/auth/verification/**", "/auth/password/**", "/error").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, ex) -> writeError(response, mapper, 401, "请先登录"))
                .accessDeniedHandler((request, response, ex) -> writeError(response, mapper, 403, "无权执行此操作")))
            .logout(logout -> logout.disable());
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
        @Value("${app.cors.allowed-origins:http://localhost:5500,http://127.0.0.1:5500}") List<String> origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static void writeError(jakarta.servlet.http.HttpServletResponse response, ObjectMapper mapper,
                                   int status, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), ApiResponse.fail(message));
    }
}
