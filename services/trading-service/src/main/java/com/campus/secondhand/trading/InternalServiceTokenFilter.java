package com.campus.secondhand.trading;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class InternalServiceTokenFilter extends OncePerRequestFilter {
    private final byte[] expected;

    InternalServiceTokenFilter(TradingProperties properties) {
        this.expected = properties.internalServiceToken().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader("X-Internal-Service-Token");
        boolean valid = supplied != null && MessageDigest.isEqual(expected, supplied.getBytes(StandardCharsets.UTF_8));
        if (!valid) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"内部服务身份无效\",\"data\":null}");
            return;
        }
        chain.doFilter(request, response);
    }
}
