## 1. Repositorio e infraestructura
- [ ] 1.1 Crear estructura de carpetas (`platform/`, `services/`, `infra/`, `postman/`, `docs/`)
- [ ] 1.2 `infra/docker-compose.dev.yml` con PostgreSQL 16 y `init-databases.sql` (catalog_db, inventory_db, order_db, payment_db, ticket_db, un usuario por base)
- [ ] 1.3 `.env.example` con `INTERNAL_TOKEN` y credenciales de bases

## 2. Starter técnico
- [ ] 2.1 Filtro `CorrelationIdFilter` (genera/propaga `X-Correlation-Id`, MDC, header de respuesta)
- [ ] 2.2 Filtro `RequestLoggingFilter` (log JSON: endpoint, método, userId, status, duración ms, correlationId)
- [ ] 2.3 `IdentityResolver` + `@RequireRole` + interceptor (401/403)
- [ ] 2.4 `InternalTokenFilter` para `/internal/**`
- [ ] 2.5 `GlobalExceptionHandler` con ProblemDetail y catálogo de códigos
- [ ] 2.6 Fábrica de `RestClient` con timeouts (connect 1 s, read 3 s) que propaga correlation-id e internal token
- [ ] 2.7 Config base de springdoc (título, versión, esquema de headers de identidad)

## 3. Pruebas
- [ ] 3.1 Tests de cada filtro con MockMvc
- [ ] 3.2 Test de que el starter no depende de ninguna clase de negocio (ArchUnit)
