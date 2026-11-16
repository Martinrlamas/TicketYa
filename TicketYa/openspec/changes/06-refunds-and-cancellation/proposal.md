## Why
El enunciado exige devoluciones hasta 72 h antes del evento con aprobación de un administrador, y reintegro masivo si el evento se cancela.
Ambos flujos cruzan order, payment, ticket e inventory y deben ser recuperables ante fallas parciales.

## What Changes
- Solicitud de devolución por entrada (cliente), aprobación/rechazo (administrador).
- Procesamiento de la devolución como saga con pasos idempotentes y reintentos.
- Reintegro masivo al cancelarse un evento.

## Impact
- Specs: `order-refunds`, `event-cancellation` (nuevas). Servicio principal: order-service.
- Usa endpoints internos ya definidos en payment, ticket e inventory; no requiere cambios en sus contratos.
