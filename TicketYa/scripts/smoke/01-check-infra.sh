#!/usr/bin/env bash
# ¿Está todo arriba? PostgreSQL + 5 bases + 6 procesos Spring + protección de /internal.
. "$(dirname "$0")/lib.sh"

section "PostgreSQL"
docker ps --format '{{.Names}}' | grep -qx "$PG_CONTAINER" && ok "contenedor $PG_CONTAINER corriendo" \
  || { bad "el contenedor $PG_CONTAINER no está corriendo (docker compose up --build -d)"; summary; exit 1; }
docker exec "$PG_CONTAINER" pg_isready -U "$PG_USER" >/dev/null && ok "pg_isready" || bad "pg_isready"
DBS=$(psqlv postgres "select datname from pg_database")
for db in catalog_db inventory_db order_db payment_db ticket_db; do
  echo "$DBS" | grep -qx "$db" && ok "base $db existe" || bad "falta la base $db (¿volumen viejo? docker compose down -v y volver a levantar)"
done

section "Servicios (cada uno debe responder)"
for s in "catalog 8081" "inventory 8082" "order 8083" "payment 8084" "ticket 8085"; do
  set -- $s
  code=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$2/v3/api-docs" 2>/dev/null)
  [ "$code" = 200 ] && ok "$1-service :$2 responde (/v3/api-docs)" || bad "$1-service :$2 no responde (HTTP $code). ¿Está levantado?"
done
code=$(curl -s -o /dev/null -w '%{http_code}' "$GW/actuator/health" 2>/dev/null)
[ "$code" = 200 ] && ok "api-gateway :8080 responde (/actuator/health)" || bad "api-gateway :8080 no responde (HTTP $code)"

section "Seguridad interna"
Z=00000000-0000-0000-0000-000000000000
code=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:8081/internal/events/$Z/sale-info")
check "catalog /internal sin token" 403 "$code"
code=$(curl -s -o /dev/null -w '%{http_code}' -H "X-Internal-Token: $INTERNAL_TOKEN" "http://localhost:8081/internal/events/$Z/sale-info")
check "catalog /internal con token (evento inexistente)" 404 "$code"
code=$(curl -s -o /dev/null -w '%{http_code}' "$GW/internal/events/$Z/sale-info")
[ "$code" != 200 ] && ok "el gateway NO expone /internal (HTTP $code)" || bad "el gateway deja pasar /internal"

section "Gateway -> catalog"
api GET "$GW/api/events" ""
check_code "GET /api/events a través del gateway" 200
echo "       eventos publicados ahora: $(j '.totalElements')"
summary
