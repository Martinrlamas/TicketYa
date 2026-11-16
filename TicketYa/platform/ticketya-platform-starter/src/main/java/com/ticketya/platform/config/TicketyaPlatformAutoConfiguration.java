package com.ticketya.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.platform.correlation.CorrelationIdFilter;
import com.ticketya.platform.error.GlobalExceptionHandler;
import com.ticketya.platform.http.PlatformRestClientFactory;
import com.ticketya.platform.idempotency.IdempotencyLock;
import com.ticketya.platform.logging.RequestLoggingFilter;
import com.ticketya.platform.openapi.OpenApiConfig;
import com.ticketya.platform.openapi.RequireRoleOperationCustomizer;
import com.ticketya.platform.security.IdentityArgumentResolver;
import com.ticketya.platform.security.IdentityResolver;
import com.ticketya.platform.security.InternalTokenFilter;
import com.ticketya.platform.security.RoleInterceptor;
import io.swagger.v3.oas.models.OpenAPI;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Autoconfiguración del starter técnico. Todo es reemplazable ({@code @ConditionalOnMissingBean}).
 * Orden de filtros: correlation-id -> request logging -> token interno.
 */
@AutoConfiguration(after = {JacksonAutoConfiguration.class, JdbcTemplateAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(PlatformProperties.class)
public class TicketyaPlatformAutoConfiguration {

    // ------------------------------------------------------------------ filtros

    @Bean
    @ConditionalOnMissingBean(name = "correlationIdFilterRegistration")
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    @ConditionalOnProperty(prefix = "ticketya.platform.request-logging", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "requestLoggingFilterRegistration")
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilterRegistration(ObjectMapper mapper) {
        FilterRegistrationBean<RequestLoggingFilter> registration = new FilterRegistrationBean<>(new RequestLoggingFilter(mapper));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean(name = "internalTokenFilterRegistration")
    public FilterRegistrationBean<InternalTokenFilter> internalTokenFilterRegistration(PlatformProperties props,
                                                                                       ObjectMapper mapper) {
        FilterRegistrationBean<InternalTokenFilter> registration =
                new FilterRegistrationBean<>(new InternalTokenFilter(props.internalToken(), mapper));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }

    // ------------------------------------------------------------------ identidad y autorización

    @Bean
    @ConditionalOnMissingBean
    public IdentityResolver identityResolver() {
        return new IdentityResolver();
    }

    @Bean
    public WebMvcConfigurer ticketyaSecurityWebMvcConfigurer(IdentityResolver resolver) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new RoleInterceptor(resolver)).addPathPatterns("/api/**");
            }

            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new IdentityArgumentResolver(resolver));
            }
        };
    }

    // ------------------------------------------------------------------ utilidades comunes

    /** Reloj inyectable (permite fijar el tiempo en pruebas). Un servicio puede definir el suyo. */
    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    // ------------------------------------------------------------------ errores, clientes HTTP, OpenAPI

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public PlatformRestClientFactory platformRestClientFactory(PlatformProperties props) {
        return new PlatformRestClientFactory(props);
    }

    /** Solo en servicios con base relacional (hay un JdbcTemplate). */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JdbcTemplate.class)
    static class IdempotencyConfiguration {

        @Bean
        @ConditionalOnBean(JdbcTemplate.class)
        @ConditionalOnMissingBean
        IdempotencyLock idempotencyLock(JdbcTemplate jdbc) {
            return new IdempotencyLock(jdbc);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(OpenAPI.class)
    static class OpenApiConfiguration {

        @Bean
        @ConditionalOnMissingBean
        OpenAPI ticketyaOpenApi(Environment environment, PlatformProperties props) {
            return OpenApiConfig.build(environment, props);
        }

        @Bean
        @ConditionalOnMissingBean
        RequireRoleOperationCustomizer requireRoleOperationCustomizer() {
            return new RequireRoleOperationCustomizer();
        }
    }
}
