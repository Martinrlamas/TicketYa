## 1. Proyecto y dominio
- [ ] 1.1 Crear `services/order-service`
- [ ] 1.2 Migraciones (órdenes, ítems, cuota, intentos de pago)
- [ ] 1.3 Dominio: `Order` (máquina de estados), `Quota`, cálculo de total
- [ ] 1.4 Puertos y adaptadores REST: `CatalogPort`, `InventoryPort`, `PaymentPort`, `TicketPort`

## 2. Casos de uso y API
- [ ] 2.1 `CreateOrderUseCase` + `POST /api/orders`
- [ ] 2.2 `PayOrderUseCase` + `POST /api/orders/{id}/pay`
- [ ] 2.3 `CancelOrderUseCase` + `DELETE /api/orders/{id}`
- [ ] 2.4 `GET /api/orders/{id}`, `GET /api/orders`
- [ ] 2.5 Job `ExpirePendingOrdersJob` y job de reconciliación de pagos

## 3. Pruebas
- [ ] 3.1 Unitarias con dobles de los puertos: camino feliz, rechazo, expiración, hold vencido tras pago, falla de tickets
- [ ] 3.2 Límite por cliente con 10 requests simultáneos del mismo cliente ⇒ no se supera el límite
- [ ] 3.3 Idempotencia: misma clave ⇒ una sola orden; otro payload ⇒ 422
- [ ] 3.4 **Prueba end-to-end de no doble venta:** 30 clientes compran el mismo asiento a través del gateway ⇒ una sola orden `CONFIRMED`, un solo ticket
- [ ] 3.5 Compensación: pago aprobado + hold vencido ⇒ reintegro automático y orden `FAILED_REFUNDED`
