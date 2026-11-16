## Tabla de ruteo (orden de evaluación = orden de la tabla)
| # | Predicado | Destino |
|---|---|---|
| 1 | `/internal/**` | **404 bloqueado** |
| 2 | `GET /api/events/*/availability` | inventory-service |
| 3 | `/api/venues/**`, `/api/events/**` | catalog-service |
| 4 | `/api/orders/**`, `/api/refund-requests/**` | order-service |
| 5 | `/api/tickets/**` | ticket-service |

payment-service **no tiene rutas públicas**: solo lo invoca order-service.

## Decisiones
1. **Gateway sin lógica de negocio:** solo ruteo, identidad, correlation-id, CORS y log. Nada de agregación (eso sería un BFF, evaluable en E2).
2. **Identidad:** filtro global que exige `X-User-Id` / `X-User-Role` salvo en rutas públicas (`GET /api/events/**`, `GET /api/venues/**`, `GET .../availability`). En E2 este filtro valida el JWT y genera esos mismos headers, y descarta los que vengan del cliente.
3. **Rate limiting:** fuera de alcance E1; el punto de extensión es un `GlobalFilter` (útil para la fila virtual en E2).
4. **Red:** en el `docker-compose` de E2 solo el gateway publica puerto; en E1 los servicios corren localmente pero escuchan en sus puertos y se asume red local.
5. Service discovery no se introduce: las URLs se configuran por propiedades/variables de entorno (en Docker, DNS por nombre de servicio).
