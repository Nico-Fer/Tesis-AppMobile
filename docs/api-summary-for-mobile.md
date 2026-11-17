# TesisApi - Backend API Summary for Mobile (Kotlin)

> This document describes the REST API contract so AI assistants can generate correct Kotlin code for the mobile app.

**Base URL:** `https://<host>/api`  
**Content-Type:** `application/json`

---

## 1. Usuarios (`/api/usuarios`)

### POST `/api/usuarios/registro/paciente`
Register a patient.

**Request:**
```json
{
  "nombre": "string (max 100, required)",
  "edad": "byte (1-120)",
  "email": "string (max 255, required, email format)",
  "contrasena": "string (8-255, required)",
  "idProfesionalAsignado": "UUID? (optional)"
}
```

**Response 201:**
```json
{
  "idUsuario": "UUID",
  "nombre": "string",
  "email": "string",
  "rol": "Paciente",
  "fechaRegistro": "datetime (ISO 8601)"
}
```

**Error 409:** `{ "message": "..." }`

---

### POST `/api/usuarios/registro/profesional`
Register a professional.

**Request:**
```json
{
  "nombre": "string (max 100, required)",
  "email": "string (max 255, required, email format)",
  "contrasena": "string (8-255, required)",
  "especialidad": "string (max 100, required)"
}
```

**Response 201:** Same as `UsuarioResponse` with `"rol": "Profesional"`.

**Error 409:** `{ "message": "..." }`

---

### POST `/api/usuarios/login`
Authenticate user.

**Request:**
```json
{
  "email": "string (required)",
  "contrasena": "string (required)"
}
```

**Response 200:**
```json
{
  "autenticado": true,
  "usuario": {
    "idUsuario": "UUID",
    "nombre": "string",
    "email": "string",
    "rol": "Paciente | Profesional",
    "fechaRegistro": "datetime"
  }
}
```

**Error 401:** `{ "message": "Credenciales invalidas." }`

---

## 2. Ejercicios (`/api/ejercicios`)

### GET `/api/ejercicios`
Get full exercise catalog.

**Response 200:** `List<Ejercicio>`
```json
[
  {
    "idEjercicio": "UUID",
    "nombre": "string",
    "descripcion": "string",
    "urlVideoExplicativo": "string?",
    "zonasAfectadas": ["Cuadriceps", "Gluteos", "Rodillas"],
    "nivelDificultad": "string"
  }
]
```

---

## 3. M�dicos (`/api/medicos`)

### POST `/api/medicos/rutinas`
Assign a routine to a patient.

**Request:**
```json
{
  "idPaciente": "UUID (required)",
  "nombreRutina": "string (max 150, required)",
  "diasAsignados": "int[]? (0=Lunes..6=Domingo; opcional, vacío = rutina flexible)",
  "ejercicios": [
    {
      "idEjercicio": "UUID (required)",
      "repeticionesObjetivo": "short (?1)",
      "series": "short (?1)"
    }
  ]
}
```

**Response 201:**
```json
{
  "idRutina": "UUID",
  "idPaciente": "UUID",
  "nombreRutina": "string",
  "fechaCreacion": "datetime",
  "activa": true,
  "diasAsignados": [0, 2, 4],
  "ejercicios": [
    {
      "idRutinaEjercicio": "UUID",
      "idEjercicio": "UUID",
      "nombreEjercicio": "string",
      "repeticionesObjetivo": "short",
      "series": "short"
    }
  ]
}
```

**Error 404:** `{ "message": "..." }`

---

### POST `/api/rutinas`
Create a routine owned by the authenticated patient (no assigning professional). A
professional gets 403.

`idCliente` makes the call idempotent, same rule as `POST /api/sesiones`: the routine is
stored with that id, and resending the same `idCliente` (the app retries routines created
while offline) returns the routine already stored instead of creating a duplicate. Because
the id is final from the start, sessions recorded offline against that routine already
reference the right `idRutina`; the app must send the routine before those sessions. An
`idCliente` already used by another patient returns 409.

