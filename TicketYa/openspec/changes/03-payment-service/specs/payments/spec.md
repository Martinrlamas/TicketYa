## ADDED Requirements

### Requirement: Registro de pagos
`POST /internal/payments` SHALL procesar un pago para una orden a través de la pasarela y SHALL persistir `orderId`, monto, moneda, medio de pago, resultado y referencia de la pasarela.
Los montos SHALL ser mayores a cero y SHALL almacenarse con precisión decimal exacta.

#### Scenario: Pago aprobado
- **WHEN** se envía un pago con token `tok_approved`
- **THEN** se devuelve `status=APPROVED` y una `gatewayRef`

#### Scenario: Pago rechazado
- **WHEN** se envía un pago con token `tok_rejected`
- **THEN** se persiste con `status=REJECTED` y se devuelve ese estado

#### Scenario: Monto inválido
- **WHEN** el monto es cero o negativo
- **THEN** la respuesta es 400 con `code=VALIDATION_ERROR`

### Requirement: Idempotencia de pagos
Cada solicitud de pago SHALL incluir una `idempotencyKey`. Repetir una solicitud con la misma clave y los mismos datos SHALL devolver el resultado original sin volver a cobrar.

#### Scenario: Reintento por falla de conexión
- **WHEN** order-service repite el pago con la misma `idempotencyKey`
- **THEN** recibe el mismo `paymentId` y estado, y la pasarela no es invocada de nuevo

#### Scenario: Clave reutilizada con datos distintos
- **WHEN** se reutiliza la clave con otro monto u otra orden
- **THEN** la respuesta es 422 con `code=IDEMPOTENCY_KEY_REUSED`

#### Scenario: Solicitudes concurrentes con la misma clave
- **WHEN** llegan simultáneamente varias solicitudes con la misma clave
- **THEN** se crea un único pago y todas reciben el mismo resultado

### Requirement: Reintegros
`POST /internal/payments/{id}/refunds` SHALL registrar un reintegro sobre un pago `APPROVED`. La suma de reintegros SHALL NOT superar el monto pagado. Cada reintegro SHALL requerir `idempotencyKey`.

#### Scenario: Reintegro total
- **WHEN** se solicita reintegrar el monto completo de un pago aprobado
- **THEN** se registra el reintegro `APPROVED` y `refundedAmount` iguala al monto pagado

#### Scenario: Reintegro excedido
- **WHEN** el reintegro solicitado supera el monto aún no reintegrado
- **THEN** la respuesta es 422 con `code=VALIDATION_ERROR`

#### Scenario: Reintegro repetido
- **WHEN** se repite la solicitud con la misma `idempotencyKey`
- **THEN** se devuelve el reintegro original sin reintegrar dos veces

#### Scenario: Pago no aprobado
- **WHEN** se intenta reintegrar un pago `REJECTED`
- **THEN** la respuesta es 409 con `code=INVALID_STATE_TRANSITION`

### Requirement: Consulta de pagos
payment-service SHALL exponer `GET /internal/payments/{id}` y `GET /internal/payments?orderId=` para que order-service consulte el estado de un pago y reconcilie pagos cuyo resultado quedó incierto (por ejemplo, por timeout).

#### Scenario: Reconciliación tras timeout
- **WHEN** order-service no recibió respuesta de un pago y consulta por `orderId`
- **THEN** payment-service devuelve el pago con su estado real (`APPROVED` o `REJECTED`)

#### Scenario: Orden sin pago
- **WHEN** se consulta una `orderId` sin pagos
- **THEN** la respuesta es 200 con lista vacía

### Requirement: Datos sensibles
El servicio SHALL NOT almacenar ni registrar en logs números completos de tarjeta; SOLO el token del medio de pago y, opcionalmente, los últimos 4 dígitos.

#### Scenario: Log de pago
- **WHEN** se procesa un pago
- **THEN** ningún log contiene datos sensibles del medio de pago
