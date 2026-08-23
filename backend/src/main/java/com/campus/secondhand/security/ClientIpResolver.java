package com.campus.secondhand.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {
    public String resolve(HttpServletRequest request) {
        // Tomcat's trusted-proxy RemoteIpValve normalizes this when forward-headers-strategy=native.
        return request.getRemoteAddr();
    }
}
