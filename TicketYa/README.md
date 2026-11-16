# TicketYa

Plataforma de venta de entradas con microservicios independientes (Spring Boot 3.5, Java 21, JPA/Hibernate, PostgreSQL, Flyway)
detrás de un API Gateway. Este repositorio corresponde a la **Entrega 1 (core)**: publicación, búsqueda y compra de entradas
con reserva temporal, **sin doble venta**, devoluciones y cancelación de eventos.

## Ejecutar

Requisitos: Java 21, Maven 3.9+ y Docker (solo para PostgreSQL y los tests de integración).

### Modo Entrega 1: servicios locales + base en Docker

```bash
docker compose up -d postgres        # una base y un usuario por servicio (infra/postgres/init-databases.sql)

# una terminal por servicio (o en segundo plano); el gateway al final
mvn -q install -DskipTests           # una vez: compila el starter y los servicios
mvn -pl services/ms-catalog-service   spring-boot:run -Dspring-boot.run.profiles=local   # :8081 (carga datos de prueba)
mvn -pl services/ms-inventory-service spring-boot:run     # :8082
mvn -pl services/ms-payment-service   spring-boot:run     # :8084
mvn -pl services/ms-ticket-service    spring-boot:run     # :8085
mvn -pl services/ms-order-service     spring-boot:run     # :8083
mvn -pl services/ms-api-gateway       spring-boot:run     # :8080
```

Los valores por defecto de cada `application.yml` apuntan a `localhost` y a las bases del paso 1.

### Todo en Docker (ya preparado para la Entrega 2)

```bash
docker compose up --build -d         # PostgreSQL + 5 servicios + gateway
docker compose ps
docker compose down                  # con -v borra también las bases
```

| Qué | URL |
|---|---|
| API Gateway (única entrada para clientes) | http://localhost:8080 |
| Swagger UI (contrato público con ejemplos y errores) | http://localhost:8080/swagger-ui.html |
| Salud del gateway | http://localhost:8080/actuator/health |

Variables (`INTERNAL_TOKEN`, credenciales de Postgres): valores de desarrollo por defecto; copiar `.env.example` a `.env` para cambiarlas.

## Probar

- **Postman:** importar `postman/TicketYa-Entrega1.postman_collection.json`. La carpeta *00 - Flujo de compra completo* se ejecuta en orden
  (Runner) y verifica: crear lugar → sectores → evento → precios → publicar → buscar → disponibilidad → orden → reintento idempotente (200)
  → otro cliente por el mismo asiento (409) → pago → reintento de pago sin doble cobro → entradas con QR → devolución → aprobación →
  asiento liberado → pago rechazado (402) → cancelación del evento.
- **Scripts:** `scripts/smoke/` (bash + curl + jq) ejecuta el mismo recorrido contra el sistema levantado. Ver su README.
- **Identidad (E1, sin login):** headers `X-User-Id` (UUID) y `X-User-Role` (`CUSTOMER`, `ORGANIZER`, `ADMIN`, `ACCESS_STAFF`).
- **Pasarela simulada:** tokens `tok_approved` (aprueba), `tok_rejected` (rechaza), `tok_timeout` (tarda y aprueba), `tok_flaky` (falla la primera vez).
- **Datos de prueba:** el perfil `local` de catalog carga 2 lugares, 4 sectores y 3 eventos; `scripts/smoke/03-seed.sh` agrega más por la API.

## Tests

```bash
mvn test        # unitarios + pruebas con PostgreSQL real (Testcontainers: requiere Docker), incluida la de no doble venta
mvn verify      # además los *IT de order-service (OrderFlowIT, RefundFlowIT), que ejecuta maven-failsafe-plugin
```

Prueba de no doble venta (`inventory-service`, `HoldServiceTest` y `MultiInstanceHoldTest`): 50 hilos sobre un asiento → 1 éxito;
100 hilos sobre una zona con 10 lugares → 10 éxitos; dos instancias del servicio sobre la misma base → 1 éxito.

## Arquitectura

