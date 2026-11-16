# Modelo de datos — un diagrama entidad-relación por microservicio

Fuente: las migraciones Flyway de cada servicio (`src/main/resources/db/migration/`).
Reglas comunes: PostgreSQL 16, **una base y un usuario por servicio**, `ddl-auto=validate`, IDs `UUID`, dinero `NUMERIC(12,2)`, tiempos `TIMESTAMPTZ`.
**No hay claves foráneas entre bases**: los atributos marcados `ref` son IDs de otro servicio (se validan por API, no por la base).

## 1. catalog_db (catalog-service)

```mermaid
erDiagram
  VENUE ||--o{ SECTOR : tiene
  SECTOR ||--o{ SECTOR_ROW : "filas (solo NUMBERED)"
  VENUE ||--o{ EVENT : aloja
  EVENT ||--o{ EVENT_PARTICIPANT : incluye
  EVENT ||--o{ EVENT_SECTOR_PRICE : "precio por sector"
  SECTOR ||--o{ EVENT_SECTOR_PRICE : "se cotiza en"

  VENUE {
    uuid id PK
    varchar name
    varchar address
    varchar city
    numeric latitude
    numeric longitude
  }
  SECTOR {
    uuid id PK
    uuid venue_id FK
    varchar name "UNIQUE con venue_id"
    varchar type "NUMBERED | GENERAL"
    int capacity "solo GENERAL"
  }
  SECTOR_ROW {
    uuid id PK
    uuid sector_id FK
    varchar row_label "UNIQUE con sector_id"
    int seat_count
  }
  EVENT {
    uuid id PK
    varchar name
    text description
    varchar type
    uuid organizer_id "ref identidad"
    uuid venue_id FK
    timestamptz starts_at
    varchar status
    numeric min_price "desnormalizado para ordenar"
    bigint version
    timestamptz created_at
  }
  EVENT_PARTICIPANT {
    uuid id PK
    uuid event_id FK
    varchar name
    varchar role "ARTIST | TEAM | SPEAKER"
  }
  EVENT_SECTOR_PRICE {
    uuid id PK
    uuid event_id FK
    uuid sector_id FK
    numeric price "mayor a 0"
    varchar currency
  }
```

`EVENT_SECTOR_PRICE` tiene `UNIQUE (event_id, sector_id)`: un precio por sector y evento.

## 2. inventory_db (inventory-service)

```mermaid
erDiagram
  EVENT_INVENTORY ||--o{ EVENT_SECTOR : "sectores del evento"
  EVENT_SECTOR ||--o{ EVENT_SEAT : "asientos (NUMBERED)"
  EVENT_SECTOR ||--o| ZONE_STOCK : "contador (GENERAL)"
  HOLD ||--o{ HOLD_ITEM : retiene
  HOLD ||--o{ EVENT_SEAT : "hold_id (sin FK)"

  EVENT_INVENTORY {
    uuid event_id PK "ref catalog"
    varchar status "OPEN | CLOSED"
    boolean sold_out_notified
  }
  EVENT_SECTOR {
    uuid event_id PK
    uuid sector_id PK "ref catalog"
    varchar type
    varchar name
    int position
  }
  EVENT_SEAT {
    uuid id PK
    uuid event_id
    uuid sector_id
    varchar row_label
    int seat_number "UNIQUE evento-sector-fila-numero"
    varchar status "AVAILABLE | HELD | SOLD"
    uuid hold_id
    bigint version
  }
  ZONE_STOCK {
    uuid event_id PK
    uuid sector_id PK
    int capacity
    int held
    int sold "CHECK held+sold<=capacity"
  }
  HOLD {
    uuid id PK
    uuid event_id
    uuid order_id "UNIQUE ref order"
    varchar status "ACTIVE | CONFIRMED | RELEASED | EXPIRED"
    timestamptz expires_at
  }
  HOLD_ITEM {
    uuid id PK
    uuid hold_id FK
    uuid sector_id
    uuid seat_id "NULL si es zona"
    int quantity
  }
  SOLD_RELEASE {
    uuid release_id PK "idempotencia"
    uuid event_id
    int released_count
  }
```

