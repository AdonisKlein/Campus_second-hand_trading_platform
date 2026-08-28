package com.campus.secondhand.governance;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class InternalServiceTokenFilter extends OncePerRequestFilter {
    private final byte[] expected;
    InternalServiceTokenFilter(GovernanceProperties properties) {
        expected = properties.internalServiceToken().getBytes(StandardCharsets.UTF_8);
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws IOException, ServletException {
        String supplied = request.getHeader("X-Internal-Service-Token");
        if (supplied == null || !MessageDigest.isEqual(expected, supplied.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(401); response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"内部服务身份无效\",\"data\":null}");
            return;
        }
        chain.doFilter(request, response);
    }
}
