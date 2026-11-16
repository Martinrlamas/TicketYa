## ADDED Requirements

### Requirement: Creación de orden con reserva temporal
`POST /api/orders` SHALL crear una orden a nombre del cliente autenticado para un evento, uno o más sectores y asientos (sector numerado) o cantidad (zona no numerada).
El sistema SHALL reservar los lugares en inventory-service y devolver la orden en estado `PENDING_PAYMENT` con la fecha de vencimiento de la reserva.

#### Scenario: Compra de asientos numerados
- **WHEN** un CUSTOMER envía una orden con los asientos A-5 y A-6 de un evento publicado
- **THEN** la respuesta es 201 con `status=PENDING_PAYMENT`, `holdExpiresAt` y el total calculado
- **AND** ambos asientos quedan retenidos

#### Scenario: Compra en zona general
- **WHEN** un CUSTOMER solicita 3 lugares de la zona "Campo"
- **THEN** se retienen 3 lugares y el total es 3 × precio del sector

#### Scenario: Asiento ya no disponible
- **WHEN** inventory-service responde que un asiento no está disponible
- **THEN** la respuesta es 409 con `code=SEAT_UNAVAILABLE`, no se crea la orden y no se consume cuota

#### Scenario: Evento no comprable
- **WHEN** el evento no está en estado `PUBLISHED`
- **THEN** la respuesta es 409 con `code=EVENT_NOT_PURCHASABLE`

#### Scenario: Ítem inconsistente
- **WHEN** se piden asientos en un sector no numerado, o cantidad en uno numerado, o un sector que no pertenece al evento
- **THEN** la respuesta es 400 con `code=VALIDATION_ERROR`

### Requirement: Snapshot de precio
La orden SHALL guardar el precio unitario y la moneda vigentes al crearse. Cambios posteriores en catalog SHALL NOT alterar órdenes existentes.

#### Scenario: Cambio de precio posterior
- **WHEN** catalog modifica el precio de un sector después de crear la orden
- **THEN** la orden mantiene el precio original

### Requirement: Límite de entradas por cliente
El sistema SHALL limitar la cantidad de entradas por cliente para un mismo evento (por defecto 6, configurable). El control SHALL ser atómico y seguro ante solicitudes concurrentes del mismo cliente.
La cuota SHALL liberarse cuando la orden se rechace, expire, se cancele o sus ítems se devuelvan.

#### Scenario: Límite superado
- **WHEN** un cliente con 5 entradas del evento solicita 2 más con límite 6
- **THEN** la respuesta es 422 con `code=PURCHASE_LIMIT_EXCEEDED` y no se reserva nada

#### Scenario: Órdenes simultáneas del mismo cliente
- **WHEN** un cliente envía 10 órdenes concurrentes de 1 entrada con límite 6
- **THEN** exactamente 6 son aceptadas

#### Scenario: Liberación de cuota
- **WHEN** una orden `PENDING_PAYMENT` expira
- **THEN** la cuota consumida se libera

### Requirement: Idempotencia de la creación de orden
`POST /api/orders` SHALL requerir el header `Idempotency-Key`. Repetir la solicitud con la misma clave y contenido SHALL devolver la respuesta original sin crear otra orden ni reservar nuevamente.

#### Scenario: Clave ausente
- **WHEN** falta `Idempotency-Key`
- **THEN** la respuesta es 400 con `code=IDEMPOTENCY_KEY_REQUIRED`

#### Scenario: Reintento por mala conexión
- **WHEN** el cliente repite el mismo `POST /api/orders` con la misma clave
- **THEN** recibe la misma orden y no se retienen lugares adicionales

#### Scenario: Clave con contenido distinto
- **WHEN** la clave se reutiliza con otro cuerpo
- **THEN** la respuesta es 422 con `code=IDEMPOTENCY_KEY_REUSED`

### Requirement: Pago de la orden
`POST /api/orders/{id}/pay` SHALL cobrar la orden `PENDING_PAYMENT` del cliente dueño, mientras la reserva esté vigente.
Si el pago es aprobado SHALL confirmar la venta en inventory-service, emitir las entradas en ticket-service y pasar la orden a `CONFIRMED`, devolviendo las entradas con su QR.

#### Scenario: Pago aprobado
- **WHEN** el cliente paga con un medio que la pasarela aprueba antes del vencimiento
- **THEN** los asientos pasan a vendidos, se emiten las entradas y la orden queda `CONFIRMED`
- **AND** la respuesta incluye las entradas con `qrCode`

