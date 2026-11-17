# Referencias DTW grabadas en la app — decisiones de diseño

> Fecha: 2026-09-28. Complementa a [comparacion-movimiento.md](comparacion-movimiento.md)
> (que describe el comparador en vivo) documentando cómo se **crean y distribuyen** las
> referencias desde la app, portando la lógica del pipeline de modelosCV
> (`src/references/`) sin modificar ese repositorio.

## Qué se portó de modelosCV (y qué ya estaba)

El pipeline de modelosCV toma un video, extrae ángulos por frame, segmenta las
repeticiones y elige la **mejor** según el criterio del **medoide DTW**: la rep con menor
distancia media al resto, descartando outliers a más de `2.0 ×` la mediana de las
distancias (`cycle.select_medoid`). Esa rep canónica se serializa como JSON schema v1.

**El criterio de selección ya estaba portado** en
[ReferenceRepSelector.kt](../app/src/main/java/com/steadypath/app/domain/comparison/ReferenceRepSelector.kt):
misma matriz de distancias DTW normalizadas por largo de camino, mismo medoide, mismo
`OUTLIER_FACTOR = 2.0`, con un refinamiento propio (con ≤ 2 reps no descarta, porque no
hay base estadística). Vivía oculto en una pantalla debug
(`ReferenceCaptureScreen`, solo `BuildConfig.DEBUG`) que exportaba el JSON a un archivo
para copiarlo a mano en `assets/references/`.

Lo que agregó este trabajo:

| Pieza | Antes | Ahora |
|---|---|---|
| Selección de la mejor rep | `ReferenceRepSelector` (debug) | Igual, sin cambios |
| Patrón de ángulos | Dropdown manual + UUID pegado a mano | Resuelto por `patronMovimiento` del backend |
| Destino del JSON | Archivo local + share intent | `PUT/POST` al backend, asociado a (paciente, ejercicio) |
| Consumo | Solo `assets/references/` | API primero → cache de disco → assets |
| Validación de cierre de ciclo | Solo en modelosCV (`schema.py`) | También en la app antes de subir |
| Quién graba | Nadie (herramienta interna) | Paciente (la propia) y Profesional (para su paciente) |

## Decisiones tomadas (con el porqué)

