## Why
Es el punto de partida del flujo: sin lugares, eventos y precios publicados no hay nada que vender ni buscar.

## What Changes
- Nuevo `catalog-service` (puerto 8081, `catalog_db`).
- ABM de lugares y sectores (numerados / no numerados).
- Ciclo de vida del evento: DRAFT → PUBLISHED → (SOLD_OUT | SUSPENDED | CANCELLED | FINISHED).
- Precio por sector y evento.
- Búsqueda multicriterio con ordenamiento y paginación.
- Endpoint interno `sale-info` para que order-service valide y obtenga precios.

## Impact
- Specs: `venues`, `events-lifecycle`, `event-search` (nuevas).
- Depende de: `platform-conventions`.
- Puertos de salida: `EventPublished` (consumido por inventory), `EventCancelled` (consumido por order). En E1 son adaptadores REST.
