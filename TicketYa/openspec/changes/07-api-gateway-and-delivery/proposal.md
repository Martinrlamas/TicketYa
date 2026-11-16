## Why
Los clientes externos deben acceder al sistema por un único punto de entrada que oculte la topología interna.
Además se necesita el material de entrega: README, datos de prueba y colección de requests.

## What Changes
- Nuevo `api-gateway` (Spring Cloud Gateway, puerto 8080).
- Tabla de ruteo explícita y sin ambigüedad; `/internal/**` bloqueado.
- Validación de identidad por headers (E1), correlation-id, CORS, log de requests.
- README, colección Postman, datos de prueba y documentación técnica.

## Impact
- Specs: `api-gateway` (nueva). Depende de: todos los servicios anteriores.
