#!/usr/bin/env bash
# Carga datos de prueba POR LA API (así catalog e inventory quedan coherentes: nunca hay un evento PUBLISHED sin inventario).
#  1) publica los 3 eventos DRAFT que catalog ya trae de seed (perfil local)
#  2) crea un lugar "Arena Demo" con sector numerado (Platea A/B x10) y zona general (Campo, 200)
#  3) crea y publica 3 eventos demo nuevos (con artistas y precios)
# Requiere catalog (8081), inventory (8082) y gateway (8080) corriendo. Cada corrida crea datos nuevos.
. "$(dirname "$0")/lib.sh"
rm -f "$STATE"

section "1) Eventos del seed de catalog"
for ev in 9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d a1c3e5f7-1b2d-4f6a-8c9e-0a1b2c3d4e5f b2d4f6a8-2c3e-4a7b-9daf-1b2c3d4e5f60; do
  api GET "$GW/api/events/$ev" "" "${ORG[@]}"
  if [ "$CODE" = 404 ]; then echo "  (no existe $ev: ¿catalog corre con perfil local?)"; continue; fi
  name=$(j .name); st=$(j .status)
  if [ "$st" = DRAFT ]; then
    api POST "$GW/api/events/$ev/publish" "" "${ORG[@]}"
    check_code "publicar '$name'" 200
  else echo "  '$name' ya está $st"; fi
done

section "2) Lugar y sectores"
api POST "$GW/api/venues" '{"name":"Arena Demo","address":"Calle Falsa 123","city":"Tandil","latitude":-37.3217,"longitude":-59.1332}' "${ORG[@]}"
check_code "crear lugar" 201; VENUE=$(j .id)
api POST "$GW/api/venues/$VENUE/sectors" '{"name":"Platea","type":"NUMBERED","rows":[{"rowLabel":"A","seatCount":10},{"rowLabel":"B","seatCount":10}]}' "${ORG[@]}"
check_code "sector numerado Platea (20 asientos)" 201; PLATEA=$(j .id)
api POST "$GW/api/venues/$VENUE/sectors" '{"name":"Campo","type":"GENERAL","capacity":200}' "${ORG[@]}"
check_code "sector general Campo (200)" 201; CAMPO=$(j .id)

section "3) Eventos demo"
mk() { # nombre tipo días artista
  local body
  body=$(jq -nc --arg n "$1" --arg t "$2" --arg v "$VENUE" --arg d "$(iso_in_days "$3")" --arg a "$4" \
    '{name:$n,description:("Evento de prueba: "+$n),type:$t,venueId:$v,startsAt:$d,participants:[{name:$a,role:"ARTIST"}]}')
  api POST "$GW/api/events" "$body" "${ORG[@]}"; check_code "crear '$1'" 201; EV=$(j .id)
  api PUT "$GW/api/events/$EV/sector-prices" "{\"prices\":[{\"sectorId\":\"$PLATEA\",\"price\":45000,\"currency\":\"ARS\"},{\"sectorId\":\"$CAMPO\",\"price\":30000,\"currency\":\"ARS\"}]}" "${ORG[@]}"
  check_code "  precios" 200
  api POST "$GW/api/events/$EV/publish" "" "${ORG[@]}"; check_code "  publicar (inicializa inventory)" 200
}
mk "Demo Rock Festival" FESTIVAL 40 "Los Ñandúes"; MAIN=$EV
mk "Demo Hamlet"        THEATER  55 "Compañía Ágora"
mk "Demo Jazz Night"    CONCERT  70 "Quinteto Sur"

save_state EVENT_ID "$MAIN"; save_state VENUE_ID "$VENUE"
save_state PLATEA_ID "$PLATEA"; save_state CAMPO_ID "$CAMPO"
echo; echo "Evento principal para las pruebas: $MAIN  (guardado en $STATE)"
section "Verificación"
api GET "$GW/api/events?size=50" ""; echo "  eventos publicados: $(j .totalElements)"; echo "$BODY" | jq -r '.content[] | "   - \(.name)  [\(.status)]  desde $\(.minPrice)  \(.city)"'
summary
