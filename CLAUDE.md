# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**DOMI.IA** (formerly SteadyPath) — an Android rehabilitation app (thesis project) that connects patients with their physiotherapists. Patients follow exercise routines, log pain levels (EVA scale 0–10), and view session history. Professionals manage routines and tolerances via a separate backend.

## Build & Run Commands

```powershell
# Build debug APK
.\gradlew assembleDebug

# Run unit tests
.\gradlew test

# Run instrumented tests (requires connected device/emulator)
.\gradlew connectedAndroidTest

# Run a single unit test class
.\gradlew test --tests "com.steadypath.app.ExampleUnitTest"

# Install on connected device
.\gradlew installDebug

# Clean build
.\gradlew clean
```

The app targets **minSdk 29**, **compileSdk 36**, JVM target 11.

## Architecture

Clean Architecture with three layers inside `app/src/main/java/com/steadypath/app/`:

```
domain/          # Pure Kotlin — models, repository interfaces, use cases
data/            # Retrofit impl — DTOs, mappers, repository impls, Hilt DI module
presentation/    # Compose UI — screens, ViewModels, navigation, shared components
```

**Data flow:** `Screen` → `ViewModel` → `UseCase` → `Repository interface` → `RepositoryImpl` (Retrofit) → backend.

**State:** ViewModels expose `UiState<T>` (sealed class: `Idle | Loading | Success | Error`) collected via `collectAsStateWithLifecycle` in screens.

**DI:** Single Hilt `NetworkModule` (`SingletonComponent`) provides `OkHttpClient`, `Retrofit`, `RehabApiService`, all three repositories, and `Dispatchers.IO`. `LocalModule` provides the Room database, its DAOs and the offline outbox (see *Offline mode*).

**Navigation:** Single-activity (`MainActivity`) with `NavGraph.kt` wiring all routes. Uses **type-safe Navigation Compose** — routes are `@Serializable` `data object`/`data class` declarations in `NavRoutes.kt` (requires the `kotlin-serialization` plugin). Screens are wired with `composable<Route> { ... }` and arguments read via `backStackEntry.toRoute<Route>()`. The graph is split into three nested graphs (`navigation<Graph>(startDestination = …)`): `AuthGraph`, `PatientGraph`, and `ProfesionalGraph`. On auth success, navigation jumps to the target graph clearing the auth stack (`popUpTo(AuthGraph) { inclusive = true }`).

## Navigation Flow

```
AuthGraph:    Splash → Login → RoleSelection → PatientRegistration ─┐
                                             → DoctorRegistration  ─┼─→ (PatientGraph | ProfesionalGraph)
                    └─ (login exitoso) ───────────────────────────────┘

PatientGraph: Dashboard → RoutineList → RoutineDetail → ExerciseDetail → PrepareSpace → ExerciseCountdown → RealtimeExercise → PainReport → SessionSummary
                        └→ CreateRoutine (picker de catálogo + series/reps + confirmación) → RecordOwnReference (por ejercicio)
              RoutineDetail → ExerciseList (catálogo completo)
              ExerciseDetail → (sin referencia propia) diálogo → RecordOwnReference

ProfesionalGraph: ProfessionalHome → PatientList → PatientDetail → (AssignRoutine | ChooseAssignMethod | ChooseTemplateForPatient
                                   | ChooseExerciseForReference → RecordPatientReference)
                  ProfessionalHome → RoutineTemplateLibrary → CreateTemplate / ChoosePatientForTemplate
                  ProfessionalHome → ProfessionalProfile → (ChangePassword | NotificationSettings | Privacy | ProfessionalCameraPreference)
```

Navigate by constructing the route object directly — pass constructor args for parameterized routes:
```kotlin
// Good — type-safe, args are real Kotlin parameters
navController.navigate(ExerciseDetail(exerciseId))
navController.navigate(RealtimeExercise(exerciseId, exerciseName))
// Not this
navController.navigate("exercise_detail/$exerciseId")
```

## Vision / Pose Estimation Pipeline

The realtime exercise screen runs **YOLOv8-Pose** on the camera feed to draw the user's skeleton. The pipeline is wired end-to-end, **including the movement-comparison layer** (DTW + shape scoring + rep counting) — see [docs/comparacion-movimiento.md](docs/comparacion-movimiento.md) for the reference format, rep-counting design decisions and current tolerances.