```
cliente ──► api-gateway :8080 ──┬─► catalog-service   :8081 ── catalog_db
 (X-User-Id / X-User-Role)      ├─► inventory-service :8082 ── inventory_db
                                ├─► order-service     :8083 ── order_db ──► catalog, inventory, payment, ticket
                                └─► ticket-service    :8085 ── ticket_db
                                    payment-service   :8084 ── payment_db   (solo lo invoca order-service)
```

- **Una base por servicio**, sin joins ni claves foráneas entre bases; solo IDs de referencia y *snapshots* (precio, fecha, etiqueta de asiento).
- **Comunicación:** REST síncrono. Los endpoints `/internal/**` exigen `X-Internal-Token` y el gateway nunca los expone.
  Ningún servicio importa clases de otro: cada uno define sus DTO de cable y llama a los demás por URL (`ticketya.clients.*`).
- **No doble venta:** la garantía vive en la base de datos (`UPDATE ... WHERE status='AVAILABLE'` por asiento, `UPDATE ... WHERE held+sold+n <= capacity`
  por zona, `CHECK` y `UNIQUE`), por eso funciona con varias instancias. Los reintentos son seguros por `Idempotency-Key`.
- **Consistencia entre servicios:** saga orquestada por `order-service` con pasos idempotentes y compensación (reintegro automático
  si el pago se aprueba pero ya no se puede vender).
- **Patrones:** capas `controller → service → repository → entity` con `dto` y `mapper`; Factory Method (`OrderException.noEncontrado(...)`,
  `StockItem.seats(...)`); puertos y adaptadores para llamar a otros servicios (`*Port` + `*RestAdapter`); `PaymentGateway` intercambiable
  (simulado hoy); beans de Spring como singletons.
- **Código compartido = solo infraestructura técnica** (`platform/ticketya-platform-starter`): correlation-id, log de requests, identidad
  y roles, errores RFC 7807, cliente REST con timeouts. Un test ArchUnit impide que tenga lógica de negocio.

## Documentación

| Documento | Contenido |
|---|---|
| [docs/01-features-user-stories.md](docs/01-features-user-stories.md) | Features y user stories con criterios de aceptación (sistema completo; marcadas E1/E2) |
| [docs/02-architecture.md](docs/02-architecture.md) | Microservicios, responsabilidades, diagrama de módulos, matriz de comunicación |
| [docs/03-sequence-purchase.md](docs/03-sequence-purchase.md) | Secuencia de compra de una entrada numerada (pago rechazado, reserva vencida, compensaciones) y estados |
| [docs/04-data-model.md](docs/04-data-model.md) | ER por servicio, relaciones entre servicios y entidades solapadas |
| [docs/05-api-endpoints.md](docs/05-api-endpoints.md) | 43 endpoints: método, ruta, parámetros, códigos y JSON de ejemplo |
| [docs/openapi/](docs/openapi) | OpenAPI por servicio y contrato público agregado (el que sirve el gateway) |
| [docs/decisiones.md](docs/decisiones.md) | Decisiones de diseño por servicio |
| [openspec/](openspec) | Especificaciones (propuesta → requisitos y escenarios → diseño → tareas) por *change* |

## Estructura

```
pom.xml                          POM raíz: versiones y reactor Maven
compose.yml                      PostgreSQL (+ servicios y gateway en contenedores)
infra/                           Dockerfile genérico e init de las bases
platform/ticketya-platform-starter/   Starter técnico compartido
services/ms-*-service/           Microservicios de negocio
services/ms-api-gateway/         API Gateway (WebFlux)
docs/  openspec/  postman/       Diseño, especificaciones y colección de requests
scripts/smoke/                   Pruebas manuales end-to-end
ticketya-web/                    Interfaz web (React + Vite) que consume el gateway
```

## Alcance

Entrega 1: ver tabla del enunciado. Queda para la Entrega 2: JWT con roles, MongoDB, fila virtual, control de acceso con QR,
recomendaciones, notificaciones, reportes, resiliencia (circuit breaker), cache, log centralizado y prueba de carga.