**Request:**
```json
{
  "idCliente": "UUID? (optional; generated once per routine by the app)",
  "nombreRutina": "string (max 150, required)",
  "diasAsignados": "int[]? (0=Lunes..6=Domingo; optional, empty = flexible routine)",
  "ejercicios": [
    {
      "idEjercicio": "UUID (required)",
      "repeticionesObjetivo": "short (>=1)",
      "series": "short (>=1)"
    }
  ]
}
```

**Response 201:** same shape as `POST /api/medicos/rutinas`, with `idRutina` equal to
`idCliente` when one was sent.

**Error 404:** an exercise is not in the catalog. **Error 409:** `idCliente` belongs to
another patient.

---

### POST `/api/medicos/tolerancias`
Adjust angle tolerances for a patient's exercise.

**Request:**
```json
{
  "idPaciente": "UUID (required)",
  "idEjercicio": "UUID (required)",
  "anguloMinimo": "decimal (0-360)",
  "anguloMaximo": "decimal (0-360)"
}
```

**Response 200:**
```json
{
  "idTolerancia": "UUID",
  "idPaciente": "UUID",
  "idEjercicio": "UUID",
  "anguloMinimo": "decimal",
  "anguloMaximo": "decimal"
}
```

**Error 404:** `{ "message": "..." }`

---

### Referencias de movimiento DTW

Una referencia por **(paciente, ejercicio)**; guardar de nuevo reemplaza la anterior (upsert).
`referenciaJson` es el JSON schema v1 que produce la app (opaco para la API; se valida
schemaVersion=1, exerciseId coincidente, trayectoria no vacía, ≤ 256 KB → 400 si falla).

- **PUT `/api/pacientes/referencias`** (Paciente; id del token)
  Request: `{ "idEjercicio": "UUID", "referenciaJson": "string" }`
- **GET `/api/pacientes/referencias/{idEjercicio}`** (Paciente)
  Siempre 200 con body; `tieneReferencia=false` = sin referencia (el cliente cae a la local).
- **POST `/api/medicos/referencias`** (Profesional; 403 si el paciente no es suyo)
  Request: `{ "idPaciente": "UUID", "idEjercicio": "UUID", "referenciaJson": "string" }`

**Response 200 (los tres):**
```json
{
  "tieneReferencia": true,
  "idEjercicio": "UUID",
  "referenciaJson": "string",
  "fechaActualizacion": "ISO-8601",
  "creadaPorProfesional": "bool"
}
```

Además, `GET /api/ejercicios` ahora incluye `patronMovimiento` (slug del catálogo de la app,
nullable): sin patrón no se puede grabar referencia para ese ejercicio.

---

## 4. Sesiones (`/api/sesiones`)

### POST `/api/sesiones`
Save a completed session with results.

**Request:**
```json
{
  "idPaciente": "UUID (required)",
  "fechaHoraInicio": "datetime (defaults to UTC now)",
  "duracionMinutos": "decimal (0.01-9999.99)",
  "estado": "string (max 20, required)",
  "resultados": [
    {
      "idEjercicio": "UUID (required)",
      "repeticionesRealizadas": "short (?0)",
      "repeticionesCorrectas": "short (?0)",
      "porcentajeCorrectitud": "decimal (0-100)",
      "escalaEVA": "int? (0-10, nullable)"
    }
  ]
}
```

**Response 201:**
```json
{
  "idSesion": "UUID",
  "idPaciente": "UUID",
  "fechaHoraInicio": "datetime",
  "duracionMinutos": "decimal",
  "estado": "string",
  "resultados": [
    {
      "idResultado": "UUID",
      "idEjercicio": "UUID",
      "repeticionesRealizadas": "short",
      "repeticionesCorrectas": "short",
      "porcentajeCorrectitud": "decimal",
      "escalaEVA": "int?"
    }
  ]
}
```

**Error 404:** `{ "message": "..." }`

---

### GET `/api/sesiones/historial/{idPaciente}`
Get session history for a patient.

**Path param:** `idPaciente` (UUID)

