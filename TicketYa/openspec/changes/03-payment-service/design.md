## Modelo de datos (payment_db)
- `payment(id, order_id, customer_id, amount, currency, method, method_detail, status APPROVED|REJECTED, gateway_ref, idempotency_key UNIQUE, refunded_amount, created_at)`
- `refund(id, payment_id, amount, status APPROVED|REJECTED, idempotency_key UNIQUE, reason, created_at)`

## Decisiones
1. **Puerto `PaymentGateway`** con adaptador `MockPaymentGateway`. Cambiar a un proveedor real implica solo un nuevo adaptador.
2. **Mock determinista por token** (para testeo): `tok_approved` → aprobado, `tok_rejected` → rechazado, `tok_timeout` → duerme más que el timeout del cliente, `tok_flaky` → falla la primera vez y aprueba al reintentar. Cualquier otro token → aprobado.
3. **Idempotencia:** `idempotency_key` con índice único; reintento con la misma clave devuelve el resultado original; la misma clave con otro monto/orden ⇒ `IDEMPOTENCY_KEY_REUSED`.
4. **Dinero:** `NUMERIC(12,2)`, nunca `double`. No se almacenan datos de tarjeta, solo token y últimos 4 dígitos.
5. Un pago rechazado se persiste (auditoría) con status REJECTED y se responde 200 con ese estado; el HTTP 402 lo decide order-service hacia el cliente.
