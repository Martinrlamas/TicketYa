# Features y User Stories — TicketYa (sistema completo)

Cada historia indica la entrega (**E1** = implementar ahora, **E2** = diferida pero diseñada), el servicio responsable
y la spec de OpenSpec donde vive el criterio de aceptación detallado. Los criterios usan formato *Dado / Cuando / Entonces*.
Numeración de features = numeración del enunciado.

## Mapa de features

| Feature | Nombre | Entrega | Servicio principal |
|---|---|---|---|
| F1 | Lugares y sectores | E1 | catalog |
| F2 | Eventos, precios y publicación | E1 | catalog |
| F3 | Compra con reserva temporal y emisión de QR | E1 | order + inventory + payment + ticket |
| F4 | Garantía de no doble venta | E1 | inventory + order |
| F5 | Devoluciones | E1 | order |
| F6 | Cancelación de evento y reintegros | E1 | catalog + order |
| F7 | Búsqueda multicriterio | E1 | catalog |
| F8 | Fila virtual | E2 | queue-service |
| F9 | Control de acceso con QR | E2 | ticket / access |
| F10 | Preferencias y recomendaciones | E2 | recommendation (MongoDB) |
| F11 | Notificaciones | E2 | notification |
| F12 | Reportes para organizadores | E2 | reporting |
| F13 | Transversales (seguridad, trazabilidad, resiliencia, cache, despliegue, carga) | E1 parcial / E2 | todos |

> Referencia al enunciado: F1=ítem 1-2, F2=ítem 3, F3=ítem 4, F4=ítem 5, F8=ítem 6, F5/F6=ítem 7, F9=ítem 8, F7=ítem 9, F10=ítem 10, F11=ítem 11, F12=ítem 12.

---

## F1 — Lugares y sectores (E1)

**US-01 Crear un lugar** — *Como administrador u organizador quiero registrar un lugar (nombre, dirección, ciudad, coordenadas) para poder asociarle eventos.* · catalog · spec `venues`
- Dado un usuario ADMIN/ORGANIZER, cuando envía datos válidos, entonces se crea el lugar y se devuelve 201 con su ID.
- Dado latitud fuera de [-90, 90] o longitud fuera de [-180, 180], cuando se envía, entonces 400 `VALIDATION_ERROR`.
- Dado un CUSTOMER, cuando intenta crear un lugar, entonces 403.

**US-02 Definir sectores numerados y no numerados** — *Como administrador/organizador quiero definir sectores con asientos identificados o con capacidad máxima.* · catalog · spec `venues`
- Dado un sector NUMBERED con filas A(20) y B(20), cuando se crea, entonces existen 40 asientos A-1…B-20.
- Dado un sector GENERAL con capacidad 5000, cuando se crea, entonces se registra su capacidad máxima.
- Dado un GENERAL con filas, o un NUMBERED sin filas, cuando se envía, entonces 400.

**US-03 Consultar un lugar** — *Como cualquier usuario quiero ver un lugar y sus sectores.*
- Dado un lugar existente, cuando se consulta, entonces se devuelven sus sectores; si no existe, 404.

## F2 — Eventos, precios y publicación (E1)

**US-04 Crear evento en borrador** — *Como organizador quiero crear un evento (nombre, descripción, tipo, participantes, fecha, lugar) en estado borrador.* · catalog · spec `events-lifecycle`
- Dado un lugar existente y fecha futura, cuando el organizador crea el evento, entonces queda `DRAFT` y asociado a él.
- Dado fecha pasada o lugar inexistente, entonces 400/404.

**US-05 Fijar precio por sector** — *Como organizador quiero definir el precio de cada sector para mi evento.*
- Dado un evento DRAFT propio, cuando defino precios > 0 para sectores del lugar, entonces se guardan.
- Dado un sector ajeno al lugar, entonces 400. Dado un evento de otro organizador, entonces 403.

