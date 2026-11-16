-- inventory_db: la garantía anti doble venta vive aquí (constraints + UPDATEs condicionales).

-- Estado del inventario de un evento (abierto/cerrado) y flag de SOLD_OUT ya notificado a catalog.
create table event_inventory (
    event_id          uuid primary key,
    status            varchar(10) not null default 'OPEN' check (status in ('OPEN', 'CLOSED')),
    sold_out_notified boolean     not null default false,
    created_at        timestamptz not null default now()
);

-- Sectores del evento (solo lo que inventory necesita: tipo y nombre opcional para mostrar).
create table event_sector (
    event_id  uuid         not null,
    sector_id uuid         not null,
    type      varchar(10)  not null check (type in ('NUMBERED', 'GENERAL')),
    name      varchar(120),
    position  int          not null default 0,
    primary key (event_id, sector_id)
);

-- Asientos numerados
create table event_seat (
    id          uuid primary key,
    event_id    uuid        not null,
    sector_id   uuid        not null,
    row_label   varchar(20) not null,
    seat_number int         not null,
    status      varchar(10) not null check (status in ('AVAILABLE', 'HELD', 'SOLD')),
    hold_id     uuid,
    version     bigint      not null default 0,
    constraint uq_event_seat unique (event_id, sector_id, row_label, seat_number)
);
create index ix_event_seat_status on event_seat (event_id, sector_id, status);
create index ix_event_seat_hold on event_seat (hold_id);

-- Contador por zona no numerada
create table zone_stock (
    event_id  uuid   not null,
    sector_id uuid   not null,
    capacity  int    not null,
    held      int    not null default 0,
    sold      int    not null default 0,
    version   bigint not null default 0,
    primary key (event_id, sector_id),
    constraint ck_zone_stock check (held >= 0 and sold >= 0 and held + sold <= capacity)
);

-- Reservas temporales
create table hold (
    id         uuid primary key,
    event_id   uuid        not null,
    order_id   uuid        not null unique,           -- idempotencia por orden
    status     varchar(10) not null check (status in ('ACTIVE', 'CONFIRMED', 'RELEASED', 'EXPIRED')),
    expires_at timestamptz not null,
    created_at timestamptz not null default now()
);
create index ix_hold_status_expires on hold (status, expires_at);

create table hold_item (
    id        uuid primary key,
    hold_id   uuid not null references hold (id),
    sector_id uuid not null,
    seat_id   uuid,                                   -- NULL si es zona general
    quantity  int  not null check (quantity > 0)
);
create index ix_hold_item_hold on hold_item (hold_id);

-- Liberaciones de lugares vendidos ya aplicadas (idempotencia por releaseId)
create table sold_release (
    release_id     uuid primary key,
    event_id       uuid        not null,
    released_count int         not null default 0,
    created_at     timestamptz not null default now()
);
