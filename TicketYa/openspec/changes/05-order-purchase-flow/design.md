## Modelo de datos (order_db)
- `purchase_order(id, customer_id, event_id, event_starts_at, status, total, currency, hold_id NULL, hold_expires_at, idempotency_key, request_hash, payment_id NULL, created_at, version)`
  - `UNIQUE(customer_id, idempotency_key)`
- `order_item(id, order_id, sector_id, seat_id NULL, seat_label, unit_price, currency, status ACTIVE|REFUND_REQUESTED|REFUNDED|CANCELLED)`  ← **snapshot de precio**
- `purchase_quota(customer_id, event_id, used, PK(customer_id,event_id))`  ← control del límite
- `pay_attempt(id, order_id, idempotency_key, result, created_at, updated_at)` con `UNIQUE(order_id, idempotency_key)`; `result` ∈ IN_PROGRESS, APPROVED, CONFIRMED, REJECTED, NEEDS_REFUND, NEEDS_REFUND_AND_RELEASE, REFUNDED (permite reanudar o compensar si el proceso se corta)

## Estados de la orden
`PENDING_PAYMENT → CONFIRMED` (camino feliz)
`PENDING_PAYMENT → PAYMENT_REJECTED` (pago rechazado, hold liberado)
`PENDING_PAYMENT → EXPIRED` (venció la reserva, hold liberado)
`PENDING_PAYMENT → CANCELLED` (cancelada por el cliente)
`PENDING_PAYMENT → FAILED_REFUNDED` (pago aprobado pero no se pudo completar la venta; pago reintegrado)
`CONFIRMED → CANCELLED_BY_EVENT` (ver change 06)

## Saga orquestada (order-service es el orquestador)
**Paso 1 — crear orden** (`POST /api/orders`)
1. Validar `Idempotency-Key`.
2. `GET sale-info` a catalog → evento comprable, precios, sectores. Calcular total (snapshot en `order_item`).
3. Reservar cuota del cliente (UPDATE condicional, ver abajo).
4. `POST /internal/holds` a inventory con `orderId`. Si falla ⇒ compensar cuota.
5. Persistir orden `PENDING_PAYMENT` con `hold_expires_at`.

**Paso 2 — pagar** (`POST /api/orders/{id}/pay`)
1. Validar orden `PENDING_PAYMENT` y no vencida.
2. `POST /internal/payments` (clave determinística `pay-{orderId}`).
3. Si **REJECTED** ⇒ `DELETE hold`, liberar cuota, orden `PAYMENT_REJECTED`, responder 402.
4. Si **APPROVED** ⇒ `POST confirm hold`:
   - OK ⇒ `POST /internal/tickets` ⇒ orden `CONFIRMED`.
   - `HOLD_EXPIRED` o asiento tomado ⇒ **compensación**: reintegro total del pago, orden `FAILED_REFUNDED`, 410.
5. Si falla la emisión de tickets tras confirmar: se reintenta (idempotente por `orderItemId`). Si persiste el fallo, se compensa: reintegro + `sold/release` + `FAILED_REFUNDED`.
6. Timeout en el pago ⇒ la orden queda `PENDING_PAYMENT` con `pay_attempt` pendiente; el job de reconciliación consulta `GET /internal/payments?orderId` y decide.

## Control del límite por cliente
`UPDATE purchase_quota SET used = used + :n WHERE customer_id=:c AND event_id=:e AND used + :n <= :limit`
(con `INSERT ... ON CONFLICT DO NOTHING` previo). 0 filas ⇒ `PURCHASE_LIMIT_EXCEEDED`. Seguro ante órdenes simultáneas del mismo cliente. La cuota se libera en rechazo, expiración, cancelación y devolución. Límite configurable (por defecto 6).

## Idempotencia de la orden
Se guarda el hash del request. Misma clave + mismo hash ⇒ misma respuesta original (200/201). Misma clave + otro hash ⇒ 422 `IDEMPOTENCY_KEY_REUSED`.

## Reconciliación y expiración
`@Scheduled` cada 30 s: toma órdenes `PENDING_PAYMENT` con `hold_expires_at < now()` con `FOR UPDATE SKIP LOCKED`, libera hold y cuota y las marca `EXPIRED`. Seguro con múltiples instancias.

## Acoplamiento
Clientes HTTP por servicio con DTOs propios (anti-corruption layer). Cada cliente es un puerto (`CatalogPort`, `InventoryPort`, `PaymentPort`, `TicketPort`) para testearlo con dobles sin levantar nada.