1. **Captura en vivo con la cámara, no procesamiento de un archivo de video.**
   La app ya tiene el pipeline completo (CameraX → `YoloPoseEstimator` →
   `DefaultAngleExtractor`) y la captura en vivo lo reutiliza **por construcción**,
   garantizando la invariante mismo-modelo/mismo-pipeline que modelosCV exige (la
   referencia se extrae con el mismo `.tflite` INT8 con el que después se compara).
   Procesar un video requeriría extracción de frames (MediaCodec) y portar la
   segmentación offline — costo alto sin beneficio para el caso de uso presencial.

   **La segmentación al grabar es propia** (`AdaptiveRestRepetitionSegmenter`, en todos los
   ejercicios), no la del comparador en vivo. El `PeakValleyRepetitionSegmenter` escala sus
   umbrales con la amplitud de la referencia; al grabar no hay referencia y caía a defaults
   absolutos (prominencia 0.35 rad ≈ 20°), con lo que un paciente con movilidad reducida
   haciendo una sentadilla superficial no cerraba ninguna rep.

   **No hay amplitud mínima, a propósito.** La referencia existe para que las correcciones
   se personalicen: si alguien apenas puede bajar en la sentadilla o subir los brazos a
   medias, tiene que poder grabarla igual y progresar desde ahí. El único piso es distinguir
   el movimiento del temblor de la pose: salir del reposo con `max(4σ, 0.08 rad)` (≈ 4.6°).
   Medido sobre ruido real de modelosCV, el temblor de un ángulo es de ≈ 0.03 rad por cuadro,
   y por debajo de ~6° la detección empieza a fallar. Bajar ese piso hacía contar acomodos
   como reps en los videos reales.

   Cómo funciona:
   - **Calibra con quietud.** Toma mediana y MAD del conductor en una ventana de 1.5 s que se
     desliza hasta que el umbral que daría sea de alguien quieto (≤ 0.12 rad). Así, quien
     toca «Comenzar» y se acomoda no calibra moviéndose. A los 6 s acepta lo que haya.
   - **Cuenta reposo → extremo → reposo** si dura ≥ 600 ms y tiene forma de pulso: la mejor
     NCC contra tres ventanas de Tukey (coseno, α 0.5 y 0.25) y `peakCount == 1`. La pausa
     en el extremo, habitual en rehabilitación, puntuaba 0.15–0.55 contra el coseno solo; el
     conteo de picos reemplaza lo que la meseta deja pasar (reps pegadas). Una excursión breve
     que apenas pasó el umbral es ruido y se descarta sin avisar.
   - **Cierra relativo al extremo**, no al reposo calibrado. Cierra al volver a él, en el
     **valle** si ya arrancó la siguiente rep (reps encadenadas), o al **asentarse** (quieto
     500 ms) cerca del reposo, así la última se cuenta sin esperar otra. En los dos últimos
     casos el reposo se re-ancla donde quedó la persona. "Cerca" es ≤ 0.3·extremo para el
     valle y ≤ 0.5·extremo para el asiento, nunca más que `FIRST_LAST_TOL_RAD`, para no
     contar una rep que después falle el cierre de ciclo (punto 7). Si se asienta más lejos
     tras haber bajado, es un cambio de postura: se descarta y se re-ancla.
   - **Acomodos.** Sin amplitud mínima, un acomodo lento cuenta igual que una rep chica.
     Aislado no se distingue; lo deja en evidencia lo que viene después. Si llega una rep 3×
     más grande que todo lo contado, lo anterior se descarta y la cuenta vuelve a empezar
     ("empiezo a contar de nuevo"). Sin esto, en el video real "con manos" cuatro acomodos
     más la primera rep cerraban la toma y el medoide salía de un acomodo. Quien solo puede
     moverse poco no lo dispara, porque sus reps son parecidas entre sí. Una rep en la otra
     dirección que no llega a 3× es `WRONG_DIRECTION`.

   El segmentador anterior, anclado al reposo, cerraba la rep solo cuando el conductor
   volvía a ±0.5·umbral (≈ 2–3°) del reposo calibrado y pedía un extremo ≥ 1.5× el umbral.
   Parado es un tope anatómico; con brazos y piernas no, y en elevación de hombros no
   contaba ninguna rep en el teléfono. Con keypoints reales de modelosCV contaba 1 de 2 en
   brazos, 5 de 7 en talones y 0–2 de 5 en extensión de rodilla. El actual cuenta 5/5 en
   sentadilla, 2/2 en brazos, 7/7 en talones y 3–4 de ~5 en extensión de rodilla, a 30, 15
   y 10 fps, sin pasarse nunca de las reps visibles (`RecordingSegmenterRealDataTest`, con
   los CSV de `src/test/resources/recording/`).

   Cada decisión se loguea con el tag `RecSeg` en debug. La pantalla dice en voz alta el
   conteo y los avisos (quedate quieto, ya podés empezar, por qué no se contó, empiezo a
   contar de nuevo, referencia guardada), porque quien graba suele estar lejos del teléfono.

2. **Referencia por (paciente, ejercicio), no global por ejercicio.**
   Decisión del usuario: cada paciente tiene su propia referencia (movilidad, postura y
   antropometría distintas). En el backend es la entidad `ReferenciaMovimiento` con índice
   único `(IdPaciente, IdEjercicio)`; guardar de nuevo reemplaza (upsert, sin historial).

3. **JSON en la base de datos, no S3.**
   El JSON pesa pocos KB (~100 frames × pocas dimensiones); las URLs prefirmadas de S3
   (patrón de los videos) agregarían piezas móviles sin necesidad. Columna `text`, patrón
   ya usado para `ZonasAfectadas`.

