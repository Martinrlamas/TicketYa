## Why
Una compra involucra a catalog, inventory, payment y ticket, y no existe una transacción única que los abarque.
Se necesita un orquestador con estados explícitos, idempotencia y compensaciones.

## What Changes
- Nuevo `order-service` (puerto 8083, `order_db`) que implementa la **saga orquestada** de compra.
- Compra en dos pasos: crear orden con reserva temporal y luego pagar.
- Límite de entradas por cliente y evento, con control de concurrencia.
- Idempotencia con `Idempotency-Key`.
- Expiración de órdenes pendientes y compensaciones ante fallas parciales.

## Impact
- Specs: `order-purchase` (nueva).
- Depende de: catalog (`sale-info`), inventory (holds), payment, ticket.