**US-06 Publicar un evento** — *Como organizador quiero publicar mi evento para que pueda venderse.*
- Dado un DRAFT con al menos un sector con precio, cuando publico, entonces se inicializa el inventario y pasa a `PUBLISHED`.
- Dado inventory caído, entonces 503 y el evento sigue en `DRAFT`; reintentar es seguro (idempotente).
- Dado un evento sin precios, entonces 409.

**US-07 Editar un borrador** — *Como organizador quiero modificar mi evento mientras es borrador.*
- Dado un evento no DRAFT, cuando intento modificarlo, entonces 409.

**US-08 Ver mis eventos** — *Como organizador quiero listar mis eventos, incluidos borradores.*
- Dado un organizador, cuando consulta `GET /api/events/mine`, entonces ve solo sus eventos en cualquier estado.

**US-09 Suspender y reactivar** — *Como administrador quiero suspender un evento problemático y reactivarlo.*
- Dado un evento PUBLISHED, cuando un ADMIN lo suspende, entonces no aparece en búsquedas ni admite compras.
- Dado un ORGANIZER, cuando intenta suspender, entonces 403.

**US-10 Cancelar un evento** — *Como administrador quiero cancelar un evento y que se reintegre a los compradores.*
- Dado un evento no terminal, cuando un ADMIN lo cancela, entonces pasa a `CANCELLED` y se notifica a order-service (ver US-30).
- Dado un evento FINISHED o CANCELLED, entonces 409.

## F7 — Búsqueda multicriterio (E1)

**US-11 Buscar eventos** — *Como cliente quiero buscar por tipo, organizador, participante, ciudad y rango de fechas.* · catalog · spec `event-search`
- Dado filtros combinados, cuando busco, entonces recibo solo eventos que cumplen **todos** (AND) y están PUBLISHED/SOLD_OUT.
- Dado un filtro `participant`, entonces la coincidencia ignora mayúsculas y acentos.
- Dado que nada coincide, entonces 200 con lista vacía.

**US-12 Ordenar y paginar** — *Como cliente quiero ordenar por fecha, precio o relevancia.*
- Dado `sort=price&order=asc`, entonces ordena por el menor precio de sector. Dado `q` y `sort=relevance`, una coincidencia en el nombre precede a una en la descripción.
- Dado `size>100`, entonces 400.

**US-13 Ver detalle de un evento** — *Como cliente quiero ver el detalle con precios por sector.*
- Dado un evento publicado, entonces veo precios; **nunca** contadores de disponibilidad (eso lo da inventory).
- Dado un DRAFT ajeno, entonces 404.

## F3 — Compra con reserva temporal y emisión de QR (E1)

**US-14 Ver disponibilidad** — *Como cliente quiero ver qué asientos/lugares están libres antes de elegir.* · inventory · spec `inventory-availability`
- Dado un evento con asientos retenidos/vendidos, cuando consulto, entonces figuran como no disponibles; la respuesta lleva `Cache-Control: no-store`.

**US-15 Reservar asientos numerados** — *Como cliente quiero elegir asientos específicos y que se me reserven 10 minutos mientras pago.* · order+inventory · specs `order-purchase`, `inventory-holds`
- Dado asientos libres, cuando creo la orden, entonces 201 `PENDING_PAYMENT` con `holdExpiresAt` = ahora + 10 min.
- Dado que alguno está ocupado, entonces 409 `SEAT_UNAVAILABLE` y **ninguno** queda retenido (todo-o-nada).

**US-16 Reservar lugares en zona general** — *Como cliente quiero reservar N lugares de un sector no numerado.*
- Dado capacidad suficiente, entonces se retienen N lugares. Dado capacidad insuficiente, entonces 409 `INSUFFICIENT_CAPACITY`.

**US-17 Pagar la orden** — *Como cliente quiero pagar y recibir mis entradas con QR.* · order+payment+inventory+ticket
- Dado pago aprobado antes del vencimiento, entonces la orden queda `CONFIRMED`, los lugares `SOLD` y se emiten entradas con QR único.
- Dado que se registra el detalle de compra y medio de pago, entonces la orden muestra ítems, precio, medio y estado.

