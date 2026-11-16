package com.ticketya.platform.security;

import com.ticketya.platform.error.ErrorCode;
import com.ticketya.platform.error.PlatformException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

/** Lee X-User-Id y X-User-Role. Cualquier ausencia o valor inválido es 401 UNAUTHENTICATED. */
public class IdentityResolver {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";

    public Identity resolve(HttpServletRequest request) {
        String rawId = request.getHeader(USER_ID_HEADER);
        if (rawId == null || rawId.isBlank()) {
            throw unauthenticated("Falta el header " + USER_ID_HEADER);
        }
        UUID userId;
        try {
            userId = UUID.fromString(rawId.trim());
        } catch (IllegalArgumentException e) {
            throw unauthenticated(USER_ID_HEADER + " no es un UUID válido");
        }
        String rawRole = request.getHeader(USER_ROLE_HEADER);
        if (rawRole == null || rawRole.isBlank()) {
            throw unauthenticated("Falta el header " + USER_ROLE_HEADER);
        }
        Role role = Role.parse(rawRole)
                .orElseThrow(() -> unauthenticated(USER_ROLE_HEADER + " tiene un valor no permitido"));
        return new Identity(userId, role);
    }

    private static PlatformException unauthenticated(String detail) {
        return new PlatformException(ErrorCode.UNAUTHENTICATED, detail);
    }
}
