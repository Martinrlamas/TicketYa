# Pruebas manuales de TicketYa (sin tests automáticos)

Copiá esta carpeta a `TicketYa/scripts/smoke/`. Requisitos: `bash`, `curl`, `jq`, `docker` (los scripts leen el `.env` de la raíz).

## Orden de uso
```bash
# 0) Levantar todo desde la raíz: docker compose up --build -d
cd scripts/smoke
./01-check-infra.sh      # ¿está arriba cada cosa? bases, servicios, gateway, /internal protegido
./02-check-db.sh         # migraciones Flyway aplicadas + filas por tabla en las 5 bases
./03-seed.sh             # carga datos de prueba POR LA API (catalog + inventory coherentes)
./04-e2e-purchase.sh     # compra completa por el gateway + errores + verificación en bases + devolución
./05-cancel-event.sh     # cancela el evento de prueba y mira la saga (reintegros + invalidación de entradas)
./inspect.sh             # foto de los datos en cualquier momento
```
Cada script imprime `OK`/`FAIL` por verificación y termina con un resumen (código de salida ≠ 0 si algo falla).

## Datos de prueba
- **Automáticos al arrancar catalog** (perfil `local`): 2 lugares, 4 sectores y 3 eventos en `DRAFT` con precios (organizador `3d4e5f60-8192-43a4-b5b0-2b3c4d5e6f78`).
- **`03-seed.sh`**: publica esos 3 eventos y crea "Arena Demo" (Platea numerada 2 filas x 10, Campo general 200) con 3 eventos demo publicados. Deja el evento principal en `.state` para los demás scripts. Se puede correr varias veces (crea datos nuevos).
- Identidad (sin login en E1): headers `X-User-Id` (UUID) y `X-User-Role` (`CUSTOMER|ORGANIZER|ADMIN|ACCESS_STAFF`). El organizador del seed es el UUID de arriba.
- Tokens de pago simulados: `tok_approved` (y cualquier otro) aprueba · `tok_rejected` rechaza · `tok_timeout` tarda y aprueba · `tok_flaky` falla la 1ª vez.

## Consultar las bases a mano
```bash
docker exec -it ticketya-postgres psql -U postgres -d order_db      # catalog_db | inventory_db | order_db | payment_db | ticket_db
\dt                                           # tablas
select * from flyway_schema_history;          # migraciones
select status, count(*) from purchase_order group by 1;
```
Tablas: catalog (`venue, sector, sector_row, event, event_participant, event_sector_price`) · inventory (`event_inventory, event_sector, event_seat, zone_stock, hold, hold_item, sold_release`) · order (`purchase_order, order_item, purchase_quota, pay_attempt, refund_request, event_cancellation`) · payment (`payment, refund`) · ticket (`ticket`).

## Empezar de cero
```bash
docker compose down -v    # borra el volumen
docker compose up --build -d
# reiniciar los servicios: Flyway recrea el esquema y catalog vuelve a cargar el seed
rm -f scripts/smoke/.state
```

## Si algo falla
| Síntoma | Causa probable |
|---|---|
| 01: servicio `HTTP 000` | proceso no levantado o puerto ocupado |
| 03: `publicar` devuelve 503 | inventory-service (8082) no está corriendo |
| 03: 403 en publicar | el `INTERNAL_TOKEN` difiere entre catalog e inventory |
| 04: `503`/timeouts en pago | payment (8084) caído; `tok_timeout` es lento a propósito |
| 04 paso 10 no llega a `COMPLETED` | revisar el log de order-service (`RefundRecoveryJob`) y `select * from refund_request` |
| `psql: role ... does not exist` | cambiaste `POSTGRES_USER` en `.env`: se usa para entrar a las bases |
