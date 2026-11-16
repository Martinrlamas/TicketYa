## Modelo de datos (ticket_db)
- `ticket(id, order_id, order_item_id UNIQUE, event_id, customer_id, sector_id, seat_id NULL, seat_label, qr_code UNIQUE, status VALID|USED|REFUNDED|CANCELLED, issued_at, used_at NULL)`

## Decisiones
1. **QR:** token opaco de 32 bytes aleatorios (`SecureRandom`, base64url), único e impredecible. No codifica datos de negocio; la validación consulta la base (E2). Sin firma en E1.
2. **Idempotencia:** `order_item_id UNIQUE`; emitir dos veces el mismo ítem devuelve la entrada existente.
3. **USED** queda definido en el modelo pero su transición se implementa en E2 (control de acceso).
4. El servicio no replica datos del evento más que `event_id`; el detalle se obtiene de catalog en la capa de presentación (o BFF en E2).
