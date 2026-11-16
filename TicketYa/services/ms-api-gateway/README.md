# api-gateway (change 07)

Punto de entrada único del sistema. **Spring Cloud Gateway (WebFlux)**, puerto **8080**. Sin base de datos y sin lógica de negocio.

## Responsabilidades
| Qué | Cómo |
|---|---|
| Ruteo | `GatewayRoutes` (tabla explícita, ordenada por especificidad) |
| `/internal/**` | Nunca se rutea: 404 (`InternalPathBlockWebFilter`). Tampoco se deja pasar `X-Internal-Token` del cliente |
| Identidad (E1) | `IdentityWebFilter`: exige `X-User-Id` (UUID) + `X-User-Role` válidos en rutas protegidas → 401. En E2 solo cambia este filtro (valida el JWT y genera los mismos headers) |
| Trazabilidad | `CorrelationIdWebFilter` (genera/propaga `X-Correlation-Id`) + `RequestLoggingWebFilter` (método, ruta, destino, usuario, status, ms) |
| Errores | `ProblemErrorWebExceptionHandler`: todo error sale como `application/problem+json` con el mismo contrato que los servicios (503 `UPSTREAM_UNAVAILABLE` si un servicio no responde) |
| CORS | `CorsConfig` (orígenes configurables) |
| Documentación | Swagger UI en `/swagger-ui.html` sobre el contrato `static/openapi/ticketya-public-api.yaml` |

## Tabla de ruteo
| Orden | Predicado | Destino |
|---|---|---|
| — | `/internal/**` | bloqueado (404) |
| 10 | `GET /api/events/*/availability` | inventory-service |
| 20 | `/api/venues/**`, `/api/events/**` | catalog-service |
| 30 | `/api/orders/**`, `/api/refund-requests/**` | order-service |
| 40 | `/api/tickets/**` | ticket-service |
| — | payment-service | sin rutas públicas |

Rutas públicas (sin identidad): `GET /api/events`, `GET /api/events/{id}` (salvo `mine`), `GET /api/events/{id}/availability`, `GET /api/venues/{id}` y preflight `OPTIONS`.

## Ejecutar
```bash
mvn spring-boot:run          # URLs de servicios por variables: CATALOG_URL, INVENTORY_URL, ORDER_URL, TICKET_URL
mvn test                     # no necesita los otros servicios: usa servicios falsos
```
## Notas
- **No usa `ticketya-platform-starter`** (ese es de Servlet; el gateway es reactivo). Implementa la misma convención con `WebFilter`.
- Si usás una versión de Spring Cloud anterior a 2025.0, el prefijo de propiedades es `spring.cloud.gateway.httpclient.*` y el starter `spring-cloud-starter-gateway`.
- Fuera de alcance E1: rate limiting (punto de extensión natural para la fila virtual de E2), circuit breaker y agregación (BFF).
