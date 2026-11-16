## 1. Implementación
- [ ] 1.1 Crear `services/payment-service`
- [ ] 1.2 Migraciones y constraints únicos
- [ ] 1.3 Puerto `PaymentGateway` + `MockPaymentGateway`
- [ ] 1.4 `POST /internal/payments`, `GET /internal/payments/{id}`, `GET /internal/payments?orderId=`, `POST /internal/payments/{id}/refunds`
- [ ] 1.5 Manejo de reintegro acumulado (no superar lo pagado) con UPDATE condicional

## 2. Pruebas
- [ ] 2.1 Unitarias: aprobado, rechazado, montos inválidos
- [ ] 2.2 Idempotencia: misma clave ⇒ un solo pago; clave reutilizada con otro monto ⇒ 422
- [ ] 2.3 Concurrencia: 20 requests simultáneos con la misma clave ⇒ un solo registro
- [ ] 2.4 Reintegro parcial acumulado no supera el total
