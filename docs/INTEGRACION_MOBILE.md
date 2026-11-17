# Integración con el backend: qué cambió y qué hay que hacer en la app

> Documento para el proyecto mobile. Describe los cambios del backend al migrar a
> AWS con autenticación (Fase 2, desplegada el 2026-07-30) y todo lo necesario
> para integrarse, sin necesidad de abrir el repo del backend.

## Resumen en 30 segundos

La API **ya no tiene login propio**. Ahora la app se autentica **directamente contra Amazon Cognito**, recibe un **ID token** (un JWT), y lo manda en cada request:

```
Authorization: Bearer <ID token>
```

Todos los endpoints exigen ese header salvo `GET /health`. Sin él, la respuesta es **401**.

Y lo segundo importante: **la identidad ya no viaja en el body ni en la URL**. Los campos `idProfesional` e `idPaciente` que la app mandaba salieron de los DTOs y de las rutas, porque ahora el backend los saca del token. Mandarlos ya no sirve de nada: el backend los ignora.

## Datos de configuración

| Dato | Valor |
|---|---|
| URL base (AWS) | `https://TU_API_GATEWAY.execute-api.us-east-1.amazonaws.com` |
| Región de Cognito | `us-east-1` |
| User Pool Id | `TU_USER_POOL_ID` |
| App Client Id | `TU_APP_CLIENT_ID` |

Ninguno es secreto: el pool id y el client id viajan en cada request de cualquier app que use Cognito, y están pensados para estar en el binario. **No hay client secret** a propósito: un secreto embebido en un APK lo extrae cualquiera con un descompilador, así que el cliente está configurado como *cliente público*.

Estos valores conviene tenerlos en configuración por variante de build (como ya se hacía con la URL base), no como constantes sueltas.

> **Ojo:** si alguna vez se borra y recrea el stack del backend, la URL, el pool id y el client id **cambian**. Es la única situación en que hay que tocarlos.

---

## 1. Autenticación

### Librería

Usar el SDK oficial de Cognito para Android: **AWS Amplify (Auth)** o **AWS SDK for Android (`CognitoIdentityProviderClient`)**. Amplify es más simple porque maneja solo el almacenamiento seguro de los tokens y la renovación.

No hace falta ninguna credencial de AWS: el sign-in de Cognito es una API pública que solo necesita el client id.

### El flujo completo

```
1. Sign-up en Cognito (email + contraseña)  ──► Cognito manda un código al email
2. Confirmar el código                       ──► la cuenta queda utilizable
3. Sign-in                                   ──► ID token (1 h) + Access token (1 h) + Refresh token (30 días)
4. POST /api/usuarios/provision              ──► crea la ficha del usuario en la base (UNA sola vez)
5. Cada request: Authorization: Bearer <ID token>
6. Cuando el ID token vence, renovarlo con el refresh token (sin volver a pedir la contraseña)
```

Los pasos 1 y 2 son nuevos y no existían: antes el registro era un `POST` al backend.

### ⚠️ El error más común: mandar el ACCESS token

Cognito devuelve **dos** tokens que parecen intercambiables, y hay que mandar el **ID token**.

Es contraintuitivo porque "access token" suena a "el que se usa para acceder". Pero el ID token es el que trae el `email`, que es lo que el backend necesita para dar de alta al usuario.

