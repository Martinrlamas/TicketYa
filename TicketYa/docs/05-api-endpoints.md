# Listado de endpoints — TicketYa (Entrega 1)

> Mismos datos que `openapi/*.yaml` y la colección Postman (`postman/`); si cambia un contrato, se actualizan los tres.

## Convenciones generales

- **Base pública:** `http://localhost:8080` (API Gateway). Solo existen rutas `/api/**` a través del gateway.
- **Endpoints internos** (`/internal/**`): solo entre servicios, requieren `X-Internal-Token`, **no se rutean por el gateway**.
- **Identidad (E1):** headers `X-User-Id` (UUID) y `X-User-Role` (`CUSTOMER`, `ORGANIZER`, `ADMIN`, `ACCESS_STAFF`). Rol `PUBLIC` = sin identidad. En E2 se reemplaza por JWT sin cambiar los servicios.
- **Trazabilidad:** header opcional `X-Correlation-Id`; siempre se devuelve en la respuesta.
- **IDs:** UUID v4. **Fechas:** ISO-8601 con zona. **Dinero:** decimal con 2 decimales y `currency` (ARS).
- **Paginación:** `page` (base 0), `size` (default 20, máx 100) → `{content, page, size, totalElements, totalPages}`.
- **Errores:** `application/problem+json` con `type, title, status, detail, code, correlationId`.

### Catálogo de códigos de error

| HTTP | code | Cuándo |
|---|---|---|
| 400 | VALIDATION_ERROR | Campo faltante o inválido |
| 400 | IDEMPOTENCY_KEY_REQUIRED | Falta `Idempotency-Key` |
| 401 | UNAUTHENTICATED | Falta identidad o rol inválido |
| 402 | PAYMENT_REJECTED | La pasarela rechazó el pago |
| 403 | FORBIDDEN | Rol insuficiente / token interno inválido |
| 404 | NOT_FOUND | No existe o pertenece a otro usuario |
| 409 | INVALID_STATE_TRANSITION | Estado no permite la operación |
| 409 | EVENT_NOT_PURCHASABLE | Evento no está a la venta |
| 409 | SEAT_UNAVAILABLE | Asiento retenido o vendido |
| 409 | INSUFFICIENT_CAPACITY | La zona no tiene lugares suficientes |
| 410 | HOLD_EXPIRED | La reserva venció |
| 422 | IDEMPOTENCY_KEY_REUSED | Misma clave con distinto contenido |
| 422 | PURCHASE_LIMIT_EXCEEDED | Supera el máximo por cliente y evento |
| 422 | REFUND_WINDOW_CLOSED | Faltan menos de 72 h |
| 503 | UPSTREAM_UNAVAILABLE | Servicio dependiente caído/timeout |

### Tabla de ruteo del gateway

| # | Predicado | Destino |
|---|---|---|
| 1 | `/internal/**` | bloqueado (404) |
| 2 | `GET /api/events/*/availability` | inventory-service |
| 3 | `/api/venues/**`, `/api/events/**` | catalog-service |
| 4 | `/api/orders/**`, `/api/refund-requests/**` | order-service |
| 5 | `/api/tickets/**` | ticket-service |

### Resumen

| Servicio | Puerto | Públicos | Internos |
|---|---|---|---|
| catalog-service | 8081 | 13 | 2 |
| inventory-service | 8082 | 1 | 6 |
| order-service | 8083 | 10 | 1 |
| payment-service | 8084 | 0 | 4 |
| ticket-service | 8085 | 2 | 4 |

---

## catalog-service

Lugares, sectores, eventos, precios, ciclo de vida y búsqueda.

### Endpoints públicos

#### `POST /api/venues` — Crear lugar

- **Rol:** ADMIN, ORGANIZER
- **Descripción:** Crea un lugar (estadio, teatro, sala) sin sectores. Los sectores se agregan luego.

**Request**

```json
{
  "name": "Estadio Municipal",
  "address": "Av. España 1200",
  "city": "Tandil",
  "latitude": -37.3217,
  "longitude": -59.1332
}
```

**Respuestas**

| Código | Significado |
|---|---|
| 201 | Lugar creado. Header Location con la URL del recurso. |
| 400 | Request inválido |
| 401 | Falta identidad o rol inválido |
| 403 | Rol insuficiente o recurso de otro usuario |

Ejemplo 201:

```json
{
  "id": "5a8e1d2c-7b3f-4c9a-a1e6-0d4b7c2f9e10",
  "name": "Estadio Municipal",
  "address": "Av. España 1200",
  "city": "Tandil",
  "latitude": -37.3217,
  "longitude": -59.1332,
  "sectors": []
}
```

#### `GET /api/venues/{venueId}` — Consultar lugar con sectores

- **Rol:** PUBLIC
- **Descripción:** Endpoint público.

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `venueId` | path | uuid | sí | ID del lugar |

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Lugar con sus sectores |
| 404 | Recurso inexistente |

Ejemplo 200:

```json
{
  "id": "5a8e1d2c-7b3f-4c9a-a1e6-0d4b7c2f9e10",
  "name": "Estadio Municipal",
  "address": "Av. España 1200",
  "city": "Tandil",
  "latitude": -37.3217,
  "longitude": -59.1332,
  "sectors": [
    {
      "id": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "name": "Platea",
      "type": "NUMBERED",
      "capacity": 40,
      "rows": [
        {
          "rowLabel": "A",
          "seatCount": 20
        },
        {
          "rowLabel": "B",
          "seatCount": 20
        }
      ]
    },
    {
      "id": "d4e5f6a7-2b3c-4d4e-9f5a-6b7c8d9e0f12",
      "name": "Campo",
      "type": "GENERAL",
      "capacity": 5000,
      "rows": []
    }
  ]
}
```

#### `POST /api/venues/{venueId}/sectors` — Agregar sector a un lugar

- **Rol:** ADMIN, ORGANIZER
- **Descripción:** NUMBERED exige `rows` (≥1 fila con seatCount>0) y NO admite `capacity`. GENERAL exige `capacity>0` y NO admite `rows`.

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `venueId` | path | uuid | sí | ID del lugar |

**Request**

```json
{
  "name": "Platea",
  "type": "NUMBERED",
  "rows": [
    {
      "rowLabel": "A",
      "seatCount": 20
    },
    {
      "rowLabel": "B",
      "seatCount": 20
    }
  ]
}
```

**Respuestas**

| Código | Significado |
|---|---|
| 201 | Sector creado |
| 400 | Request inválido |
| 401 | Falta identidad o rol inválido |
| 403 | Rol insuficiente o recurso de otro usuario |
| 404 | Recurso inexistente |

Ejemplo 201:

```json
{
  "id": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
  "name": "Platea",
  "type": "NUMBERED",
  "capacity": 40,
  "rows": [
    {
      "rowLabel": "A",
      "seatCount": 20
    },
    {
      "rowLabel": "B",
      "seatCount": 20
    }
  ]
}
```

#### `POST /api/events` — Crear evento (borrador)

- **Rol:** ORGANIZER
- **Descripción:** El evento queda en DRAFT y asociado al organizador (X-User-Id). `startsAt` debe ser futura.

**Request**

```json
{
  "name": "Divididos en Tandil",
  "description": "Gira 2027",
  "type": "CONCERT",
  "venueId": "5a8e1d2c-7b3f-4c9a-a1e6-0d4b7c2f9e10",
  "startsAt": "2027-03-20T21:00:00-03:00",
  "participants": [
    {
      "name": "Divididos",
      "role": "ARTIST"
    }
  ]
}
```

**Respuestas**

| Código | Significado |
|---|---|
| 201 | Evento creado en DRAFT |
| 400 | Request inválido |
| 401 | Falta identidad o rol inválido |
| 403 | Rol insuficiente o recurso de otro usuario |
| 404 | Recurso inexistente |

Ejemplo 201:

```json
{
  "id": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
  "name": "Divididos en Tandil",
  "description": "Gira 2027",
  "type": "CONCERT",
  "organizerId": "3d4e5f60-8192-43a4-b5b0-2b3c4d5e6f78",
  "venueId": "5a8e1d2c-7b3f-4c9a-a1e6-0d4b7c2f9e10",
  "city": "Tandil",
  "startsAt": "2027-03-20T21:00:00-03:00",
  "status": "DRAFT",
  "participants": [
    {
      "name": "Divididos",
      "role": "ARTIST"
    }
  ],
  "prices": [],
  "minPrice": null,
  "createdAt": "2026-11-07T15:30:00-03:00"
}
```

#### `GET /api/events/mine` — Listar mis eventos (incluye borradores)

- **Rol:** ORGANIZER

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `status` | query | string | no | DRAFT|PUBLISHED|SOLD_OUT|SUSPENDED|CANCELLED|FINISHED |
| `page` | query | int | no | Base 0 (default 0) |
| `size` | query | int | no | Default 20, máx 100 |

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Página de eventos del organizador |
| 401 | Falta identidad o rol inválido |
| 403 | Rol insuficiente o recurso de otro usuario |

