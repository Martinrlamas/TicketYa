## 1. Gateway
- [ ] 1.1 Crear `services/api-gateway` con Spring Cloud Gateway y el starter (versión reactiva de filtros)
- [ ] 1.2 Rutas según la tabla, URLs por variables de entorno
- [ ] 1.3 Filtro global de identidad, correlation-id y logging
- [ ] 1.4 Bloqueo de `/internal/**`
- [ ] 1.5 CORS configurable

## 2. Entrega
- [ ] 2.1 Datos de prueba (V2 de catalog; script `infra/seed.sh` que crea evento publicado con sectores)
- [ ] 2.2 Importar `postman/TicketYa-Entrega1.postman_collection.json` (ya generada: carpeta "00 - Flujo de compra completo" con 21 pasos encadenados y una carpeta por servicio) y verificar que corre en verde contra el sistema real
- [ ] 2.3 README: requisitos, cómo levantar bases (`docker compose -f infra/docker-compose.dev.yml up`), orden de arranque, puertos, usuarios de prueba (headers)
- [ ] 2.4 Verificar que `docs/` (features y user stories, arquitectura, secuencias, modelo de datos y entidades solapadas, endpoints) coincide con lo implementado; actualizar si hubo cambios
- [ ] 2.5 Comparar el OpenAPI generado por springdoc de cada servicio contra `docs/openapi/<servicio>.yaml` (rutas, métodos, códigos de respuesta y headers); corregir diferencias en el código o en el contrato
- [ ] 2.6 Si cambia un contrato, actualizar a la vez `docs/05-api-endpoints.md`, `docs/openapi/` y `postman/`

## 3. Pruebas
- [ ] 3.1 Gateway: enruta correctamente cada predicado, bloquea `/internal/**`, exige identidad
- [ ] 3.2 Smoke test del flujo completo a través del gateway
