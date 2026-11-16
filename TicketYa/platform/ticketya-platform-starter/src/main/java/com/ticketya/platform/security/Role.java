package com.ticketya.platform.security;

import java.util.Optional;

/** Roles de la plataforma (E1: vienen por header; E2: del JWT, sin cambiar los servicios). */
public enum Role {
    CUSTOMER,
    ORGANIZER,
    ADMIN,
    ACCESS_STAFF;

    public static Optional<Role> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (Role r : values()) {
            if (r.name().equals(value.trim())) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }
}
