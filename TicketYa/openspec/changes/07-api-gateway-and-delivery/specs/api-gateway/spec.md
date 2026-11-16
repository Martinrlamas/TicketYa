## ADDED Requirements

### Requirement: Punto de entrada único
El sistema SHALL exponer todos los endpoints `/api/**` exclusivamente a través del api-gateway.

#### Scenario: Ruteo por recurso
- **WHEN** el cliente invoca `GET /api/events`
- **THEN** el gateway reenvía la solicitud a catalog-service

#### Scenario: Ruteo de disponibilidad
- **WHEN** el cliente invoca `GET /api/events/42/availability`
- **THEN** el gateway la reenvía a inventory-service y no a catalog-service

#### Scenario: Ruta desconocida
- **WHEN** la ruta no coincide con ninguna regla
- **THEN** la respuesta es 404 con `code=NOT_FOUND`

### Requirement: Bloqueo de endpoints internos
El gateway SHALL NOT rutear `/internal/**` ni exponer payment-service.

#### Scenario: Intento de acceso interno
- **WHEN** un cliente externo invoca `/internal/holds`
- **THEN** la respuesta es 404 y la solicitud no llega a ningún servicio

### Requirement: Validación de identidad en el borde
El gateway SHALL rechazar con 401 las solicitudes a rutas no públicas sin `X-User-Id` válido y `X-User-Role` permitido, y SHALL reenviar ambos headers al servicio destino.

#### Scenario: Ruta pública
- **WHEN** se invoca `GET /api/events` sin headers de identidad
- **THEN** la solicitud se reenvía sin exigir identidad

#### Scenario: Ruta protegida sin identidad
- **WHEN** se invoca `POST /api/orders` sin headers de identidad
- **THEN** la respuesta es 401 con `code=UNAUTHENTICATED`

### Requirement: Correlation ID en el borde
El gateway SHALL generar `X-Correlation-Id` si no viene en la solicitud, reenviarlo a los servicios, incluirlo en la respuesta y registrar cada solicitud con endpoint, usuario, código y duración.

#### Scenario: Seguimiento de una compra
- **WHEN** un cliente realiza `POST /api/orders`
- **THEN** los logs de gateway, order, inventory y catalog comparten el mismo `correlationId`

### Requirement: Errores de upstream
Cuando un servicio destino no responde, el gateway SHALL devolver 503 con `code=UPSTREAM_UNAVAILABLE` en formato problem+json.

#### Scenario: Servicio caído
- **WHEN** inventory-service no está disponible
- **THEN** el gateway responde 503 `UPSTREAM_UNAVAILABLE`

### Requirement: Material de entrega
El repositorio SHALL incluir un README con instrucciones de ejecución, datos de prueba y una colección de requests que cubra todos los endpoints públicos, incluyendo un flujo de compra completo.

#### Scenario: Ejecución de la colección
- **WHEN** se ejecuta la carpeta "Flujo de compra" de la colección sobre un entorno recién levantado
- **THEN** el flujo crea el evento, lo publica, reserva, paga y devuelve las entradas con QR
