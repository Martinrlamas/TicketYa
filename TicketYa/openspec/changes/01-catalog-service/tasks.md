## 1. Proyecto y dominio
- [ ] 1.1 Crear `services/catalog-service` con dependencia al starter, Flyway y springdoc
- [ ] 1.2 Dominio: `Venue`, `Sector`, `Event` (con máquina de estados), `EventSectorPrice`
- [ ] 1.3 Migraciones V1 (esquema) y V2 (datos de prueba: 2 lugares, 3 eventos)

## 2. Casos de uso y API
- [ ] 2.1 `POST/GET /api/venues`, `POST /api/venues/{id}/sectors`
- [ ] 2.2 `POST/PUT/GET /api/events`, `GET /api/events/mine`, `PUT /api/events/{id}/sector-prices`
- [ ] 2.3 `POST /api/events/{id}/publish|suspend|resume|cancel`
- [ ] 2.4 `GET /api/events` (búsqueda) con Specifications dinámicas
- [ ] 2.5 `GET /internal/events/{id}/sale-info`, `PUT /internal/events/{id}/status`
- [ ] 2.6 Puertos `EventPublished` / `EventCancelled` con adaptadores REST hacia inventory y order
- [ ] 2.7 Jobs: `finishPastEvents` y reintento de avisos de cancelación (`CancellationNotifier`)

## 3. Pruebas
- [ ] 3.1 Unitarias de la máquina de estados (todas las transiciones válidas e inválidas)
- [ ] 3.2 Unitarias de validación de sectores y precios
- [ ] 3.3 Tests de búsqueda con Testcontainers (cada criterio, combinaciones, orden, paginación)
- [ ] 3.4 Test de publicación con inventory caído (evento sigue en DRAFT)

## 4. Documentación
- [ ] 4.1 OpenAPI con ejemplos y errores
- [ ] 4.2 Requests de Postman de catalog
