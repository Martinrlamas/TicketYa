#!/usr/bin/env bash
# Utilidades compartidas por los scripts de prueba manual. No se ejecuta solo.
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Carga el .env del repo (INTERNAL_TOKEN, POSTGRES_*) si existe: scripts/smoke -> ../../.env
[ -f "$HERE/../../.env" ] && { set -a; . "$HERE/../../.env"; set +a; }

GW="${GW:-http://localhost:8080}"
INTERNAL_TOKEN="${INTERNAL_TOKEN:-dev-internal-token}"
PG_CONTAINER="${PG_CONTAINER:-ticketya-postgres}"
PG_USER="${POSTGRES_USER:-postgres}"
STATE="$HERE/.state"
ORG_ID="3d4e5f60-8192-43a4-b5b0-2b3c4d5e6f78"   # dueño de los eventos del seed de catalog

PASS=0; FAIL=0
GREEN=$'\033[32m'; RED=$'\033[31m'; YEL=$'\033[33m'; DIM=$'\033[2m'; RST=$'\033[0m'

for bin in curl jq docker; do
  command -v "$bin" >/dev/null || { echo "Falta '$bin' en el PATH"; exit 1; }
done

uuid()  { (uuidgen 2>/dev/null || python3 -c 'import uuid;print(uuid.uuid4())') | tr 'A-Z' 'a-z'; }
iso_in_days() {  # fecha ISO-8601 UTC dentro de N días (Linux y macOS)
  date -u -d "+$1 days" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v+"$1"d +%Y-%m-%dT%H:%M:%SZ
}

# Headers de identidad (E1: sin autenticación real, solo headers)
ORG=(-H "X-User-Id: $ORG_ID" -H "X-User-Role: ORGANIZER")
ADMIN_ID="$(uuid)"
ADMIN=(-H "X-User-Id: $ADMIN_ID" -H "X-User-Role: ADMIN")
cust() { CUST_H=(-H "X-User-Id: $1" -H "X-User-Role: CUSTOMER"); }

# api METHOD URL BODY [curl args...]  ->  deja CODE y BODY
_TMP="$(mktemp)"; trap 'rm -f "$_TMP"' EXIT
api() {
  local method=$1 url=$2 body=$3; shift 3
  local args=(-s -X "$method" -o "$_TMP" -w '%{http_code}' -H 'Content-Type: application/json' "$@")
  [ -n "$body" ] && args+=(-d "$body")
  CODE=$(curl "${args[@]}" "$url" 2>/dev/null) || CODE=000
  BODY=$(cat "$_TMP")
}
j() { echo "$BODY" | jq -r "$1" 2>/dev/null; }

section() { echo; echo "${YEL}== $* ${RST}"; }
ok()   { PASS=$((PASS+1)); echo "  ${GREEN}OK${RST}   $*"; }
bad()  { FAIL=$((FAIL+1)); echo "  ${RED}FAIL${RST} $*"; }
# check "descripción" esperado obtenido
check() { if [ "$2" = "$3" ]; then ok "$1 (= $3)"; else bad "$1: esperado '$2', obtuve '$3'"; [ -n "${BODY:-}" ] && echo "${DIM}       cuerpo: ${BODY:0:300}${RST}"; fi; }
check_code() { # check_code "desc" 201 [200 ...]
  local desc=$1; shift; local c
  for c in "$@"; do [ "$CODE" = "$c" ] && { ok "$desc (HTTP $CODE)"; return; }; done
  bad "$desc: esperado HTTP $*, obtuve $CODE"; echo "${DIM}       cuerpo: ${BODY:0:300}${RST}"
}
summary() {
  echo; echo "Resultado: ${GREEN}$PASS ok${RST}, ${RED}$FAIL fallas${RST}"
  [ "$FAIL" -eq 0 ]
}

# SQL contra una base (superusuario del contenedor)
psqlq() { docker exec -i "$PG_CONTAINER" psql -U "$PG_USER" -d "$1" -v ON_ERROR_STOP=1 -c "$2"; }
psqlv() { docker exec -i "$PG_CONTAINER" psql -U "$PG_USER" -d "$1" -At -c "$2"; }

save_state() { printf '%s=%q\n' "$1" "$2" >> "$STATE"; }
load_state() { [ -f "$STATE" ] && . "$STATE"; }
