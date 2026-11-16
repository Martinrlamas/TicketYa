## Why
La regla central del negocio es que una entrada nunca se venda dos veces, incluso con miles de compradores
simultáneos y varias instancias del servicio. Esa garantía debe vivir en un único lugar.

## What Changes
- Nuevo `inventory-service` (puerto 8082, `inventory_db`), única fuente de verdad sobre qué se puede vender.
- Inicialización de inventario al publicar un evento (idempotente).
- Holds (reservas temporales) con TTL, confirmación, liberación y expiración automática.
- Consulta pública de disponibilidad (sin cache).
- Notificación de SOLD_OUT a catalog.

## Impact
- Specs: `inventory-availability`, `inventory-holds` (nuevas).
- Depende de: `platform-conventions`. Es consumido por catalog (init) y order (holds).
