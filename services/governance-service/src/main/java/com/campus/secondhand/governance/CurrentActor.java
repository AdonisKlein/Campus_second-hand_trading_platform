package com.campus.secondhand.governance;

public record CurrentActor(long userId, String role) {
    public boolean student() { return "STUDENT".equals(role); }
    public boolean admin() { return "ADMIN".equals(role); }
}
