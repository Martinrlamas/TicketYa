package com.ticketya.gateway.filter;

import org.springframework.http.HttpMethod;

public final class IdentityWebFilterAccess {
    private IdentityWebFilterAccess() {}
    public static boolean isPublic(HttpMethod m, String p) { return IdentityWebFilter.isPublic(m, p); }
    public static boolean valid(String id, String role) { return IdentityWebFilter.isValid(id, role); }
}
