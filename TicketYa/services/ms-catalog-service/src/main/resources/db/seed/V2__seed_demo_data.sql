-- Datos de prueba (solo perfil local). IDs iguales a los ejemplos de docs/05.
-- Los eventos quedan en DRAFT con precios: publicarlos (POST /api/events/{id}/publish con el organizador
-- 3d4e5f60-...) inicializa el inventario en inventory-service. Así nunca hay eventos PUBLISHED sin inventario.

INSERT INTO venue (id, name, address, city, latitude, longitude) VALUES
  ('5a8e1d2c-7b3f-4c9a-a1e6-0d4b7c2f9e10', 'Estadio Municipal', 'Av. España 1200', 'Tandil', -37.321700, -59.133200),
  ('6b9f2e3d-8c4a-4dab-b2f7-1e5c8d3a0f21', 'Teatro del Fuerte', 'Rivadavia 456', 'Tandil', -37.325900, -59.136800);

INSERT INTO sector (id, venue_id, name, type, capacity) VALUES
  ('c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01', '5a8e1d2c-7b3f-4c9a-a1e6-0d4b7c2f9e10', 'Platea', 'NUMBERED', NULL),
  ('d4e5f6a7-2b3c-4d4e-9f5a-6b7c8d9e0f12', '5a8e1d2c-7b3f-4c9a-a1e6-0d4b7c2f9e10', 'Campo',  'GENERAL', 5000),
  ('e5f6a7b8-3c4d-4e5f-8a6b-7c8d9e0f1a23', '6b9f2e3d-8c4a-4dab-b2f7-1e5c8d3a0f21', 'Butacas', 'NUMBERED', NULL),
  ('f6a7b8c9-4d5e-4f6a-9b7c-8d9e0f1a2b34', '6b9f2e3d-8c4a-4dab-b2f7-1e5c8d3a0f21', 'Pullman', 'NUMBERED', NULL);

INSERT INTO sector_row (id, sector_id, row_label, seat_count) VALUES
  (gen_random_uuid(), 'c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01', 'A', 20),
  (gen_random_uuid(), 'c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01', 'B', 20),
  (gen_random_uuid(), 'e5f6a7b8-3c4d-4e5f-8a6b-7c8d9e0f1a23', 'A', 10),
  (gen_random_uuid(), 'e5f6a7b8-3c4d-4e5f-8a6b-7c8d9e0f1a23', 'B', 10),
  (gen_random_uuid(), 'f6a7b8c9-4d5e-4f6a-9b7c-8d9e0f1a2b34', 'P', 8);

INSERT INTO event (id, name, description, type, organizer_id, venue_id, starts_at, status, min_price, version, created_at) VALUES
  ('9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d', 'Divididos en Tandil', 'Gira 2027 - rock nacional', 'CONCERT',
   '3d4e5f60-8192-43a4-b5b0-2b3c4d5e6f78', '5a8e1d2c-7b3f-4c9a-a1e6-0d4b7c2f9e10',
   now() + interval '60 days', 'DRAFT', 30000.00, 0, now()),
  ('a1c3e5f7-1b2d-4f6a-8c9e-0a1b2c3d4e5f', 'Hamlet', 'Obra clásica de Shakespeare', 'THEATER',
   '3d4e5f60-8192-43a4-b5b0-2b3c4d5e6f78', '6b9f2e3d-8c4a-4dab-b2f7-1e5c8d3a0f21',
   now() + interval '45 days', 'DRAFT', 12000.00, 0, now()),
  ('b2d4f6a8-2c3e-4a7b-9daf-1b2c3d4e5f60', 'Festival de Jazz', 'Tres bandas en vivo', 'FESTIVAL',
   '3d4e5f60-8192-43a4-b5b0-2b3c4d5e6f78', '5a8e1d2c-7b3f-4c9a-a1e6-0d4b7c2f9e10',
   now() + interval '90 days', 'DRAFT', 15000.00, 0, now());

INSERT INTO event_participant (id, event_id, name, role) VALUES
  (gen_random_uuid(), '9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d', 'Divididos', 'ARTIST'),
  (gen_random_uuid(), 'a1c3e5f7-1b2d-4f6a-8c9e-0a1b2c3d4e5f', 'Compañía Estable', 'ARTIST'),
  (gen_random_uuid(), 'b2d4f6a8-2c3e-4a7b-9daf-1b2c3d4e5f60', 'Quinteto Sur', 'ARTIST'),
  (gen_random_uuid(), 'b2d4f6a8-2c3e-4a7b-9daf-1b2c3d4e5f60', 'Ágatha Trío', 'ARTIST');

INSERT INTO event_sector_price (id, event_id, sector_id, price, currency) VALUES
  (gen_random_uuid(), '9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d', 'c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01', 45000.00, 'ARS'),
  (gen_random_uuid(), '9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d', 'd4e5f6a7-2b3c-4d4e-9f5a-6b7c8d9e0f12', 30000.00, 'ARS'),
  (gen_random_uuid(), 'a1c3e5f7-1b2d-4f6a-8c9e-0a1b2c3d4e5f', 'e5f6a7b8-3c4d-4e5f-8a6b-7c8d9e0f1a23', 12000.00, 'ARS'),
  (gen_random_uuid(), 'a1c3e5f7-1b2d-4f6a-8c9e-0a1b2c3d4e5f', 'f6a7b8c9-4d5e-4f6a-9b7c-8d9e0f1a2b34', 18000.00, 'ARS'),
  (gen_random_uuid(), 'b2d4f6a8-2c3e-4a7b-9daf-1b2c3d4e5f60', 'd4e5f6a7-2b3c-4d4e-9f5a-6b7c8d9e0f12', 15000.00, 'ARS');
