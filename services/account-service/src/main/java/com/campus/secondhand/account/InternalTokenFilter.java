package com.campus.secondhand.account;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class InternalTokenFilter extends OncePerRequestFilter {
    private final byte[] expected;

    InternalTokenFilter(@Value("${app.security.internal-service-token}") String token) {
        expected = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (request.getRequestURI().startsWith(request.getContextPath() + "/internal/")) {
            byte[] actual = Optional.ofNullable(request.getHeader("X-Internal-Service-Token"))
                    .map(value -> value.getBytes(StandardCharsets.UTF_8))
                    .orElseGet(() -> new byte[0]);
            if (!MessageDigest.isEqual(expected, actual)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                        "{\"success\":false,\"message\":\"internal token invalid\",\"data\":null}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
