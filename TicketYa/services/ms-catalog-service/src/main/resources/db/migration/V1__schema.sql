-- unaccent es "trusted" desde PostgreSQL 13; infra/postgres/init-databases.sql también la crea.
CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE TABLE venue (
    id        UUID         PRIMARY KEY,
    name      VARCHAR(150) NOT NULL,
    address   VARCHAR(255) NOT NULL,
    city      VARCHAR(100) NOT NULL,
    latitude  NUMERIC(9,6) NOT NULL,
    longitude NUMERIC(9,6) NOT NULL,
    CONSTRAINT ck_venue_lat CHECK (latitude  BETWEEN -90  AND 90),
    CONSTRAINT ck_venue_lon CHECK (longitude BETWEEN -180 AND 180)
);
CREATE INDEX ix_venue_city ON venue (lower(city));

CREATE TABLE sector (
    id       UUID         PRIMARY KEY,
    venue_id UUID         NOT NULL REFERENCES venue (id),
    name     VARCHAR(100) NOT NULL,
    type     VARCHAR(10)  NOT NULL,
    capacity INTEGER,
    CONSTRAINT ck_sector_type CHECK (type IN ('NUMBERED', 'GENERAL')),
    CONSTRAINT ck_sector_capacity CHECK (
        (type = 'GENERAL'  AND capacity IS NOT NULL AND capacity > 0) OR
        (type = 'NUMBERED' AND capacity IS NULL)),
    CONSTRAINT uq_sector_venue_name UNIQUE (venue_id, name)
);

CREATE TABLE sector_row (
    id         UUID        PRIMARY KEY,
    sector_id  UUID        NOT NULL REFERENCES sector (id),
    row_label  VARCHAR(10) NOT NULL,
    seat_count INTEGER     NOT NULL CHECK (seat_count > 0),
    CONSTRAINT uq_sector_row UNIQUE (sector_id, row_label)
);

CREATE TABLE event (
    id           UUID         PRIMARY KEY,
    name         VARCHAR(200) NOT NULL,
    description  TEXT,
    type         VARCHAR(20)  NOT NULL,
    organizer_id UUID         NOT NULL,  -- ref: identidad (E2), sin FK
    venue_id     UUID         NOT NULL REFERENCES venue (id),
    starts_at    TIMESTAMPTZ  NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    min_price    NUMERIC(12,2),          -- desnormalizado para ordenar por precio
    version      BIGINT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_event_type   CHECK (type   IN ('CONCERT','THEATER','SPORT','CONFERENCE','FESTIVAL','OTHER')),
    CONSTRAINT ck_event_status CHECK (status IN ('DRAFT','PUBLISHED','SOLD_OUT','SUSPENDED','CANCELLED','FINISHED'))
);
CREATE INDEX ix_event_status_starts ON event (status, starts_at);
CREATE INDEX ix_event_type          ON event (type);
CREATE INDEX ix_event_organizer     ON event (organizer_id);

CREATE TABLE event_participant (
    id       UUID         PRIMARY KEY,
    event_id UUID         NOT NULL REFERENCES event (id),
    name     VARCHAR(150) NOT NULL,
    role     VARCHAR(20)  NOT NULL,
    CONSTRAINT ck_participant_role CHECK (role IN ('ARTIST','TEAM','SPEAKER'))
);
CREATE INDEX ix_participant_event ON event_participant (event_id);

-- (event_id, sector_id) único; se usa un id surrogate para simplificar el mapeo JPA.
CREATE TABLE event_sector_price (
    id        UUID          PRIMARY KEY,
    event_id  UUID          NOT NULL REFERENCES event (id),
    sector_id UUID          NOT NULL REFERENCES sector (id),
    price     NUMERIC(12,2) NOT NULL CHECK (price > 0),
    currency  VARCHAR(3)    NOT NULL,
    CONSTRAINT uq_event_sector UNIQUE (event_id, sector_id)
);
