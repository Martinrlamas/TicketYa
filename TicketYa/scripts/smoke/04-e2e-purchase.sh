#!/usr/bin/env bash
# Recorrido completo A TRAVÉS DEL GATEWAY: búsqueda -> disponibilidad -> orden -> pago -> entradas -> devolución,
# con casos de error (asiento tomado, pago rechazado, límite de compra, acceso ajeno) y verificación en las bases.
# Requiere los 6 procesos y haber corrido 03-seed.sh.
. "$(dirname "$0")/lib.sh"
load_state
[ -n "${EVENT_ID:-}" ] || { echo "Primero corré ./03-seed.sh"; exit 1; }
A=$(uuid); B=$(uuid); C=$(uuid); D=$(uuid); E=$(uuid)
PAY_OK='{"method":"CARD","token":"tok_approved","last4":"4242"}'
PAY_KO='{"method":"CARD","token":"tok_rejected","last4":"0002"}'

new_order() {  # cliente clave json
  cust "$1"; api POST "$GW/api/orders" "$3" "${CUST_H[@]}" -H "Idempotency-Key: $2"
}
pay() {        # cliente orderId json
  cust "$1"; api POST "$GW/api/orders/$2/pay" "$3" "${CUST_H[@]}" -H "Idempotency-Key: $(uuid)"
}
seats_body() { jq -nc --arg e "$EVENT_ID" --arg s "$PLATEA_ID" --argjson ids "$1" '{eventId:$e,items:[{sectorId:$s,seatIds:$ids}]}'; }
qty_body()   { jq -nc --arg e "$EVENT_ID" --arg s "$CAMPO_ID" --argjson q "$1" '{eventId:$e,items:[{sectorId:$s,quantity:$q}]}'; }
seat_status() { # seatId -> estado en la disponibilidad pública
  api GET "$GW/api/events/$EVENT_ID/availability" ""
  echo "$BODY" | jq -r --arg id "$1" '.sectors[]|select(.type=="NUMBERED")|.seats[]|select(.seatId==$id)|.status'
}

section "1) Búsqueda pública (catalog vía gateway)"
api GET "$GW/api/events?q=demo&sort=relevance" ""; check_code "buscar 'demo' por relevancia" 200
[ "$(j .totalElements)" -ge 1 ] && ok "hay resultados ($(j .totalElements))" || bad "no hay eventos 'demo' (¿corriste 03-seed.sh?)"
api GET "$GW/api/events?q=nandudes" ""; echo "       búsqueda sin acentos 'nandudes' -> $(j .totalElements) resultado(s)"
api GET "$GW/api/events/$EVENT_ID" ""; check_code "detalle público del evento" 200; check "estado" PUBLISHED "$(j .status)"

section "2) Disponibilidad (inventory vía gateway)"
cust "$A"; api GET "$GW/api/events/$EVENT_ID/availability" "" "${CUST_H[@]}"; check_code "disponibilidad" 200
SEATS=$(echo "$BODY" | jq -c '[.sectors[]|select(.type=="NUMBERED")|.seats[]|select(.status=="AVAILABLE")|.seatId]')
echo "       asientos libres en Platea: $(echo "$SEATS" | jq length)"
S1=$(echo "$SEATS" | jq -r '.[0]'); S2=$(echo "$SEATS" | jq -r '.[1]'); S3=$(echo "$SEATS" | jq -r '.[2]'); S4=$(echo "$SEATS" | jq -r '.[3]')
[ "$S4" != null ] || { bad "se necesitan >=4 asientos libres (corré ./03-seed.sh de nuevo)"; summary; exit 1; }

section "3) Compra feliz: cliente A, 2 asientos numerados"
K1=$(uuid); new_order "$A" "$K1" "$(seats_body "[\"$S1\",\"$S2\"]")"
check_code "crear orden (hold)" 201; OID=$(j .orderId)
check "estado" PENDING_PAYMENT "$(j .status)"; check "total (2 x 45000)" 90000 "$(j '.total|floor')"
new_order "$A" "$K1" "$(seats_body "[\"$S1\",\"$S2\"]")"
check_code "reintento con la MISMA Idempotency-Key" 200 201; check "misma orden" "$OID" "$(j .orderId)"
check "asiento ahora HELD" HELD "$(seat_status "$S1")"

section "4) Otro cliente intenta el mismo asiento"
new_order "$B" "$(uuid)" "$(seats_body "[\"$S1\"]")"
check_code "doble reserva" 409; check "código" SEAT_UNAVAILABLE "$(j .code)"

section "5) Pago aprobado (tok_approved)"
pay "$A" "$OID" "$PAY_OK"; check_code "pagar" 200
cust "$A"; api GET "$GW/api/orders/$OID" "" "${CUST_H[@]}"
check "orden" CONFIRMED "$(j .status)"; check "entradas emitidas" 2 "$(j '.tickets|length')"
ITEM=$(j '.items[0].itemId')
api GET "$GW/api/tickets" "" "${CUST_H[@]}"; check_code "GET /api/tickets (ticket-service vía gateway)" 200
check "mis entradas" 2 "$(j '.content|length')"
check "todas VALID con QR" 2 "$(echo "$BODY" | jq '[.content[]|select(.status=="VALID" and (.qrCode|length)>20)]|length')"
check "QR distintos" 2 "$(echo "$BODY" | jq '[.content[].qrCode]|unique|length')"
TID=$(echo "$BODY" | jq -r --arg i "$ITEM" '.content[]|select(.orderItemId==$i)|.ticketId')
check "asiento ahora SOLD" SOLD "$(seat_status "$S1")"

