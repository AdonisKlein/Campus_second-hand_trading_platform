package com.campus.secondhand.trading;

public record CurrentActor(long userId, String role, int authVersion) {
    public boolean student() { return "STUDENT".equals(role); }
}
