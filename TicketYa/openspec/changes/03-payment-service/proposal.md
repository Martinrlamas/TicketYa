## Why
El pago es una frontera externa. Hoy se simula; mañana será un proveedor real. El resto del sistema no debe enterarse del cambio.

## What Changes
- Nuevo `payment-service` (puerto 8084, `payment_db`) con pasarela simulada (mock).
- Registro de pagos con medio de pago y detalle, e idempotencia por clave.
- Reintegros (totales o parciales) idempotentes.

## Impact
- Specs: `payments` (nueva). Depende de: `platform-conventions`. Consumido solo por order-service.