4. **El patrón de movimiento vive en el backend (`Ejercicio.PatronMovimiento`).**
   Para grabar hay que saber qué ángulos medir. El slug (p.ej. `sentadilla_parcial`)
   referencia a `MovementPatternCatalog` de la app, que sigue siendo la única fuente de las
   ternas de keypoints. Ejercicio sin slug ⇒ la opción de grabar no se ofrece. Un slug que
   la app no conoce ⇒ mensaje de "actualizá la app", nunca un crash.

5. **Consumo API-first con degradación en dos niveles.**
   [RemoteFirstReferenceRepository](../app/src/main/java/com/steadypath/app/data/comparison/RemoteFirstReferenceRepository.kt):
   1. `GET /api/pacientes/referencias/{idEjercicio}` — si responde, es **autoritativo**:
      con referencia se cachea a disco (`filesDir/references/`); si dice "no hay", se
      saltea el cache de disco (estaría obsoleto) y se cae directo a assets.
   2. Cache de disco — última referencia remota conocida, para sesiones offline.
   3. `assets/references/` ([LocalAssetReferenceRepository]) — referencia genérica de fábrica.

   El comparador en vivo, `PrepareSpace` y la calibración no cambiaron: siguen llamando a
   `ReferenceRepository.getReference(exerciseId)`; solo cambió el binding en `VisionModule`.
   Tras subir una referencia propia, el repositorio pisa sus propios caches sin re-descargar.

6. **Mínimo 3 repeticiones para subir.**
   modelosCV recomienda 3–7 reps por clip; con menos de 3 el medoide degenera (no hay
   mediana de distancias que sirva para descartar outliers). La pantalla lo comunica antes
   de habilitar el botón. El export debug mantiene el comportamiento anterior (cualquier
   cantidad).

7. **Validación de cierre de ciclo antes de subir (paridad con `schema.py`).**
   `cycleClosureErrorRad(rep) ≤ 0.35 rad` por dimensión (mismo umbral
   `first_last_tol_rad` de modelosCV, mismo TODO de calibración): una referencia válida
   cierra reposo→extremo→reposo. Si falla, se pide regrabar terminando en reposo — antes
   ese error solo se detectaba al validar el JSON en la PC.

8. **Roles y autorización en el backend, no en la UI.**
   - `PUT /api/pacientes/referencias` — el paciente, id del token (patrón tolerancias).
   - `POST /api/medicos/referencias` — el profesional, con `ValidarPacienteDelProfesionalAsync`
     (403 si el paciente no es suyo).
   - El JSON se valida mínimamente en la API (schemaVersion=1, exerciseId coincidente,
     trayectoria no vacía, ≤ 256 KB) → `ReferenciaInvalidaException` → **400**. La API no
     reimplementa el parser de la app: el contenido es opaco, solo se frena la basura.

9. **Rutas de navegación separadas por grafo.**
   `RecordOwnReference` (PatientGraph) y `RecordPatientReference`/`ChooseExerciseForReference`
   (ProfesionalGraph) son clases distintas aunque la pantalla (`RecordReferenceScreen`) sea
   compartida: registrar el mismo objeto de ruta en dos grafos anidados es ambiguo para el
   NavController (misma razón documentada para `PatientChangePassword`).

10. **La pantalla debug se conserva.** `ReferenceCapture` (dropdown + UUID manual + export
    a archivo) sigue existiendo solo en DEBUG: sirve para generar los assets de fábrica y
    para probar patrones sin backend. Comparte el mismo ViewModel, así que cualquier fix al
    pipeline de captura aplica a ambos modos.

## Divergencias aceptadas respecto de modelosCV