**Con el access token la API responde 401.** Si algo devuelve 401 con un usuario recién logueado, esto es lo primero para revisar: pegá el token en [jwt.io](https://jwt.io) y fijate que diga `"token_use": "id"`.

En Amplify, el ID token es:

```kotlin
val session = Amplify.Auth.fetchAuthSession() as AWSCognitoAuthSession
val idToken = session.userPoolTokensResult.value?.idToken   // NO accessToken
```

### Manejo de errores de autenticación

| Código | Qué significa | Qué debería hacer la app |
|---|---|---|
| **401** | Falta el token, venció, o es del tipo equivocado | Renovar con el refresh token y reintentar **una** vez. Si vuelve a dar 401, mandar al login |
| **403** | El token es válido, pero ese usuario no puede hacer eso | Mostrar un mensaje. **No reintentar**, no ayuda |
| **409** | Autenticado pero sin ficha en la base | Llamar a `POST /api/usuarios/provision` y reintentar |

Un interceptor de OkHttp que renueve ante 401 y reintente una vez cubre el caso normal. Importante que sea **una** vez: si el token es del tipo equivocado, reintentar en loop no lo arregla.

### El paso de provisión (nuevo, y fácil de olvidar)

Una cuenta recién creada en Cognito **existe como cuenta pero todavía no tiene ficha** en la base del backend. Hasta que la tenga, todos los endpoints devuelven **409**:

```json
{ "message": "El usuario esta autenticado pero no fue provisionado. Llamar a POST /api/usuarios/provision." }
```

Se resuelve con una llamada:

```http
POST /api/usuarios/provision
Authorization: Bearer <ID token>
Content-Type: application/json
```

**Body para un paciente:**
```json
{
  "rol": "Paciente",
  "nombre": "Juan Pérez",
  "edad": 35,
  "idProfesionalAsignado": "a1000000-0000-0000-0000-000000000001"
}
```
(`idProfesionalAsignado` es opcional: un paciente puede registrarse antes de que lo tome un profesional.)

**Body para un profesional:**
```json
{
  "rol": "Profesional",
  "nombre": "Dra. Ana García",
  "especialidad": "Kinesiología"
}
```

**Respuesta (200):**
```json
{
  "idUsuario": "b1000000-0000-0000-0000-000000000001",
  "nombre": "Juan Pérez",
  "email": "juan.perez@email.com",
  "rol": "Paciente",
  "fechaRegistro": "2025-03-01T14:00:00Z"
}
```

Notas:

- **El email no va en el body**: sale del token, ya verificado por Cognito. Nadie puede registrarse con el email de otro.
- **La contraseña tampoco**: el backend nunca la ve.
- **Es idempotente.** Si ya está provisionado, devuelve la ficha y no hace nada. Lo más simple para la app es llamarlo al arrancar, después del sign-in, sin llevar la cuenta de si ya lo hizo.
- Si el email ya existía en la base (los usuarios de prueba), **vincula** la ficha existente en lugar de crear una nueva, así que el usuario conserva sus rutinas y sesiones.

### Saber quién soy

```http
GET /api/usuarios/me
Authorization: Bearer <ID token>
```

Devuelve lo mismo que `provision`. Sirve para saber el `rol` (`"Paciente"` o `"Profesional"`) y decidir qué pantallas mostrar, y para obtener el `idUsuario`, que es el GUID con el que el backend referencia todo.

---

## 2. Cambios incompatibles endpoint por endpoint

### Eliminados

| Endpoint viejo | Reemplazo |
|---|---|
| `POST /api/usuarios/login` | Sign-in contra Cognito |
| `POST /api/usuarios/registro/paciente` | Sign-up en Cognito + `POST /api/usuarios/provision` |
| `POST /api/usuarios/registro/profesional` | Ídem |

Estos endpoints **ya no existen** (devuelven 404).

### Ruta cambiada

| Antes | Ahora |
|---|---|
| `GET /api/medicos/{idProfesional}/pacientes` | `GET /api/medicos/pacientes` |

El id salió de la URL porque el profesional es el del token. Con la ruta vieja alcanzaba con conocer el GUID de un profesional para leer su lista de pacientes con nombres y emails.

### Bodies que perdieron campos

En los cuatro casos el campo **se ignora si se manda**; el backend usa el del token.

| Endpoint | Campo que se fue |
|---|---|
| `POST /api/medicos/rutinas` | `idProfesional` |
| `POST /api/medicos/tolerancias` | `idProfesional` |
| `PATCH /api/rutinas/{idRutina}/estado` | `idProfesional` |
| `POST /api/sesiones` | `idPaciente` |

En `POST /api/sesiones` esto significa que **una sesión siempre se registra a nombre del usuario logueado**. Ya no se puede cargar una sesión "de" otro paciente.

### Sin cambios de forma, pero ahora con reglas de acceso

Estos endpoints se llaman igual que antes, pero **pueden devolver 403** donde antes siempre devolvían 200:

- `GET /api/rutinas/paciente/{idPaciente}`
- `GET /api/rutinas/{idRutina}`
- `GET /api/rutinas/{idRutina}/cumplimiento`
- `GET /api/rutinas/cumplimiento/paciente/{idPaciente}`
- `GET /api/sesiones/historial/{idPaciente}`
- `GET /api/sesiones/eva/{idPaciente}`
- `GET /api/sesiones/cumplimiento/{idPaciente}`

**La regla:** podés ver los datos de un paciente si **sos ese paciente** o si **sos el profesional que lo tiene asignado**. Cualquier otro caso es 403.

En la práctica, para la app esto significa: un paciente solo debería pedir su propio `idUsuario`, y un profesional solo los de los pacientes que le devuelve `GET /api/medicos/pacientes`. Si aparece un 403 acá, es un bug de la app (está pidiendo datos que no le corresponden), no un problema de sesión.

---

## 3. Referencia de endpoints

Todos requieren `Authorization: Bearer <ID token>` salvo el primero. Todo el JSON usa **camelCase**. Las fechas son **UTC en ISO 8601** (`2026-07-30T16:36:02.0253550Z`) — mandarlas en UTC, porque el backend las guarda así.

### Público

| Método | Ruta | Respuesta |
|---|---|---|
| `GET` | `/health` | `{ "status": "ok", "version": "1.0.0.0", "utc": "..." }` |

Útil para chequear conectividad antes de pedir credenciales.

### Usuarios

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| `POST` | `/api/usuarios/provision` | cualquiera | Crea o vincula la ficha. Idempotente |
| `GET` | `/api/usuarios/me` | cualquiera | Identidad y rol del usuario actual |

### Ejercicios

| Método | Ruta | Rol |
|---|---|---|
| `GET` | `/api/ejercicios` | cualquiera |

Catálogo completo. Cada elemento:

```json
{
  "idEjercicio": "c1000000-0000-0000-0000-000000000001",
  "nombre": "Sentadilla",
  "descripcion": "...",
  "urlVideoExplicativo": null,
  "zonasAfectadas": [3, 4],
  "nivelDificultad": "Medio"
}
```

`zonasAfectadas` es un enum serializado como número: `0 Hombros`, `1 Brazos`, `2 Lumbares`, `3 Rodillas`, `4 Piernas`, `5 Pecho`, `6 Abdominales`, `7 Biceps`, `8 Antebrazos`, `9 Cuadriceps`, `10 Aductores`, `11 Abductores`, `12 Tibiales`, `13 Trapecios`, `14 Dorsales`, `15 Triceps`, `16 Gluteos`, `17 Isquiotibiales`, `18 Gemelos`. Los valores 0-4 son zonas amplias legadas; los ejercicios nuevos usan los músculos específicos.

> Este endpoint devuelve la entidad tal cual, así que el JSON incluye además algunas colecciones vacías (`resultados`, `rutinaEjercicios`, `tolerancias`) que la app puede ignorar. `urlVideoExplicativo` va a cobrar sentido en la Fase 3 (videos en S3).

### Profesional

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/medicos/pacientes` | Los pacientes a cargo del profesional del token |
| `POST` | `/api/medicos/rutinas` | Asignar una rutina a un paciente |
| `POST` | `/api/medicos/tolerancias` | Ajustar los ángulos tolerados de un ejercicio para un paciente |

Los tres devuelven **403** si el token es de un paciente.

**`GET /api/medicos/pacientes`** →
```json
[{
  "idPaciente": "b1000000-0000-0000-0000-000000000001",
  "nombre": "Juan Pérez",
  "email": "juan.perez@email.com",
  "edad": 35,
  "cantidadRutinasActivas": 2,
  "fechaUltimaSesion": "2026-05-20T00:00:00Z"
}]
```

**`POST /api/medicos/rutinas`** (201) — el paciente tiene que estar asignado a este profesional, si no es 403:
```json
{
  "idPaciente": "b1000000-0000-0000-0000-000000000001",
  "nombreRutina": "Rehabilitación de rodilla — semana 3",
  "ejercicios": [
    { "idEjercicio": "c1000000-0000-0000-0000-000000000001", "repeticionesObjetivo": 10, "series": 3 }
  ]
}
```

**`POST /api/medicos/tolerancias`** (200):
```json
{
  "idPaciente": "b1000000-0000-0000-0000-000000000001",
  "idEjercicio": "c1000000-0000-0000-0000-000000000001",
  "anguloMinimo": 10.0,
  "anguloMaximo": 90.0
}
```

### Rutinas

| Método | Ruta | Quién |
|---|---|---|
| `GET` | `/api/rutinas/paciente/{idPaciente}?soloActivas=true` | el paciente o su profesional |
| `GET` | `/api/rutinas/{idRutina}` | ídem |
| `GET` | `/api/rutinas/{idRutina}/cumplimiento` | ídem |
| `GET` | `/api/rutinas/cumplimiento/paciente/{idPaciente}?soloActivas=true` | ídem |
| `PATCH` | `/api/rutinas/{idRutina}/estado` | **solo el profesional a cargo** |

`PATCH .../estado` — body: `{ "activa": false }`. Un paciente puede *ver* sus rutinas pero no activarlas ni desactivarlas: eso da 403.

Un paciente puede tener **varias rutinas activas a la vez**; no es un error.

### Sesiones

| Método | Ruta | Quién |
|---|---|---|
| `POST` | `/api/sesiones` | **solo el propio paciente** |
| `GET` | `/api/sesiones/historial/{idPaciente}` | el paciente o su profesional |
| `GET` | `/api/sesiones/eva/{idPaciente}` | ídem |
| `GET` | `/api/sesiones/cumplimiento/{idPaciente}` | ídem |

**`POST /api/sesiones`** (201) — **sin `idPaciente`**:
```json
{
  "idRutina": "d1000000-0000-0000-0000-000000000001",
  "fechaHoraInicio": "2026-07-30T14:00:00Z",
  "duracionMinutos": 15.5,
  "estado": "Completada",
  "resultados": [{
    "idEjercicio": "c1000000-0000-0000-0000-000000000001",
    "repeticionesRealizadas": 10,
    "repeticionesCorrectas": 8,
    "porcentajeCorrectitud": 80.0,
    "escalaEVA": 3
  }]
}
```

- `idRutina` es opcional (`null` = práctica libre). Si se manda, tiene que ser una rutina **de ese paciente**, si no es 404.
- `escalaEVA` es opcional (0-10, dolor). `null` si no se reporta.
- `estado` es texto libre de hasta 20 caracteres; los valores en uso son `"Completada"` y `"Abandonada"` (el cálculo de cumplimiento cuenta las completadas sobre el total).
- Un profesional llamando a este endpoint recibe **403**.

---

## 4. Desarrollo local

El backend local expone la misma API y **también valida tokens de Cognito reales**, contra el mismo User Pool. O sea: los mismos tokens sirven en local y en AWS, no hay modo de prueba sin autenticación.

| Escenario | URL base |
|---|---|
| Emulador Android contra backend local | `http://10.0.2.2:5264` |
| Dispositivo físico contra backend local | `http://<IP-de-la-Mac>:5264` |
| AWS | `https://TU_API_GATEWAY.execute-api.us-east-1.amazonaws.com` |

`localhost` no funciona desde el emulador: ahí `localhost` es el emulador mismo. Y para las variantes locales hace falta permitir tráfico HTTP en claro (`android:usesCleartextTraffic="true"` en el manifest de debug, o una `network-security-config` restringida a esa IP). Contra AWS es HTTPS y no hace falta.

### Usuarios de prueba

Contraseña de todos: `<CONTRASENA_DE_PRUEBA>`

| Email | Rol |
|---|---|
| `ana.garcia@clinica.com` | Profesional (tiene 2 pacientes asignados) |
| `carlos.lopez@clinica.com` | Profesional |
| `juan.perez@email.com` | Paciente (con rutinas y sesiones cargadas) |
| `maria.rodriguez@email.com` | Paciente |
| `pedro.sanchez@email.com` | Paciente (de Carlos, sirve para probar los 403) |

Para probar con `curl` sin la app, en el repo del backend hay un script que imprime un ID token:

```bash
./Scripts/get-cognito-token.sh juan.perez@email.com '<CONTRASENA_DE_PRUEBA>'
```

Un caso útil para verificar el manejo de 403 en la app: `juan.perez` pidiendo el historial de `pedro.sanchez` (que es paciente de otro profesional) tiene que dar **403**.

> **La API de AWS solo responde con la base encendida.** Es un lab con presupuesto acotado, así que la instancia se frena al terminar cada sesión de trabajo. Si `GET /health` responde 200 pero el resto tarda y falla, es eso: hay que pedir que arranquen la base. `/health` no toca la base a propósito, justamente para poder distinguir este caso.

---

## 5. Checklist de integración

- [ ] Agregar el SDK de Cognito (Amplify Auth) y configurarlo con el pool id, el client id y la región.
- [ ] Pantallas de sign-up (con confirmación por código de email) y sign-in contra Cognito.
- [ ] Guardar los tokens de forma segura (Amplify ya lo hace; si se usa el SDK crudo, `EncryptedSharedPreferences`).
- [ ] Interceptor HTTP que agregue `Authorization: Bearer <ID token>` — **el ID token, no el access token**.
- [ ] Interceptor que ante 401 renueve con el refresh token y reintente **una** vez.
- [ ] Llamar a `POST /api/usuarios/provision` después del primer sign-in (o ante cualquier 409) y reintentar.
- [ ] `GET /api/usuarios/me` al arrancar para saber el rol y el `idUsuario`.
- [ ] Quitar los campos `idProfesional`/`idPaciente` de los bodies que los llevaban.
- [ ] Cambiar `GET /api/medicos/{id}/pacientes` por `GET /api/medicos/pacientes`.
- [ ] Borrar las llamadas a `/api/usuarios/login` y `/api/usuarios/registro/*`.
- [ ] Manejar 403 como error de permisos (mensaje al usuario, sin reintentar).
- [ ] Mover pool id, client id y URL base a configuración por variante de build.

---

## 6. Preguntas que probablemente surjan

**¿Por qué la app habla con Cognito y no con el backend para el login?**
Así el backend nunca ve las contraseñas: no puede filtrarlas ni loguearlas por accidente. Cognito además maneja la verificación de email, el bloqueo por intentos fallidos y la renovación de tokens.

**¿Qué pasa si el token vence en medio de una operación?**
La API devuelve 401. Con el refresh token se obtiene uno nuevo sin intervención del usuario (dura 30 días). Por eso conviene el interceptor: el usuario no debería notar nada.

**¿Se puede cerrar sesión "de verdad"?**
El sign-out local borra los tokens del dispositivo. Para invalidar el refresh token en el servidor está `Amplify.Auth.signOut(global = true)`. El ID token que ya se emitió sigue siendo válido hasta que expire (máximo 1 hora): es una característica de los JWT, no un bug.

**¿El GUID del usuario cambia?**
No. El `idUsuario` que devuelve `/api/usuarios/me` es estable y es el que referencian rutinas y sesiones. Es distinto del `sub` de Cognito (que es el id **en Cognito**); la app debería usar el `idUsuario` para todo lo que sea la API.

**¿Hace falta CORS?**
No: es una restricción de navegadores y una app mobile no la aplica.

---

## Referencias en el repo del backend

- `docs/DEPLOY.md` — runbook, verificación con `curl`, troubleshooting de autenticación.
- `docs/APUNTES_INFRA.md` — explicación de Cognito, JWT y autorizadores desde cero.
- `docs/BITACORA_AWS.md` — por qué se tomó cada decisión.
