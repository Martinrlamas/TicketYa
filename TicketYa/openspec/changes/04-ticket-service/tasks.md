## 1. Implementación
- [ ] 1.1 Crear `services/ticket-service`
- [ ] 1.2 Migraciones y constraints únicos
- [ ] 1.3 Generador de QR (`QrTokenGenerator`)
- [ ] 1.4 `GET /internal/tickets?orderId=`, `POST /internal/tickets`, `POST /internal/tickets/{id}/invalidate`, `POST /internal/events/{eventId}/tickets/invalidate`
- [ ] 1.5 `GET /api/tickets`, `GET /api/tickets/{id}` (solo del dueño)

## 2. Pruebas
- [ ] 2.1 Unitarias: transiciones de estado válidas e inválidas
- [ ] 2.2 Unicidad de QR (10.000 tokens sin colisión)
- [ ] 2.3 Idempotencia de emisión (llamadas concurrentes con el mismo `orderItemId`)
- [ ] 2.4 Acceso: un cliente no puede ver tickets de otro