**Data flow:**
```
CameraX ImageAnalysis (1-thread executor, KEEP_ONLY_LATEST)  → backpressure by frame drop
  → PosePreprocessor   (YUV→RGB→letterbox 640²→float32 NHWC /255 ByteBuffer + LetterboxParams)
  → YoloPoseEstimator  (TFLite Interpreter, GPU delegate → CPU/XNNPACK fallback)
  → PosePostprocessor  (decode [56,8400] channel-major, conf filter, NMS, main person, de-letterbox)
  → PoseFrame (domain) → RealtimeExerciseViewModel → StateFlow<PoseFrame?> → PoseOverlay
                       └→ DtwMovementComparator → ComparisonResult → StateFlow<RealtimeUiState>
```

The pose rides its **own** `StateFlow`, separate from `RealtimeUiState` — at ~20 fps, folding it into the shared UI state recomposed the badge, rep counter and button on every frame. `PoseOverlay` takes it as a `() -> PoseFrame?` lambda read *inside* the `Canvas`, so a new frame invalidates only the draw phase. Screens must collect it **without `by`** and never read `.value` during composition, or the optimization is silently lost.

**Key conventions:**
- **`PoseModelSpec` is the single source of truth for the model contract.** Asset name, input size, normalization, channel order, output layout, **coordinate space**, keypoint count and thresholds all live there; preprocessor, postprocessor and estimator derive every constant from it. **Swapping models = editing `PoseModelSpec.ACTIVE`** (provided via `VisionModule`), not the pipeline.
- **`coordinateSpace` is the classic export gotcha.** Ultralytics' **TFLite** export normalizes box and keypoint coordinates to `[0,1]` of the input tensor (unlike the ONNX export, which leaves them in pixels) for int8 numerical stability. `PosePostprocessor.decode()` multiplies by `spec.coordinateScale` to bring everything back to input-tensor pixels before NMS and de-letterboxing. If the skeleton collapses into a corner, this is the field to check. Derived values (`outputChannels`, `outputDetections`, expected shapes) are computed — e.g. anchors come from the input size via the YOLOv8 strides 8/16/32 (640 → 8400).
- **Model contract (verified against the `.tflite`):** input `[1,640,640,3]` float32 NHWC, normalized `/255`; output `[1,56,8400]` float32, **no embedded NMS** (`[cx,cy,w,h,score,(x,y,conf)×17]`). `YoloPoseEstimator.verifyContract()` re-asserts this at init against the spec — a mismatched re-export fails fast with a message naming the `PoseModelSpec` field to fix. Only **float32 I/O** is supported (internal int8 quantization is fine).
- **Layering:** `PoseFrame`/`Keypoint`/`KeypointType` are pure-Kotlin domain models (keypoints normalized to the original frame). Anything touching `ImageProxy`/`Bitmap`/`Interpreter` lives in `data/vision/` — **never put Android/TFLite types in `domain`**.
- **Keypoints are deliberately not clamped to `[0,1]`.** A joint outside the frame must read as outside; clamping pinned it to the border, which drew bones snapping to the edge and — worse — disguised coordinate-space bugs as plausible skeletons. Consumers clip; `PoseOverlay` drops anything projecting beyond the canvas.
- **Diagnosing a blank overlay:** `PoseDiagnostics` (tag `PoseDiag`, every 30 frames, `BuildConfig.DEBUG` only) logs the **raw, untransformed** `cx,cy,w,h` plus how many keypoints clear the confidence threshold and how many land inside the frame. It lives outside `PosePostprocessor` because that class must stay pure for JVM tests. It flags a `coordinateSpace` mismatch explicitly.
- **`PosePostprocessor` is a pure, dependency-free class** (operates on `FloatArray`) so it's unit-tested on the JVM — see `PosePostprocessorTest`. Keep decode/NMS/de-letterbox logic there, not in the estimator.
- **Threading:** inference runs **synchronously inside the CameraX analyzer thread** (off main); `KEEP_ONLY_LATEST` drops frames under load. Do not move inference into `viewModelScope` — that breaks the backpressure model.
- **Estimator lifecycle is reference-counted.** `YoloPoseEstimator` is a `@Singleton`; `acquire()` registers a user and lazily builds the `Interpreter` for the first one (blocking, background thread); `release()` drops a user and closes interpreter + delegate only when the count hits zero. ViewModels never call those directly: each injects its own `PoseEstimatorSession` (unscoped) and calls `start(viewModelScope)` / `stop()` — both idempotent, and a `stop()` that lands mid-load releases as soon as the load finishes, so one session can never contribute more or less than one reference. This replaced the per-VM `warmUp()`/`close()` pairs, which broke whenever two camera screens were alive at once (e.g. `PrepareSpace` in the back stack while the since-removed `CalibrationCapture` closed the singleton). `PoseEstimator.estado` (`Inactivo | Cargando | Listo | Error`) is observable; screens show it through `CameraStatusOverlay`. `estimate()` still drops frames (returns null) when the interpreter is absent: a stale analyzer frame must never re-create the model, that leaked interpreter+GPU delegate was the cause of permanent app-wide slowdown.
- **Camera — both lenses are supported; always compose them through `PoseCameraView`.** `CameraPreview` and `PoseOverlay` must never be called side by side again: the front lens arrives **mirrored in the preview but not in the analysis frame**, so the overlay has to flip x (`1 - kp.x`) to match, and wiring the lens into one without the mirror into the other draws the skeleton on the wrong side *and still compiles*. `PoseCameraView` derives both from a single `CameraLens`, and `CameraLens.espejaElPreview()` is the one place that decides mirroring. The flip is **display-only** — the comparator keeps reading the unflipped domain keypoints — but do **not** conclude that mirroring is harmless to comparison: `DefaultAngleExtractor` produces **signed** angles (`atan2(cross, dot)`, (−π, π]) and a mirrored execution inverts their sign, which is why `MovementPatternCatalog` keeps left/right as separate patterns. References stay interchangeable between lenses only if both deliver the same handedness to `ImageAnalysis` (expected of CameraX: the preview mirrors, the analysis frame does not). **That is unverified on a device.** If it turned out false the comparison would fail silently, returning wrong corrections rather than an error. Defaults: `FRONTAL` for the patient (screen faces them, so they can see the skeleton, rep counter and emergency button), `TRASERA` for the professional (holds the phone pointed at someone else). Resolution order is the per-flow choice (`SessionCameraHolder`, cleared in the camera screen's `onCleared`) over the persisted per-role preference (`CameraPreferencesStore`), combined in `GetEffectiveCameraLensUseCase`. `CameraLensViewModel.lente` is **`null` until resolved** and `PoseCameraView` composes nothing until then: opening with a provisional lens and swapping a frame later forces a rebind, and on the emulator (both lenses mapped to the same host webcam) that fast swap wedges the camera HAL until reboot. `CameraPreview` binds inside `DisposableEffect(lensFacing)` (a lens change unbinds and rebinds), unbinds **only its own use cases** (never `unbindAll()` — the provider is process-wide and would steal the next screen's camera mid-transition), falls back to the other lens if the requested one doesn't exist (reported via `onLensResolved`, so the mirror follows the *effective* lens) and surfaces bind failures through `onCameraError` instead of a silent black box.

