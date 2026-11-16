-- Una base por servicio, con su propio usuario. Se ejecuta una sola vez, al crear el volumen de PostgreSQL.
-- Contraseñas SOLO DE DESARROLLO (iguales al nombre del usuario, que son los valores por defecto de cada servicio).
-- Un usuario no puede conectarse a la base de otro servicio (REVOKE CONNECT ... FROM PUBLIC).

create user catalog   with password 'catalog';
create user inventory with password 'inventory';
create user orders    with password 'orders';
create user payment   with password 'payment';
create user ticket    with password 'ticket';

create database catalog_db   owner catalog;
create database inventory_db owner inventory;
create database order_db     owner orders;
create database payment_db   owner payment;
create database ticket_db    owner ticket;

revoke connect on database catalog_db   from public;
revoke connect on database inventory_db from public;
revoke connect on database order_db     from public;
revoke connect on database payment_db   from public;
revoke connect on database ticket_db    from public;

grant connect on database catalog_db   to catalog;
grant connect on database inventory_db to inventory;
grant connect on database order_db     to orders;
grant connect on database payment_db   to payment;
grant connect on database ticket_db    to ticket;
