## Modelo de datos (catalog_db)
- `venue(id, name, address, city, latitude, longitude)`
- `sector(id, venue_id, name, type NUMBERED|GENERAL, capacity NULL si numerado)`
- `sector_row(id, sector_id, row_label, seat_count)` — solo sectores numerados
- `event(id, name, description, type, organizer_id, venue_id, starts_at, status, min_price, version, created_at)`
- `event_participant(id, event_id, name, role)`
- `event_sector_price(event_id, sector_id, price, currency)` PK (event_id, sector_id)

`min_price` se desnormaliza al guardar precios para ordenar por precio sin joins costosos.

## Decisiones
1. **Búsqueda dentro de catalog** como módulo aislado (`SearchQueryService` con puerto `EventSearchPort`).
   En E2 puede moverse a un `search-service` sin tocar consumidores.
2. **Relevancia (E1):** solo aplica si hay parámetro `q`; puntaje = 3×coincidencia en nombre + 2×en participante + 1×en descripción. Sin `q`, `sort=relevance` equivale a fecha ascendente.
3. **Publicación = operación con efecto externo:** catalog llama a `POST /internal/events/{id}/inventory` a través del puerto `EventPublished`. Si falla, el evento permanece en DRAFT y se responde 503. La inicialización es idempotente, por lo que reintentar es seguro.
4. **Asientos del mapa:** catalog es dueño de la definición de filas/asientos del lugar; inventory recibe una copia (snapshot) al publicar.
5. **SOLD_OUT / FINISHED:** SOLD_OUT lo informa inventory vía `PUT /internal/events/{id}/status`; FINISHED lo marca un job programado cuando `starts_at` pasó.
6. **Cacheable en E2:** `GET /api/events/{id}` y búsquedas. No se implementa cache en E1 pero los controllers devuelven `ETag` y no incluyen datos de disponibilidad.

## Decisiones tomadas al implementar
- **Cancelación con aviso diferido:** `event.cancellation_pending` + `cancel_reason`. Se confirma el estado y luego se avisa a inventory (`close`) y order (reintegros) fuera de la transacción; si falla, un job reintenta (ambos endpoints son idempotentes). Así un servicio caído no deja compradores sin reintegro.
- **`event_sector_price`** usa PK surrogate con `UNIQUE(event_id, sector_id)`; los precios se actualizan en el lugar (Hibernate inserta antes de borrar y violaría el UNIQUE).
- **`q` es también filtro** (nombre, descripción o participante, sin acentos); `sort=relevance` solo ordena si hay `q`.
- La extensión `unaccent` se crea con `CREATE EXTENSION IF NOT EXISTS` en `V1` (es *trusted* desde PostgreSQL 13) y también en `infra/postgres/init-databases.sql`.

## Máquina de estados
| Desde | Hacia | Quién |
|---|---|---|
| DRAFT | PUBLISHED | ORGANIZER dueño |
| PUBLISHED | SOLD_OUT | inventory (interno) |
| SOLD_OUT | PUBLISHED | inventory (interno, al liberarse lugares) |
| PUBLISHED / SOLD_OUT | SUSPENDED | ADMIN |
| SUSPENDED | PUBLISHED | ADMIN |
| DRAFT / PUBLISHED / SOLD_OUT / SUSPENDED | CANCELLED | ADMIN |
| PUBLISHED / SOLD_OUT | FINISHED | job |