| Tema | modelosCV | App | Por qué es aceptable |
|---|---|---|---|
| Segmentación de reps | Offline: baseline circular (mediana de reposo) + picos por prominencia | Online: `AdaptiveRestRepetitionSegmenter` (reposo calibrado con quietud, umbral relativo al ruido sin amplitud mínima, cierre por valle/asiento con re-anclaje, gate de forma NCC tolerante a pausa + conteo de picos) | Es el análogo online del baseline circular: mediana/MAD del reposo en vez de la mediana del clip. El comparador en vivo usa `PeakValleyRepetitionSegmenter` (ancla en valles, umbrales derivados de la amplitud de la referencia); `ReferenceAlignment.alignToDriverValley` rota la referencia a esa convención en carga, así que las dos segmentaciones no necesitan coincidir |
| DTW | Completo, sin ventana | Sakoe-Chiba (banda) | La banda solo poda alineaciones extremas; para reps de duración similar el camino óptimo cae dentro de la banda |
| Interpolación de gaps | `interpolate_gaps_wrapped` + `max_gap_ms=150` | Frames con cualquier keypoint de baja confianza se descartan (`valid.all()`) | En captura supervisada (fisio presente, encuadre correcto) es alcanzable; el schema v1 no tiene máscara de validez, así que solo entran frames 100 % confiables |
| fps de la referencia | Del video (30) | Mediana de intervalos entre frames (~10–20) | El comparador mide tempo por duración en ms, no por conteo de frames (ver comparacion-movimiento.md) |
| Suavizado de almacenamiento | `store_window` configurable | Sin suavizado (frames crudos) | Equivale a `store_window: 1`, el default de modelosCV |

## Flujo resultante

```
Profesional: PatientDetail → «Grabar referencia» → ChooseExerciseForReference
             (solo ejercicios con patronMovimiento) → RecordReferenceScreen
             → N reps → medoide → POST /api/medicos/referencias

Paciente:    ExerciseDetail → «Grabar mi referencia» → RecordReferenceScreen
             → N reps → medoide → PUT /api/pacientes/referencias

Ejercicio en vivo (sin cambios de firma):
             DtwMovementComparator → ReferenceRepository.getReference(exerciseId)
             → [API → cache disco → assets]
```

## Piezas nuevas / modificadas

**appMobile**
- `data/comparison/RemoteFirstReferenceRepository.kt` — nuevo; binding en `VisionModule`.
- `domain/repository/MovementReferenceRepository.kt` — nuevo (subida).
- `domain/comparison/ReferenceRepSelector.kt` — + `cycleClosureErrorRad` / `FIRST_LAST_TOL_RAD`.
- `presentation/screens/capture/ReferenceCaptureViewModel.kt` — + `configure()` / `saveToBackend()`.
- `presentation/screens/capture/RecordReferenceScreen.kt` — nuevo (producción).
- `presentation/screens/professional/ChooseExerciseForReferenceScreen.kt` — nuevo.
- `ExerciseDetailScreen` / `PatientDetailScreen` — puntos de entrada.
- `NavRoutes` / `NavGraph` — rutas `RecordOwnReference`, `ChooseExerciseForReference`,
  `RecordPatientReference`.
- `RehabApiService` + DTOs — 3 endpoints; `Exercise.patronMovimiento`.

**tesisApi** — ver [docs/REFERENCIAS_MOVIMIENTO.md](../../tesisApi/docs/REFERENCIAS_MOVIMIENTO.md).

## Pendientes conocidos

- `scoring.similarityScale` / `goodThreshold` siguen en valores placeholder (mismo TODO de
  calibración que modelosCV y que la pantalla debug).
- `FIRST_LAST_TOL_RAD = 0.35` y `OUTLIER_FACTOR = 2.0` heredan el TODO de calibración con
  datos reales (> 50 capturas).
- No hay pantalla para que el profesional *vea* la referencia vigente de un paciente
  (solo reemplazarla); el backend ya devuelve `creadaPorProfesional`/`fechaActualizacion`
  para cuando se necesite.
- El paciente que graba su referencia pisa la que grabó el profesional (y viceversa) —
  deliberado por la decisión de upsert único, pero puede ameritar una confirmación en UI.
