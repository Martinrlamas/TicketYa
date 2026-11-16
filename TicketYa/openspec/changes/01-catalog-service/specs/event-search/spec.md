## ADDED Requirements

### Requirement: Búsqueda multicriterio
`GET /api/events` SHALL aceptar los filtros opcionales `type`, `organizerId`, `participant`, `city`, `dateFrom`, `dateTo` y `q`, combinables entre sí con semántica AND.
La búsqueda SHALL devolver únicamente eventos en estado `PUBLISHED` o `SOLD_OUT`.

#### Scenario: Filtro por tipo y ciudad
- **WHEN** se busca `type=CONCERT&city=Tandil`
- **THEN** solo se devuelven eventos de ese tipo realizados en lugares de esa ciudad

#### Scenario: Filtro por participante
- **WHEN** se busca `participant=Divididos`
- **THEN** se devuelven eventos con un participante cuyo nombre coincide (sin distinguir mayúsculas ni acentos)

#### Scenario: Rango de fechas
- **WHEN** se busca con `dateFrom` y `dateTo`
- **THEN** solo se devuelven eventos cuya fecha está dentro del rango inclusive

#### Scenario: Sin resultados
- **WHEN** ningún evento cumple los criterios
- **THEN** la respuesta es 200 con lista vacía

### Requirement: Ordenamiento
El parámetro `sort` SHALL admitir `date`, `price` y `relevance`, con `order=asc|desc`.
El orden por precio SHALL usar el menor precio de sector del evento.
El orden por relevancia SHALL usar el parámetro `q`; sin `q` equivale a fecha ascendente.

#### Scenario: Orden por precio
- **WHEN** se busca con `sort=price&order=asc`
- **THEN** los resultados se ordenan por precio mínimo ascendente

#### Scenario: Orden por relevancia
- **WHEN** se busca `q=rock&sort=relevance`
- **THEN** una coincidencia en el nombre se ubica antes que una coincidencia solo en la descripción

### Requirement: Paginación
La búsqueda SHALL paginar con `page` (base 0) y `size` (por defecto 20, máximo 100) e informar `totalElements` y `totalPages`.

#### Scenario: Tamaño excedido
- **WHEN** se solicita `size=500`
- **THEN** la respuesta es 400 con `code=VALIDATION_ERROR`

### Requirement: Resultados sin disponibilidad
Las respuestas de búsqueda y detalle SHALL NOT incluir datos de disponibilidad de lugares; esos datos pertenecen a inventory-service.

#### Scenario: Contrato de respuesta
- **WHEN** se consulta el detalle de un evento
- **THEN** la respuesta incluye precios por sector pero ningún contador de lugares libres
