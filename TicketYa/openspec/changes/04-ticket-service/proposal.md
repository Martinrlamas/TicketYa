## Why
Las entradas tienen un ciclo de vida y un perfil de carga propios (el día del evento la validación de QR es intensiva). Separarlas de las órdenes permite escalarlas y evolucionarlas (control de acceso en E2) de forma independiente.

## What Changes
- Nuevo `ticket-service` (puerto 8085, `ticket_db`).
- Emisión idempotente de entradas con QR único, consulta por cliente e invalidación.

## Impact
- Specs: `tickets` (nueva). Depende de: `platform-conventions`. Consumido por order-service.
