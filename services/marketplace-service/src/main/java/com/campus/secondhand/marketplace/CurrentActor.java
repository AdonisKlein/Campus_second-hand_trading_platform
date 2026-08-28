package com.campus.secondhand.marketplace;

public record CurrentActor(Long userId, String role, String email, long authVersion) {
    public boolean student() { return "STUDENT".equals(role); }
    public boolean admin() { return "ADMIN".equals(role); }
}
