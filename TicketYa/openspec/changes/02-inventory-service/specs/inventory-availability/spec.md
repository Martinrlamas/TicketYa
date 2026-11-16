## ADDED Requirements

### Requirement: Inicialización del inventario
inventory-service SHALL exponer `POST /internal/events/{eventId}/inventory` que recibe los sectores del evento (tipo, capacidad o filas) y crea un asiento por cada lugar numerado y un contador por cada zona no numerada.
La operación SHALL ser idempotente.

#### Scenario: Primera inicialización
- **WHEN** catalog solicita inicializar un evento con un sector numerado de 40 asientos y una zona de 5000
- **THEN** se crean 40 asientos `AVAILABLE` y un contador con `capacity=5000, held=0, sold=0`

#### Scenario: Reinicialización
- **WHEN** se solicita inicializar un evento ya inicializado
- **THEN** la respuesta es 200 sin duplicar asientos ni modificar contadores existentes

### Requirement: Consulta pública de disponibilidad
`GET /api/events/{eventId}/availability` SHALL devolver por sector la disponibilidad actual: para sectores numerados el estado de cada asiento y para zonas los lugares libres.
La respuesta SHALL incluir `Cache-Control: no-store`.

#### Scenario: Sector numerado
- **WHEN** un cliente consulta la disponibilidad de un evento con asientos retenidos
- **THEN** los asientos retenidos o vendidos figuran como no disponibles

#### Scenario: Zona general
- **WHEN** se consulta una zona con capacidad 100, 30 vendidos y 10 retenidos
- **THEN** la respuesta informa 60 lugares disponibles

#### Scenario: Evento inexistente
- **WHEN** el evento no tiene inventario
- **THEN** la respuesta es 404 con `code=NOT_FOUND`

### Requirement: Cierre de inventario
inventory-service SHALL exponer `POST /internal/events/{eventId}/close` que impide crear nuevos holds para el evento.

#### Scenario: Hold sobre evento cerrado
- **WHEN** se solicita un hold para un evento cerrado
- **THEN** la respuesta es 409 con `code=EVENT_NOT_PURCHASABLE`

### Requirement: Notificación de agotado
Cuando `held + sold` alcance la capacidad total de todos los sectores, inventory-service SHALL notificar `SOLD_OUT` a catalog; cuando se libere algún lugar SHALL notificar la reapertura.

#### Scenario: Último lugar vendido
- **WHEN** se confirma la venta del último lugar disponible del evento
- **THEN** se notifica `SOLD_OUT` a catalog
