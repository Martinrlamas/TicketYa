-- Change 06: devoluciones por entrada y cancelación de evento.

-- Solicitud de devolución. step = último paso de la saga completado (retoma desde ahí).
create table refund_request (
    id               uuid primary key,
    order_id         uuid          not null references purchase_order (id),
    order_item_id    uuid          not null references order_item (id),
    customer_id      uuid          not null,
    event_id         uuid          not null,
    reason           varchar(500),
    decision_reason  varchar(500),
    status           varchar(12)   not null check (status in
        ('PENDING', 'REJECTED', 'APPROVED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    step             varchar(20)   not null default 'NONE' check (step in
        ('NONE', 'TICKET_INVALIDATED', 'SEAT_RELEASED', 'PAYMENT_REFUNDED')),
    automatic        boolean       not null default false,   -- true: por cancelación de evento (sin aprobación)
    attempts         int           not null default 0,       -- fallas acumuladas del procesamiento
    next_attempt_at  timestamptz,                            -- backoff / lease del job de recuperación
    last_error       varchar(300),
    decided_by       uuid,
    decided_at       timestamptz,
    created_at       timestamptz   not null,
    updated_at       timestamptz   not null
);
-- Una sola solicitud activa por ítem (la garantía vive en la base)
create unique index uq_refund_active_item on refund_request (order_item_id)
    where status in ('PENDING', 'APPROVED', 'PROCESSING');
create index ix_refund_status_created on refund_request (status, created_at);
create index ix_refund_customer_created on refund_request (customer_id, created_at);
create index ix_refund_processing on refund_request (status, next_attempt_at);

-- Cancelaciones de evento recibidas (idempotencia por event_id) y su avance.
create table event_cancellation (
    event_id            uuid primary key,
    reason              varchar(500),
    orders_expired      int         not null default 0,
    items_to_refund     int         not null default 0,
    inventory_closed    boolean     not null default false,
    tickets_invalidated boolean     not null default false,
    settled             boolean     not null default false,  -- ya no quedan órdenes pendientes por resolver
    created_at          timestamptz not null,
    updated_at          timestamptz not null
);
create index ix_order_event_status on purchase_order (event_id, status);
