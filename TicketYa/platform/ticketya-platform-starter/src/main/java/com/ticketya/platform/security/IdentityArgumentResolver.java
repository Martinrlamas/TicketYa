package com.ticketya.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.ParameterizedType;
import java.util.Optional;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Entrega la {@link Identity} del request como parámetro de un controller.
 * {@code Identity} es obligatoria (401 si falta); {@code Optional<Identity>} sirve para endpoints públicos
 * que se comportan distinto si el usuario viene identificado.
 */
public class IdentityArgumentResolver implements HandlerMethodArgumentResolver {

    private final IdentityResolver resolver;

    public IdentityArgumentResolver(IdentityResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Identity.class.equals(parameter.getParameterType()) || esOptionalDeIdentity(parameter);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        boolean opcional = Optional.class.equals(parameter.getParameterType());
        if (opcional && !traeIdentidad(request)) {
            return Optional.empty();
        }
        Object guardada = webRequest.getAttribute(Identity.REQUEST_ATTRIBUTE, NativeWebRequest.SCOPE_REQUEST);
        Identity identity = guardada instanceof Identity i ? i : resolver.resolve(request);
        return opcional ? Optional.of(identity) : identity;
    }

    private static boolean esOptionalDeIdentity(MethodParameter parameter) {
        return Optional.class.equals(parameter.getParameterType())
                && parameter.getGenericParameterType() instanceof ParameterizedType tipo
                && Identity.class.equals(tipo.getActualTypeArguments()[0]);
    }

    private static boolean traeIdentidad(HttpServletRequest request) {
        return tieneValor(request.getHeader(IdentityResolver.USER_ID_HEADER))
                || tieneValor(request.getHeader(IdentityResolver.USER_ROLE_HEADER));
    }

    private static boolean tieneValor(String header) {
        return header != null && !header.isBlank();
    }
}
