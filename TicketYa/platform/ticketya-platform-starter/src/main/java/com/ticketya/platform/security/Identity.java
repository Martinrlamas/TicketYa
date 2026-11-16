package com.ticketya.platform.security;

import com.ticketya.platform.error.ErrorCode;
import com.ticketya.platform.error.PlatformException;
import java.util.UUID;

/** Quién llama: usuario y rol, tal como los informó el gateway. Se puede recibir como parámetro de un controller. */
public record Identity(UUID userId, Role role) {

    public static final String REQUEST_ATTRIBUTE = "ticketya.identity";

    public boolean hasRole(Role... roles) {
        for (Role r : roles) {
            if (r == role) {
                return true;
            }
        }
        return false;
    }

    /** Atajo de {@code hasRole(role)} para condiciones de negocio (p. ej. "es el dueño o es ADMIN"). */
    public boolean is(Role expected) {
        return role == expected;
    }

    /** Defensa en profundidad: cada servicio autoriza por rol por su cuenta (403 si no corresponde). */
    public void requireAnyRole(Role... allowed) {
        if (!hasRole(allowed)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "El rol " + role + " no puede acceder a este recurso");
        }
    }
}
