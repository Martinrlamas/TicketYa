-- payment_db. Dinero siempre NUMERIC(12,2). NO se guardan datos de tarjeta: solo token y últimos 4 dígitos.

create table payment (
    id              uuid primary key,
    order_id        uuid           not null,
    customer_id     uuid           not null,
    amount          numeric(12, 2) not null check (amount > 0),
    currency        varchar(3)     not null,
    method          varchar(20)    not null,
    method_token    varchar(100)   not null,
    last4           varchar(4),
    status          varchar(10)    not null check (status in ('APPROVED', 'REJECTED')),
    gateway_ref     varchar(60),
    idempotency_key varchar(100)   not null,
    request_hash    varchar(64)    not null,
    refunded_amount numeric(12, 2) not null default 0,
    created_at      timestamptz    not null default now(),
    constraint uq_payment_idempotency_key unique (idempotency_key),
    -- última red de seguridad del reintegro acumulado
    constraint ck_payment_refunded check (refunded_amount >= 0 and refunded_amount <= amount)
);
create index ix_payment_order on payment (order_id);

create table refund (
    id              uuid primary key,
    payment_id      uuid           not null references payment (id),
    amount          numeric(12, 2) not null check (amount > 0),
    status          varchar(10)    not null check (status in ('APPROVED', 'REJECTED')),
    reason          varchar(50),
    idempotency_key varchar(100)   not null,
    created_at      timestamptz    not null default now(),
    constraint uq_refund_idempotency_key unique (idempotency_key)
);
create index ix_refund_payment on refund (payment_id);
