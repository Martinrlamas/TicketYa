package com.ticketya.platform.openapi;

import com.ticketya.platform.config.PlatformProperties;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.core.env.Environment;

/**
 * Base de springdoc: título y versión del servicio y los headers de identidad como esquema de seguridad.
 * Qué endpoints lo exigen lo marca {@link RequireRoleOperationCustomizer}.
 */
public final class OpenApiConfig {

    public static final String USER_ID_SCHEME = "X-User-Id";
    public static final String USER_ROLE_SCHEME = "X-User-Role";

    private OpenApiConfig() {}

    public static OpenAPI build(Environment env, PlatformProperties props) {
        String service = env.getProperty("spring.application.name", "service");
        return new OpenAPI()
                .info(new Info()
                        .title(service)
                        .version(props.api().version())
                        .description("TicketYa - " + service + ". Los errores siguen RFC 7807 (application/problem+json) "
                                + "con los campos code y correlationId. Identidad (E1): headers X-User-Id (UUID) y "
                                + "X-User-Role (CUSTOMER, ORGANIZER, ADMIN, ACCESS_STAFF)."))
                .components(new Components()
                        .addSecuritySchemes(USER_ID_SCHEME, headerScheme(USER_ID_SCHEME, "UUID del usuario (lo informa el gateway)"))
                        .addSecuritySchemes(USER_ROLE_SCHEME, headerScheme(USER_ROLE_SCHEME,
                                "Rol: CUSTOMER, ORGANIZER, ADMIN o ACCESS_STAFF")));
    }

    private static SecurityScheme headerScheme(String name, String description) {
        return new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER)
                .name(name).description(description);
    }
}
