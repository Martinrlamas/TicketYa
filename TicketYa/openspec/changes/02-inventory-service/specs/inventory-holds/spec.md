## ADDED Requirements

### Requirement: Creación de hold atómico y todo-o-nada
`POST /internal/holds` SHALL retener todos los lugares solicitados o ninguno. Para asientos numerados SHALL pasar el asiento de `AVAILABLE` a `HELD` mediante una actualización condicional atómica.
Para zonas no numeradas SHALL incrementar el contador `held` solo si `held + sold + n <= capacity`, en una única sentencia.

#### Scenario: Asiento libre
- **WHEN** se solicita un hold para el asiento A-5 que está `AVAILABLE`
- **THEN** el asiento pasa a `HELD`, se devuelve `holdId` y `expiresAt = ahora + TTL`

#### Scenario: Respuesta con etiquetas
- **WHEN** se crea un hold con asientos numerados
- **THEN** la respuesta incluye `seats` con `seatId`, `sectorId` y `label` (por ejemplo `A-5`) de cada asiento retenido

#### Scenario: Asiento ya retenido
- **WHEN** se solicita un hold para un asiento que está `HELD` o `SOLD`
- **THEN** la respuesta es 409 con `code=SEAT_UNAVAILABLE`

#### Scenario: Pedido parcialmente disponible
- **WHEN** se piden los asientos A-1, A-2 y A-3 y A-2 está `SOLD`
- **THEN** la respuesta es 409 y ninguno de los tres queda retenido

#### Scenario: Capacidad insuficiente en zona
- **WHEN** se piden 5 lugares en una zona con 3 libres
- **THEN** la respuesta es 409 con `code=INSUFFICIENT_CAPACITY` y el contador no cambia

### Requirement: Garantía de no doble venta bajo concurrencia
El sistema SHALL garantizar que un mismo asiento sea retenido o vendido a lo sumo una vez, y que `held + sold` de una zona nunca supere su capacidad, aun con múltiples solicitudes simultáneas y múltiples instancias del servicio.
La garantía SHALL implementarse en la base de datos (sentencias condicionales y constraints) y SHALL NOT depender de locks en memoria.

#### Scenario: Carrera por un mismo asiento
- **WHEN** 50 solicitudes concurrentes piden el mismo asiento libre
- **THEN** exactamente una obtiene 201 y las otras 49 reciben 409 `SEAT_UNAVAILABLE`

#### Scenario: Carrera por el último lugar de una zona
- **WHEN** 100 solicitudes concurrentes piden 1 lugar en una zona con capacidad restante 1
- **THEN** exactamente una tiene éxito y `held + sold` no supera la capacidad

#### Scenario: Varias instancias
- **WHEN** dos instancias del servicio reciben solicitudes simultáneas por el mismo asiento
- **THEN** solo una obtiene el hold

### Requirement: Idempotencia del hold por orden
Solicitar un hold con un `orderId` que ya tiene hold SHALL devolver el hold existente sin retener lugares adicionales.

#### Scenario: Reintento por falla de conexión
- **WHEN** order-service repite `POST /internal/holds` con el mismo `orderId`
- **THEN** recibe el mismo `holdId` y `expiresAt`, y no se retienen lugares nuevos

### Requirement: Tiempo de vida del hold
Cada hold SHALL expirar tras un TTL configurable (por defecto 10 minutos).

#### Scenario: TTL por defecto
- **WHEN** se crea un hold sin TTL explícito
- **THEN** `expiresAt` es 10 minutos posterior a la creación

### Requirement: Confirmación de venta
`POST /internal/holds/{id}/confirm` SHALL pasar los lugares de `HELD` a `SOLD` solo si el hold está `ACTIVE` y no venció.
La operación SHALL ser idempotente para un hold ya `CONFIRMED`.

#### Scenario: Confirmación en término
- **WHEN** se confirma un hold activo antes de `expiresAt`
- **THEN** los asientos pasan a `SOLD` y el hold a `CONFIRMED`

#### Scenario: Confirmación tardía
- **WHEN** se confirma un hold con `expiresAt` vencido
- **THEN** la respuesta es 410 con `code=HOLD_EXPIRED` y los lugares se liberan

#### Scenario: Reintento de confirmación
- **WHEN** se confirma dos veces el mismo hold
- **THEN** la segunda llamada devuelve 200 sin modificar el estado

### Requirement: Liberación de hold
`DELETE /internal/holds/{id}` SHALL liberar los lugares de un hold `ACTIVE` y SHALL ser idempotente.

#### Scenario: Liberación
- **WHEN** order-service libera un hold activo
- **THEN** los asientos vuelven a `AVAILABLE` o el contador `held` disminuye

#### Scenario: Liberación repetida
- **WHEN** se libera un hold ya `RELEASED` o `EXPIRED`
- **THEN** la respuesta es 204 sin efectos adicionales

### Requirement: Expiración automática
El servicio SHALL liberar periódicamente los holds `ACTIVE` vencidos. El proceso SHALL ser seguro con múltiples instancias ejecutándolo a la vez.

#### Scenario: Hold vencido
- **WHEN** pasa `expiresAt` sin confirmación
- **THEN** el hold pasa a `EXPIRED` y los lugares quedan disponibles

### Requirement: Liberación de lugares vendidos
`POST /internal/events/{eventId}/sold/release` SHALL devolver a disponibles lugares `SOLD` indicados por asiento o por cantidad de zona, de forma idempotente por `releaseId`.

#### Scenario: Devolución de asiento
- **WHEN** se libera el asiento A-5 vendido
- **THEN** el asiento pasa a `AVAILABLE`

#### Scenario: Liberación repetida
- **WHEN** se repite el mismo `releaseId`
- **THEN** no se libera nada adicional y la respuesta es 200