**US-18 Pago rechazado libera los lugares** — *Como sistema debo liberar lo reservado si el pago falla.*
- Dado rechazo de la pasarela, entonces 402 `PAYMENT_REJECTED`, hold y cuota liberados, orden `PAYMENT_REJECTED`.

**US-19 La reserva vence** — *Como sistema debo liberar lugares de órdenes abandonadas.*
- Dado `holdExpiresAt` vencido sin pago, entonces la orden pasa a `EXPIRED` y los lugares quedan disponibles.
- Dado un pago tardío, entonces 410 `HOLD_EXPIRED`.

**US-20 Ver mis entradas** — *Como cliente quiero ver mis entradas con el código QR.* · ticket · spec `tickets`
- Dado mis entradas, cuando las listo, entonces veo evento, sector, asiento, estado y `qrCode`. Entradas de otro cliente → 404.

**US-21 Límite de entradas por cliente** — *Como organizador quiero que un cliente no acapare entradas.* · spec `order-purchase`
- Dado límite 6 y un cliente con 5, cuando pide 2 más, entonces 422 `PURCHASE_LIMIT_EXCEEDED`.
- Dado 10 órdenes simultáneas del mismo cliente de 1 entrada, entonces se aceptan exactamente 6.

**US-22 Cancelar una orden pendiente** — *Como cliente quiero arrepentirme antes de pagar.*
- Dado una orden `PENDING_PAYMENT`, cuando la cancelo, entonces `CANCELLED` y lugares/cuota liberados. Si está `CONFIRMED`, 409.

**US-23 Reintentos sin duplicar** — *Como cliente con mala conexión quiero reintentar sin que se cobre o reserve dos veces.* · spec `order-purchase`
- Dado `POST /api/orders` repetido con la misma `Idempotency-Key`, entonces devuelve la orden original.
- Dado `POST .../pay` repetido con la misma clave, entonces un solo cobro y las mismas entradas. Misma clave con otro cuerpo → 422.

**US-24 Compensación de pago aprobado sin lugares** — *Como sistema debo reintegrar si cobré pero no puedo vender.*
- Dado pago aprobado pero hold vencido/ocupado, entonces reintegro automático, orden `FAILED_REFUNDED`, 410.
- Dado ticket-service caído tras reintentos, entonces reintegro + liberación de lugares + `FAILED_REFUNDED`.

## F4 — Garantía de no doble venta (E1)

**US-25 Un asiento nunca se vende dos veces** — *Como organizador quiero la certeza de que no habrá dos entradas para el mismo asiento.* · inventory · spec `inventory-holds`
- Dado 50 solicitudes concurrentes por el mismo asiento, entonces exactamente 1 tiene éxito.
- Dado dos instancias del servicio compitiendo, entonces solo una obtiene el hold.

**US-26 El último lugar de una zona** — *Como organizador quiero que la capacidad de una zona nunca se exceda.*
- Dado capacidad restante 1 y 100 solicitudes de 1 lugar, entonces exactamente 1 tiene éxito y `held+sold ≤ capacidad`.

**US-27 Venta de extremo a extremo** — Dado 30 clientes comprando el mismo asiento vía gateway, entonces una sola orden `CONFIRMED` y una sola entrada `VALID`.

## F5 — Devoluciones (E1)

**US-28 Solicitar una devolución** — *Como cliente quiero devolver una entrada hasta 72 h antes del evento.* · order · spec `order-refunds`
- Dado >72 h para el evento, entonces la solicitud queda `PENDING`.
- Dado <72 h, entonces 422 `REFUND_WINDOW_CLOSED`. Dado una solicitud activa para el ítem, 409.

**US-29 Aprobar o rechazar** — *Como administrador quiero decidir las solicitudes.*
- Dado una solicitud PENDING, cuando la apruebo, entonces se invalida la entrada, se libera el asiento/lugar, se solicita el reintegro y se libera la cuota.
- Dado que rechazo, entonces `REJECTED` y el ítem vuelve a `ACTIVE`. Un CUSTOMER no puede decidir (403).

