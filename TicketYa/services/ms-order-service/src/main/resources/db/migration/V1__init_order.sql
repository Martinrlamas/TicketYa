-- order_db. Sin FKs hacia otras bases: eventId, sectorId, seatId, holdId, paymentId son IDs simples.
-- Los precios son SNAPSHOT: cambios posteriores en catalog no alteran órdenes existentes.

create table purchase_order (
    id               uuid primary key,
    customer_id      uuid           not null,
    event_id         uuid           not null,
    event_starts_at  timestamptz    not null,              -- snapshot (regla de 72 h de devoluciones)
    status           varchar(20)    not null check (status in
        ('PENDING_PAYMENT', 'CONFIRMED', 'PAYMENT_REJECTED', 'EXPIRED', 'CANCELLED', 'FAILED_REFUNDED', 'CANCELLED_BY_EVENT')),
    total            numeric(12, 2) not null check (total >= 0),
    currency         varchar(3)     not null,
    hold_id          uuid,
    hold_expires_at  timestamptz,
    idempotency_key  varchar(100)   not null,
    request_hash     varchar(64)    not null,
    payment_id       uuid,
    payment_method   varchar(20),
    payment_last4    varchar(4),
    version          bigint         not null default 0,
    created_at       timestamptz    not null,
    constraint uq_order_customer_idempotency unique (customer_id, idempotency_key)
);
create index ix_order_status_hold_expires on purchase_order (status, hold_expires_at);   -- job de expiración
create index ix_order_customer_created on purchase_order (customer_id, created_at);

-- En zonas generales hay UN ítem por lugar (seat_id NULL): simplifica devoluciones por unidad.
create table order_item (
    id         uuid primary key,
    order_id   uuid           not null references purchase_order (id),
    position   int            not null,
    sector_id  uuid           not null,
    seat_id    uuid,
    seat_label varchar(120)   not null,                    -- snapshot, ej. "Platea A-5"
    unit_price numeric(12, 2) not null check (unit_price >= 0),
    currency   varchar(3)     not null,
    status     varchar(20)    not null check (status in ('ACTIVE', 'REFUND_REQUESTED', 'REFUNDED', 'CANCELLED'))
);
create index ix_order_item_order on order_item (order_id);

-- Control del límite por cliente y evento (UPDATE condicional used + n <= limit)
create table purchase_quota (
    customer_id uuid not null,
    event_id    uuid not null,
    used        int  not null check (used >= 0),
    primary key (customer_id, event_id)
);

-- Intentos de pago: son el "estado de la saga" que permite reanudar tras fallas parciales.
-- result: PENDING (cobro iniciado) | APPROVED (cobrado, falta vender) | COMPENSATING (reintegrando)
--         COMPLETED | REJECTED | COMPENSATED | ABANDONED (sin rastro en payment-service)
create table pay_attempt (
    id              uuid primary key,
    order_id        uuid         not null references purchase_order (id),
    idempotency_key varchar(150) not null,                 -- "{orderId}:{Idempotency-Key del cliente}"
    result          varchar(15)  not null check (result in
        ('PENDING', 'APPROVED', 'COMPENSATING', 'COMPLETED', 'REJECTED', 'COMPENSATED', 'ABANDONED')),
    reason          varchar(30),                           -- HOLD_EXPIRED | TICKETS_FAILED (al compensar)
    created_at      timestamptz  not null,
    updated_at      timestamptz  not null,
    constraint uq_pay_attempt_key unique (idempotency_key)
);
create index ix_pay_attempt_order on pay_attempt (order_id);
create index ix_pay_attempt_result_updated on pay_attempt (result, updated_at);
