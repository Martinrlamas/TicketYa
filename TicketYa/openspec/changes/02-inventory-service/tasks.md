## 1. Proyecto y dominio
- [ ] 1.1 Crear `services/inventory-service` (starter, Flyway, springdoc)
- [ ] 1.2 Migraciones con constraints (`UNIQUE`, `CHECK`)
- [ ] 1.3 Dominio: `Hold`, `HoldItem`, `ZoneStock`, `Seat` y reglas de transición
- [ ] 1.4 Puerto `HoldStore` + implementación JPA/JDBC con UPDATEs condicionales

## 2. Casos de uso y API
- [ ] 2.1 `POST /internal/events/{eventId}/inventory` (init idempotente)
- [ ] 2.2 `GET /api/events/{eventId}/availability` con `Cache-Control: no-store`
- [ ] 2.3 (respuesta de hold con `seats[{seatId,sectorId,label}]`) `POST /internal/holds`, `POST /internal/holds/{id}/confirm`, `DELETE /internal/holds/{id}`
- [ ] 2.4 `POST /internal/events/{eventId}/sold/release` (devoluciones)
- [ ] 2.5 `POST /internal/events/{eventId}/close` (cancelación: bloquea nuevos holds)
- [ ] 2.6 Job `ExpireHoldsJob` con `SKIP LOCKED`
- [ ] 2.7 Notificación `SOLD_OUT` / reapertura hacia catalog (puerto `EventStatusNotifier`)

## 3. Pruebas
- [ ] 3.1 Unitarias de reglas de dominio
- [ ] 3.2 **Concurrencia asiento numerado:** 50 hilos piden el mismo asiento ⇒ exactamente 1 éxito
- [ ] 3.3 **Concurrencia zona:** capacidad 10, 100 hilos piden 1 lugar ⇒ exactamente 10 éxitos, `held+sold == 10`
- [ ] 3.4 **Multi-instancia:** dos contextos Spring contra el mismo PostgreSQL (Testcontainers) compitiendo por el mismo asiento ⇒ 1 éxito
- [ ] 3.5 Todo-o-nada: pedido de 3 asientos con 1 ocupado ⇒ ninguno queda retenido
- [ ] 3.6 Expiración: hold vencido libera lugares; `confirm` tardío responde `HOLD_EXPIRED`
- [ ] 3.7 Idempotencia por `orderId`