**Garantía anti doble venta (vive en esta base):** `UPDATE event_seat ... WHERE status='AVAILABLE'` (el que gana ve 1 fila afectada),
`UPDATE zone_stock ... WHERE held + sold + n <= capacity` y el `CHECK ck_zone_stock`, más `UNIQUE (order_id)` en `hold` para reintentos.

## 3. order_db (order-service)

```mermaid
erDiagram
  PURCHASE_ORDER ||--|{ ORDER_ITEM : contiene
  PURCHASE_ORDER ||--o{ PAY_ATTEMPT : "estado de la saga"
  PURCHASE_ORDER ||--o{ REFUND_REQUEST : "devoluciones"
  ORDER_ITEM ||--o{ REFUND_REQUEST : "una activa por ítem"

  PURCHASE_ORDER {
    uuid id PK
    uuid customer_id "ref identidad"
    uuid event_id "ref catalog"
    timestamptz event_starts_at "snapshot (regla 72 h)"
    varchar status
    numeric total
    varchar currency
    uuid hold_id "ref inventory"
    timestamptz hold_expires_at
    varchar idempotency_key "UNIQUE con customer_id"
    varchar request_hash
    uuid payment_id "ref payment"
    varchar payment_method
    varchar payment_last4
    bigint version
  }
  ORDER_ITEM {
    uuid id PK
    uuid order_id FK
    int position
    uuid sector_id "ref"
    uuid seat_id "ref, NULL en zona"
    varchar seat_label "snapshot"
    numeric unit_price "snapshot"
    varchar status "ACTIVE | REFUND_REQUESTED | REFUNDED | CANCELLED"
  }
  PURCHASE_QUOTA {
    uuid customer_id PK
    uuid event_id PK
    int used "limite por cliente y evento"
  }
  PAY_ATTEMPT {
    uuid id PK
    uuid order_id FK
    varchar idempotency_key "UNIQUE"
    varchar result "PENDING | APPROVED | COMPENSATING | COMPLETED | REJECTED | COMPENSATED | ABANDONED"
    varchar reason
  }
  REFUND_REQUEST {
    uuid id PK
    uuid order_id FK
    uuid order_item_id FK
    uuid customer_id
    uuid event_id
    varchar status "PENDING | REJECTED | APPROVED | PROCESSING | COMPLETED | FAILED"
    varchar step "NONE | TICKET_INVALIDATED | SEAT_RELEASED | PAYMENT_REFUNDED"
    boolean automatic "true si viene de una cancelación de evento"
    int attempts
    timestamptz next_attempt_at
  }
  EVENT_CANCELLATION {
    uuid event_id PK "idempotencia"
    int orders_expired
    int items_to_refund
    boolean inventory_closed
    boolean tickets_invalidated
    boolean settled
  }
```

`PURCHASE_QUOTA` se actualiza con `UPDATE ... WHERE used + n <= límite`. `REFUND_REQUEST` tiene un índice único parcial
`(order_item_id) WHERE status IN ('PENDING','APPROVED','PROCESSING')`: una sola solicitud activa por entrada.
Los precios y la etiqueta del asiento son **snapshot**: cambios posteriores en catalog no alteran órdenes ya creadas.

## 4. payment_db (payment-service)

```mermaid
erDiagram
  PAYMENT ||--o{ REFUND : "reintegros"
  PAYMENT {
    uuid id PK
    uuid order_id "ref order"
    uuid customer_id
    numeric amount
    varchar currency
    varchar method
    varchar method_token "no se guardan datos de tarjeta"
    varchar last4
    varchar status "APPROVED | REJECTED"
    varchar gateway_ref
    varchar idempotency_key "UNIQUE"
    varchar request_hash
    numeric refunded_amount "CHECK 0..amount"
  }
  REFUND {
    uuid id PK
    uuid payment_id FK
    numeric amount
    varchar status
    varchar reason
    varchar idempotency_key "UNIQUE"
  }
```

## 5. ticket_db (ticket-service)