**Response 200:**
```json
[
  {
    "idSesion": "UUID",
    "idRutina": "UUID? (null = práctica libre)",
    "nombreRutina": "string?",
    "fechaHoraInicio": "datetime",
    "duracionMinutos": "decimal",
    "estado": "string",
    "resultados": [
      {
        "idResultado": "UUID",
        "idEjercicio": "UUID",
        "nombreEjercicio": "string",
        "repeticionesRealizadas": "short",
        "repeticionesCorrectas": "short",
        "porcentajeCorrectitud": "decimal",
        "escalaEVA": "int?"
      }
    ]
  }
]
```

**Error 404:** `{ "message": "..." }`

---

### GET `/api/sesiones/eva/{idPaciente}`
Get EVA (pain scale) history for a patient.

**Path param:** `idPaciente` (UUID)

**Response 200:**
```json
[
  {
    "idSesion": "UUID",
    "fecha": "datetime",
    "idEjercicio": "UUID",
    "nombreEjercicio": "string",
    "escalaEVA": "int"
  }
]
```

**Error 404:** `{ "message": "..." }`

---

### GET `/api/sesiones/cumplimiento/{idPaciente}`
Get compliance percentage for a patient.

**Path param:** `idPaciente` (UUID)

**Response 200:**
```json
{
  "idPaciente": "UUID",
  "porcentajeCumplimiento": "decimal"
}
```

**Error 404:** `{ "message": "..." }`

---

### GET `/api/sesiones/progreso/{idPaciente}`
Aggregated progress report for the patient's Progress screen.

**Path param:** `idPaciente` (UUID)
**Query param:** `tzOffsetMinutes` (int) — device offset from UTC in minutes (e.g. `-180` for Argentina). Defines the day boundaries for streak and weekly activity.

**Response 200:**
```json
{
  "idPaciente": "UUID",
  "sesionesTotales": "int",
  "ejerciciosRealizados": "int",
  "minutosTotales": "decimal",
  "diasActivos": "int",
  "rachaDias": "int",
  "promedioEVA": "decimal? (0-10, null si nunca se reportó dolor)",
  "actividadSemanal": [
    { "fecha": "ISO date (día local)", "minutos": "decimal", "sesiones": "int" }
  ],
  "progresoPorZona": [
    { "zona": "string (nombre del enum ZonaCorporal)", "porcentajeCumplimiento": "decimal (0-100)" }
  ],
  "historialReciente": [ "últimas 10 sesiones, mismo shape que /historial" ]
}
```

Notes:
- `actividadSemanal` always has 7 items in chronological order; the last one is today.
- `rachaDias` is 0 if the last activity was before yesterday.
- `progresoPorZona` only includes zones of **active** routines with at least one session.

**Error 404:** `{ "message": "..." }`

---

## Enums

### ZonaCorporal
```
Hombros, Brazos, Lumbares, Rodillas, Piernas   // zonas amplias legadas (0-4)
Pecho, Abdominales, Biceps, Antebrazos,        // músculos específicos — frente (5-12)
Cuadriceps, Aductores, Abductores, Tibiales
Trapecios, Dorsales, Triceps, Gluteos,         // músculos específicos — espalda (13-18)
Isquiotibiales, Gemelos
```

---

## Type Mapping (C# ? Kotlin)

| C# Type | Kotlin Type |
|---------|-------------|
| `Guid` | `String` (UUID format) |
| `string` | `String` |
| `byte` | `Int` |
| `short` | `Int` |
| `int` | `Int` |
| `int?` | `Int?` |
| `decimal` | `Double` or `BigDecimal` |
| `bool` | `Boolean` |
| `DateTime` | `String` (ISO 8601) or `Instant`/`LocalDateTime` (kotlinx-datetime) |
| `List<T>` | `List<T>` |
| `Guid?` | `String?` |

---

## Notes for Implementation

- All IDs are UUIDs serialized as strings in JSON.
- Dates use ISO 8601 format (e.g., `"2025-01-15T10:30:00Z"`).
- Error responses always have shape `{ "message": "string" }`.
- No authentication tokens are used yet (no Authorization header required).
- The API uses standard HTTP status codes: 200, 201, 401, 404, 409.
