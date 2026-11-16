## ADDED Requirements

### Requirement: Recepción de la cancelación de un evento
order-service SHALL exponer `POST /internal/events/{eventId}/cancellation` para ser notificado por catalog. La operación SHALL ser idempotente por `eventId`.

#### Scenario: Notificación repetida
- **WHEN** catalog notifica dos veces la misma cancelación
- **THEN** la segunda llamada devuelve 200 y no genera reintegros duplicados

### Requirement: Reintegro automático de entradas vendidas
Al cancelarse un evento, todos los ítems de órdenes `CONFIRMED` SHALL pasar a reintegro automático sin requerir aprobación del administrador, y las entradas SHALL invalidarse con motivo `CANCELLED`.

#### Scenario: Evento con ventas
- **WHEN** se cancela un evento con 100 entradas vendidas
- **THEN** se crean 100 reintegros automáticos y todas las entradas pasan a `CANCELLED`
- **AND** los ítems quedan `CANCELLED` una vez reintegrados

#### Scenario: Falla parcial
- **WHEN** payment-service falla durante el procesamiento de algunos reintegros
- **THEN** los restantes se reintentan hasta completarse sin duplicar los ya realizados

### Requirement: Órdenes pendientes al cancelar
Las órdenes `PENDING_PAYMENT` del evento cancelado SHALL pasar a `EXPIRED` liberando su cuota.

#### Scenario: Orden en proceso de pago
- **WHEN** se cancela el evento con una orden `PENDING_PAYMENT`
- **THEN** la orden queda `EXPIRED` y un pago posterior responde 409 `EVENT_NOT_PURCHASABLE`

### Requirement: Bloqueo de nuevas ventas
Cancelado el evento, inventory-service SHALL rechazar nuevos holds.

#### Scenario: Intento de compra posterior
- **WHEN** un cliente intenta comprar un evento cancelado
- **THEN** la respuesta es 409 con `code=EVENT_NOT_PURCHASABLE`

### Requirement: Aviso a compradores
El sistema SHALL invocar el puerto `BuyerNotifier` por cada comprador afectado. En esta entrega la implementación SHALL limitarse a registrar el aviso en el log.

#### Scenario: Registro del aviso
- **WHEN** se procesa un reintegro por cancelación
- **THEN** se registra en el log el aviso destinado al cliente, con el `correlationId` de la operación
