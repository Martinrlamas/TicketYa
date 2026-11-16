#!/usr/bin/env bash
# Cancelación de evento por ADMIN: catalog -> inventory (cierra) + order (reintegros automáticos) -> ticket (invalida en bloque).
# Es destructivo para el evento de prueba: correr 04 antes y 03-seed.sh de nuevo para volver a empezar.
. "$(dirname "$0")/lib.sh"
load_state; [ -n "${EVENT_ID:-}" ] || { echo "Primero corré ./03-seed.sh y ./04-e2e-purchase.sh"; exit 1; }

section "Antes"
psqlq ticket_db "select status, count(*) from ticket where event_id='$EVENT_ID' group by 1"
psqlq order_db  "select status, count(*) from purchase_order where event_id='$EVENT_ID' group by 1"

section "Cancelar (ADMIN)"
api POST "$GW/api/events/$EVENT_ID/cancel" '{"reason":"Cancelado por prueba manual"}' "${ADMIN[@]}"
check_code "POST /api/events/{id}/cancel" 200; check "estado" CANCELLED "$(j .status)"
api GET "$GW/api/events?q=demo" ""; echo "       ya no aparece en la búsqueda pública: $(echo "$BODY" | jq --arg id "$EVENT_ID" '[.content[]|select(.id==$id)]|length') coincidencias"
api GET "$GW/api/events/$EVENT_ID" ""; check_code "detalle público de un evento cancelado" 404

section "Esperando a la saga de cancelación (jobs de order-service, hasta ~90 s)"
for i in $(seq 1 30); do
  V=$(psqlv ticket_db "select count(*) from ticket where event_id='$EVENT_ID' and status='VALID'")
  echo "   VALID restantes: $V"; [ "$V" = 0 ] && break; sleep 3
done
check "no quedan entradas VALID" 0 "$V"

section "Después"
psqlq ticket_db    "select status, count(*) from ticket where event_id='$EVENT_ID' group by 1"
psqlq order_db     "select status, count(*) from purchase_order where event_id='$EVENT_ID' group by 1"
psqlq order_db     "select status, automatic, count(*) from refund_request group by 1,2"
psqlq inventory_db "select status, count(*) from event_seat where event_id='$EVENT_ID' group by 1"
summary
