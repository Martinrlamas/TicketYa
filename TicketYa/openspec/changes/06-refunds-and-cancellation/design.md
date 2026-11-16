## Modelo de datos adicional (order_db)
- `refund_request(id, order_item_id, order_id, customer_id, reason, status PENDING|REJECTED|APPROVED|PROCESSING|COMPLETED|FAILED, step, attempts, decided_by, decided_at, created_at)`
  - Índice único parcial: una sola solicitud activa por `order_item_id`
    (`WHERE status IN ('PENDING','APPROVED','PROCESSING')`).

## Saga de devolución (orquestada por order-service)
Pasos, cada uno idempotente (se pueden repetir sin efecto adicional):
1. `invalidate ticket` (ticket-service) → entrada `REFUNDED`
2. `release sold seat/quantity` (inventory-service, `releaseId = refundRequestId`)
3. `refund payment` (payment-service, `idempotencyKey = refund-{refundRequestId}`)
4. Marcar ítem `REFUNDED`, liberar cuota, solicitud `COMPLETED`

Se persiste el paso alcanzado (`step`). Si un paso falla, la solicitud queda `PROCESSING` y un job de recuperación
(`@Scheduled`, `SKIP LOCKED`) reintenta desde el último paso con backoff. Tras N intentos pasa a `FAILED` y queda visible para un ADMIN.
Se prefiere **consistencia eventual con reintento hacia adelante** (roll-forward) porque la decisión de reintegrar ya fue tomada.

## Regla de 72 h
Se calcula contra `event_starts_at` guardado en la orden (snapshot). Se valida al solicitar; la aprobación posterior del administrador no revalida la ventana, ya que la solicitud fue legítima al crearse.

## Cancelación de evento
catalog invoca `POST /internal/events/{eventId}/cancellation`. order-service:
1. Responde 202 y registra la cancelación (idempotente por `eventId`).
2. Expira órdenes `PENDING_PAYMENT` del evento (libera cuota; el hold ya no importa porque inventory cierra el evento).
3. Marca ítems `ACTIVE` como `CANCELLED` y crea un `refund_request` automático (`PROCESSING`, sin aprobación) por ítem.
4. Procesa en lotes con el mismo procesador de devoluciones.
5. Pide invalidación masiva a ticket-service y `close` a inventory.
La notificación a compradores queda como puerto `BuyerNotifier` (implementación vacía/log en E1; real en E2).
