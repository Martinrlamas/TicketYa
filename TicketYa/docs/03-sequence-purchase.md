# Diagramas de secuencia — compra de una entrada numerada

Convenciones: `GW` = api-gateway, `O` = order-service, `CA` = catalog, `I` = inventory, `P` = payment, `T` = ticket.
Todos los mensajes llevan `X-Correlation-Id`; las llamadas internas llevan `X-Internal-Token`.
En el código, el paso 1 es `CreateOrderService` y el paso 2 es `PayOrderService` (order-service).

## 1. Flujo completo con rechazo de pago y vencimiento de reserva

```mermaid
sequenceDiagram
  autonumber
  actor C as Cliente
  participant GW as API Gateway
  participant O as order-service
  participant CA as catalog-service
  participant I as inventory-service
  participant P as payment-service
  participant T as ticket-service

  Note over C,T: PASO 1 — Reserva temporal
  C->>GW: GET /api/events/{id}/availability
  GW->>I: (ruteo) availability
  I-->>C: asientos AVAILABLE/HELD/SOLD (no-store)
  C->>GW: POST /api/orders [Idempotency-Key, asientos A-5, A-6]
  GW->>GW: valida X-User-Id/Role, genera correlation-id
  GW->>O: POST /api/orders
  O->>O: ¿clave ya usada? si sí → devuelve orden original
  O->>CA: GET /internal/events/{id}/sale-info
  CA-->>O: purchasable, precios, startsAt
  O->>O: calcula total y snapshot de precio
  Note over O: desde acá, UNA transacción de base en order_db
  O->>O: reserva cuota del cliente (UPDATE condicional)
  alt límite superado
    O-->>C: 422 PURCHASE_LIMIT_EXCEEDED
  end
  O->>I: POST /internal/holds {orderId, asientos, ttl=600s}
  alt algún asiento ocupado
    I-->>O: 409 SEAT_UNAVAILABLE (nada queda retenido)
    O->>O: rollback (la cuota vuelve sola)
    O-->>C: 409 SEAT_UNAVAILABLE
  else asientos retenidos (UPDATE condicional AVAILABLE→HELD)
    I-->>O: 201 holdId, expiresAt, seats[etiqueta]
    O->>O: guarda orden PENDING_PAYMENT con etiquetas "Platea A-5"
    O-->>C: 201 orden + holdExpiresAt
  end

  Note over C,T: PASO 2 — Pago
  C->>GW: POST /api/orders/{id}/pay [Idempotency-Key, token]
  GW->>O: POST /api/orders/{id}/pay
  alt reserva vencida (now > holdExpiresAt)
    O->>I: DELETE /internal/holds/{id}
    O->>O: orden EXPIRED, libera cuota
    O-->>C: 410 HOLD_EXPIRED
  else reserva vigente
    O->>P: POST /internal/payments [clave pay-{orderId}]
    alt pago RECHAZADO
      P-->>O: 200 status=REJECTED
      O->>I: DELETE /internal/holds/{id}
      I-->>O: 204 (asientos HELD→AVAILABLE)
      O->>O: orden PAYMENT_REJECTED, libera cuota
      O-->>C: 402 PAYMENT_REJECTED
    else pago APROBADO
      P-->>O: 200 status=APPROVED
      O->>I: POST /internal/holds/{id}/confirm
      alt hold venció durante el pago
        I-->>O: 410 HOLD_EXPIRED
        O->>P: POST /internal/payments/{id}/refunds (total)
        O->>O: orden FAILED_REFUNDED, libera cuota
        O-->>C: 410 HOLD_EXPIRED (reintegro automático)
      else confirmado (HELD→SOLD)
        I-->>O: 200 CONFIRMED
        O->>T: POST /internal/tickets [por orderItemId]
        T-->>O: 201 entradas con QR
        O->>O: orden CONFIRMED, guarda paymentId
        O-->>C: 200 orden + entradas con QR
      end
    end
  end

  Note over O,I: En paralelo, jobs periódicos (seguros con N instancias)
  O->>I: DELETE hold (órdenes PENDING vencidas → EXPIRED)
  I->>I: ExpireHoldsJob libera holds ACTIVE vencidos (SKIP LOCKED)
```

## 2. Compensación si falla la emisión de entradas

```mermaid
sequenceDiagram
  autonumber
  participant O as order-service
  participant I as inventory-service
  participant P as payment-service
  participant T as ticket-service
  Note over O: pago APPROVED y hold CONFIRMED
  O->>T: POST /internal/tickets
  T--xO: timeout / 503
  loop reintentos idempotentes (orderItemId único)
    O->>T: POST /internal/tickets
  end
  alt un reintento responde OK
    T-->>O: 201 → orden CONFIRMED
  else se agotan los reintentos
    O->>P: refund total (clave refund-{orderId})
    O->>I: POST /internal/events/{id}/sold/release (releaseId)
    O->>O: orden FAILED_REFUNDED, libera cuota
  end
```

## 3. Devolución aprobada (saga con roll-forward)

```mermaid
sequenceDiagram
  autonumber
  actor C as Cliente
  actor A as Administrador
  participant O as order-service
  participant T as ticket-service
  participant I as inventory-service
  participant P as payment-service
  C->>O: POST .../refund-requests (≥72 h antes)
  O-->>C: 201 PENDING
  A->>O: POST /api/refund-requests/{id}/approve
  O->>O: PROCESSING (guarda step)
  O->>T: invalidate ticket → REFUNDED
  O->>I: sold/release {releaseId=requestId}
  O->>P: refund {clave refund-{requestId}}
  alt payment falla
    P--xO: 503
    O->>O: queda PROCESSING; RefundRecoveryJob reintenta desde el último paso
  end
  O->>O: ítem REFUNDED, libera cuota, solicitud COMPLETED
```

## 4. Estados

```mermaid
stateDiagram-v2
  [*] --> PENDING_PAYMENT: POST /api/orders
  PENDING_PAYMENT --> CONFIRMED: pago aprobado + venta + entradas
  PENDING_PAYMENT --> PAYMENT_REJECTED: pago rechazado
  PENDING_PAYMENT --> EXPIRED: venció la reserva / evento cancelado
  PENDING_PAYMENT --> CANCELLED: cancela el cliente
  PENDING_PAYMENT --> FAILED_REFUNDED: pagó pero no se pudo vender
  CONFIRMED --> [*]
```

```mermaid
stateDiagram-v2
  [*] --> DRAFT
  DRAFT --> PUBLISHED: publicar
  PUBLISHED --> SOLD_OUT: sin lugares (inventory)
  SOLD_OUT --> PUBLISHED: se liberan lugares
  PUBLISHED --> SUSPENDED: admin
  SOLD_OUT --> SUSPENDED: admin
  SUSPENDED --> PUBLISHED: admin
  DRAFT --> CANCELLED: admin
  PUBLISHED --> CANCELLED: admin
  SOLD_OUT --> CANCELLED: admin
  SUSPENDED --> CANCELLED: admin
  PUBLISHED --> FINISHED: pasó la fecha
  SOLD_OUT --> FINISHED: pasó la fecha
```

```mermaid
stateDiagram-v2
  state "Asiento" as S
  [*] --> AVAILABLE
  AVAILABLE --> HELD: hold (UPDATE condicional)
  HELD --> SOLD: confirm
  HELD --> AVAILABLE: release / expira
  SOLD --> AVAILABLE: devolución / reintegro
```
