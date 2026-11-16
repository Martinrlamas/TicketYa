## ADDED Requirements

### Requirement: Solicitud de devolución
Un CUSTOMER SHALL poder solicitar la devolución de una entrada de una orden `CONFIRMED` propia hasta 72 horas antes del inicio del evento.
Solo SHALL existir una solicitud activa por ítem.

#### Scenario: Solicitud dentro de plazo
- **WHEN** el cliente solicita la devolución 5 días antes del evento
- **THEN** se crea la solicitud en estado `PENDING` y el ítem queda `REFUND_REQUESTED`

#### Scenario: Fuera de plazo
- **WHEN** faltan menos de 72 horas para el evento
- **THEN** la respuesta es 422 con `code=REFUND_WINDOW_CLOSED`

#### Scenario: Solicitud duplicada
- **WHEN** ya existe una solicitud activa para el ítem
- **THEN** la respuesta es 409 con `code=INVALID_STATE_TRANSITION`

#### Scenario: Entrada ajena
- **WHEN** el cliente solicita devolver un ítem de otro cliente
- **THEN** la respuesta es 404 con `code=NOT_FOUND`

### Requirement: Decisión del administrador
Un ADMIN SHALL poder listar las solicitudes `PENDING`, aprobarlas o rechazarlas. Solo el rol ADMIN SHALL poder decidir.

#### Scenario: Rechazo
- **WHEN** un ADMIN rechaza una solicitud
- **THEN** la solicitud pasa a `REJECTED` y el ítem vuelve a `ACTIVE`

#### Scenario: Decisión por rol no autorizado
- **WHEN** un CUSTOMER intenta aprobar una solicitud
- **THEN** la respuesta es 403 con `code=FORBIDDEN`

#### Scenario: Solicitud ya decidida
- **WHEN** se intenta aprobar una solicitud que no está `PENDING`
- **THEN** la respuesta es 409 con `code=INVALID_STATE_TRANSITION`

### Requirement: Procesamiento de la devolución aprobada
Al aprobarse, el sistema SHALL invalidar la entrada, liberar el asiento o el lugar de la zona, solicitar el reintegro del monto del ítem a payment-service y liberar la cuota del cliente.
Cada paso SHALL ser idempotente y el proceso SHALL poder reanudarse desde el último paso completado.

#### Scenario: Devolución exitosa
- **WHEN** un ADMIN aprueba una solicitud
- **THEN** la entrada queda `REFUNDED`, el asiento queda disponible, se solicita el reintegro y el ítem queda `REFUNDED`

#### Scenario: Falla transitoria de payment
- **WHEN** payment-service no responde durante el reintegro
- **THEN** la solicitud permanece `PROCESSING` y se reintenta automáticamente
- **AND** al recuperarse el servicio el reintegro se realiza una sola vez

#### Scenario: Reintentos agotados
- **WHEN** se superan los reintentos configurados
- **THEN** la solicitud pasa a `FAILED` y queda visible para el ADMIN

#### Scenario: Asiento recomprable
- **WHEN** una devolución se completa para el asiento A-5
- **THEN** otro cliente puede reservarlo y comprarlo

### Requirement: Consulta de solicitudes
Un CUSTOMER SHALL poder consultar el estado de sus solicitudes y un ADMIN SHALL poder listar todas filtrando por estado.

#### Scenario: Listado de pendientes
- **WHEN** un ADMIN consulta `GET /api/refund-requests?status=PENDING`
- **THEN** recibe las solicitudes pendientes ordenadas por antigüedad
