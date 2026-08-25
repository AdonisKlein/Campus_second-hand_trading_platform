package com.campus.secondhand.security;

public record CurrentActor(Long userId, String role) {
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
