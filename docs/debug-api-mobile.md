# Probar y debugear la API desde la app mobile

> Guía práctica: cómo apuntar la app al backend, probar los flujos principales
> y diagnosticar problemas, tanto desde la app como desde la consola de AWS.

## 1. Configuración: a qué backend apunta la app

La app tiene dos *product flavors* (dimension `backend`). La URL base vive en
**un solo lugar**: `buildConfigField("API_BASE_URL")` en `app/build.gradle.kts`.

| Flavor | URL base | Cuándo usarlo |
|---|---|---|
| `aws` (default) | `https://TU_API_GATEWAY.execute-api.us-east-1.amazonaws.com/` | Backend desplegado en AWS |
| `local` | `http://10.0.2.2:5264/` | Backend corriendo en tu máquina, app en el emulador |

```powershell
# Instalar contra AWS
.\gradlew installAwsDebug

# Instalar contra backend local
.\gradlew installLocalDebug
```

En Android Studio: **Build Variants** → `awsDebug` o `localDebug`.

Notas:
- **Ambos flavors usan el mismo User Pool de Cognito** (`TU_USER_POOL_ID`);
  los tokens sirven en local y en AWS por igual.
- El flavor `local` permite tráfico HTTP en claro (manifest overlay en
  `app/src/local/AndroidManifest.xml`). El `aws` es HTTPS puro.
- Si el stack de AWS se borra y recrea, cambian la URL, el pool id y el client id:
  se actualizan en `app/build.gradle.kts` (URL) y
  `app/src/main/res/raw/amplifyconfiguration.json` (Cognito). Nada más.

## 2. Verificar el backend antes de tocar la app

```powershell
curl https://TU_API_GATEWAY.execute-api.us-east-1.amazonaws.com/health
```

- **200** → la API está viva. Ojo: `/health` **no toca la base** a propósito.
- Si `/health` responde pero el resto de los endpoints cuelga o falla:
  **la base de datos está apagada** (es un lab con presupuesto acotado; se
  frena al terminar cada sesión de trabajo). Pedir que la arranquen.

## 3. Usuarios de prueba

Contraseña de todos: `<CONTRASENA_DE_PRUEBA>`

| Email | Rol | Nota |
|---|---|---|
| `juan.perez@email.com` | Paciente | Con rutinas y sesiones cargadas |
| `maria.rodriguez@email.com` | Paciente | |
| `pedro.sanchez@email.com` | Paciente | De Carlos — sirve para probar 403 |
| `ana.garcia@clinica.com` | Profesional | Tiene 2 pacientes asignados |
| `carlos.lopez@clinica.com` | Profesional | |

## 4. Flujos de prueba en la app

### Login con usuario de prueba
1. Welcome → **Comenzar** → desde el registro tocá **Iniciar Sesión**.
2. Entrá con `juan.perez@email.com` / `<CONTRASENA_DE_PRUEBA>`.
3. Resultados posibles:
   - **Dashboard** con cumplimiento real → todo OK.
   - **"Tu cuenta no tiene perfil todavía"** (409) → la ficha existe pero nunca
     se vinculó. Ir a RoleSelection → Paciente → completar el formulario **con
     ese mismo email y contraseña**: el registro detecta la cuenta existente,
     hace sign-in y vincula la ficha (conserva rutinas y sesiones).

### Auto-login
Matar la app y reabrirla → el Splash debe llevar **directo al Dashboard**
(refresh token de 30 días, lo maneja Amplify).

### Guardar una sesión
Completar el flujo de ejercicio hasta el reporte EVA → confirmar.
En Logcat (filtro `okhttp`) debe verse `POST /api/sesiones` → **201**,
con el body **sin** `idPaciente` (la identidad sale del token).

### Rol profesional
Logout (botón en el top bar del Dashboard) → login con
`ana.garcia@clinica.com` → debe caer en el placeholder de profesional.

### Registro con cuenta nueva
RoleSelection → Paciente → email real → llega código de 6 dígitos por mail →
pantalla de confirmación → entra solo al Dashboard (sign-in + provision
automáticos).

### Probar el 403 a propósito
Logueado como `juan.perez`, pedir datos de `pedro.sanchez` (paciente de otro
profesional) debe dar **403**. Si aparece un 403 en uso normal, es un bug de la
app (está pidiendo datos que no le corresponden), no un problema de sesión.

## 5. Debug desde la app (Logcat)

El build debug loguea **todo** el tráfico HTTP (interceptor con `Level.BODY`).