**US-30 Devolución resiliente** — *Como sistema debo completar una devolución aunque un servicio falle.*
- Dado payment caído en el reintegro, entonces la solicitud queda `PROCESSING`, se reintenta y el reintegro se hace **una sola vez**.
- Dado reintentos agotados, entonces `FAILED`, visible para el ADMIN.

## F6 — Cancelación de evento (E1)

**US-31 Reintegro masivo por cancelación** — *Como comprador quiero que me devuelvan el dinero si cancelan el evento.* · order · spec `event-cancellation`
- Dado un evento cancelado con 100 entradas, entonces 100 reintegros automáticos (sin aprobación) y entradas `CANCELLED`.
- Dado una notificación repetida, entonces no se duplican reintegros. Órdenes pendientes pasan a `EXPIRED`.
- Dado un intento de compra posterior, entonces 409 `EVENT_NOT_PURCHASABLE`.

## F8 — Fila virtual (E2)

**US-32 Entrar a la fila virtual** — *Como cliente quiero ver mi posición cuando se abre una venta masiva.*
- Dado un evento de alta demanda, cuando abro la venta, entonces entro a la fila y veo mi posición.
**US-33 Habilitación por tandas** — Dado una tanda habilitada, entonces los clientes de esa tanda pueden crear órdenes en orden de llegada; los demás reciben 403/423.

## F9 — Control de acceso (E2)

**US-34 Escanear QR en el ingreso** — *Como personal de acceso quiero validar el QR.*
- Dado QR válido del evento correcto y no usado, entonces se marca `USED` y se permite el ingreso.
- Dado QR ya usado, de otro evento, inexistente, `REFUNDED` o `CANCELLED`, entonces se deniega con motivo.
- Dado dos escaneos simultáneos del mismo QR, entonces solo uno ingresa.

## F10 — Preferencias y recomendaciones (E2)

**US-35 Registrar preferencias** — *Como cliente quiero indicar tipos de evento, artistas y ciudad favoritos.*
**US-36 Recibir recomendaciones** — Dado mi historial, entonces recibo eventos sugeridos ("quienes compraron X también compraron Y").

## F11 — Notificaciones (E2)

**US-37 Confirmación de compra** — Dado una compra confirmada, entonces recibo email/push.
**US-38 Cambio o cancelación de evento** — Dado un evento cancelado o modificado, entonces los compradores son notificados (en E1: solo log vía `BuyerNotifier`).
**US-39 Artista favorito publica evento** — Dado que un artista favorito publica un evento en mi ciudad, entonces recibo aviso.

## F12 — Reportes (E2)

**US-40 Reportes del organizador** — Dado mis eventos, entonces veo entradas vendidas y devueltas por sector, porcentaje de ocupación y recaudación.

## F13 — Transversales

**US-41 Autenticación y roles con JWT (E2)** — Dado un token válido con rol, entonces accedo solo a lo permitido por mi rol; token inválido/vencido → 401. *(E1: identidad por headers.)*
**US-42 Trazabilidad (E1 básica / E2 centralizada)** — Dado cualquier solicitud, entonces se registra endpoint, usuario, código y tiempo, con un `X-Correlation-Id` que atraviesa todos los servicios.
**US-43 Resiliencia (E1 timeouts / E2 reintentos, circuit breaker, fallback)** — Dado un servicio externo lento o caído, entonces el llamador responde 503 sin dejar estado inconsistente.
**US-44 Cache (E2)** — Dado catálogo y búsquedas cacheados con TTL e invalidación; la disponibilidad **nunca** se cachea.
**US-45 Despliegue (E2)** — Dado `docker compose up`, entonces todo el sistema arranca; en E1 las bases en Docker y los servicios locales.
**US-46 Prueba de carga (E2)** — Dada la apertura de venta masiva, entonces se mide el flujo de compra (usuarios concurrentes, tiempos, errores).
**US-47 Documentación de API (E1)** — Dado cada servicio, entonces expone OpenAPI con descripciones, ejemplos y códigos de error; la colección Postman cubre todos los endpoints públicos.
