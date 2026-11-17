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

**DI:** Single Hilt `NetworkModule` (`SingletonComponent`) provides `OkHttpClient`, `Retrofit`, `RehabApiService`, all three repositories, and `Dispatchers.IO`.

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

**Reference recording (in-app):** both roles can record a patient's reference with the camera — patient from `CreateRoutine` («Grabar referencia» on each draft exercise card), professional from `PatientDetail` → exercise picker (`ChooseExerciseForReference`). `ExerciseDetail` no longer has a record button: «Comenzar» goes through `ExerciseDetailViewModel.startGate` (`HasOwnReferenceUseCase` = `getReference() != null` **and** provenance ≠ `EMPAQUETADA`) and, with no own reference, shows a blocking dialog that sends to `RecordOwnReference`; exercises without `patronMovimiento` pass straight through. That is the **only** place the patient is asked: `PrepareSpace` used to also offer a range calibration (`CalibrationPromptDialog` → `CalibrationCapture`/`CalibrationSuccess`), which was removed as a duplicate — with an own reference the amplitude already comes from the patient's own movement. `DtwMovementComparator` still honors a stored `tieneCalibracion` tolerance if the backend has one. `RecordReferenceScreen` mirrors `RealtimeExerciseScreen` (3:4 viewer + `CameraStatusOverlay`, status badge, `n / TARGET_REPS` counter) and has **no manual selection step**: the take auto-closes at `TARGET_REPS` (5) — or on «Terminar» with ≥3 reps — and the ViewModel immediately runs `ReferenceRepSelector` (DTW-medoid, outlier discard at 2.0× median — ported from modelosCV `src/references/`), validates cycle closure (≤0.35 rad) and uploads. Fewer than 3 reps or a non-closing cycle discards the take and asks to re-record. Which angles to measure comes from `Exercise.patronMovimiento` (backend slug → `MovementPatternCatalog`); no slug = recording not offered. Decisions and modelosCV parity table: [docs/referencias-dtw-en-app.md](docs/referencias-dtw-en-app.md). The DEBUG-only `ReferenceCapture` screen (manual pattern + UUID, file export) still exists for authoring bundled assets and keeps the manual rep list.

**Model asset:** `app/src/main/assets/student_pruned_ft_deploy_v3_int8.tflite` (pruned student model, INT8 weights with float32 I/O), loaded via `FileUtil.loadMappedFile`.

> **v2 rendered a collapsed skeleton and the cause was the model, not this pipeline.** Pruning at 30% destroyed the COCO prior and the recovery fine-tune used only in-domain data, so outside that domain the keypoints bunched into a vertical stripe. v3 retrains on a mixed dataset and fixes it. The A/B that proved it is worth repeating whenever the model changes: drop the candidate in `assets/`, point `assetFileName` at it, and read the `Rango crudo` line from `PoseDiag` — keypoints should span **≥0.75** of the box on *both* axes. Note the on-device ratio divides by the **predicted** box, which a broken model also inflates, so it understates damage; rigorous measurement needs ground-truth boxes offline. `noCompress += "tflite"` in `build.gradle.kts` keeps it memory-mappable.

> **`.tflite` files are not versioned** (`*.tflite` is git-ignored — they weigh several MB). Drop the model into `app/src/main/assets/` and point `PoseModelSpec.ACTIVE.assetFileName` at it. A fresh clone has no model: the app compiles but `YoloPoseEstimator` fails at init until the asset is present.

## Performance benchmark (Firebase Test Lab)

`PoseBenchmarkTest` (`app/src/androidTest/.../benchmark/`) replays dataset clips through the **production entry point** (`estimate(ImageProxy, rotationDegrees)`, fed by `MediaImageProxy` over `MediaCodec` YUV frames) with a virtual clock that reproduces `KEEP_ONLY_LATEST`, then runs the real `DtwMovementComparator` on the detected poses. It runs on Test Lab physical devices through the `com.google.firebase.testlab` Gradle plugin (`ftlLote1GroupAwsDebugAndroidTest` / `ftlLote2…`; batches of 5 devices in `ftlLotes` in `app/build.gradle.kts`). Full guide, device rationale and clip catalog: [docs/benchmark-firebase-test-lab.md](docs/benchmark-firebase-test-lab.md).

- The only production seam is in `YoloPoseEstimator`: a primary constructor taking `useGpu` + `PoseTimingSink` (per-stage ms), and a read-only `backend`. Hilt uses the secondary `@Inject` constructor, so DI is unchanged — keep it that way rather than adding bindings.
- Clips (`.mp4`) are generated by `tools/benchmark/preparar_clips.py` from `modelosCV` and are **not versioned** (like `.tflite`); `clips.json` is. They mimic the analyzer: 640×480 sensor-oriented + `rotationDegrees=90`. The desktop baseline in `tools/benchmark/linea_base/` is model-specific: regenerate when `PoseModelSpec.ACTIVE` changes.
- Output goes to `Android/media/<pkg>/benchmark` (pullable by Test Lab and adb); `tools/benchmark/agregar_resultados.py` consolidates runs into the `metricas_*.csv` / `rep-metrics.csv` schemas.

## Backend API

Base URL is hardcoded in `NetworkModule` as `https://10.0.2.2:7031/` (Android emulator loopback to localhost port 7031). The backend must be run with the **https** Kestrel profile in Visual Studio (not IIS Express — IIS Express rejects `Host: 10.0.2.2` at the HTTP.sys level). The OkHttpClient trusts all SSL certificates — intentional for local dev with a self-signed cert.

Full API contract is documented in [`docs/api-summary-for-mobile.md`](docs/api-summary-for-mobile.md). Key points:
- All IDs are UUID strings.
- No auth tokens — no `Authorization` header needed.
- Error responses always `{ "message": "string" }`.
- Two user roles: `Paciente` and `Profesional`.

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
| `data/datasource/RehabApiService.kt` | All Retrofit endpoint declarations |
| `data/mapper/DtoMappers.kt` | DTO ↔ domain model conversions |
| `presentation/components/GradientButton.kt` | Hero CTA button (gradient, 56dp height) |
| `presentation/components/DomiTextField.kt` | Large-scale text input (64dp height) |
| `presentation/components/DomiTopBar.kt` | App bar component |
| `presentation/components/DomiBottomNavBar.kt` | Bottom navigation bar |
