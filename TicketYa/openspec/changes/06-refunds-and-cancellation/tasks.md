## 1. Devoluciones
- [ ] 1.1 Migración `refund_request` + índice único parcial
- [ ] 1.2 `RequestRefundUseCase` + `POST /api/orders/{orderId}/items/{itemId}/refund-requests`
- [ ] 1.3 `GET /api/refund-requests` (ADMIN, filtro por estado) y `GET /api/refund-requests/mine`
- [ ] 1.4 `POST /api/refund-requests/{id}/approve|reject` (ADMIN)
- [ ] 1.5 `RefundProcessor` (saga por pasos) + `RefundRecoveryJob`

## 2. Cancelación de evento
- [ ] 2.1 `POST /internal/events/{eventId}/cancellation`
- [ ] 2.2 `CancelEventUseCase` con procesamiento por lotes
- [ ] 2.3 Puerto `BuyerNotifier` con adaptador log
- [ ] 2.4 Adaptadores en catalog (`EventCancelled`) e inventory (`close`)

## 3. Pruebas
- [ ] 3.1 Unitarias: ventana de 72 h (justo en el límite, antes, después), duplicados, propiedad
- [ ] 3.2 Unitarias: aprobar/rechazar y transiciones
- [ ] 3.3 Falla de payment en el paso 3 ⇒ reintento y finalización sin duplicar reintegro
- [ ] 3.4 Devolver un asiento permite que otro cliente lo compre
- [ ] 3.5 Cancelación con 100 órdenes ⇒ todas reintegradas, todas las entradas `CANCELLED`
