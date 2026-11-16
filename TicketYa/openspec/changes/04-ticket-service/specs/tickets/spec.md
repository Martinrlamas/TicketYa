## ADDED Requirements

### Requirement: Emisión de entradas
`POST /internal/tickets` SHALL emitir una entrada por cada ítem de orden recibido, con estado `VALID` y un código QR único.

#### Scenario: Emisión de varias entradas
- **WHEN** order-service solicita emitir entradas para 3 ítems de una orden
- **THEN** se crean 3 entradas `VALID`, cada una con un `qrCode` distinto

### Requirement: QR único e impredecible
Cada `qrCode` SHALL ser único en todo el sistema y SHALL generarse con un generador criptográficamente seguro, sin contener información derivable del evento, asiento o cliente.

#### Scenario: Unicidad
- **WHEN** se emiten entradas en volumen
- **THEN** no existen dos entradas con el mismo `qrCode`

### Requirement: Idempotencia de la emisión
La emisión SHALL ser idempotente por `orderItemId`: repetir la solicitud SHALL devolver las entradas ya emitidas sin crear duplicados.

#### Scenario: Reintento de emisión
- **WHEN** order-service reintenta la emisión de un ítem ya emitido
- **THEN** recibe la misma entrada y no se crea otra

#### Scenario: Emisión concurrente
- **WHEN** dos solicitudes simultáneas emiten el mismo `orderItemId`
- **THEN** existe una única entrada para ese ítem

### Requirement: Consulta de entradas propias
`GET /api/tickets` SHALL devolver las entradas del usuario autenticado y `GET /api/tickets/{id}` SHALL devolver una entrada solo si pertenece a ese usuario o si el rol es `ADMIN`.

#### Scenario: Listado propio
- **WHEN** un CUSTOMER consulta `GET /api/tickets`
- **THEN** solo recibe sus propias entradas, incluyendo `qrCode`, evento, sector y asiento

#### Scenario: Entrada ajena
- **WHEN** un CUSTOMER consulta el ticket de otro cliente
- **THEN** la respuesta es 404 con `code=NOT_FOUND`

### Requirement: Consulta interna por orden
`GET /internal/tickets?orderId=` SHALL devolver las entradas de una orden para que order-service las muestre en el detalle.

#### Scenario: Orden con entradas
- **WHEN** order-service consulta las entradas de una orden confirmada
- **THEN** recibe cada entrada con su `qrCode` y estado

#### Scenario: Orden sin entradas
- **WHEN** la orden no tiene entradas emitidas
- **THEN** la respuesta es 200 con lista vacía

### Requirement: Invalidación de entradas
`POST /internal/tickets/{id}/invalidate` SHALL cambiar una entrada `VALID` a `REFUNDED` o `CANCELLED` según el motivo informado, y SHALL ser idempotente.
Una entrada `USED` SHALL NOT poder invalidarse.

#### Scenario: Invalidación por devolución
- **WHEN** se invalida una entrada `VALID` con motivo `REFUNDED`
- **THEN** su estado pasa a `REFUNDED` y su QR deja de ser válido

#### Scenario: Entrada ya usada
- **WHEN** se intenta invalidar una entrada `USED`
- **THEN** la respuesta es 409 con `code=INVALID_STATE_TRANSITION`

#### Scenario: Invalidación repetida
- **WHEN** se invalida dos veces la misma entrada con el mismo motivo
- **THEN** la segunda llamada devuelve 200 sin cambios

### Requirement: Invalidación masiva por evento
`POST /internal/events/{eventId}/tickets/invalidate` SHALL invalidar todas las entradas `VALID` del evento con motivo `CANCELLED`.

#### Scenario: Evento cancelado
- **WHEN** order-service solicita la invalidación masiva de un evento cancelado
- **THEN** todas sus entradas `VALID` pasan a `CANCELLED`
