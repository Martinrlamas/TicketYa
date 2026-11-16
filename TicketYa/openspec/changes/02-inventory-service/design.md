## Modelo de datos (inventory_db)
- `event_seat(id, event_id, sector_id, row_label, seat_number, status AVAILABLE|HELD|SOLD, hold_id NULL, version)`
  - `UNIQUE(event_id, sector_id, row_label, seat_number)`
  - índice `(event_id, sector_id, status)`
- `zone_stock(event_id, sector_id, capacity, held, sold, version)`
  - PK `(event_id, sector_id)`, `CHECK (held + sold <= capacity AND held >= 0 AND sold >= 0)`
- `hold(id, event_id, order_id UNIQUE, status ACTIVE|CONFIRMED|RELEASED|EXPIRED, expires_at, created_at)`
- `hold_item(id, hold_id, sector_id, seat_id NULL, quantity)`

## Mecanismo anti doble venta (decisión principal)
La garantía está en la base de datos, no en memoria, por lo que funciona con N instancias.

**Asientos numerados — UPDATE condicional atómico**
```sql
UPDATE event_seat
   SET status='HELD', hold_id=:holdId, version=version+1
 WHERE id IN (:ids) AND status='AVAILABLE';
```
Si `filas afectadas != cantidad pedida` se hace rollback de toda la transacción y se responde `SEAT_UNAVAILABLE`
(todo-o-nada). Los IDs se ordenan antes de actualizar para evitar deadlocks entre compradores con asientos solapados.

**Zonas no numeradas — contador atómico**
```sql
UPDATE zone_stock
   SET held = held + :n, version = version + 1
 WHERE event_id=:e AND sector_id=:s AND held + sold + :n <= capacity;
```
0 filas afectadas ⇒ `INSUFFICIENT_CAPACITY`. Nunca se lee y luego escribe. El `CHECK` es la última red de seguridad.

**Confirmación:** `HELD→SOLD` (`WHERE hold_id=:id AND status='HELD'`) y `held=held-n, sold=sold+n`.
**Liberación/expiración:** `HELD→AVAILABLE` con las mismas condiciones, por lo que es idempotente.

## Expiración de holds
- `@Scheduled` cada 15 s ejecuta una sola sentencia que selecciona holds `ACTIVE` vencidos con
  `FOR UPDATE SKIP LOCKED` y los libera. Varias instancias pueden correrlo sin duplicar trabajo.
- Además, `confirm` revalida `expires_at` (expiración perezosa), por lo que el job no es crítico para la corrección.

## Idempotencia
`hold.order_id` es UNIQUE: repetir `POST /internal/holds` con el mismo `orderId` devuelve el hold existente.

## Evolución a E2
El puerto `HoldStore` permite reemplazar la implementación por Redis (TTL nativo + scripts Lua) sin tocar
el dominio. La tabla relacional queda como registro durable de ventas.

## Riesgos
- Contención en filas de `zone_stock` para eventos masivos: mitigación futura con fila virtual y sharding del contador.

## Tabla adicional
- `sold_release(release_id PK, event_id, created_at)`: registro de liberaciones aplicadas; garantiza idempotencia de `sold/release`.
