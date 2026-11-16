#!/usr/bin/env bash
# Estado de las 5 bases: migraciones Flyway aplicadas y filas por tabla.
. "$(dirname "$0")/lib.sh"
ROWS="select table_name as tabla,
  (xpath('/row/c/text()', query_to_xml('select count(*) as c from '||quote_ident(table_name), false, true, '')))[1]::text::int as filas
  from information_schema.tables where table_schema='public' and table_type='BASE TABLE' order by 1"
for db in catalog_db inventory_db order_db payment_db ticket_db; do
  section "$db"
  psqlq "$db" "select version, description, success from flyway_schema_history order by installed_rank" || { bad "no pude leer $db"; continue; }
  psqlq "$db" "$ROWS"
  [ "$(psqlv "$db" "select count(*) from flyway_schema_history where not success")" = 0 ] && ok "todas las migraciones OK" || bad "hay migraciones fallidas"
done
summary