#### Scenario: Pago rechazado
- **WHEN** la pasarela rechaza el pago
- **THEN** la respuesta es 402 con `code=PAYMENT_REJECTED`
- **AND** el hold se libera, la cuota se libera y la orden queda `PAYMENT_REJECTED`

#### Scenario: Reserva vencida antes de pagar
- **WHEN** el cliente intenta pagar después de `holdExpiresAt`
- **THEN** la respuesta es 410 con `code=HOLD_EXPIRED` y la orden queda `EXPIRED`

#### Scenario: Orden ajena
- **WHEN** un cliente intenta pagar una orden de otro cliente
- **THEN** la respuesta es 404 con `code=NOT_FOUND`

### Requirement: Idempotencia del pago
`POST /api/orders/{id}/pay` SHALL requerir `Idempotency-Key`. Un reintento SHALL NOT cobrar dos veces ni emitir entradas duplicadas, y SHALL devolver el resultado original.

#### Scenario: Reintento tras pérdida de conexión
- **WHEN** el cliente repite el pago con la misma clave luego de que la orden quedó `CONFIRMED`
- **THEN** recibe 200 con las mismas entradas y no hay un segundo cobro

#### Scenario: Pagos simultáneos de la misma orden
- **WHEN** llegan dos pagos concurrentes de la misma orden
- **THEN** solo uno se procesa y la orden se cobra una vez

### Requirement: Compensación ante pago aprobado y venta imposible
Si el pago fue aprobado pero la confirmación del hold falla porque venció o los lugares ya no están reservados, el sistema SHALL reintegrar automáticamente el pago y marcar la orden `FAILED_REFUNDED`.

#### Scenario: Hold vencido durante el pago
- **WHEN** el pago es aprobado pero inventory responde `HOLD_EXPIRED` al confirmar
- **THEN** order-service solicita el reintegro total del pago
- **AND** la orden queda `FAILED_REFUNDED` y la respuesta es 410 `HOLD_EXPIRED`

### Requirement: Recuperación ante falla de emisión de entradas
Si la emisión de entradas falla después de confirmar la venta, el sistema SHALL reintentar de forma idempotente y, si persiste, SHALL compensar con reintegro y liberación de los lugares.

#### Scenario: ticket-service con falla transitoria
- **WHEN** ticket-service falla una vez y responde en el segundo intento
- **THEN** la orden termina `CONFIRMED` con una única entrada por ítem

#### Scenario: ticket-service caído
- **WHEN** los reintentos se agotan
- **THEN** se reintegra el pago, se liberan los lugares y la orden queda `FAILED_REFUNDED`

### Requirement: Expiración automática de órdenes pendientes
Un proceso periódico SHALL expirar las órdenes `PENDING_PAYMENT` cuya reserva venció, liberando hold y cuota. El proceso SHALL ser seguro con múltiples instancias.

#### Scenario: Orden abandonada
- **WHEN** pasa `holdExpiresAt` sin pago
- **THEN** la orden pasa a `EXPIRED` y los lugares quedan disponibles

### Requirement: Cancelación de orden pendiente
`DELETE /api/orders/{id}` SHALL permitir al cliente dueño cancelar una orden `PENDING_PAYMENT`, liberando hold y cuota.

#### Scenario: Cliente se arrepiente
- **WHEN** el cliente cancela su orden pendiente
- **THEN** la orden queda `CANCELLED` y los lugares se liberan

#### Scenario: Orden ya confirmada
- **WHEN** se intenta cancelar una orden `CONFIRMED`
- **THEN** la respuesta es 409 con `code=INVALID_STATE_TRANSITION`

### Requirement: Consulta de órdenes
`GET /api/orders` SHALL listar las órdenes del cliente autenticado y `GET /api/orders/{id}` SHALL devolver el detalle con estado, ítems, precios, medio de pago y total.

#### Scenario: Detalle de compra
- **WHEN** el cliente consulta una orden confirmada
- **THEN** recibe ítems con precio snapshot, estado de pago y entradas asociadas

### Requirement: Unicidad de la venta de extremo a extremo
Ante cualquier número de clientes que intenten comprar el mismo asiento, el sistema SHALL generar como máximo una orden `CONFIRMED` y una entrada para ese asiento.

#### Scenario: Carrera completa
- **WHEN** 30 clientes crean y pagan una orden por el mismo asiento a través del gateway
- **THEN** exactamente una orden queda `CONFIRMED` y existe una sola entrada `VALID` para ese asiento