> **Emulator setup for the camera:** map only **one** lens to the host webcam (Back = `Webcam0`, Front = `Emulated`) and **Cold Boot** after changing it (`fastboot.forceFastBoot=yes` ignores camera changes otherwise). With both lenses on `webcam0`, switching lenses reopens the same host device and the emulated HAL fails with `CAMERA_IN_USE` / "No available camera can be found" for the rest of the session.

**Movement comparison (implemented):** `DtwMovementComparator` (bound in `VisionModule`) chains angle extraction → rep segmentation (`PeakValleyRepetitionSegmenter`, thresholds derived from the reference amplitude) → DTW + shape scoring against the exercise reference. References resolve **API-first** via `RemoteFirstReferenceRepository` (patient's own reference from `GET /api/pacientes/referencias/{idEjercicio}` → disk cache in `filesDir/references/` → bundled `assets/references/{exerciseId}.json` as last fallback). References authored in any anchoring are rotated to the segmenter's valley convention at load (`ReferenceAlignment`); the first closed segment counts as a rep but is not scored. Full rationale, tolerances and the add-a-reference checklist live in [docs/comparacion-movimiento.md](docs/comparacion-movimiento.md). `NoOpMovementComparator` remains only as a fallback to disable comparison.

**Reference recording (in-app):** both roles can record a patient's reference with the camera — patient from `CreateRoutine` («Grabar referencia» on each draft exercise card), professional from `PatientDetail` → exercise picker (`ChooseExerciseForReference`). `ExerciseDetail` no longer has a record button: «Comenzar» goes through `ExerciseDetailViewModel.startGate` (`HasOwnReferenceUseCase` = `getReference() != null` **and** provenance ≠ `EMPAQUETADA`) and, with no own reference, shows a blocking dialog that sends to `RecordOwnReference`; exercises without `patronMovimiento` pass straight through. That is the **only** place the patient is asked: `PrepareSpace` used to also offer a range calibration (`CalibrationPromptDialog` → `CalibrationCapture`/`CalibrationSuccess`), which was removed as a duplicate — with an own reference the amplitude already comes from the patient's own movement. `DtwMovementComparator` still honors a stored `tieneCalibracion` tolerance if the backend has one. `RecordReferenceScreen` mirrors `RealtimeExerciseScreen` (3:4 viewer + `CameraStatusOverlay`, status badge, `n / TARGET_REPS` counter, **no emergency button**), offers a `CameraLensPicker` while idle (per-flow choice, hidden during the take — the patient's path from `CreateRoutine` has no other picker) and has **no manual selection step**: the take auto-closes at `TARGET_REPS` (5) — or on «Terminar» with ≥3 reps — and the ViewModel immediately runs `ReferenceRepSelector` (DTW-medoid, outlier discard at 2.0× median — ported from modelosCV `src/references/`), validates cycle closure (≤0.35 rad) and uploads. Fewer than 3 reps or a non-closing cycle discards the take and asks to re-record. The screen speaks the rep count and status messages through `ReferenceCaptureViewModel.announcements` → `FeedbackEffects.speakAnnouncement`, because whoever records is usually away from the phone. **Rep counting while recording uses `AdaptiveRestRepetitionSegmenter` for every pattern, not the live `PeakValleyRepetitionSegmenter`**: with no reference there is no amplitude to scale thresholds from, and the live defaults (0.35 rad prominence) counted zero reps for a reduced-mobility patient. **There is no minimum amplitude, by design**: the reference exists to personalize corrections, so whoever can barely squat or half-raise their arms must still be able to record it. The only floor is telling movement from pose jitter: exit rest at `max(4σ, 0.08 rad)`, with σ from a stillness-gated calibration (sliding 1.5 s window accepted at ≤0.12 rad, timeout 6 s). An excursion counts if it lasts ≥600 ms and has pulse shape (`RepShapeGate.score`, best NCC vs raised-cosine/Tukey templates so holds at the extreme pass, plus `peakCount == 1` against two glued reps). It closes on return to the calibrated rest, at the valley when the next rep already started, or on settling near rest (relative to the extreme, capped by `FIRST_LAST_TOL_RAD`), re-anchoring rest there, because arms and legs never return within ±2–3° of the initial rest. Settling far away after coming down is a posture change and is discarded. Without an amplitude floor a slow adjustment counts like a tiny rep. So a rep ≥3× everything accepted retracts the earlier ones (`RecordingSegmenterListener.onRetracted`; the VM drops them and says it restarts counting) and may change the direction; smaller opposite-direction reps are `WRONG_DIRECTION`. Regression-tested against real modelosCV keypoints in `RecordingSegmenterRealDataTest` (squat, arms, heels, knee extension, at 30/15/10 fps, never above the visible count); decisions are logged under `RecSeg` (debug). Anchoring in rest means the first rep is already complete even when rest is the angle's maximum (squat); `ReferenceAlignment` rotates it to the valley convention at load. Which angles to measure comes from `Exercise.patronMovimiento` (backend slug → `MovementPatternCatalog`); no slug = recording not offered. Decisions and modelosCV parity table: [docs/referencias-dtw-en-app.md](docs/referencias-dtw-en-app.md). The DEBUG-only `ReferenceCapture` screen (manual pattern + UUID, file export) still exists for authoring bundled assets and keeps the manual rep list.

**Model asset:** `app/src/main/assets/student_pruned_ft_deploy_v3_int8.tflite` (pruned student model, INT8 weights with float32 I/O), loaded via `FileUtil.loadMappedFile`.

> **v2 rendered a collapsed skeleton and the cause was the model, not this pipeline.** Pruning at 30% destroyed the COCO prior and the recovery fine-tune used only in-domain data, so outside that domain the keypoints bunched into a vertical stripe. v3 retrains on a mixed dataset and fixes it. The A/B that proved it is worth repeating whenever the model changes: drop the candidate in `assets/`, point `assetFileName` at it, and read the `Rango crudo` line from `PoseDiag` — keypoints should span **≥0.75** of the box on *both* axes. Note the on-device ratio divides by the **predicted** box, which a broken model also inflates, so it understates damage; rigorous measurement needs ground-truth boxes offline. `noCompress += "tflite"` in `build.gradle.kts` keeps it memory-mappable.

> **`.tflite` files are not versioned** (`*.tflite` is git-ignored — they weigh several MB). Drop the model into `app/src/main/assets/` and point `PoseModelSpec.ACTIVE.assetFileName` at it. A fresh clone has no model: the app compiles but `YoloPoseEstimator` fails at init until the asset is present.

## Backend API

Base URL is hardcoded in `NetworkModule` as `https://10.0.2.2:7031/` (Android emulator loopback to localhost port 7031). The backend must be run with the **https** Kestrel profile in Visual Studio (not IIS Express — IIS Express rejects `Host: 10.0.2.2` at the HTTP.sys level). The OkHttpClient trusts all SSL certificates — intentional for local dev with a self-signed cert.

Full API contract is documented in [`docs/api-summary-for-mobile.md`](docs/api-summary-for-mobile.md). Key points:
- All IDs are UUID strings.
- No auth tokens — no `Authorization` header needed.
- Error responses always `{ "message": "string" }`.
- Two user roles: `Paciente` and `Profesional`.

## Offline mode (modo sin conexión)

The patient can do their whole exercise flow without network; what they do is queued and uploaded when the connection returns. The professional can only **read** what they already opened; every professional write still needs network (it affects someone else, and queueing it risks overwriting changes made from another device). Still online-only for everyone: first sign-in on a device, sign-up, password, account deletion, consent, invitations, unlinking the professional, "Mis datos" and exercise videos (signed S3 URLs expire in minutes, so they are never cached).

**Reads — `LecturaConCache` (`data/local/`).** Repositories wrap their calls in `lectura.flujo(...)` (data with its own Room table) or `lectura.instantanea(...)` (server-computed aggregates stored as the DTO JSON, by key). The policy is the pure function `flujoConCache`, JVM-tested in `FlujoConCacheTest`:
1. local copy → emitted immediately;
2. copy **and** known-offline → stop there (no waiting for a doomed call); without a copy the call is attempted anyway;
3. server answers → saved and emitted;
4. transient failure with a copy → nothing more is emitted (the screen keeps the copy);
5. **403/404 → the copy is deleted and the error emitted.** A patient who revoked consent or was unlinked must not stay visible from the professional's phone. `MedicRepositoryImpl.getMyPatients` additionally purges everything stored about patients that left the list or show `consentimientoRevocado` (`pacientesAOlvidar`).

It emits **once or twice, never zero**. ViewModels must therefore tolerate two emissions and must not `first()` these flows (they would only ever see the stale copy). `DashboardViewModel` uses `combine` over them for that reason — collecting them one after another made the home screen wait for every network call before showing anything.

**Room (`DomiDatabase`).** `ejercicios`, `rutinas` + `rutina_ejercicios`, `sesiones` + `sesion_resultados` (both parents carry `pendienteDeSubir`), `tolerancias_propias`, `instantaneas`, `operaciones_pendientes`. Routines and sessions have real tables (not snapshots) so that what was created offline shows up in lists, and so `getRoutine(id)` resolves from the list without having opened each routine online. An `instantaneas` row with empty `json` is a "this list was synced at least once" mark: it tells an empty list from one never fetched. Version 1, destructive fallback — **write a real migration before bumping the version**, or the outbox is lost on upgrade. Everything is the signed-in user's data: `BaseLocalDelUsuario` is a `UserScopedCache`, so sign-out wipes it, **including whatever was never uploaded** (the sign-out dialog warns how many changes would be lost).

**Writes — outbox (`data/sync/`).** `ColaDeOperaciones` holds `SESION`, `RUTINA_PROPIA`, `REFERENCIA_PROPIA` and `PERFIL_PACIENTE`. On a *retryable* failure (`EnvioDePendientes.esReintentable`: no network, 5xx, 401, not provisioned) the repository applies the change locally, enqueues the original request and reports success — except sessions, which keep reporting `SesionEnEsperaException` because `PainReportViewModel` words its message from it. `Sincronizador` sends in **arrival order and stops at the first retryable error**. Order is load-bearing: the backend rejects (404) a session whose `idRutina` it does not know, so a routine created offline must arrive before the sessions done with it. A definitive rejection leaves the queue and `deshacer` removes the local row the phone had assumed. References and profile edits coalesce (`TipoDeOperacion.seFusiona`); sessions and routines never do.
- **Client-generated ids.** `idCliente` is used by the backend **as the primary key** of sessions and own routines. That is what makes an offline routine usable at once (its id is already final) and a resend harmless. `Sincronizador.alSubirRutina` still re-points local rows and queued sessions if the server answers with a different id — only reachable against a backend deployed before the `idCliente` change on `POST /api/rutinas`.
- **Cancellation is not a rejection.** `SafeApiCaller.execute` and `Sincronizador.enviar` rethrow `CancellationException`. Swallowing it turned an interrupted send (WorkManager stopping the worker) into a "definitive" failure that deleted the operation without ever sending it.
- **Pending reference wins.** While an own-reference upload is queued, `RemoteFirstReferenceRepository.resolve` serves the disk copy without asking the server, which would answer with the older one.
- **Triggers.** Network coming back (`MonitorDeConectividad`), any successful save, sign-in, and `SincronizacionWorker` (WorkManager, `CONNECTED` constraint) so the queue drains with the app closed. The worker gets `Sincronizador` through a Hilt `@EntryPoint`, so there is no custom `WorkerFactory`.

**Prefetch — `PrecargaOffline`.** Caches only fill with what is requested, so without this an exercise never opened online would have neither reference nor tolerance on the phone, and offline the app would ask the patient to record a reference they already have. With network (throttled to once per 10 min unless forced) it walks the patient's catalog, active routines, history, compliance and progress, then the reference and tolerance of every exercise in the active routines. `ToleranciaConCache` exists for the same reason: `DtwMovementComparator` used to silently skip the calibrated tolerance offline and correct the same exercise with a different range.

**`MonitorDeConectividad` is a hint, never the truth about a call.** It only decides whether to skip a call when a copy exists, what the banner says, and when to trigger a sync. It requires `NET_CAPABILITY_VALIDATED` except in the `local` flavor, where the backend is on the LAN and reachable through a network Android does not consider validated.

**Signing in offline.** `AuthRepositoryImpl.restoreSession` lets the user in with the stored session (Amplify credentials + the full identity in `UserSessionStore`) when there is no network or `/me` does not answer within 5 s. No password or derivative is stored, so after an explicit sign-out the login screen needs network again. An expired token offline does not block use: operations wait in the outbox. If the session can no longer be renewed once online (the send stops on a 401), nothing local is deleted and the banner asks to sign in again; signing in as a *different* user clears the `UserScopedCache`s first. Checked on the emulator with a stored session whose refresh token had expired: `Amplify.Auth.fetchAuthSession().isSignedIn` stays `true` both online (every call then answers 401 and the banner asks to sign in) and in airplane mode on a cold start (the app opens on the home screen with the offline banner). `CognitoAuthService.isSignedIn` falls back to `getCurrentUser()` only if that call throws. A 401 that survives `TokenAuthenticator` is recorded by `SafeApiCaller` in `EstadoDeLaSesion`, which is what the banner reads; before, an expired session just showed empty screens. **Not exercised on a device:** the offline flows with a *valid* session (doing a routine, creating a routine, recording a reference, editing the profile, and the sync on reconnect).

**UI.** `AvisoDeConexion` is mounted once in `MainActivity`, above the `NavHost` (it is app state, not screen state), and is hidden on Splash and on the camera screens so it never shifts the viewer mid-exercise. Its wording is the pure function `avisoPara`.

Known limits: streak, compliance and progress percentages are server-computed and only update after sync (the weekly bars do count offline sessions, via `actividadConSesionesLocales`); the local database is not encrypted beyond Android's app-private storage.

## Design System

From [`Mocks/steady_path/DESIGN.md`](Mocks/steady_path/DESIGN.md):

- **No 1px borders** — use background color shifts (`surface` → `surface_container_low`) to separate sections.
- **Gradient CTAs** — hero buttons use linear gradient from `primary` (#0044a1) to `primary_container` (#265dc0) at 135°. Implemented in `GradientButton.kt`.
- **Minimum touch targets** — 48×48dp minimum, 64×64dp preferred for rehab users.
- **Body text minimum** — `titleMedium` (1.125rem) as the floor for standard body text; target demographic includes older users.
- **No pure black** — use `onSurface` (#181c22) instead of `#000000`.
- **Progress ring** — uses `secondary` (#006b5f) for completed state, `secondaryFixedDim` for track.
- **Font** — Public Sans; CAPS labels use 0.05rem letter spacing.

## Key Files

| File | Purpose |
|------|---------|
| `data/di/NetworkModule.kt` | Networking DI bindings — change base URL here |
| `data/di/VisionModule.kt` | Vision DI bindings — `PoseModelSpec` + `PoseEstimator` + `MovementComparator` |
| `data/vision/PoseModelSpec.kt` | **Contrato del modelo — cambiar de `.tflite` se hace acá** (`ACTIVE`) |
| `presentation/navigation/NavRoutes.kt` | `@Serializable` type-safe route + graph definitions |
| `presentation/navigation/NavGraph.kt` | Nested-graph screen wiring (`composable<T>` / `toRoute<T>()`) |
| `data/vision/YoloPoseEstimator.kt` | TFLite Interpreter + GPU/CPU delegate; pipeline orchestration |
| `data/vision/PosePostprocessor.kt` | Pure decode/NMS/de-letterbox core (JVM-tested) |
| `data/vision/PoseDiagnostics.kt` | `PoseDiag` logcat tag — why the skeleton isn't drawing |
| `presentation/screens/realtime/PoseOverlay.kt` | Skeleton `Canvas` over the camera (cover mapping) |
| `domain/comparison/MovementComparator.kt` | Comparison contract — `DtwMovementComparator` is the real impl (see docs/comparacion-movimiento.md) |
| `presentation/common/UiState.kt` | Shared state sealed class |
| `data/local/LecturaConCache.kt` | Offline read policy (`flujoConCache`) + snapshot store |
| `data/local/DomiDatabase.kt` | Room database of the offline mode (entities in `Entidades.kt`, `Daos.kt`) |
| `data/sync/Sincronizador.kt` | Drains the outbox; exposes `SyncStatusRepository` for the banner |
| `data/sync/EnvioDePendientes.kt` | Pure outbox rules: retryable vs definitive, ordering (JVM-tested) |
| `data/sync/PrecargaOffline.kt` | Prefetch of references/tolerances for the active routines |
| `data/di/LocalModule.kt` | DI bindings of the offline layer |
| `presentation/components/AvisoDeConexion.kt` | Global offline / pending-changes banner |
| `data/datasource/RehabApiService.kt` | All Retrofit endpoint declarations |
| `data/mapper/DtoMappers.kt` | DTO ↔ domain model conversions |
| `presentation/components/GradientButton.kt` | Hero CTA button (gradient, 56dp height) |
| `presentation/components/DomiTextField.kt` | Large-scale text input (64dp height) |
| `presentation/components/DomiTopBar.kt` | App bar component |
| `presentation/components/DomiBottomNavBar.kt` | Bottom navigation bar |
