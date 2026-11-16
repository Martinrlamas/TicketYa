package com.ticketya.platform.openapi;

import com.ticketya.platform.security.RequireRole;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

/**
 * Marca en Swagger como protegido todo endpoint con {@link RequireRole} (en el método o en la clase),
 * así los controllers no repiten {@code @SecurityRequirement} y los públicos no muestran candado.
 */
public class RequireRoleOperationCustomizer implements OperationCustomizer {

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        boolean protectedEndpoint = handlerMethod.hasMethodAnnotation(RequireRole.class)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), RequireRole.class);
        if (protectedEndpoint) {
            operation.addSecurityItem(new SecurityRequirement()
                    .addList(OpenApiConfig.USER_ID_SCHEME)
                    .addList(OpenApiConfig.USER_ROLE_SCHEME));
        }
        return operation;
    }
}
