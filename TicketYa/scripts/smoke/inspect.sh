#!/usr/bin/env bash
# Foto rápida de los datos de negocio en las 5 bases (úsalo en cualquier momento).
. "$(dirname "$0")/lib.sh"
section "catalog_db: eventos";   psqlq catalog_db   "select name, status, type, min_price, starts_at::date as fecha from event order by starts_at"
section "inventory_db: asientos"; psqlq inventory_db "select event_id, status, count(*) from event_seat group by 1,2 order by 1,2"
section "order_db: órdenes";     psqlq order_db     "select status, count(*) as ordenes, sum(total) as total from purchase_order group by 1"
section "payment_db: pagos";     psqlq payment_db   "select status, count(*) from payment group by 1"
section "ticket_db: entradas";   psqlq ticket_db    "select event_id, status, count(*) from ticket group by 1,2 order by 1,2"
