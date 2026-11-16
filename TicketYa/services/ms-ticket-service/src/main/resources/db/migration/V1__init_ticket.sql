-- ticket_db. Solo IDs de referencia (sin FK hacia otras bases) y snapshot de la etiqueta del asiento.

create table ticket (
    id            uuid primary key,
    order_id      uuid         not null,
    order_item_id uuid         not null,
    event_id      uuid         not null,
    customer_id   uuid         not null,
    sector_id     uuid         not null,
    seat_id       uuid,                                  -- NULL si es zona general
    seat_label    varchar(120) not null,                 -- snapshot, ej. "Platea A-5"
    qr_code       varchar(64)  not null,                 -- 32 bytes aleatorios en base64url (43 chars)
    status        varchar(10)  not null check (status in ('VALID', 'USED', 'REFUNDED', 'CANCELLED')),
    issued_at     timestamptz  not null,
    used_at       timestamptz,                           -- NULL hasta E2 (control de acceso)
    constraint uq_ticket_order_item unique (order_item_id),   -- idempotencia de la emisión
    constraint uq_ticket_qr unique (qr_code)
);
create index ix_ticket_customer_issued on ticket (customer_id, issued_at);
create index ix_ticket_event_status on ticket (event_id, status);
create index ix_ticket_order on ticket (order_id);
