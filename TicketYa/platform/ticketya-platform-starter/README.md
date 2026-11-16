# ticketya-platform-starter

Infraestructura técnica transversal. **Cero lógica de negocio**: nada de Event, Seat, Order... (lo verifica un
test ArchUnit). Se agrega como dependencia y se autoconfigura:

```xml
<dependency>
  <groupId>com.ticketya</groupId>
  <artifactId>ticketya-platform-starter</artifactId>   <!-- la versión la fija el POM raíz -->
</dependency>
```

Es un módulo del reactor Maven de la raíz: `mvn verify` en la raíz lo construye junto con los servicios.

## Qué aporta

| Pieza | Comportamiento |
|---|---|
| `CorrelationIdFilter` | Usa el `X-Correlation-Id` del cliente (si es válido: `[A-Za-z0-9._-]{1,100}`) o genera un UUID; lo pone en el MDC y lo devuelve en la respuesta, también en los errores. El patrón de log por defecto lo incluye en cada línea |
| `RequestLoggingFilter` | Una línea JSON por request en el logger `ticketya.http`: `method`, `path`, `userId`, `status`, `durationMs`, `correlationId`. Sin query string ni body. Se apaga con `ticketya.platform.request-logging.enabled=false` |
| `@RequireRole` + `RoleInterceptor` | Sobre `/api/**`. Sin identidad válida: 401 `UNAUTHENTICATED`. Rol no permitido: 403 `FORBIDDEN`. Sin roles en la anotación: basta estar autenticado. **Un endpoint sin la anotación es público** |
| `Identity` | Se puede declarar como parámetro del controller: `public X handler(Identity identity)` |
| `InternalTokenFilter` | `/internal/**` exige `X-Internal-Token` (comparación en tiempo constante). Falta o no coincide: 403. Falla cerrado si no hay secreto |
| `GlobalExceptionHandler` | RFC 7807 con `type`, `title`, `status`, `detail`, `code`, `correlationId`. Cubre validación (400 con `errors[]`), JSON ilegible, 404, 405, 415, toda `ApiException` y errores inesperados (500 sin filtrar el mensaje) |
| `ErrorCode` / `ApiException` / `PlatformException` | Catálogo de los 15 códigos de la spec con su status HTTP. `ApiException` es la base de todas las excepciones de negocio: cada servicio extiende esa clase y no necesita su propio `@RestControllerAdvice` |
| `PageResponse` | Contrato de paginación común (`PageResponse.of(page, mapper)`) |
| `IdempotencyLock` / `Fingerprint` | Lock advisory de Postgres por clave (solo si el servicio tiene `JdbcTemplate`) y huella SHA-256 para detectar reutilización de claves de idempotencia |
| `Clock` | Bean `Clock` UTC por defecto (el servicio puede definir el suyo) |
| `PlatformRestClientFactory` | `RestClient` con timeouts (connect 1 s, read 3 s), propaga `X-Correlation-Id`, `createInternal(...)` agrega `X-Internal-Token`. Timeout, caída y 5xx => `UPSTREAM_UNAVAILABLE` (503); los 4xx pasan al llamador |
| OpenAPI | Bean `OpenAPI` con título (`spring.application.name`), versión y los headers de identidad como esquema de seguridad |

## Propiedades

```yaml
ticketya:
  platform:
    internal-token: ${INTERNAL_TOKEN:dev-internal-token}   # valor por defecto, ya aplicado por el starter
    http:
      connect-timeout: PT1S
      read-timeout: PT3S
    request-logging:
      enabled: true
    api:
      version: 1.0.0
```

## Notas de diseño

- **Códigos fuera del catálogo:** 405, 415 y 406 se devuelven con `code=METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE` y `NOT_ACCEPTABLE`, y los 500 con `INTERNAL_ERROR`: el catálogo de la spec no los cubre.
- **Roles en el interceptor y no en el filtro:** así `@RequireRole` se resuelve por handler y los errores salen por el mismo `GlobalExceptionHandler`. El filtro de token interno sí es un filtro (corre antes de Spring MVC) y escribe el problem+json por su cuenta.
- **Qué NO hace:** no valida JWT (E2), no tiene entidades ni DTOs de negocio, no define `DomainEventPublisher` (cada servicio define el suyo).