section "6) Pago rechazado (tok_rejected) libera el lugar"
new_order "$D" "$(uuid)" "$(seats_body "[\"$S3\",\"$S4\"]")"; check_code "crear orden" 201; OD=$(j .orderId)
pay "$D" "$OD" "$PAY_KO"; check_code "pago rechazado" 402; check "código" PAYMENT_REJECTED "$(j .code)"
cust "$D"; api GET "$GW/api/orders/$OD" "" "${CUST_H[@]}"; check "orden" PAYMENT_REJECTED "$(j .status)"
check "asiento vuelve a AVAILABLE" AVAILABLE "$(seat_status "$S3")"

section "7) Zona general: cliente C compra 3 entradas"
new_order "$C" "$(uuid)" "$(qty_body 3)"; check_code "crear orden" 201; OC=$(j .orderId)
pay "$C" "$OC" "$PAY_OK"; check_code "pagar" 200
cust "$C"; api GET "$GW/api/orders/$OC" "" "${CUST_H[@]}"; check "orden" CONFIRMED "$(j .status)"; check "entradas" 3 "$(j '.tickets|length')"

section "8) Reglas y seguridad"
new_order "$E" "$(uuid)" "$(qty_body 7)"; check_code "más de 6 entradas por evento" 422; check "código" PURCHASE_LIMIT_EXCEEDED "$(j .code)"
cust "$E"; api POST "$GW/api/orders" "$(qty_body 1)" "${CUST_H[@]}"; check_code "crear orden sin Idempotency-Key" 400; check "código" IDEMPOTENCY_KEY_REQUIRED "$(j .code)"
cust "$B"; api GET "$GW/api/orders/$OID" "" "${CUST_H[@]}"; check_code "orden de otro cliente" 404
api GET "$GW/api/tickets/$TID" "" "${CUST_H[@]}"; check_code "entrada de otro cliente" 404
api GET "$GW/api/tickets" ""; check_code "sin identidad" 401
cust "$A"; api POST "$GW/api/events/$EVENT_ID/cancel" "{}" "${CUST_H[@]}"; check_code "CUSTOMER no puede cancelar eventos" 403

section "9) Verificación en las bases (lo que quedó guardado)"
q() { echo "${DIM}-- $1: $3${RST}"; psqlq "$1" "$2" | sed 's/^/   /'; }
q order_db     "select status, count(*) from purchase_order where event_id='$EVENT_ID' group by 1 order by 1" "órdenes por estado"
q payment_db   "select status, count(*) from payment group by 1 order by 1" "pagos"
q ticket_db    "select status, count(*) from ticket where event_id='$EVENT_ID' group by 1 order by 1" "entradas"
q inventory_db "select status, count(*) from event_seat where event_id='$EVENT_ID' group by 1 order by 1" "asientos"
check "órdenes CONFIRMED en order_db" 2 "$(psqlv order_db "select count(*) from purchase_order where event_id='$EVENT_ID' and status='CONFIRMED'")"
check "entradas VALID en ticket_db (2+3)" 5 "$(psqlv ticket_db "select count(*) from ticket where event_id='$EVENT_ID' and status='VALID'")"
check "asientos SOLD en inventory_db" 2 "$(psqlv inventory_db "select count(*) from event_seat where event_id='$EVENT_ID' and status='SOLD'")"
check "asientos HELD (ninguno colgado)" 0 "$(psqlv inventory_db "select count(*) from event_seat where event_id='$EVENT_ID' and status='HELD'")"

section "10) Devolución de una entrada (cliente pide, ADMIN aprueba, saga automática)"
cust "$A"; api POST "$GW/api/orders/$OID/items/$ITEM/refund-requests" '{"reason":"No puedo asistir"}' "${CUST_H[@]}"
check_code "solicitar devolución" 201; RID=$(j .refundRequestId); check "estado" PENDING "$(j .status)"
api POST "$GW/api/refund-requests/$RID/approve" "" "${ADMIN[@]}"; check_code "ADMIN aprueba" 200
echo "       esperando a la saga (job cada ~5 s)..."
for i in $(seq 1 20); do
  sleep 3; cust "$A"; api GET "$GW/api/refund-requests/mine" "" "${CUST_H[@]}"
  ST=$(echo "$BODY" | jq -r --arg r "$RID" '.content[]|select(.refundRequestId==$r)|.status'); [ "$ST" = COMPLETED ] || [ "$ST" = FAILED ] && break
done
check "solicitud" COMPLETED "$ST"
check "entrada del ítem" REFUNDED "$(psqlv ticket_db "select status from ticket where order_item_id='$ITEM'")"
check "asiento liberado (SOLD baja a 1)" 1 "$(psqlv inventory_db "select count(*) from event_seat where event_id='$EVENT_ID' and status='SOLD'")"
check "reintegro registrado en payment_db" 1 "$(psqlv payment_db "select count(*) from refund where status='APPROVED'")"

summary