| Filtro Logcat | Qué muestra |
|---|---|
| `okhttp.OkHttpClient` | Request/response completos: URL, headers, body, código HTTP |
| `AuthRepository` | Fallos de sign-in, registro, provision, restore de sesión |
| `SteadyPath` | Errores de inicialización de Amplify |
| `PoseDiag` | Pipeline de pose (no API) |

Con eso se distingue rápido: request mal armado (app), 401/403/409 (auth/permisos),
5xx o timeout (backend/base).

## 6. Debug sin la app (curl)

Aísla si el problema es la API o la app:

```bash
# 1. ¿API viva? (sin auth, sin base)
curl https://TU_API_GATEWAY.execute-api.us-east-1.amazonaws.com/health

# 2. Token de un usuario de prueba (script en el repo del backend)
./Scripts/get-cognito-token.sh juan.perez@email.com '<CONTRASENA_DE_PRUEBA>'

# 3. Endpoint real con token (SÍ toca la base)
curl -H "Authorization: Bearer <token>" \
  https://TU_API_GATEWAY.execute-api.us-east-1.amazonaws.com/api/usuarios/me
```

- 1 responde y 3 cuelga → base apagada.
- 3 da 401 → problema de token (ver tabla de abajo).
- curl anda pero la app no → el bug es de la app.

## 7. Dónde mirar en AWS (región `us-east-1`)

1. **API Gateway → Monitor/Metrics**: `Count`, `4XXError`, `5XXError`,
   `Latency`, `IntegrationLatency`. Primer lugar a mirar: pico de 5xx =
   backend/base caídos; 4xx altos = tokens o permisos.
2. **CloudWatch → Log Groups**:
   - Access logs de API Gateway (un renglón por request).
   - Logs de la aplicación .NET (nombre exacto del log group en
     `docs/DEPLOY.md` del repo del backend) — ahí están los stack traces de los 500.
3. **Cognito → User Pools → `TU_USER_POOL_ID`**: estado de los usuarios
   (`CONFIRMED`/`UNCONFIRMED`), útil para verificar registros o resetear un
   usuario de prueba.
4. **RDS → Databases**: si la instancia dice `Stopped`, ese es el problema.

**Correlacionar app ↔ servidor**: cada respuesta trae el header
`apigw-requestid` (visible en el log de `okhttp`). Buscar ese ID en CloudWatch
encuentra el request exacto del lado del servidor.

## 8. Tabla de errores

| Código | Significado | Qué hace la app | Qué hacer si aparece |
|---|---|---|---|
| **401** | Token faltante, vencido o del tipo equivocado | `TokenAuthenticator` refresca y reintenta **una** vez; si persiste, hay que reloguear | Pegar el token en [jwt.io](https://jwt.io): debe decir `"token_use": "id"`. La API exige el **ID token**, no el access token |
| **403** | Token válido pero sin permisos (`PermissionDeniedException`) | Muestra mensaje, **no reintenta** | Es un bug de la app si pasa en uso normal: está pidiendo datos de otro usuario |
| **409** | Autenticado pero sin ficha (`NotProvisionedException`) | En login: mensaje para completar registro. En registro: provisiona solo | Completar el registro con el mismo email/contraseña |
| **404** en `/api/usuarios/login` o `/registro/*` | Endpoints eliminados en la migración | — | Código viejo llamando rutas muertas; usar Cognito + provision |
| Timeout con `/health` OK | Base de AWS apagada | — | Pedir que arranquen la instancia RDS |

## 9. Arquitectura de red de la app (para tocar con criterio)

Puntos únicos de cambio:

| Qué | Dónde (único lugar) |
|---|---|
| URL base | `app/build.gradle.kts` → flavors `aws`/`local` |
| Config de Cognito | `app/src/main/res/raw/amplifyconfiguration.json` |
| Rutas de endpoints | `data/datasource/RehabApiService.kt` |
| Manejo de respuesta/errores HTTP | `data/datasource/SafeApiCaller.kt` (403/409 → excepciones tipadas, parseo de `{message}`) |
| Header `Authorization` | `data/auth/AuthInterceptor.kt` (ID token; salta `/health`) |
| Retry ante 401 | `data/auth/TokenAuthenticator.kt` (refresh + **un** reintento) |
| Contacto con Amplify/Cognito | `data/auth/CognitoAuthService.kt` (nada más importa tipos de Amplify) |

Regla: los repositorios no repiten manejo de errores — todo pasa por
`SafeApiCaller`. Un endpoint nuevo = ruta en `RehabApiService` + un
`caller.flowCall(...)` en el repo que corresponda.
