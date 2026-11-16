## ADDED Requirements

### Requirement: Servicios independientes
Cada servicio SHALL ser un proyecto Spring Boot con su propio `pom.xml`, construible y ejecutable por
separado, y SHALL NOT depender en compilación de otro servicio de negocio.

#### Scenario: Construcción aislada
- **WHEN** se ejecuta `mvn verify` dentro de `services/catalog-service`
- **THEN** el build finaliza sin requerir artefactos de otros servicios de negocio

### Requirement: Base de datos propia por servicio
Cada servicio SHALL usar exclusivamente su propia base de datos y SHALL NOT leer ni escribir en la base de otro servicio.
Las referencias entre servicios SHALL almacenarse como IDs simples, sin claves foráneas entre bases.

#### Scenario: Referencia entre servicios
- **WHEN** order-service almacena un ítem de compra
- **THEN** guarda `eventId`, `sectorId` y `seatId` como columnas simples sin FK hacia otras bases

### Requirement: Identidad por headers
Todo endpoint `/api/**` no público SHALL requerir los headers `X-User-Id` y `X-User-Role`.
Los valores válidos de rol SHALL ser `CUSTOMER`, `ORGANIZER`, `ADMIN` y `ACCESS_STAFF`.

#### Scenario: Falta identidad
- **WHEN** se invoca un endpoint protegido sin `X-User-Id`
- **THEN** la respuesta es 401 con código `UNAUTHENTICATED`

#### Scenario: Rol inválido
- **WHEN** `X-User-Role` contiene un valor fuera del conjunto permitido
- **THEN** la respuesta es 401 con código `UNAUTHENTICATED`

### Requirement: Autorización por rol en cada servicio
Cada servicio SHALL verificar el rol requerido por endpoint, independientemente de lo que haya validado el gateway.

#### Scenario: Rol insuficiente
- **WHEN** un usuario con rol `CUSTOMER` invoca un endpoint exclusivo de `ADMIN`
- **THEN** la respuesta es 403 con código `FORBIDDEN`

### Requirement: Protección de endpoints internos
Los endpoints `/internal/**` SHALL requerir el header `X-Internal-Token` con el secreto configurado y SHALL NOT ser accesibles a través del gateway.

#### Scenario: Token ausente
- **WHEN** se invoca `/internal/**` sin `X-Internal-Token` válido
- **THEN** la respuesta es 403 con código `FORBIDDEN`

### Requirement: Correlation ID
Cada solicitud SHALL tener un `X-Correlation-Id`. Si el cliente no lo envía, el primer componente que la recibe SHALL generarlo.
El identificador SHALL propagarse en todas las llamadas salientes, incluirse en cada línea de log y devolverse en la respuesta.

#### Scenario: Generación y propagación
- **WHEN** llega una solicitud sin `X-Correlation-Id` al gateway
- **THEN** el gateway genera uno, lo reenvía al servicio destino y lo devuelve en la respuesta
- **AND** las llamadas posteriores entre servicios llevan el mismo valor

### Requirement: Registro de cada solicitud HTTP
Cada servicio SHALL registrar por cada solicitud: método, ruta, `userId`, código de respuesta, duración en milisegundos y `correlationId`, en formato JSON estructurado.

#### Scenario: Log de request
- **WHEN** se procesa `GET /api/events/42`
- **THEN** se emite una línea de log con endpoint, usuario, status y tiempo de procesamiento

### Requirement: Formato de errores estándar
Toda respuesta de error SHALL seguir RFC 7807 (`application/problem+json`) e incluir los campos `type`, `title`, `status`, `detail`, `code` y `correlationId`.

Catálogo de códigos: `VALIDATION_ERROR`(400), `IDEMPOTENCY_KEY_REQUIRED`(400), `UNAUTHENTICATED`(401),
`PAYMENT_REJECTED`(402), `FORBIDDEN`(403), `NOT_FOUND`(404), `INVALID_STATE_TRANSITION`(409),
`EVENT_NOT_PURCHASABLE`(409), `SEAT_UNAVAILABLE`(409), `INSUFFICIENT_CAPACITY`(409), `HOLD_EXPIRED`(410),
`IDEMPOTENCY_KEY_REUSED`(422), `PURCHASE_LIMIT_EXCEEDED`(422), `REFUND_WINDOW_CLOSED`(422),
`UPSTREAM_UNAVAILABLE`(503).

#### Scenario: Error de validación
- **WHEN** un request tiene un campo obligatorio vacío
- **THEN** la respuesta es 400 con `code=VALIDATION_ERROR` y detalle del campo inválido

### Requirement: Timeouts en clientes HTTP
Toda llamada entre servicios SHALL tener timeout de conexión y de lectura configurables; ante vencimiento SHALL traducirse a `UPSTREAM_UNAVAILABLE` (503).

#### Scenario: Servicio caído
- **WHEN** un servicio destino no responde dentro del timeout
- **THEN** el llamador responde 503 con `code=UPSTREAM_UNAVAILABLE` y no deja estado inconsistente

### Requirement: Documentación OpenAPI por servicio
Cada servicio SHALL exponer su especificación en `/v3/api-docs` con descripciones, ejemplos y códigos de error.

#### Scenario: Publicación del contrato
- **WHEN** el servicio arranca
- **THEN** `/v3/api-docs` devuelve la especificación con todos los endpoints `/api/**` documentados

### Requirement: Migraciones versionadas
El esquema de cada base SHALL gestionarse con migraciones Flyway y Hibernate SHALL ejecutarse con `ddl-auto=validate`.

#### Scenario: Arranque en base vacía
- **WHEN** el servicio arranca contra una base vacía
- **THEN** las migraciones crean el esquema completo y la aplicación inicia correctamente
