# Decisiones de diseño

Decisiones donde la especificación no alcanzaba, por servicio.

## Convenciones transversales (revisión de optimización)
| Tema | Decisión |
|---|---|
| Clientes HTTP entre servicios | Todos (catalog, inventory, order) usan `PlatformRestClientFactory.createInternal(url)`: timeouts, `X-Internal-Token`, correlation-id y traducción de 5xx/caídas a `UPSTREAM_UNAVAILABLE` salen del starter. Las URLs están en `ticketya.clients.<servicio>-url` y los timeouts en `ticketya.platform.http.*` |
| Swagger y seguridad | `RequireRoleOperationCustomizer` (starter) marca como protegido todo endpoint con `@RequireRole`; los controllers ya no repiten `@SecurityRequirement` y los endpoints públicos no muestran candado |
| Comparar códigos de error | `ApiException.hasCode(ErrorCode)` en vez de strings |
| Cuota de una orden | `QuotaService.release(PurchaseOrder)` en lugar de repetir cliente/evento/cantidad en cada transición |
| Idempotency-Key | `IdempotencyKey.require(...)` (order) valida y normaliza el header en crear y pagar |
| Gateway | Sin `logstash-logback-encoder`: la línea de log de cada request es texto `clave=valor`. El filtro `InternalPathBlockWebFilter` ya elimina `X-Internal-Token`, por eso las rutas no lo repiten |
| `GET /internal/tickets?orderId=` | Implementado en ticket-service (`listarPorOrden`). order-service todavía lee las entradas re-emitiendo de forma idempotente; puede migrarse a este endpoint (mejora opcional) |
| Identidad en controllers | Una sola vía: `Identity` (o `Optional<Identity>` en endpoints públicos). Se eliminaron `CurrentUser` y `@AuthenticatedUser` |
| Excepciones | Los servicios lanzan su excepción propia con métodos de fábrica (Factory Method: `OrderException.noEncontrado(...)`); el dominio de catalog usa `PlatformException`. Se eliminó `BusinessException` |
| Paquetes | Todos los servicios usan `entity/` (catalog dejó de usar `model/`) |
| Nombres | Métodos de servicios, dominio y fábricas en español (los getters JPA y las consultas derivadas de Spring Data siguen la convención del framework) |
| Dependencias | Los servicios ya no repiten `spring-boot-starter-web` ni `-validation`: llegan por el starter |

## General
| Tema | Decisión |
|---|---|
| Estado "cerrado" del inventario | Tabla `event_inventory(status, sold_out_notified)` |
| `name` de sector en availability | Campo opcional en el init; se guarda en `event_sector` |
| Idempotencia de `sold/release` | `sold_release.released_count` para devolver el mismo resultado al repetir |
| `DELETE` de un hold CONFIRMED | 204 sin efecto (la compensación post-venta va por `sold/release`) |
| Idempotencia de pagos concurrentes | Lock advisory por clave (`pg_advisory_xact_lock`) antes de llamar a la pasarela |
| Reintegro excedido | 422 con `code=VALIDATION_ERROR`, como dice el escenario de la spec |
| Clave de reintegro reutilizada con otros datos | 422 `IDEMPOTENCY_KEY_REUSED` (no está en la spec) |
| `tok_flaky` | El contador de fallos es por `idempotencyKey`, no por token |
| Emisión idempotente de tickets | `INSERT ... ON CONFLICT (order_item_id) DO NOTHING` por JDBC (seat_id puede ser NULL); 201 si creó alguna, 200 si todas existían |
| Invalidar un ticket ya invalidado con *otro* motivo | 409 `INVALID_STATE_TRANSITION` (REFUNDED/CANCELLED son terminales); no está en la spec |
| Invalidación masiva | Solo acepta `reason=CANCELLED` (400 si no) |
| Autorización en los servicios | Todos usan `Identity` (X-User-Id / X-User-Role) y `requireAnyRole` del starter (401/403) |
| Ticket ajeno | 404 para CUSTOMER (la spec lo pide), aunque `docs/05` menciona 403 |

## order-service (saga de compra)
- `POST /api/orders` (Idempotency-Key) crea cuota + hold + orden en una transacción; `POST /api/orders/{id}/pay`, `DELETE /api/orders/{id}`, `GET /api/orders[/{id}]`.
- La saga de pago es un orquestador sin transacción propia que alterna pasos de base cortos (`PayOrderTransactions`) con llamadas idempotentes a payment, inventory y ticket. El estado queda en `pay_attempt`, por lo que `PaymentReconciliationService` puede reanudarla.
- Jobs: expiración de órdenes (`FOR UPDATE SKIP LOCKED`) y reconciliación de pagos; se apagan con `order.jobs.*.enabled=false`.
- Los tests `*Test` corren con `mvn test`; los `*IT` (`OrderFlowIT`, `RefundFlowIT`) los ejecuta `maven-failsafe-plugin` en `mvn verify`.
- Los tests de integración usan PostgreSQL real y puertos (catalog/inventory/payment/ticket) simulados; el E2E con el gateway (30 clientes, un asiento) corresponde al change 07.

