# Proyecto: TicketYa (Entrega 1 — Core)

## Propósito
Plataforma de venta de entradas. La Entrega 1 cubre: eventos y lugares, publicación,
compra con reserva temporal y emisión de QR, garantía de no doble venta, devoluciones,
cancelación de eventos y búsqueda multicriterio. La arquitectura queda preparada para
la Entrega 2 (JWT, MongoDB, Redis, mensajería, fila virtual, notificaciones, reportes).

## Stack
- Java 21, Spring Boot 3.x, Maven (un proyecto independiente por servicio)
- Spring Cloud Gateway (api-gateway)
- PostgreSQL 16: **una base de datos por servicio** (instancia única en Docker, bases separadas)
- Flyway para migraciones, Spring Data JPA, springdoc-openapi
- Tests: JUnit 5, Mockito, Testcontainers (PostgreSQL), AssertJ

## Mapa de servicios
| Servicio | Puerto | Base | Responsabilidad |
|---|---|---|---|
| api-gateway | 8080 (único publicado) | — | Entrada única, ruteo, identidad, correlation-id |
| catalog-service | 8081 | catalog_db | Lugares, sectores, eventos, precios, ciclo de vida, búsqueda |
| inventory-service | 8082 | inventory_db | Disponibilidad, holds con TTL, garantía de no doble venta |
| order-service | 8083 | order_db | Saga de compra, límite por cliente, idempotencia, devoluciones |
| payment-service | 8084 | payment_db | Pasarela simulada, pagos y reintegros |
| ticket-service | 8085 | ticket_db | Emisión de entradas con QR, estados |

## Estructura del repositorio
```
ticketya/
├── openspec/
├── platform/ticketya-platform-starter/   # solo cross-cutting técnico, CERO lógica de negocio
├── services/<servicio>/                  # cada uno buildable y ejecutable por separado
├── compose.yml                           # PostgreSQL (E1) + servicios y gateway en contenedores (E2)
├── infra/postgres/init-databases.sql
├── postman/TicketYa-Entrega1.postman_collection.json
└── docs/
    ├── 01-features-user-stories.md       # features y user stories con criterios de aceptación (todo el sistema)
    ├── 02-architecture.md                # diagrama de módulos, responsabilidades, matriz de comunicación
    ├── 03-sequence-purchase.md           # secuencia de compra numerada (rechazo, vencimiento, compensaciones)
    ├── 04-data-model.md                  # ER por servicio + entidades solapadas
    ├── 05-api-endpoints.md               # endpoints: método, ruta, parámetros, códigos, JSON
    └── openapi/                          # contratos OpenAPI por servicio + público agregado
```

## Fuente de verdad
1. `openspec/specs` y `openspec/changes` → **comportamiento** (requisitos y escenarios).
2. `docs/05-api-endpoints.md` + `docs/openapi/` + `postman/` → **contratos HTTP** (rutas, códigos, ejemplos); se actualizan juntos.
3. `docs/` → documentación de diseño que acompaña la entrega.
Si una implementación contradice a alguna, se corrige la implementación o se actualiza la fuente con un nuevo change.

## Convenciones
- **Arquitectura hexagonal por servicio**: paquetes `domain` (puro, sin Spring/JPA), `application`
  (casos de uso y puertos), `infrastructure` (JPA, clientes REST, jobs), `api` (controllers, DTOs).
- **Sin clases compartidas de dominio** entre servicios. Cada consumidor define su propio DTO
  (anti-corruption layer) para las respuestas de otros servicios.
- **Sin joins ni FKs entre bases**. Solo se almacenan IDs de referencia y snapshots.
- Rutas públicas: `/api/**` (pasan por el gateway). Rutas internas: `/internal/**` (nunca ruteadas por el gateway).
- Todo cambio de estado relevante se publica a través de un puerto `DomainEventPublisher`.
  En E1 los adaptadores son llamadas REST o listeners locales; en E2 se reemplazan por broker + outbox.
- Los textos de las specs están en español; las palabras clave de OpenSpec (SHALL, MUST, WHEN, THEN,
  Requirement, Scenario) permanecen en inglés porque el validador las exige.

## Roles (E1: vía header; E2: JWT)
`CUSTOMER`, `ORGANIZER`, `ADMIN`, `ACCESS_STAFF`

## Orden de implementación
00 → 01 → 02 → 03 → 04 → 05 → 06 → 07. Cada change se implementa con `/opsx:apply <id>` y se
archiva con `/opsx:archive <id>` al terminar (pasa sus deltas a `openspec/specs/`).

## Trazabilidad con el enunciado (Entrega 1)
| Enunciado | Change / capability |
|---|---|
| 1, 2 Eventos y lugares | 01 `venues`, `events-lifecycle` |
| 3 Publicación | 01 `events-lifecycle` |
| 4 Compra con reserva y QR | 02 `inventory-holds`, 03 `payments`, 04 `tickets`, 05 `order-purchase` |
| 5 No doble venta | 02 `inventory-holds`, 05 `order-purchase` (idempotencia) |
| 7 Devoluciones y cancelación | 06 `order-refunds`, `event-cancellation` |
| 9 Búsqueda | 01 `event-search` |
| Gateway, trazabilidad básica | 00 `platform-conventions`, 07 `api-gateway` |
