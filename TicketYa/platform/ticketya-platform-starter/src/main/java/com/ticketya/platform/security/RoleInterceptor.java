package com.ticketya.platform.security;

import com.ticketya.platform.error.ErrorCode;
import com.ticketya.platform.error.PlatformException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Autoriza por rol en CADA servicio (defensa en profundidad: no se confía solo en el gateway).
 * Los errores se lanzan como {@link PlatformException} y los traduce el GlobalExceptionHandler.
 */
public class RoleInterceptor implements HandlerInterceptor {

    private final IdentityResolver resolver;

    public RoleInterceptor(IdentityResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequireRole required = method.getMethodAnnotation(RequireRole.class);
        if (required == null) {
            required = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequireRole.class);
        }
        if (required == null) {
            return true;                                   // endpoint público
        }
        Identity identity = resolver.resolve(request);     // 401
        if (required.value().length > 0 && !identity.hasRole(required.value())) {
            throw new PlatformException(ErrorCode.FORBIDDEN,
                    "El rol " + identity.role() + " no puede acceder a este recurso (se requiere "
                            + Arrays.toString(required.value()) + ")");
        }
        request.setAttribute(Identity.REQUEST_ATTRIBUTE, identity);
        return true;
    }
}