| Tema | Decisión |
|---|---|
| Entidad `Order` | Se llama `PurchaseOrder` (`Order` es palabra reservada de HQL) |
| Etiquetas de asiento | La respuesta de `POST /internal/holds` incluye `seats[{seatId, sectorId, label}]` y `sale-info` trae el `name` de cada sector: order arma "Platea A-5" sin descargar la disponibilidad del evento (con decenas de miles de asientos, cada compra bajaba todo el mapa). Un `seatId` inexistente lo rechaza inventory al crear el hold (404 `NOT_FOUND`) |
| `GET /api/orders` | No incluye entradas; el detalle de una orden CONFIRMED las trae con `ticketPort.issue` (idempotente). Conviene un `GET /internal/tickets?orderId=` |
| Clave de `pay_attempt` | Se guarda como `{orderId}:{Idempotency-Key}` |
| Estados nuevos del intento | `COMPENSATING` (ya se reintegra: solo se continúa reintegrando) y `ABANDONED` (sin rastro del cobro tras `abandon-after`) |
| Entradas emitidas pero con timeout antes de compensar | No se invalidan (pendiente) |
| Cuota | Se libera en la misma transacción que la transición terminal (rechazo, expiración, cancelación, FAILED_REFUNDED) |
| Pago con otra clave mientras hay uno en curso | 409 `INVALID_STATE_TRANSITION` |
| Pago de una orden ajena / consulta ajena | 404 |

## Devoluciones y cancelación de evento (change 06, dentro de order-service)
- Migración `V2__refunds_and_cancellation.sql`: `refund_request` (índice único parcial: una solicitud activa por ítem) y `event_cancellation`.
- Cliente: `POST /api/orders/{orderId}/items/{itemId}/refund-requests` (422 `REFUND_WINDOW_CLOSED`, 409 duplicada, 404 ajena) y `GET /api/refund-requests/mine`.
- Admin: `GET /api/refund-requests?status=`, `POST .../{id}/approve|reject`.
- Saga por solicitud (`RefundProcessor`): invalidar entrada, devolver el lugar (`releaseId` = id de la solicitud), reintegrar (`refund-{id}`), cerrar. El paso alcanzado se persiste en `refund_request.step`; `RefundRecoveryJob` reanuda con backoff exponencial y, agotados los intentos, deja la solicitud `FAILED` (visible para el ADMIN).
- Cancelación: `POST /internal/events/{eventId}/cancellation` (202 la primera vez, 200 si se repite). `CancelEventService` es reanudable: cierra el inventario, expira órdenes pendientes, pasa las confirmadas a `CANCELLED_BY_EVENT`, crea un reintegro automático por ítem y, cuando no queda nada pendiente, pide la invalidación en bloque. `EventCancellationJob` repite el ciclo hasta dejarla `settled`.
- `BuyerNotifier` (puerto) con adaptador que solo escribe en el log, con el `correlationId`.

| Tema | Decisión |
|---|---|
| Aprobar | Deja la solicitud en `PROCESSING` y responde; la saga la ejecuta el job (hasta ~5 s), no en el request. `APPROVED` queda en el esquema pero no se usa |
| `ticketId` de un ítem | No hay consulta por ítem en ticket-service: se usa la emisión idempotente por `orderItemId`. Conviene un `GET /internal/tickets?orderItemId=` |
| Falla de negocio en un paso (p. ej. entrada `USED`, 409) | `FAILED` de inmediato, sin reintentos; solo `UPSTREAM_UNAVAILABLE` se reintenta |
| Solicitud manual abierta al cancelarse el evento | Se absorbe: pasa a automática y la entrada se invalida con `CANCELLED` |
| Ítems al cancelar | Pasan a `CANCELLED` al crear el reintegro (como dice el design); la solicitud `COMPLETED` indica que ya se reintegró |
| Orden con pago en curso al cancelar | No se toca; la reconciliación la resuelve y la siguiente pasada del job la expira o la convierte en reintegro. Un pago nuevo responde 409 `EVENT_NOT_PURCHASABLE` |
| Holds de órdenes expiradas por cancelación | No se liberan explícitamente: inventory cierra el evento y los expira por TTL |
| Cuota | Se libera al completarse cada devolución (manual o automática) |
| Aviso al comprador | Se emite al completar el reintegro automático; si el proceso cae justo entre completar y avisar, el aviso (solo log en E1) se pierde |
| Solicitud manual `FAILED` | El ítem queda `REFUND_REQUESTED` y no hay endpoint de reintento para el ADMIN (no está en la spec) |
| Carrera conocida | Si una devolución manual está justo entre leer su motivo e invalidar la entrada cuando llega la cancelación, ticket-service puede responder 409 por motivo distinto: la solicitud queda `FAILED` y visible |
| `catalog` (`EventCancelled`) | Es el lado que llama; no está en este zip (task 2.4 de catalog) |
| Prueba "asiento recomprable" | Con inventory simulado solo se verifica el pedido `sold/release`; que el lugar vuelva a ser comprable lo prueba inventory y el E2E del change 07 |

## Starter de plataforma (change 00)
Ver `platform/ticketya-platform-starter/README.md`. Decisiones donde la spec no alcanzaba:

| Tema | Decisión |
|---|---|
| Endpoint `/api/**` sin `@RequireRole` | Es público (así `availability` y la búsqueda no exigen identidad); los protegidos se anotan |
| Códigos fuera del catálogo | `METHOD_NOT_ALLOWED` (405), `UNSUPPORTED_MEDIA_TYPE` (415), `NOT_ACCEPTABLE` (406) e `INTERNAL_ERROR` (500) |
| Log JSON | Una línea JSON en el logger `ticketya.http` (sin dependencias de encoders); el `correlationId` también sale en cada línea de log vía el patrón |
| Token interno | `ticketya.platform.internal-token`, por defecto `${INTERNAL_TOKEN:dev-internal-token}`; debe ser igual en todos los servicios. Sin secreto, `/internal/**` rechaza todo |
| Usuario de order_db | `orders` (no `order`, que es palabra reservada de SQL); coincide con el default de order-service |
| `init-databases.sql` | Contraseñas de desarrollo fijas; cada usuario solo puede conectarse a su propia base |