```mermaid
erDiagram
  TICKET {
    uuid id PK
    uuid order_id "ref order"
    uuid order_item_id "UNIQUE: idempotencia de emisión"
    uuid event_id "ref catalog"
    uuid customer_id
    uuid sector_id
    uuid seat_id "NULL en zona"
    varchar seat_label "snapshot"
    varchar qr_code "UNIQUE, 32 bytes aleatorios"
    varchar status "VALID | USED | REFUNDED | CANCELLED"
    timestamptz issued_at
    timestamptz used_at "reservado para el control de acceso (E2)"
  }
```

## 6. Relaciones entre servicios (sin FK)

| Desde | Atributo | Hacia | Cómo se valida |
|---|---|---|---|
| inventory | `event_inventory.event_id`, `event_sector.sector_id` | catalog `event.id`, `sector.id` | catalog manda el detalle al publicar (`POST /internal/events/{id}/inventory`) |
| order | `purchase_order.event_id`, `order_item.sector_id/seat_id` | catalog / inventory | `sale-info` al crear la orden; inventory rechaza asientos inexistentes al crear el hold |
| order | `purchase_order.hold_id` | inventory `hold.id` | lo devuelve inventory al crear el hold |
| inventory | `hold.order_id` | order `purchase_order.id` | lo envía order (UNIQUE: un hold por orden) |
| order | `purchase_order.payment_id` | payment `payment.id` | lo devuelve payment al cobrar |
| payment | `payment.order_id` | order `purchase_order.id` | lo envía order |
| ticket | `ticket.order_id`, `order_item_id` | order | lo envía order al emitir (UNIQUE por `order_item_id`) |
| catalog / order / payment / ticket | `organizer_id`, `customer_id` | identidad (E2) | en E1 viene del header `X-User-Id` |

## 7. Entidades y datos solapados entre bases

| Dato | Fuente de verdad | Copias | Tipo de copia | Por qué existe | Cómo se mantiene |
|---|---|---|---|---|---|
| Estructura de asientos del lugar | catalog (`sector`, `sector_row`) | inventory (`event_seat`, `zone_stock`) | **Derivada** (una fila por asiento y evento) | inventory necesita una fila por asiento para bloquear | Se crea al publicar (`init` idempotente); desde ahí inventory es dueño del **estado** del asiento |
| Estado SOLD_OUT del evento | catalog (`event.status`) | derivado de inventory (contadores) | **Derivada** | Mostrar "agotado" en búsquedas sin consultar inventory | inventory avisa SOLD_OUT / reapertura a catalog |
| Precio unitario | catalog (`event_sector_price`) | order (`order_item.unit_price`) | **Snapshot** | La compra conserva el precio pactado | Se copia una vez al crear la orden |
| Fecha del evento | catalog (`event.starts_at`) | order (`event_starts_at`) | **Snapshot** | Aplicar la regla de 72 h sin llamar a catalog | Se copia al crear la orden |
| Etiqueta del asiento ("Platea A-5") | inventory (fila/número) + catalog (nombre del sector) | order (`seat_label`), ticket (`seat_label`) | **Desnormalización de lectura** | Mostrar entradas sin joins entre servicios | order la arma con la respuesta del hold y la envía a ticket al emitir |
| Monto de la compra | payment (`payment.amount`) | order (`total`) | **Duplicado intencional** | order calcula el total; payment registra lo cobrado | payment es la autoridad sobre el dinero movido |
| Medio de pago | payment (`method`, `last4`) | order (`payment_method`, `payment_last4`) | **Copia de lectura** | Mostrar el detalle de la compra | Se copia al aprobarse el pago |
| Estado de la entrada vs. del ítem | ticket (`ticket.status`) | order (`order_item.status`) | **Estados relacionados** | Cada servicio modela su ciclo | La saga de devolución actualiza ambos en pasos idempotentes |
| `eventId`, `sectorId`, `seatId`, `orderId`, `orderItemId` | su servicio dueño | inventory, order, payment, ticket | **Referencia / clave de idempotencia** | Correlacionar sin FK | UNIQUE en cada consumidor |

Entre estas copias se aplica **consistencia eventual con compensaciones** (ver `03-sequence-purchase.md`);
dentro de cada servicio, transacciones ACID y restricciones de base.
