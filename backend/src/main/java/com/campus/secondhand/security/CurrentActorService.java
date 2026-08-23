package com.campus.secondhand.security;

import com.campus.secondhand.user.User;
import com.campus.secondhand.user.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class CurrentActorService {
    private final UserRepository users;
    private final HttpServletRequest request;

    public CurrentActorService(UserRepository users, HttpServletRequest request) {
        this.users = users;
        this.request = request;
    }

    public CurrentActor require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AccessDeniedException("请先登录");
        }
        User user = users.findByEmailIgnoreCase(authentication.getName())
            .orElseThrow(() -> new AccessDeniedException("登录状态已失效"));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new AccessDeniedException("账号不可用");
        }
        Object sessionVersion = request.getSession(false) == null ? null
            : request.getSession(false).getAttribute("AUTH_VERSION");
        if (!(sessionVersion instanceof Integer) || !sessionVersion.equals(user.getAuthVersion())) {
            throw new AccessDeniedException("登录状态已失效");
        }
        return new CurrentActor(user.getId(), user.getRole());
    }
}