Ejemplo 200:

```json
{
  "content": [
    {
      "id": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
      "name": "Divididos en Tandil",
      "description": "Gira 2027",
      "type": "CONCERT",
      "organizerId": "3d4e5f60-8192-43a4-b5b0-2b3c4d5e6f78",
      "venueId": "5a8e1d2c-7b3f-4c9a-a1e6-0d4b7c2f9e10",
      "city": "Tandil",
      "startsAt": "2027-03-20T21:00:00-03:00",
      "status": "DRAFT",
      "participants": [
        {
          "name": "Divididos",
          "role": "ARTIST"
        }
      ],
      "prices": [],
      "minPrice": null,
      "createdAt": "2026-11-07T15:30:00-03:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

#### `GET /api/events/{eventId}` — Detalle de evento

- **Rol:** PUBLIC
- **Descripción:** Eventos PUBLISHED/SOLD_OUT son públicos. Los demás estados solo los ve el organizador dueño o un ADMIN; para el resto responde 404. No incluye disponibilidad.

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `eventId` | path | uuid | sí | ID del evento |

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Detalle del evento con precios por sector |
| 404 | Recurso inexistente |

Ejemplo 200:

```json
{
  "id": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
  "name": "Divididos en Tandil",
  "description": "Gira 2027",
  "type": "CONCERT",
  "organizerId": "3d4e5f60-8192-43a4-b5b0-2b3c4d5e6f78",
  "venueId": "5a8e1d2c-7b3f-4c9a-a1e6-0d4b7c2f9e10",
  "city": "Tandil",
  "startsAt": "2027-03-20T21:00:00-03:00",
  "status": "PUBLISHED",
  "participants": [
    {
      "name": "Divididos",
      "role": "ARTIST"
    }
  ],
  "prices": [
    {
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "sectorName": "Platea",
      "price": 45000.0,
      "currency": "ARS"
    },
    {
      "sectorId": "d4e5f6a7-2b3c-4d4e-9f5a-6b7c8d9e0f12",
      "sectorName": "Campo",
      "price": 30000.0,
      "currency": "ARS"
    }
  ],
  "minPrice": 30000.0,
  "createdAt": "2026-11-07T15:30:00-03:00"
}
```

#### `PUT /api/events/{eventId}` — Modificar evento en borrador

- **Rol:** ORGANIZER
- **Descripción:** Solo el dueño y solo en DRAFT.

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `eventId` | path | uuid | sí | ID del evento |

**Request**

```json
{
  "name": "Divididos en Tandil - Fecha 1",
  "description": "Gira 2027",
  "type": "CONCERT",
  "venueId": "5a8e1d2c-7b3f-4c9a-a1e6-0d4b7c2f9e10",
  "startsAt": "2027-03-20T21:00:00-03:00",
  "participants": [
    {
      "name": "Divididos",
      "role": "ARTIST"
    }
  ]
}
```

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Evento actualizado |
| 400 | Request inválido |
| 401 | Falta identidad o rol inválido |
| 403 | Rol insuficiente o recurso de otro usuario |
| 404 | Recurso inexistente |
| 409 | Transición de estado inválida |

Ejemplo 200:

```json
{
  "id": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
  "name": "Divididos en Tandil",
  "description": "Gira 2027",
  "type": "CONCERT",
  "organizerId": "3d4e5f60-8192-43a4-b5b0-2b3c4d5e6f78",
  "venueId": "5a8e1d2c-7b3f-4c9a-a1e6-0d4b7c2f9e10",
  "city": "Tandil",
  "startsAt": "2027-03-20T21:00:00-03:00",
  "status": "DRAFT",
  "participants": [
    {
      "name": "Divididos",
      "role": "ARTIST"
    }
  ],
  "prices": [],
  "minPrice": null,
  "createdAt": "2026-11-07T15:30:00-03:00"
}
```

Ejemplo de error 409:

```json
{
  "type": "https://ticketya.example/errors/invalid-state-transition",
  "title": "Estado inválido",
  "status": 409,
  "detail": "La operación no es válida en el estado actual",
  "code": "INVALID_STATE_TRANSITION",
  "correlationId": "b7d2c9e4-1f3a-4a5b-8c6d-7e8f9a0b1c2d"
}
```

#### `PUT /api/events/{eventId}/sector-prices` — Definir precios por sector

- **Rol:** ORGANIZER
- **Descripción:** Reemplaza el conjunto de precios. Solo DRAFT y solo el dueño. Precios > 0 y sectores del lugar del evento.

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `eventId` | path | uuid | sí | ID del evento |

**Request**

```json
{
  "prices": [
    {
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "price": 45000.0,
      "currency": "ARS"
    },
    {
      "sectorId": "d4e5f6a7-2b3c-4d4e-9f5a-6b7c8d9e0f12",
      "price": 30000.0,
      "currency": "ARS"
    }
  ]
}
```

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Precios guardados |
| 400 | Request inválido |
| 401 | Falta identidad o rol inválido |
| 403 | Rol insuficiente o recurso de otro usuario |
| 404 | Recurso inexistente |
| 409 | Transición de estado inválida |

Ejemplo 200:

```json
{
  "id": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
  "name": "Divididos en Tandil",
  "description": "Gira 2027",
  "type": "CONCERT",
  "organizerId": "3d4e5f60-8192-43a4-b5b0-2b3c4d5e6f78",
  "venueId": "5a8e1d2c-7b3f-4c9a-a1e6-0d4b7c2f9e10",
  "city": "Tandil",
  "startsAt": "2027-03-20T21:00:00-03:00",
  "status": "PUBLISHED",
  "participants": [
    {
      "name": "Divididos",
      "role": "ARTIST"
    }
  ],
  "prices": [
    {
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "sectorName": "Platea",
      "price": 45000.0,
      "currency": "ARS"
    },
    {
      "sectorId": "d4e5f6a7-2b3c-4d4e-9f5a-6b7c8d9e0f12",
      "sectorName": "Campo",
      "price": 30000.0,
      "currency": "ARS"
    }
  ],
  "minPrice": 30000.0,
  "createdAt": "2026-11-07T15:30:00-03:00"
}
```

Ejemplo de error 409:

```json
{
  "type": "https://ticketya.example/errors/invalid-state-transition",
  "title": "Estado inválido",
  "status": 409,
  "detail": "La operación no es válida en el estado actual",
  "code": "INVALID_STATE_TRANSITION",
  "correlationId": "b7d2c9e4-1f3a-4a5b-8c6d-7e8f9a0b1c2d"
}
```

#### `POST /api/events/{eventId}/publish` — Publicar evento

- **Rol:** ORGANIZER
- **Descripción:** Requiere ≥1 sector con precio y fecha futura. Inicializa el inventario en inventory-service antes de pasar a PUBLISHED.

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `eventId` | path | uuid | sí | ID del evento |

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Evento publicado |
| 401 | Falta identidad o rol inválido |
| 403 | Rol insuficiente o recurso de otro usuario |
| 404 | Recurso inexistente |
| 409 | Sin precios o estado inválido |
| 503 | Servicio dependiente no disponible |

Ejemplo 200:

```json
{
  "id": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
  "name": "Divididos en Tandil",
  "description": "Gira 2027",
  "type": "CONCERT",
  "organizerId": "3d4e5f60-8192-43a4-b5b0-2b3c4d5e6f78",
  "venueId": "5a8e1d2c-7b3f-4c9a-a1e6-0d4b7c2f9e10",
  "city": "Tandil",
  "startsAt": "2027-03-20T21:00:00-03:00",
  "status": "PUBLISHED",
  "participants": [
    {
      "name": "Divididos",
      "role": "ARTIST"
    }
  ],
  "prices": [
    {
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "sectorName": "Platea",
      "price": 45000.0,
      "currency": "ARS"
    },
    {
      "sectorId": "d4e5f6a7-2b3c-4d4e-9f5a-6b7c8d9e0f12",
      "sectorName": "Campo",
      "price": 30000.0,
      "currency": "ARS"
    }
  ],
  "minPrice": 30000.0,
  "createdAt": "2026-11-07T15:30:00-03:00"
}
```

Ejemplo de error 409:

```json
{
  "type": "https://ticketya.example/errors/invalid-state-transition",
  "title": "Estado inválido",
  "status": 409,
  "detail": "El evento no tiene sectores con precio",
  "code": "INVALID_STATE_TRANSITION",
  "correlationId": "b7d2c9e4-1f3a-4a5b-8c6d-7e8f9a0b1c2d"
}
```

#### `POST /api/events/{eventId}/suspend` — Suspender evento

- **Rol:** ADMIN
- **Descripción:** Suspende un evento PUBLISHED o SOLD_OUT.

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `eventId` | path | uuid | sí | ID del evento |

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Evento actualizado |
| 401 | Falta identidad o rol inválido |
| 403 | Rol insuficiente o recurso de otro usuario |
| 404 | Recurso inexistente |
| 409 | Transición de estado inválida |

Ejemplo 200:

```json
{
  "id": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
  "name": "Divididos en Tandil",
  "description": "Gira 2027",
  "type": "CONCERT",
  "organizerId": "3d4e5f60-8192-43a4-b5b0-2b3c4d5e6f78",
  "venueId": "5a8e1d2c-7b3f-4c9a-a1e6-0d4b7c2f9e10",
  "city": "Tandil",
  "startsAt": "2027-03-20T21:00:00-03:00",
  "status": "SUSPENDED",
  "participants": [
    {
      "name": "Divididos",
      "role": "ARTIST"
    }
  ],
  "prices": [
    {
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "sectorName": "Platea",
      "price": 45000.0,
      "currency": "ARS"
    },
    {
      "sectorId": "d4e5f6a7-2b3c-4d4e-9f5a-6b7c8d9e0f12",
      "sectorName": "Campo",
      "price": 30000.0,
      "currency": "ARS"
    }
  ],
  "minPrice": 30000.0,
  "createdAt": "2026-11-07T15:30:00-03:00"
}
```

Ejemplo de error 409:

```json
{
  "type": "https://ticketya.example/errors/invalid-state-transition",
  "title": "Estado inválido",
  "status": 409,
  "detail": "La operación no es válida en el estado actual",
  "code": "INVALID_STATE_TRANSITION",
  "correlationId": "b7d2c9e4-1f3a-4a5b-8c6d-7e8f9a0b1c2d"
}
```

#### `POST /api/events/{eventId}/resume` — Reactivar evento

- **Rol:** ADMIN
- **Descripción:** Reactiva un evento SUSPENDED.

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `eventId` | path | uuid | sí | ID del evento |

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Evento actualizado |
| 401 | Falta identidad o rol inválido |
| 403 | Rol insuficiente o recurso de otro usuario |
| 404 | Recurso inexistente |
| 409 | Transición de estado inválida |

Ejemplo 200:

```json
{
  "id": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
  "name": "Divididos en Tandil",
  "description": "Gira 2027",
  "type": "CONCERT",
  "organizerId": "3d4e5f60-8192-43a4-b5b0-2b3c4d5e6f78",
  "venueId": "5a8e1d2c-7b3f-4c9a-a1e6-0d4b7c2f9e10",
  "city": "Tandil",
  "startsAt": "2027-03-20T21:00:00-03:00",
  "status": "PUBLISHED",
  "participants": [
    {
      "name": "Divididos",
      "role": "ARTIST"
    }
  ],
  "prices": [
    {
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "sectorName": "Platea",
      "price": 45000.0,
      "currency": "ARS"
    },
    {
      "sectorId": "d4e5f6a7-2b3c-4d4e-9f5a-6b7c8d9e0f12",
      "sectorName": "Campo",
      "price": 30000.0,
      "currency": "ARS"
    }
  ],
  "minPrice": 30000.0,
  "createdAt": "2026-11-07T15:30:00-03:00"
}
```

Ejemplo de error 409:

```json
{
  "type": "https://ticketya.example/errors/invalid-state-transition",
  "title": "Estado inválido",
  "status": 409,
  "detail": "La operación no es válida en el estado actual",
  "code": "INVALID_STATE_TRANSITION",
  "correlationId": "b7d2c9e4-1f3a-4a5b-8c6d-7e8f9a0b1c2d"
}
```

#### `POST /api/events/{eventId}/cancel` — Cancelar evento

- **Rol:** ADMIN
- **Descripción:** Cancela un evento (DRAFT, PUBLISHED, SOLD_OUT o SUSPENDED) y dispara los reintegros en order-service.

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `eventId` | path | uuid | sí | ID del evento |

**Request**

```json
{
  "reason": "Motivo de la cancelación (opcional)"
}
```

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Evento actualizado |
| 401 | Falta identidad o rol inválido |
| 403 | Rol insuficiente o recurso de otro usuario |
| 404 | Recurso inexistente |
| 409 | Transición de estado inválida |

Ejemplo 200:

```json
{
  "id": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
  "name": "Divididos en Tandil",
  "description": "Gira 2027",
  "type": "CONCERT",
  "organizerId": "3d4e5f60-8192-43a4-b5b0-2b3c4d5e6f78",
  "venueId": "5a8e1d2c-7b3f-4c9a-a1e6-0d4b7c2f9e10",
  "city": "Tandil",
  "startsAt": "2027-03-20T21:00:00-03:00",
  "status": "CANCELLED",
  "participants": [
    {
      "name": "Divididos",
      "role": "ARTIST"
    }
  ],
  "prices": [
    {
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "sectorName": "Platea",
      "price": 45000.0,
      "currency": "ARS"
    },
    {
      "sectorId": "d4e5f6a7-2b3c-4d4e-9f5a-6b7c8d9e0f12",
      "sectorName": "Campo",
      "price": 30000.0,
      "currency": "ARS"
    }
  ],
  "minPrice": 30000.0,
  "createdAt": "2026-11-07T15:30:00-03:00"
}
```

Ejemplo de error 409:

```json
{
  "type": "https://ticketya.example/errors/invalid-state-transition",
  "title": "Estado inválido",
  "status": 409,
  "detail": "La operación no es válida en el estado actual",
  "code": "INVALID_STATE_TRANSITION",
  "correlationId": "b7d2c9e4-1f3a-4a5b-8c6d-7e8f9a0b1c2d"
}
```

#### `GET /api/events` — Buscar eventos

- **Rol:** PUBLIC
- **Descripción:** Búsqueda multicriterio (AND). Solo devuelve PUBLISHED y SOLD_OUT. `sort=relevance` usa `q`; sin `q` equivale a fecha ascendente.

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `type` | query | string | no | CONCERT|THEATER|SPORT|CONFERENCE|FESTIVAL|OTHER |
| `organizerId` | query | uuid | no | ID del organizador |
| `participant` | query | string | no | Nombre del participante (sin distinguir mayúsculas/acentos) |
| `city` | query | string | no | Ciudad del lugar |
| `dateFrom` | query | date | no | Fecha mínima inclusive (yyyy-MM-dd) |
| `dateTo` | query | date | no | Fecha máxima inclusive (yyyy-MM-dd) |
| `q` | query | string | no | Texto libre (nombre, participantes, descripción) |
| `sort` | query | string | no | date | price | relevance (default date) |
| `order` | query | string | no | asc | desc (default asc) |
| `page` | query | int | no | Base 0 |
| `size` | query | int | no | Default 20, máx 100 |

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Página de resultados |
| 400 | Request inválido |

Ejemplo 200:

```json
{
  "content": [
    {
      "id": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
      "name": "Divididos en Tandil",
      "description": "Gira 2027",
      "type": "CONCERT",
      "organizerId": "3d4e5f60-8192-43a4-b5b0-2b3c4d5e6f78",
      "venueId": "5a8e1d2c-7b3f-4c9a-a1e6-0d4b7c2f9e10",
      "city": "Tandil",
      "startsAt": "2027-03-20T21:00:00-03:00",
      "status": "PUBLISHED",
      "participants": [
        {
          "name": "Divididos",
          "role": "ARTIST"
        }
      ],
      "prices": [
        {
          "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
          "sectorName": "Platea",
          "price": 45000.0,
          "currency": "ARS"
        },
        {
          "sectorId": "d4e5f6a7-2b3c-4d4e-9f5a-6b7c8d9e0f12",
          "sectorName": "Campo",
          "price": 30000.0,
          "currency": "ARS"
        }
      ],
      "minPrice": 30000.0,
      "createdAt": "2026-11-07T15:30:00-03:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### Endpoints internos

#### `GET /internal/events/{eventId}/sale-info` — Información de venta (para order-service)

- **Rol:** INTERNAL  (header `X-Internal-Token`)
- **Descripción:** Devuelve si el evento es comprable y los precios/tipos por sector.

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `eventId` | path | uuid | sí | ID del evento |

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Información de venta |
| 403 | Falta o es inválido X-Internal-Token |
| 404 | Recurso inexistente |

Ejemplo 200:

```json
{
  "eventId": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
  "status": "PUBLISHED",
  "purchasable": true,
  "startsAt": "2027-03-20T21:00:00-03:00",
  "organizerId": "3d4e5f60-8192-43a4-b5b0-2b3c4d5e6f78",
  "sectors": [
    {
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "name": "Platea",
      "type": "NUMBERED",
      "price": 45000.0,
      "currency": "ARS"
    },
    {
      "sectorId": "d4e5f6a7-2b3c-4d4e-9f5a-6b7c8d9e0f12",
      "name": "Campo",
      "type": "GENERAL",
      "price": 30000.0,
      "currency": "ARS"
    }
  ]
}
```

#### `PUT /internal/events/{eventId}/status` — Actualizar estado SOLD_OUT/PUBLISHED (desde inventory)

- **Rol:** INTERNAL  (header `X-Internal-Token`)
- **Descripción:** Solo admite SOLD_OUT ↔ PUBLISHED.

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `eventId` | path | uuid | sí | ID del evento |

**Request**

```json
{
  "status": "SOLD_OUT"
}
```

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Estado actualizado |
| 403 | Falta o es inválido X-Internal-Token |
| 404 | Recurso inexistente |
| 409 | Transición de estado inválida |

Ejemplo 200:

```json
{
  "eventId": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
  "status": "SOLD_OUT"
}
```

Ejemplo de error 409:

```json
{
  "type": "https://ticketya.example/errors/invalid-state-transition",
  "title": "Estado inválido",
  "status": 409,
  "detail": "La operación no es válida en el estado actual",
  "code": "INVALID_STATE_TRANSITION",
  "correlationId": "b7d2c9e4-1f3a-4a5b-8c6d-7e8f9a0b1c2d"
}
```

---

## inventory-service

Disponibilidad, holds con TTL y garantía de no doble venta.

### Endpoints públicos

#### `GET /api/events/{eventId}/availability` — Disponibilidad actual del evento

- **Rol:** PUBLIC
- **Descripción:** Nunca se cachea: responde `Cache-Control: no-store`. Estados de asiento: AVAILABLE, HELD, SOLD.

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `eventId` | path | uuid | sí | ID del evento |

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Disponibilidad por sector |
| 404 | Recurso inexistente |

Ejemplo 200:

```json
{
  "eventId": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
  "generatedAt": "2026-11-07T15:30:00-03:00",
  "sectors": [
    {
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "name": "Platea",
      "type": "NUMBERED",
      "capacity": 40,
      "available": 38,
      "seats": [
        {
          "seatId": "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a23",
          "rowLabel": "A",
          "number": 5,
          "status": "AVAILABLE"
        },
        {
          "seatId": "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a24",
          "rowLabel": "A",
          "number": 6,
          "status": "HELD"
        }
      ]
    },
    {
      "sectorId": "d4e5f6a7-2b3c-4d4e-9f5a-6b7c8d9e0f12",
      "name": "Campo",
      "type": "GENERAL",
      "capacity": 5000,
      "available": 4960
    }
  ]
}
```

### Endpoints internos

#### `POST /internal/events/{eventId}/inventory` — Inicializar inventario (idempotente)

- **Rol:** INTERNAL  (header `X-Internal-Token`)
- **Descripción:** Llamado por catalog al publicar. Reinvocar no duplica asientos.

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `eventId` | path | uuid | sí | ID del evento |

**Request**

```json
{
  "sectors": [
    {
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "type": "NUMBERED",
      "rows": [
        {
          "rowLabel": "A",
          "seatCount": 20
        },
        {
          "rowLabel": "B",
          "seatCount": 20
        }
      ]
    },
    {
      "sectorId": "d4e5f6a7-2b3c-4d4e-9f5a-6b7c8d9e0f12",
      "type": "GENERAL",
      "capacity": 5000
    }
  ]
}
```

**Respuestas**

| Código | Significado |
|---|---|
| 201 | Inventario creado |
| 200 | Ya estaba inicializado |
| 400 | Request inválido |
| 403 | Falta o es inválido X-Internal-Token |

Ejemplo 201:

```json
{
  "eventId": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
  "seatsCreated": 40,
  "zonesCreated": 1
}
```

#### `POST /internal/events/{eventId}/close` — Cerrar inventario (no admite nuevos holds)

- **Rol:** INTERNAL  (header `X-Internal-Token`)

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `eventId` | path | uuid | sí | ID del evento |

**Respuestas**

| Código | Significado |
|---|---|
| 204 | Inventario cerrado (idempotente) |
| 403 | Falta o es inválido X-Internal-Token |
| 404 | Recurso inexistente |

#### `POST /internal/holds` — Crear hold (reserva temporal, todo-o-nada, idempotente por orderId)

- **Rol:** INTERNAL  (header `X-Internal-Token`)
- **Descripción:** Numerado: `seatIds`. General: `quantity`. Un mismo orderId devuelve el hold existente (200).

**Request**

```json
{
  "eventId": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
  "orderId": "f6a7b8c9-4d5e-4f60-b17c-8d9e0f1a2b34",
  "ttlSeconds": 600,
  "items": [
    {
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "seatIds": [
        "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a23",
        "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a24"
      ]
    },
    {
      "sectorId": "d4e5f6a7-2b3c-4d4e-9f5a-6b7c8d9e0f12",
      "quantity": 2
    }
  ]
}
```

**Respuestas**

| Código | Significado |
|---|---|
| 201 | Hold creado. `seats` informa la etiqueta de cada asiento numerado retenido. |
| 200 | Hold ya existente para esa orden |
| 400 | Request inválido |
| 409 | Asiento ocupado / capacidad insuficiente / evento cerrado |
| 403 | Falta o es inválido X-Internal-Token |
| 404 | Recurso inexistente |

Ejemplo 201:

```json
{
  "holdId": "0a1b2c3d-5e6f-4071-828d-9e0f1a2b3c45",
  "orderId": "f6a7b8c9-4d5e-4f60-b17c-8d9e0f1a2b34",
  "status": "ACTIVE",
  "expiresAt": "2026-11-07T15:40:00-03:00",
  "seats": [
    {
      "seatId": "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a23",
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "label": "A-5"
    },
    {
      "seatId": "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a24",
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "label": "A-6"
    }
  ]
}
```

Ejemplo de error 409:

```json
{
  "type": "https://ticketya.example/errors/seat-unavailable",
  "title": "Asiento no disponible",
  "status": 409,
  "detail": "El asiento A-5 ya no está disponible",
  "code": "SEAT_UNAVAILABLE",
  "correlationId": "b7d2c9e4-1f3a-4a5b-8c6d-7e8f9a0b1c2d"
}
```

#### `POST /internal/holds/{holdId}/confirm` — Confirmar venta del hold (idempotente)

- **Rol:** INTERNAL  (header `X-Internal-Token`)

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `holdId` | path | uuid | sí | ID del hold |

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Venta confirmada (también si ya estaba confirmado) |
| 410 | Hold vencido: los lugares se liberan |
| 403 | Falta o es inválido X-Internal-Token |
| 404 | Recurso inexistente |

Ejemplo 200:

```json
{
  "holdId": "0a1b2c3d-5e6f-4071-828d-9e0f1a2b3c45",
  "status": "CONFIRMED"
}
```

Ejemplo de error 410:

```json
{
  "type": "https://ticketya.example/errors/hold-expired",
  "title": "Reserva vencida",
  "status": 410,
  "detail": "El hold venció a las 15:40",
  "code": "HOLD_EXPIRED",
  "correlationId": "b7d2c9e4-1f3a-4a5b-8c6d-7e8f9a0b1c2d"
}
```

#### `DELETE /internal/holds/{holdId}` — Liberar hold (idempotente)

- **Rol:** INTERNAL  (header `X-Internal-Token`)

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `holdId` | path | uuid | sí | ID del hold |

**Respuestas**

| Código | Significado |
|---|---|
| 204 | Liberado (o ya estaba liberado/expirado) |
| 403 | Falta o es inválido X-Internal-Token |
| 404 | Recurso inexistente |

#### `POST /internal/events/{eventId}/sold/release` — Devolver lugares vendidos al stock (idempotente por releaseId)

- **Rol:** INTERNAL  (header `X-Internal-Token`)

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `eventId` | path | uuid | sí | ID del evento |

**Request**

```json
{
  "releaseId": "5f607182-a3b4-45c6-97d2-4d5e6f708190",
  "items": [
    {
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "seatIds": [
        "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a23"
      ]
    },
    {
      "sectorId": "d4e5f6a7-2b3c-4d4e-9f5a-6b7c8d9e0f12",
      "quantity": 1
    }
  ]
}
```

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Lugares liberados (o liberación ya aplicada) |
| 400 | Request inválido |
| 403 | Falta o es inválido X-Internal-Token |
| 404 | Recurso inexistente |

Ejemplo 200:

```json
{
  "releaseId": "5f607182-a3b4-45c6-97d2-4d5e6f708190",
  "released": 2
}
```

---

## order-service

Saga de compra, límite por cliente, idempotencia y devoluciones.

### Endpoints públicos

#### `POST /api/orders` — Crear orden y reservar lugares (paso 1 de la compra)

- **Rol:** CUSTOMER
- **Descripción:** Valida el evento (catalog), consume cuota por cliente (máx. 6 por evento, configurable), crea el hold en inventory y deja la orden en PENDING_PAYMENT. Numerado: `seatIds`. General: `quantity` (se generan N ítems). Idempotente por `Idempotency-Key`: mismo cuerpo → misma respuesta (200); otro cuerpo → 422.

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `Idempotency-Key` | header | string | sí | Clave única generada por el cliente (UUID). Un reintento con la misma clave devuelve el resultado original. |

**Request**

```json
{
  "eventId": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
  "items": [
    {
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "seatIds": [
        "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a23",
        "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a24"
      ]
    }
  ]
}
```

**Respuestas**

| Código | Significado |
|---|---|
| 201 | Orden creada con reserva vigente |
| 200 | Reintento idempotente: devuelve la orden original |
| 400 | Falta Idempotency-Key o request inválido |
| 401 | Falta identidad o rol inválido |
| 403 | Rol insuficiente o recurso de otro usuario |
| 404 | Evento inexistente |
| 409 | Asiento/capacidad no disponible o evento no comprable |
| 422 | Límite por cliente superado o clave reutilizada con otro cuerpo |
| 503 | Servicio dependiente no disponible |

Ejemplo 201:

```json
{
  "orderId": "f6a7b8c9-4d5e-4f60-b17c-8d9e0f1a2b34",
  "customerId": "2c3d4e5f-7081-4293-a4af-1a2b3c4d5e67",
  "eventId": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
  "eventStartsAt": "2027-03-20T21:00:00-03:00",
  "status": "PENDING_PAYMENT",
  "items": [
    {
      "itemId": "a7b8c9d0-5e6f-4071-828d-9e0f1a2b3c01",
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "seatId": "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a23",
      "seatLabel": "Platea A-5",
      "unitPrice": 45000.0,
      "currency": "ARS",
      "status": "ACTIVE"
    },
    {
      "itemId": "a7b8c9d0-5e6f-4071-828d-9e0f1a2b3c02",
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "seatId": "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a24",
      "seatLabel": "Platea A-6",
      "unitPrice": 45000.0,
      "currency": "ARS",
      "status": "ACTIVE"
    }
  ],
  "total": 90000.0,
  "currency": "ARS",
  "holdExpiresAt": "2026-11-07T15:40:00-03:00",
  "createdAt": "2026-11-07T15:30:00-03:00"
}
```

Ejemplo de error 409:

```json
{
  "type": "https://ticketya.example/errors/seat-unavailable",
  "title": "Asiento no disponible",
  "status": 409,
  "detail": "El asiento A-5 ya no está disponible",
  "code": "SEAT_UNAVAILABLE",
  "correlationId": "b7d2c9e4-1f3a-4a5b-8c6d-7e8f9a0b1c2d"
}
```

#### `POST /api/orders/{orderId}/pay` — Pagar la orden (paso 2 de la compra)

- **Rol:** CUSTOMER
- **Descripción:** Cobra en payment-service, confirma el hold en inventory y emite las entradas en ticket-service. Token de prueba (mock): `tok_approved`, `tok_rejected`, `tok_timeout`, `tok_flaky`. Si el pago se aprueba pero el hold ya venció, se reintegra automáticamente y se responde 410.

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `orderId` | path | uuid | sí | ID de la orden |
| `Idempotency-Key` | header | string | sí | Clave única generada por el cliente (UUID). Un reintento con la misma clave devuelve el resultado original. |

**Request**

```json
{
  "method": "CARD",
  "token": "tok_approved",
  "last4": "4242"
}
```

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Orden CONFIRMED con entradas y QR (también en reintento idempotente) |
| 402 | Pago rechazado: hold y cuota liberados, orden PAYMENT_REJECTED |
| 401 | Falta identidad o rol inválido |
| 403 | Rol insuficiente o recurso de otro usuario |
| 404 | Recurso inexistente |
| 409 | La orden no está en PENDING_PAYMENT |
| 410 | Reserva vencida (orden EXPIRED, o FAILED_REFUNDED si se había aprobado el pago) |
| 503 | Servicio dependiente no disponible |

Ejemplo 200:

```json
{
  "orderId": "f6a7b8c9-4d5e-4f60-b17c-8d9e0f1a2b34",
  "customerId": "2c3d4e5f-7081-4293-a4af-1a2b3c4d5e67",
  "eventId": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
  "eventStartsAt": "2027-03-20T21:00:00-03:00",
  "status": "CONFIRMED",
  "items": [
    {
      "itemId": "a7b8c9d0-5e6f-4071-828d-9e0f1a2b3c01",
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "seatId": "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a23",
      "seatLabel": "Platea A-5",
      "unitPrice": 45000.0,
      "currency": "ARS",
      "status": "ACTIVE"
    },
    {
      "itemId": "a7b8c9d0-5e6f-4071-828d-9e0f1a2b3c02",
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "seatId": "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a24",
      "seatLabel": "Platea A-6",
      "unitPrice": 45000.0,
      "currency": "ARS",
      "status": "ACTIVE"
    }
  ],
  "total": 90000.0,
  "currency": "ARS",
  "holdExpiresAt": null,
  "createdAt": "2026-11-07T15:30:00-03:00",
  "paymentId": "1b2c3d4e-6f70-4182-939e-0f1a2b3c4d56",
  "payment": {
    "method": "CARD",
    "last4": "4242",
    "status": "APPROVED"
  },
  "tickets": [
    {
      "ticketId": "6a708192-b3c4-46d7-a8e3-5e6f70819201",
      "orderItemId": "a7b8c9d0-5e6f-4071-828d-9e0f1a2b3c01",
      "eventId": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "seatId": "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a23",
      "seatLabel": "Platea A-5",
      "qrCode": "q7Yk2mC0v3Xn8Lr1Tz5AeHb9PwUd4JfGs6OiVhNxRcM",
      "status": "VALID",
      "issuedAt": "2026-11-07T15:30:00-03:00"
    },
    {
      "ticketId": "6a708192-b3c4-46d7-a8e3-5e6f70819202",
      "orderItemId": "a7b8c9d0-5e6f-4071-828d-9e0f1a2b3c02",
      "eventId": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "seatId": "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a24",
      "seatLabel": "Platea A-6",
      "qrCode": "Zb3Rt9KpLw1Qe7NvYx0HcUj5AiOs2DmFgT8ErBnCy4V",
      "status": "VALID",
      "issuedAt": "2026-11-07T15:30:00-03:00"
    }
  ]
}
```

Ejemplo de error 402:

```json
{
  "type": "https://ticketya.example/errors/payment-rejected",
  "title": "Pago rechazado",
  "status": 402,
  "detail": "La pasarela rechazó el pago",
  "code": "PAYMENT_REJECTED",
  "correlationId": "b7d2c9e4-1f3a-4a5b-8c6d-7e8f9a0b1c2d"
}
```

#### `DELETE /api/orders/{orderId}` — Cancelar orden pendiente

- **Rol:** CUSTOMER
- **Descripción:** Solo PENDING_PAYMENT. Libera hold y cuota.

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `orderId` | path | uuid | sí | ID de la orden |

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Orden CANCELLED |
| 401 | Falta identidad o rol inválido |
| 403 | Rol insuficiente o recurso de otro usuario |
| 404 | Recurso inexistente |
| 409 | Transición de estado inválida |

Ejemplo 200:

```json
{
  "orderId": "f6a7b8c9-4d5e-4f60-b17c-8d9e0f1a2b34",
  "customerId": "2c3d4e5f-7081-4293-a4af-1a2b3c4d5e67",
  "eventId": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
  "eventStartsAt": "2027-03-20T21:00:00-03:00",
  "status": "CANCELLED",
  "items": [
    {
      "itemId": "a7b8c9d0-5e6f-4071-828d-9e0f1a2b3c01",
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "seatId": "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a23",
      "seatLabel": "Platea A-5",
      "unitPrice": 45000.0,
      "currency": "ARS",
      "status": "ACTIVE"
    },
    {
      "itemId": "a7b8c9d0-5e6f-4071-828d-9e0f1a2b3c02",
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "seatId": "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a24",
      "seatLabel": "Platea A-6",
      "unitPrice": 45000.0,
      "currency": "ARS",
      "status": "ACTIVE"
    }
  ],
  "total": 90000.0,
  "currency": "ARS",
  "holdExpiresAt": "2026-11-07T15:40:00-03:00",
  "createdAt": "2026-11-07T15:30:00-03:00"
}
```

Ejemplo de error 409:

```json
{
  "type": "https://ticketya.example/errors/invalid-state-transition",
  "title": "Estado inválido",
  "status": 409,
  "detail": "La operación no es válida en el estado actual",
  "code": "INVALID_STATE_TRANSITION",
  "correlationId": "b7d2c9e4-1f3a-4a5b-8c6d-7e8f9a0b1c2d"
}
```

#### `GET /api/orders` — Listar mis órdenes

- **Rol:** CUSTOMER

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `status` | query | string | no | Filtra por estado |
| `page` | query | int | no | Base 0 |
| `size` | query | int | no | Default 20, máx 100 |

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Página de órdenes del cliente (sin entradas; el QR está en el detalle) |
| 401 | Falta identidad o rol inválido |
| 403 | Rol insuficiente o recurso de otro usuario |

Ejemplo 200:

```json
{
  "content": [
    {
      "orderId": "f6a7b8c9-4d5e-4f60-b17c-8d9e0f1a2b34",
      "customerId": "2c3d4e5f-7081-4293-a4af-1a2b3c4d5e67",
      "eventId": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
      "eventStartsAt": "2027-03-20T21:00:00-03:00",
      "status": "CONFIRMED",
      "items": [
        {
          "itemId": "a7b8c9d0-5e6f-4071-828d-9e0f1a2b3c01",
          "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
          "seatId": "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a23",
          "seatLabel": "Platea A-5",
          "unitPrice": 45000.0,
          "currency": "ARS",
          "status": "ACTIVE"
        },
        {
          "itemId": "a7b8c9d0-5e6f-4071-828d-9e0f1a2b3c02",
          "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
          "seatId": "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a24",
          "seatLabel": "Platea A-6",
          "unitPrice": 45000.0,
          "currency": "ARS",
          "status": "ACTIVE"
        }
      ],
      "total": 90000.0,
      "currency": "ARS",
      "holdExpiresAt": null,
      "createdAt": "2026-11-07T15:30:00-03:00",
      "paymentId": "1b2c3d4e-6f70-4182-939e-0f1a2b3c4d56",
      "payment": {
        "method": "CARD",
        "last4": "4242",
        "status": "APPROVED"
      }
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

#### `GET /api/orders/{orderId}` — Detalle de una orden

- **Rol:** CUSTOMER, ADMIN
- **Descripción:** El CUSTOMER solo ve sus órdenes (404 si es de otro); un ADMIN ve cualquiera. Incluye ítems con precio snapshot, medio de pago y, si está CONFIRMED, las entradas con QR (si ticket-service no responde, `tickets` es null).

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `orderId` | path | uuid | sí | ID de la orden |

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Detalle de la orden |
| 401 | Falta identidad o rol inválido |
| 403 | Rol insuficiente o recurso de otro usuario |
| 404 | Recurso inexistente |

Ejemplo 200:

```json
{
  "orderId": "f6a7b8c9-4d5e-4f60-b17c-8d9e0f1a2b34",
  "customerId": "2c3d4e5f-7081-4293-a4af-1a2b3c4d5e67",
  "eventId": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
  "eventStartsAt": "2027-03-20T21:00:00-03:00",
  "status": "CONFIRMED",
  "items": [
    {
      "itemId": "a7b8c9d0-5e6f-4071-828d-9e0f1a2b3c01",
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "seatId": "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a23",
      "seatLabel": "Platea A-5",
      "unitPrice": 45000.0,
      "currency": "ARS",
      "status": "ACTIVE"
    },
    {
      "itemId": "a7b8c9d0-5e6f-4071-828d-9e0f1a2b3c02",
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "seatId": "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a24",
      "seatLabel": "Platea A-6",
      "unitPrice": 45000.0,
      "currency": "ARS",
      "status": "ACTIVE"
    }
  ],
  "total": 90000.0,
  "currency": "ARS",
  "holdExpiresAt": null,
  "createdAt": "2026-11-07T15:30:00-03:00",
  "paymentId": "1b2c3d4e-6f70-4182-939e-0f1a2b3c4d56",
  "payment": {
    "method": "CARD",
    "last4": "4242",
    "status": "APPROVED"
  },
  "tickets": [
    {
      "ticketId": "6a708192-b3c4-46d7-a8e3-5e6f70819201",
      "orderItemId": "a7b8c9d0-5e6f-4071-828d-9e0f1a2b3c01",
      "eventId": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "seatId": "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a23",
      "seatLabel": "Platea A-5",
      "qrCode": "q7Yk2mC0v3Xn8Lr1Tz5AeHb9PwUd4JfGs6OiVhNxRcM",
      "status": "VALID",
      "issuedAt": "2026-11-07T15:30:00-03:00"
    },
    {
      "ticketId": "6a708192-b3c4-46d7-a8e3-5e6f70819202",
      "orderItemId": "a7b8c9d0-5e6f-4071-828d-9e0f1a2b3c02",
      "eventId": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "seatId": "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a24",
      "seatLabel": "Platea A-6",
      "qrCode": "Zb3Rt9KpLw1Qe7NvYx0HcUj5AiOs2DmFgT8ErBnCy4V",
      "status": "VALID",
      "issuedAt": "2026-11-07T15:30:00-03:00"
    }
  ]
}
```

#### `POST /api/orders/{orderId}/items/{itemId}/refund-requests` — Solicitar devolución de una entrada

- **Rol:** CUSTOMER
- **Descripción:** Hasta 72 h antes del evento. Una sola solicitud activa por ítem.

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `orderId` | path | uuid | sí | ID de la orden |
| `itemId` | path | uuid | sí | ID del ítem de la orden |

**Request**

```json
{
  "reason": "No puedo asistir"
}
```

**Respuestas**

| Código | Significado |
|---|---|
| 201 | Solicitud creada en PENDING |
| 401 | Falta identidad o rol inválido |
| 403 | Rol insuficiente o recurso de otro usuario |
| 404 | Recurso inexistente |
| 409 | Ya existe una solicitud activa o el ítem no está ACTIVE |
| 422 | Faltan menos de 72 h para el evento |

Ejemplo 201:

```json
{
  "refundRequestId": "5f607182-a3b4-45c6-97d2-4d5e6f708190",
  "orderId": "f6a7b8c9-4d5e-4f60-b17c-8d9e0f1a2b34",
  "orderItemId": "a7b8c9d0-5e6f-4071-828d-9e0f1a2b3c01",
  "customerId": "2c3d4e5f-7081-4293-a4af-1a2b3c4d5e67",
  "status": "PENDING",
  "reason": "No puedo asistir",
  "createdAt": "2026-11-07T15:30:00-03:00",
  "decidedBy": null,
  "decidedAt": null,
  "automatic": false,
  "step": "NONE",
  "attempts": 0
}
```

Ejemplo de error 409:

```json
{
  "type": "https://ticketya.example/errors/invalid-state-transition",
  "title": "Estado inválido",
  "status": 409,
  "detail": "Ya existe una solicitud activa para el ítem",
  "code": "INVALID_STATE_TRANSITION",
  "correlationId": "b7d2c9e4-1f3a-4a5b-8c6d-7e8f9a0b1c2d"
}
```

#### `GET /api/refund-requests/mine` — Listar mis solicitudes de devolución

- **Rol:** CUSTOMER

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Solicitudes del cliente |
| 401 | Falta identidad o rol inválido |
| 403 | Rol insuficiente o recurso de otro usuario |

Ejemplo 200:

```json
{
  "content": [
    {
      "refundRequestId": "5f607182-a3b4-45c6-97d2-4d5e6f708190",
      "orderId": "f6a7b8c9-4d5e-4f60-b17c-8d9e0f1a2b34",
      "orderItemId": "a7b8c9d0-5e6f-4071-828d-9e0f1a2b3c01",
      "customerId": "2c3d4e5f-7081-4293-a4af-1a2b3c4d5e67",
      "status": "PENDING",
      "reason": "No puedo asistir",
      "createdAt": "2026-11-07T15:30:00-03:00",
      "decidedBy": null,
      "decidedAt": null,
      "automatic": false,
      "step": "NONE",
      "attempts": 0
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

#### `GET /api/refund-requests` — Listar solicitudes de devolución (backoffice)

- **Rol:** ADMIN

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `status` | query | string | no | PENDING|REJECTED|APPROVED|PROCESSING|COMPLETED|FAILED |
| `page` | query | int | no | Base 0 |
| `size` | query | int | no | Default 20, máx 100 |

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Solicitudes ordenadas por antigüedad |
| 401 | Falta identidad o rol inválido |
| 403 | Rol insuficiente o recurso de otro usuario |

Ejemplo 200:

```json
{
  "content": [
    {
      "refundRequestId": "5f607182-a3b4-45c6-97d2-4d5e6f708190",
      "orderId": "f6a7b8c9-4d5e-4f60-b17c-8d9e0f1a2b34",
      "orderItemId": "a7b8c9d0-5e6f-4071-828d-9e0f1a2b3c01",
      "customerId": "2c3d4e5f-7081-4293-a4af-1a2b3c4d5e67",
      "status": "PENDING",
      "reason": "No puedo asistir",
      "createdAt": "2026-11-07T15:30:00-03:00",
      "decidedBy": null,
      "decidedAt": null,
      "automatic": false,
      "step": "NONE",
      "attempts": 0
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

#### `POST /api/refund-requests/{refundRequestId}/approve` — Aprobar devolución

- **Rol:** ADMIN
- **Descripción:** Dispara el procesamiento: invalida entrada → libera asiento → reintegra pago → libera cuota. Si un paso falla queda PROCESSING y se reintenta.

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `refundRequestId` | path | uuid | sí | ID de la solicitud |

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Solicitud aprobada y en procesamiento |
| 401 | Falta identidad o rol inválido |
| 403 | Rol insuficiente o recurso de otro usuario |
| 404 | Recurso inexistente |
| 409 | Transición de estado inválida |

Ejemplo 200:

```json
{
  "refundRequestId": "5f607182-a3b4-45c6-97d2-4d5e6f708190",
  "orderId": "f6a7b8c9-4d5e-4f60-b17c-8d9e0f1a2b34",
  "orderItemId": "a7b8c9d0-5e6f-4071-828d-9e0f1a2b3c01",
  "customerId": "2c3d4e5f-7081-4293-a4af-1a2b3c4d5e67",
  "status": "PROCESSING",
  "reason": "No puedo asistir",
  "createdAt": "2026-11-07T15:30:00-03:00",
  "decidedBy": "4e5f6071-92a3-44b5-86c1-3c4d5e6f7089",
  "decidedAt": "2026-11-07T15:30:00-03:00",
  "automatic": false,
  "step": "NONE",
  "attempts": 0
}
```

Ejemplo de error 409:

```json
{
  "type": "https://ticketya.example/errors/invalid-state-transition",
  "title": "Estado inválido",
  "status": 409,
  "detail": "La operación no es válida en el estado actual",
  "code": "INVALID_STATE_TRANSITION",
  "correlationId": "b7d2c9e4-1f3a-4a5b-8c6d-7e8f9a0b1c2d"
}
```

#### `POST /api/refund-requests/{refundRequestId}/reject` — Rechazar devolución

- **Rol:** ADMIN

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `refundRequestId` | path | uuid | sí | ID de la solicitud |

**Request**

```json
{
  "reason": "Fuera de política"
}
```

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Solicitud rechazada; el ítem vuelve a ACTIVE |
| 401 | Falta identidad o rol inválido |
| 403 | Rol insuficiente o recurso de otro usuario |
| 404 | Recurso inexistente |
| 409 | Transición de estado inválida |

Ejemplo 200:

```json
{
  "refundRequestId": "5f607182-a3b4-45c6-97d2-4d5e6f708190",
  "orderId": "f6a7b8c9-4d5e-4f60-b17c-8d9e0f1a2b34",
  "orderItemId": "a7b8c9d0-5e6f-4071-828d-9e0f1a2b3c01",
  "customerId": "2c3d4e5f-7081-4293-a4af-1a2b3c4d5e67",
  "status": "REJECTED",
  "reason": "No puedo asistir",
  "createdAt": "2026-11-07T15:30:00-03:00",
  "decidedBy": "4e5f6071-92a3-44b5-86c1-3c4d5e6f7089",
  "decidedAt": "2026-11-07T15:30:00-03:00",
  "automatic": false,
  "step": "NONE",
  "attempts": 0
}
```

Ejemplo de error 409:

```json
{
  "type": "https://ticketya.example/errors/invalid-state-transition",
  "title": "Estado inválido",
  "status": 409,
  "detail": "La operación no es válida en el estado actual",
  "code": "INVALID_STATE_TRANSITION",
  "correlationId": "b7d2c9e4-1f3a-4a5b-8c6d-7e8f9a0b1c2d"
}
```

### Endpoints internos

#### `POST /internal/events/{eventId}/cancellation` — Notificar cancelación de evento (desde catalog)

- **Rol:** INTERNAL  (header `X-Internal-Token`)
- **Descripción:** Idempotente por eventId. Expira órdenes pendientes y procesa reintegros automáticos en segundo plano.

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `eventId` | path | uuid | sí | ID del evento |

**Request**

```json
{
  "reason": "Cancelado por el organizador"
}
```

**Respuestas**

| Código | Significado |
|---|---|
| 202 | Cancelación registrada; reintegros en proceso |
| 200 | Cancelación ya registrada |
| 403 | Falta o es inválido X-Internal-Token |

Ejemplo 202:

```json
{
  "eventId": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
  "ordersExpired": 3,
  "itemsToRefund": 100
}
```

---

## payment-service

Pasarela simulada, pagos y reintegros. Sin endpoints públicos.

### Endpoints internos

#### `POST /internal/payments` — Procesar pago (idempotente)

- **Rol:** INTERNAL  (header `X-Internal-Token`)
- **Descripción:** Un rechazo se persiste y se responde 200 con status=REJECTED (el 402 lo decide order-service). Misma clave+datos → mismo resultado; misma clave+otros datos → 422.

**Request**

```json
{
  "orderId": "f6a7b8c9-4d5e-4f60-b17c-8d9e0f1a2b34",
  "customerId": "2c3d4e5f-7081-4293-a4af-1a2b3c4d5e67",
  "amount": 90000.0,
  "currency": "ARS",
  "method": "CARD",
  "token": "tok_approved",
  "last4": "4242",
  "idempotencyKey": "pay-f6a7b8c9-4d5e-4f60-b17c-8d9e0f1a2b34"
}
```

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Resultado del pago (APPROVED o REJECTED) |
| 400 | Request inválido |
| 403 | Falta o es inválido X-Internal-Token |
| 422 | Clave reutilizada con datos distintos |

Ejemplo 200:

```json
{
  "paymentId": "1b2c3d4e-6f70-4182-939e-0f1a2b3c4d56",
  "orderId": "f6a7b8c9-4d5e-4f60-b17c-8d9e0f1a2b34",
  "customerId": "2c3d4e5f-7081-4293-a4af-1a2b3c4d5e67",
  "amount": 90000.0,
  "currency": "ARS",
  "method": "CARD",
  "last4": "4242",
  "status": "APPROVED",
  "gatewayRef": "mock-8f3a21c4",
  "refundedAmount": 0.0,
  "createdAt": "2026-11-07T15:30:00-03:00"
}
```

Ejemplo de error 422:

```json
{
  "type": "https://ticketya.example/errors/idempotency-key-reused",
  "title": "Clave reutilizada",
  "status": 422,
  "detail": "La clave ya se usó con otro monto u orden",
  "code": "IDEMPOTENCY_KEY_REUSED",
  "correlationId": "b7d2c9e4-1f3a-4a5b-8c6d-7e8f9a0b1c2d"
}
```

#### `GET /internal/payments/{paymentId}` — Consultar pago

- **Rol:** INTERNAL  (header `X-Internal-Token`)

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `paymentId` | path | uuid | sí | ID del pago |

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Pago |
| 403 | Falta o es inválido X-Internal-Token |
| 404 | Recurso inexistente |

Ejemplo 200:

```json
{
  "paymentId": "1b2c3d4e-6f70-4182-939e-0f1a2b3c4d56",
  "orderId": "f6a7b8c9-4d5e-4f60-b17c-8d9e0f1a2b34",
  "customerId": "2c3d4e5f-7081-4293-a4af-1a2b3c4d5e67",
  "amount": 90000.0,
  "currency": "ARS",
  "method": "CARD",
  "last4": "4242",
  "status": "APPROVED",
  "gatewayRef": "mock-8f3a21c4",
  "refundedAmount": 0.0,
  "createdAt": "2026-11-07T15:30:00-03:00"
}
```

#### `GET /internal/payments` — Buscar pago por orden (reconciliación)

- **Rol:** INTERNAL  (header `X-Internal-Token`)

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `orderId` | query | uuid | sí | ID de la orden |

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Pagos de la orden |
| 400 | Request inválido |
| 403 | Falta o es inválido X-Internal-Token |

Ejemplo 200:

```json
[
  {
    "paymentId": "1b2c3d4e-6f70-4182-939e-0f1a2b3c4d56",
    "orderId": "f6a7b8c9-4d5e-4f60-b17c-8d9e0f1a2b34",
    "customerId": "2c3d4e5f-7081-4293-a4af-1a2b3c4d5e67",
    "amount": 90000.0,
    "currency": "ARS",
    "method": "CARD",
    "last4": "4242",
    "status": "APPROVED",
    "gatewayRef": "mock-8f3a21c4",
    "refundedAmount": 0.0,
    "createdAt": "2026-11-07T15:30:00-03:00"
  }
]
```

#### `POST /internal/payments/{paymentId}/refunds` — Reintegrar total o parcialmente (idempotente)

- **Rol:** INTERNAL  (header `X-Internal-Token`)
- **Descripción:** La suma de reintegros no puede superar lo pagado. Solo pagos APPROVED.

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `paymentId` | path | uuid | sí | ID del pago |

**Request**

```json
{
  "amount": 45000.0,
  "reason": "REFUND_REQUEST",
  "idempotencyKey": "refund-5f607182-a3b4-45c6-97d2-4d5e6f708190"
}
```

**Respuestas**

| Código | Significado |
|---|---|
| 201 | Reintegro registrado |
| 200 | Reintento idempotente: devuelve el reintegro original |
| 409 | Pago no aprobado |
| 422 | Excede el monto pagado |
| 403 | Falta o es inválido X-Internal-Token |
| 404 | Recurso inexistente |

Ejemplo 201:

```json
{
  "refundId": "70819203-c4d5-47e8-b9f4-6f7081920314",
  "paymentId": "1b2c3d4e-6f70-4182-939e-0f1a2b3c4d56",
  "amount": 45000.0,
  "status": "APPROVED",
  "createdAt": "2026-11-07T15:30:00-03:00"
}
```

Ejemplo de error 409:

```json
{
  "type": "https://ticketya.example/errors/invalid-state-transition",
  "title": "Estado inválido",
  "status": 409,
  "detail": "El pago está REJECTED",
  "code": "INVALID_STATE_TRANSITION",
  "correlationId": "b7d2c9e4-1f3a-4a5b-8c6d-7e8f9a0b1c2d"
}
```

---

## ticket-service

Emisión de entradas con QR único y estados.

### Endpoints públicos

#### `GET /api/tickets` — Listar mis entradas

- **Rol:** CUSTOMER

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `eventId` | query | uuid | no | Filtra por evento |
| `status` | query | string | no | VALID|USED|REFUNDED|CANCELLED |
| `page` | query | int | no | Base 0 |
| `size` | query | int | no | Default 20, máx 100 |

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Entradas del cliente con QR |
| 401 | Falta identidad o rol inválido |
| 403 | Rol insuficiente o recurso de otro usuario |

Ejemplo 200:

```json
{
  "content": [
    {
      "ticketId": "6a708192-b3c4-46d7-a8e3-5e6f70819201",
      "orderItemId": "a7b8c9d0-5e6f-4071-828d-9e0f1a2b3c01",
      "eventId": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "seatId": "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a23",
      "seatLabel": "Platea A-5",
      "qrCode": "q7Yk2mC0v3Xn8Lr1Tz5AeHb9PwUd4JfGs6OiVhNxRcM",
      "status": "VALID",
      "issuedAt": "2026-11-07T15:30:00-03:00"
    },
    {
      "ticketId": "6a708192-b3c4-46d7-a8e3-5e6f70819202",
      "orderItemId": "a7b8c9d0-5e6f-4071-828d-9e0f1a2b3c02",
      "eventId": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "seatId": "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a24",
      "seatLabel": "Platea A-6",
      "qrCode": "Zb3Rt9KpLw1Qe7NvYx0HcUj5AiOs2DmFgT8ErBnCy4V",
      "status": "VALID",
      "issuedAt": "2026-11-07T15:30:00-03:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2,
  "totalPages": 1
}
```

#### `GET /api/tickets/{ticketId}` — Detalle de una entrada

- **Rol:** CUSTOMER, ADMIN
- **Descripción:** El CUSTOMER solo accede a sus entradas (404 si es ajena).

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `ticketId` | path | uuid | sí | ID de la entrada |

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Entrada |
| 401 | Falta identidad o rol inválido |
| 403 | Rol insuficiente o recurso de otro usuario |
| 404 | Recurso inexistente |

Ejemplo 200:

```json
{
  "ticketId": "6a708192-b3c4-46d7-a8e3-5e6f70819201",
  "orderItemId": "a7b8c9d0-5e6f-4071-828d-9e0f1a2b3c01",
  "eventId": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
  "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
  "seatId": "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a23",
  "seatLabel": "Platea A-5",
  "qrCode": "q7Yk2mC0v3Xn8Lr1Tz5AeHb9PwUd4JfGs6OiVhNxRcM",
  "status": "VALID",
  "issuedAt": "2026-11-07T15:30:00-03:00"
}
```

### Endpoints internos

#### `POST /internal/tickets` — Emitir entradas (idempotente por orderItemId)

- **Rol:** INTERNAL  (header `X-Internal-Token`)

**Request**

```json
{
  "orderId": "f6a7b8c9-4d5e-4f60-b17c-8d9e0f1a2b34",
  "eventId": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
  "customerId": "2c3d4e5f-7081-4293-a4af-1a2b3c4d5e67",
  "items": [
    {
      "orderItemId": "a7b8c9d0-5e6f-4071-828d-9e0f1a2b3c01",
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "seatId": "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a23",
      "seatLabel": "Platea A-5"
    },
    {
      "orderItemId": "a7b8c9d0-5e6f-4071-828d-9e0f1a2b3c02",
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "seatId": "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a24",
      "seatLabel": "Platea A-6"
    }
  ]
}
```

**Respuestas**

| Código | Significado |
|---|---|
| 201 | Entradas emitidas |
| 200 | Ya emitidas: devuelve las existentes |
| 400 | Request inválido |
| 403 | Falta o es inválido X-Internal-Token |

Ejemplo 201:

```json
{
  "tickets": [
    {
      "ticketId": "6a708192-b3c4-46d7-a8e3-5e6f70819201",
      "orderItemId": "a7b8c9d0-5e6f-4071-828d-9e0f1a2b3c01",
      "eventId": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "seatId": "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a23",
      "seatLabel": "Platea A-5",
      "qrCode": "q7Yk2mC0v3Xn8Lr1Tz5AeHb9PwUd4JfGs6OiVhNxRcM",
      "status": "VALID",
      "issuedAt": "2026-11-07T15:30:00-03:00"
    },
    {
      "ticketId": "6a708192-b3c4-46d7-a8e3-5e6f70819202",
      "orderItemId": "a7b8c9d0-5e6f-4071-828d-9e0f1a2b3c02",
      "eventId": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "seatId": "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a24",
      "seatLabel": "Platea A-6",
      "qrCode": "Zb3Rt9KpLw1Qe7NvYx0HcUj5AiOs2DmFgT8ErBnCy4V",
      "status": "VALID",
      "issuedAt": "2026-11-07T15:30:00-03:00"
    }
  ]
}
```

#### `GET /internal/tickets` — Consultar entradas de una orden

- **Rol:** INTERNAL  (header `X-Internal-Token`)
- **Descripción:** Lo usa order-service para mostrar el QR en el detalle de la orden.

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `orderId` | query | uuid | sí | ID de la orden |

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Entradas de la orden (lista vacía si no hay) |
| 400 | Request inválido |
| 403 | Falta o es inválido X-Internal-Token |

Ejemplo 200:

```json
{
  "tickets": [
    {
      "ticketId": "6a708192-b3c4-46d7-a8e3-5e6f70819201",
      "orderItemId": "a7b8c9d0-5e6f-4071-828d-9e0f1a2b3c01",
      "eventId": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "seatId": "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a23",
      "seatLabel": "Platea A-5",
      "qrCode": "q7Yk2mC0v3Xn8Lr1Tz5AeHb9PwUd4JfGs6OiVhNxRcM",
      "status": "VALID",
      "issuedAt": "2026-11-07T15:30:00-03:00"
    },
    {
      "ticketId": "6a708192-b3c4-46d7-a8e3-5e6f70819202",
      "orderItemId": "a7b8c9d0-5e6f-4071-828d-9e0f1a2b3c02",
      "eventId": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
      "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
      "seatId": "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a24",
      "seatLabel": "Platea A-6",
      "qrCode": "Zb3Rt9KpLw1Qe7NvYx0HcUj5AiOs2DmFgT8ErBnCy4V",
      "status": "VALID",
      "issuedAt": "2026-11-07T15:30:00-03:00"
    }
  ]
}
```

#### `POST /internal/tickets/{ticketId}/invalidate` — Invalidar entrada (idempotente)

- **Rol:** INTERNAL  (header `X-Internal-Token`)

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `ticketId` | path | uuid | sí | ID de la entrada |

**Request**

```json
{
  "reason": "REFUNDED"
}
```

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Entrada invalidada (REFUNDED o CANCELLED) |
| 400 | Request inválido |
| 403 | Falta o es inválido X-Internal-Token |
| 404 | Recurso inexistente |
| 409 | La entrada ya fue usada |

Ejemplo 200:

```json
{
  "ticketId": "6a708192-b3c4-46d7-a8e3-5e6f70819201",
  "orderItemId": "a7b8c9d0-5e6f-4071-828d-9e0f1a2b3c01",
  "eventId": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
  "sectorId": "c3d4e5f6-1a2b-4c3d-8e4f-5a6b7c8d9e01",
  "seatId": "e5f6a7b8-3c4d-4e5f-a06b-7c8d9e0f1a23",
  "seatLabel": "Platea A-5",
  "qrCode": "q7Yk2mC0v3Xn8Lr1Tz5AeHb9PwUd4JfGs6OiVhNxRcM",
  "status": "REFUNDED",
  "issuedAt": "2026-11-07T15:30:00-03:00"
}
```

Ejemplo de error 409:

```json
{
  "type": "https://ticketya.example/errors/invalid-state-transition",
  "title": "Estado inválido",
  "status": 409,
  "detail": "La entrada está USED",
  "code": "INVALID_STATE_TRANSITION",
  "correlationId": "b7d2c9e4-1f3a-4a5b-8c6d-7e8f9a0b1c2d"
}
```

#### `POST /internal/events/{eventId}/tickets/invalidate` — Invalidar todas las entradas de un evento (cancelación)

- **Rol:** INTERNAL  (header `X-Internal-Token`)

| Parámetro | En | Tipo | Oblig. | Descripción |
|---|---|---|---|---|
| `eventId` | path | uuid | sí | ID del evento |

**Request**

```json
{
  "reason": "CANCELLED"
}
```

**Respuestas**

| Código | Significado |
|---|---|
| 200 | Cantidad de entradas invalidadas |
| 403 | Falta o es inválido X-Internal-Token |

Ejemplo 200:

```json
{
  "eventId": "9b2f6c1a-4d3e-4a7b-8c5d-2e1f0a9b8c7d",
  "invalidated": 100
}
```
