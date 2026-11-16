## ADDED Requirements

### Requirement: Alta de lugares
El sistema SHALL permitir a usuarios `ADMIN` u `ORGANIZER` crear un lugar con nombre, dirección, ciudad, latitud y longitud.

#### Scenario: Alta exitosa
- **WHEN** un ORGANIZER envía `POST /api/venues` con datos válidos
- **THEN** la respuesta es 201 con el `id` del lugar y su `Location`

#### Scenario: Coordenadas inválidas
- **WHEN** la latitud está fuera de [-90, 90] o la longitud fuera de [-180, 180]
- **THEN** la respuesta es 400 con `code=VALIDATION_ERROR`

### Requirement: Sectores numerados y no numerados
Un lugar SHALL tener uno o más sectores. Un sector numerado SHALL definir al menos una fila con cantidad de asientos mayor a cero.
Un sector no numerado SHALL definir una capacidad máxima mayor a cero y SHALL NOT definir filas.

#### Scenario: Sector numerado válido
- **WHEN** se agrega el sector "Platea" de tipo NUMBERED con filas A (20 asientos) y B (20 asientos)
- **THEN** el sector queda con 40 asientos identificables como A-1 … B-20

#### Scenario: Sector no numerado válido
- **WHEN** se agrega el sector "Campo" de tipo GENERAL con capacidad 5000
- **THEN** el sector se crea con esa capacidad máxima

#### Scenario: Configuración inconsistente
- **WHEN** se envía un sector GENERAL con filas, o un sector NUMBERED sin filas
- **THEN** la respuesta es 400 con `code=VALIDATION_ERROR`

### Requirement: Consulta de lugares
El sistema SHALL permitir consultar un lugar con sus sectores a cualquier usuario, autenticado o no.

#### Scenario: Lugar inexistente
- **WHEN** se consulta un `id` que no existe
- **THEN** la respuesta es 404 con `code=NOT_FOUND`
