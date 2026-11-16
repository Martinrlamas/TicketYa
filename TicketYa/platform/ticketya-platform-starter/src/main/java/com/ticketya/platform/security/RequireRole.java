package com.ticketya.platform.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exige identidad (X-User-Id y X-User-Role válidos => si no, 401) y que el rol esté entre los indicados
 * (si no, 403). Sin roles = cualquier usuario autenticado. En un método prevalece sobre la clase.
 * Un endpoint {@code /api/**} sin esta anotación se considera público.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequireRole {

    Role[] value() default {};
}
