## Why
Antes de construir los servicios de negocio hay que fijar las reglas transversales: estructura del
repositorio, identidad, formato de errores, trazabilidad y protección de endpoints internos. Definirlas
una sola vez evita repetir código y ambigüedades en cada servicio.

## What Changes
- Nuevo módulo `ticketya-platform-starter` (solo infraestructura técnica, sin lógica de negocio).
- Convenciones de identidad por headers (E1), autorización por rol, correlation-id, logging de requests,
  errores RFC 7807 con código de negocio, protección de `/internal/**`, OpenAPI y migraciones.
- `infra/docker-compose.dev.yml` con PostgreSQL y una base por servicio.

## Impact
- Specs afectadas: `platform-conventions` (nueva).
- Código afectado: `platform/ticketya-platform-starter`, `infra/`.
- Todos los servicios dependen del starter, nunca entre sí.
