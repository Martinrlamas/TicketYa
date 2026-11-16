## Context
Seis aplicaciones Spring Boot independientes. Lo único compartido es un starter técnico.

## Decisions
1. **Starter técnico y no librería de dominio.** Contiene: filtro de correlation-id, filtro de request
   logging, `@RequireRole` + interceptor, `GlobalExceptionHandler` (ProblemDetail), filtro de token
   interno, config base de springdoc y de `RestClient` con timeouts. Si algo tiene vocabulario de negocio
   (Event, Seat, Order) NO va al starter.
2. **Identidad en E1:** el gateway exige `X-User-Id` y `X-User-Role` y los reenvía. En E2 el gateway
   valida el JWT y produce los mismos headers, de modo que los servicios no cambian.
3. **Defensa en profundidad:** cada servicio autoriza por rol por su cuenta; no confía solo en el gateway.
4. **Endpoints internos:** no se rutean por el gateway, solo escuchan en la red Docker y exigen
   `X-Internal-Token` (secreto compartido por variable de entorno). En E2 puede pasar a mTLS o JWT de servicio.
5. **Una base por servicio** en una única instancia PostgreSQL (bases separadas, usuarios separados).
   Migraciones con Flyway; `ddl-auto=validate`.
6. **Puerto de eventos de dominio** (`DomainEventPublisher`) definido en cada servicio, no en el starter,
   porque los eventos son parte de cada dominio.

## Risks
- El starter puede degenerar en "common library" con negocio. Regla de revisión: PR que agregue una
  entidad o DTO de negocio al starter se rechaza.
