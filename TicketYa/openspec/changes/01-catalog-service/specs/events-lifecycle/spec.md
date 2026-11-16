## ADDED Requirements

### Requirement: Creación de eventos en borrador
Un ORGANIZER SHALL poder crear un evento con nombre, descripción, tipo, participantes, fecha y hora futura y lugar existente.
El evento SHALL crearse en estado `DRAFT` y SHALL quedar asociado al organizador que lo creó.

#### Scenario: Creación válida
- **WHEN** un ORGANIZER envía `POST /api/events` con un `venueId` existente y fecha futura
- **THEN** la respuesta es 201 y el evento queda en `DRAFT`

#### Scenario: Fecha pasada
- **WHEN** la fecha del evento es anterior a la actual
- **THEN** la respuesta es 400 con `code=VALIDATION_ERROR`

### Requirement: Consulta de eventos propios y visibilidad
Un ORGANIZER SHALL poder listar sus propios eventos en cualquier estado mediante `GET /api/events/mine`, con paginación y filtro opcional por estado.
`GET /api/events/{id}` SHALL devolver eventos `PUBLISHED` y `SOLD_OUT` a cualquier usuario; los eventos en otros estados SHALL ser visibles únicamente para el organizador dueño y los ADMIN.

#### Scenario: Listado propio
- **WHEN** un ORGANIZER consulta `GET /api/events/mine`
- **THEN** recibe solo sus eventos, incluidos los `DRAFT`

#### Scenario: Borrador ajeno
- **WHEN** un usuario distinto del dueño (y no ADMIN) consulta el detalle de un evento `DRAFT`
- **THEN** la respuesta es 404 con `code=NOT_FOUND`

### Requirement: Precio por sector
El organizador dueño SHALL definir el precio de cada sector del lugar para el evento mientras éste esté en `DRAFT`.
Los precios SHALL ser mayores a cero y SHALL referirse solo a sectores del lugar del evento.

#### Scenario: Definir precios
- **WHEN** el organizador dueño envía `PUT /api/events/{id}/sector-prices` con precios válidos
- **THEN** los precios se guardan y se actualiza el precio mínimo del evento

#### Scenario: Sector ajeno al lugar
- **WHEN** se informa un `sectorId` que no pertenece al lugar del evento
- **THEN** la respuesta es 400 con `code=VALIDATION_ERROR`

#### Scenario: Evento ajeno
- **WHEN** un ORGANIZER distinto del dueño intenta modificar el evento
- **THEN** la respuesta es 403 con `code=FORBIDDEN`

### Requirement: Publicación
El organizador dueño SHALL poder publicar un evento en `DRAFT` que tenga al menos un sector con precio y fecha futura.
Al publicar, el sistema SHALL solicitar la inicialización del inventario antes de pasar el evento a `PUBLISHED`.

#### Scenario: Publicación exitosa
- **WHEN** el organizador dueño publica un evento con precios configurados
- **THEN** inventory-service inicializa el inventario
- **AND** el evento pasa a `PUBLISHED` y la respuesta es 200

#### Scenario: Sin precios
- **WHEN** se publica un evento sin ningún sector con precio
- **THEN** la respuesta es 409 con `code=INVALID_STATE_TRANSITION` y el evento sigue en `DRAFT`

#### Scenario: Inventario no disponible
- **WHEN** inventory-service no responde durante la publicación
- **THEN** la respuesta es 503 con `code=UPSTREAM_UNAVAILABLE` y el evento sigue en `DRAFT`

#### Scenario: Reintento de publicación
- **WHEN** se reintenta publicar tras un fallo de inventario
- **THEN** la inicialización del inventario es idempotente y la publicación se completa sin duplicar asientos

### Requirement: Suspensión y reactivación
Un ADMIN SHALL poder suspender un evento `PUBLISHED` o `SOLD_OUT` y reactivar uno `SUSPENDED`.
Un evento suspendido SHALL NOT aparecer en búsquedas públicas ni admitir nuevas compras.

#### Scenario: Suspensión
- **WHEN** un ADMIN suspende un evento publicado
- **THEN** el estado pasa a `SUSPENDED`

#### Scenario: Rol no permitido
- **WHEN** un ORGANIZER intenta suspender un evento
- **THEN** la respuesta es 403 con `code=FORBIDDEN`

### Requirement: Cancelación por administrador
Un ADMIN SHALL poder cancelar un evento en `DRAFT`, `PUBLISHED`, `SOLD_OUT` o `SUSPENDED`.
Al cancelar, el sistema SHALL notificar el hecho a order-service mediante el puerto `EventCancelled`.

#### Scenario: Cancelación de evento con ventas
- **WHEN** un ADMIN cancela un evento publicado
- **THEN** el estado pasa a `CANCELLED`
- **AND** se notifica a order-service para iniciar los reintegros

#### Scenario: Estado terminal
- **WHEN** se intenta cancelar un evento `FINISHED` o ya `CANCELLED`
- **THEN** la respuesta es 409 con `code=INVALID_STATE_TRANSITION`

### Requirement: Estados agotado y finalizado
El estado `SOLD_OUT` SHALL ser actualizado únicamente por inventory-service mediante un endpoint interno cuando no queden lugares, y revertido a `PUBLISHED` cuando se liberen.
El estado `FINISHED` SHALL asignarse automáticamente cuando la fecha del evento haya pasado.

#### Scenario: Agotado
- **WHEN** inventory-service informa `SOLD_OUT` para un evento publicado
- **THEN** el evento pasa a `SOLD_OUT` y sigue visible en búsquedas

### Requirement: Información de venta para otros servicios
catalog-service SHALL exponer `GET /internal/events/{id}/sale-info` con estado, fecha, organizador y, por sector, nombre, tipo, precio y moneda.

#### Scenario: Evento comprable
- **WHEN** order-service consulta `sale-info` de un evento `PUBLISHED`
- **THEN** recibe `purchasable=true`, `startsAt` y los precios por sector

#### Scenario: Evento no comprable
- **WHEN** el evento está en `DRAFT`, `SUSPENDED`, `CANCELLED` o `FINISHED`
- **THEN** `purchasable=false` y se informa el estado
