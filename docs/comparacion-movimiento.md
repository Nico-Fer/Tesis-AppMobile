# Comparación de movimiento: referencias, conteo de reps y tolerancias

> Estado a agosto 2026. Documenta los cambios de la sesión que habilitó **elevación de
> hombros (bilateral)** y **sentadilla parcial**, y los criterios de diseño detrás, para
> que futuras sesiones no re-deriven (ni contradigan) estas decisiones.

## Referencias shipped

Assets en `app/src/main/assets/references/{exerciseId}.json`, generados **offline**:
grabación en video + el mismo modelo de assets (`yolov8n-pose` int8) + script que exporta
el schema v1. El `ExerciseDefinition` embebido en el JSON es la fuente autoritativa en
runtime (invariante del mismo-pipeline); el catálogo no se consulta después del export.

| exerciseId | Ejercicio | Ángulos (driver primero) | Amplitud driver | Anclaje original |
|---|---|---|---|---|
| `c1000000-…-0001` | Elevación de hombros (ambos brazos) | `hombro_izq`, `hombro_der` (w=1.0) | ~1.41 rad | reposo (peak-first) |
| `c1000000-…-0002` | Sentadilla parcial (bilateral) | `cadera_izq`, `rodilla_izq`, `cadera_der`, `rodilla_der` (w=1.0) | ~0.4 rad | reposo (peak-first) |
| `c1000000-…-0005` | Elevación de talones (sentado) | `rodilla_izq`, `cadera_izq` (w=1.0) | ~0.17 rad | reposo (peak-first) |
| `c1000000-…-0006` | Extensión de rodilla (sentado) | `rodilla_izq` (único) | ~0.43 rad, **cruza ±π** | reposo (peak-first) |

**Peak-first** = el ángulo conductor está en su *máximo* en reposo y baja durante el
esfuerzo (parado→fondo en la sentadilla; brazos abajo→arriba con el ángulo signado del
hombro). Es lo opuesto a los ejercicios "valley-first" para los que se diseñó el pipeline
original.

## Qué se cambió y por qué

### 1. Rotación de referencia al valle (`ReferenceAlignment`, domain)

`PeakValleyRepetitionSegmenter` ancla los segmentos en vivo en los **mínimos** del
conductor (ciclo valle→pico→valle). Una referencia peak-first anclada al reposo queda en
**contrafase** con esos segmentos: el DTW comparaba parado→fondo→parado contra
fondo→parado→fondo y todo rep daba `NeedsCorrection` falso con hints basura.

Fix: `ReferenceAlignment.profileOf()` (amplitud des-envuelta + índice del valle del
conductor) y `alignToDriverValley()` (rota el ciclo para arrancar en el valle;
re-estampa timestamps uniformes — aguas abajo solo importan los índices). Se aplica **una
vez al cargar** en `DtwMovementComparator`; para referencias ya ancladas al valle es
no-op. Consecuencia: **las referencias pueden autorarse con cualquier anclaje**, el
pipeline las normaliza.

### 2. Umbrales de conteo derivados de la referencia (`tunedFor`)

La prominencia fija (0.35 rad) casi excluía la sentadilla parcial (amplitud 0.41 rad): un
paciente apenas más superficial que la referencia contaba **0 reps**. Ahora
`PeakValleyRepetitionSegmenter.tunedFor(driverIdx, amplitud)`:

- `minProminenceRad = max(0.25 × amplitud, 0.15)` **capeado a `0.6 × amplitud`** — sin el
  cap, el piso fijo exigiría ~90% de la amplitud en ejercicios de excursión chica
  (elevación de talones ~0.17 rad → prominencia ~0.10)
- `hysteresisRad = (0.25 × amplitud)` acotada a `[0.05, 0.15]`
- `RepetitionSegmenterFactory.create(driverIdx, amplitud?)` — `null` = defaults (flujo de
  captura, que no tiene referencia todavía)

### 3. Primer segmento: cuenta y se muestra como correcta

El primer segmento tras arrancar `compare()` va del reposo inicial al primer extremo —
**medio ciclo**. Se emite con `repDelta=1` y `status=Good` **sin score** (puntuar medio
ciclo contra un ciclo entero daba corrección falsa en la rep 1). En pantalla se ve
"¡Buena repetición!" sin porcentaje.

### 4. Catálogo y fixes de datos

- Patrones en `MovementPatternCatalog` **replican los JSONs shipped** (ángulos, orden y
  pesos): `sentadilla_parcial` (bilateral, 4 ángulos), `elevacion_talones_izq`,
  `extension_pierna_sentada_izq` (solo rodilla).
- `metadata.version` en el JSON **debe ser string** (`"1"`, no `1`): el parser kotlinx
  falla con número. Lo atrapa el test golden. **El script de export offline sigue
  generando `version: 1` y el scoring viejo (0.5/0.7)** — corregir el script.
- El test golden (`todos los assets de referencias del repo son validos`) valida
  cualquier anclaje: alinea con `ReferenceAlignment` y exige prominencia **relativa**
  (> 0.5 × amplitud del propio ejercicio) sobre el conductor **des-envuelto**, con piso
  de amplitud 0.1 rad (nivel de ruido de pose).

### 5. NCC des-envuelta (±π)

La extensión de rodilla trabaja con la pierna casi recta: su ángulo cruza ±π y los
valores crudos saltan de +3.14 a −3.14. La NCC y el ratio de amplitud de
`ShapeSimilarity` ahora **des-envuelven** (delta a delta) las secuencias alineadas antes
de correlacionar — sin esto, cualquier ejercicio cerca de ±π daba score de forma basura
aunque la ejecución fuera perfecta. `ReferenceAlignment.unwrappedDriver()` expone la
misma técnica para perfil/rotación/tests.

### 6. Tempo por duración real y asimétrico (ago-2026, 2da/3ra tanda device)

La referencia está a 30 fps pero la inferencia en vivo corre a ~10-15 fps: comparar
**cantidad de frames** usuario/referencia marcaba `TOO_FAST` espurio a velocidad real
idéntica. `ErrorAttributor` ahora mide el tempo por **timestamps** (duración de la fase
en ms de cada lado). Además los umbrales son **asimétricos**: lento se tolera hasta 3×
la referencia (sostener el extremo no es un error y el público objetivo ejecuta más
lento), rápido hasta 1.9×.

### 6b. Normalización de línea base (offset postural)

La comparación mide la **excursión del ciclo, no el ángulo absoluto**:
`ReferenceAlignment.toStartDeviation()` resta a cada dimensión su valor en el primer
cuadro válido, tanto en la referencia (al cargar) como en cada rep en vivo. Sin esto, la
postura base distinta (altura de silla, ángulo de cámara) metía un offset constante que
se disfrazaba de déficit de amplitud — en talones generaba "ampliá el movimiento de la
rodilla" permanente aunque la ejecución fuera correcta.

### 7. Ruido en ejercicios de excursión chica

Talones (~0.17 rad) y rodilla (~0.43 rad) viven al nivel del ruido de pose: reps que no
contaban y reps dobles. `tunedFor` aplica **EMA α=0.5 sobre el conductor solo si la
amplitud de referencia < 0.6 rad** (hombros con 1.41 queda intacto) — el suavizado lo ve
solo el detector, el DTW recibe los frames crudos. Además el cap de prominencia bajó a
`0.5×amplitud` (talones exige ~0.085 rad).

### 8. Mensajes conscientes del conductor

`CorrectionHint.isDriver` (lo setea `ErrorAttributor`): la cadera solo dice "mantené el
tronco firme" cuando es **control postural**; cuando conduce el ejercicio (sentadilla
bilateral) dice "bajá un poco más / no bajes tanto". Antes toda sentadilla imperfecta
recibía el mensaje de tronco, que no era el error real.

### 9. Feedback positivo y cierre automático de serie

- Reps `Good` disparan TTS: la primera de una racha dice "¡Muy bien!" (cooldown 8 s); a
  partir de la segunda consecutiva (`goodStreak ≥ 2`) el TTS **cuenta en voz alta** el
  número de rep (gap 1.5 s, nunca pisa una corrección).
- Al llegar a `targetReps` el TTS anuncia "N de N repeticiones hechas, serie finalizada"
  (`speakAnnouncement`, sin cooldown) y ~2.5 s después el ViewModel cierra la serie solo
  (mismo flujo que el botón: descanso o pantalla de completado).
- Mientras la serie no llegó al objetivo, el paciente puede **saltearla** («Saltear serie»,
  con un diálogo de confirmación que muestra `n / targetReps`). Toma el mismo camino que el
  cierre automático (`onStop`: descanso o completado) y se registran las reps contadas, sin
  completar hasta la prescripción. Al alcanzar el objetivo el botón desaparece: el cierre
  automático ya está agendado y un segundo `onStop` cerraría la serie siguiente.

## Criterios de diseño (no re-derivar)

1. **Una rep mala CUENTA y recibe corrección; no cierra segmento = no hay feedback.** Por
   eso `PROMINENCE_FRACTION = 0.25` es deliberadamente baja: el gate de calidad es el
   score DTW×forma, no el detector de ciclos. El detector solo filtra ruido y movimientos
   ajenos al ejercicio (verificado en device: otros movimientos no cuentan).
2. **El contador incrementa apenas el paciente sale del extremo** (histéresis sobre el
   mínimo), no al volver al reposo. Incluye siempre la última rep de la serie.
3. **La rotación es interna; el ejercicio físico sigue empezando en reposo.** El ciclo
   rotado va extremo→reposo→extremo. La "costura" de la rotación queda en el reposo y es
   despreciable (≪ histéresis).
4. **Tolerancia simétrica de amplitud (±20%)**: la variabilidad humana normal no es
   error. Si algún ejercicio no debe tolerar overshoot clínicamente, eso saldrá de las
   tolerancias del profesional (feature aparte), no de esta constante.
5. **El ajuste por ejercicio vive en el bloque `scoring` del JSON** — se calibra sin
   recompilar. Los defaults de código son globales.

## Tolerancias vigentes (calibración manual en device, ago-2026)

Validadas por Nico con la elevación de hombros: reps normales con variación humana en
verde; reps claramente cortas o muy rápidas siguen marcando corrección; movimientos
ajenos no cuentan.

| Parámetro | Antes | Ahora | Dónde | Efecto |
|---|---|---|---|---|
| `AMPLITUDE_TOLERANCE` | — | 0.2 | `ShapeSimilarity` | ±20% de amplitud sin castigo; afuera, rampa (`ratio/0.8` cap 1) |
| `similarityScale` | 0.5 | 0.8 (hombros) / **1.2 (sentadilla, talones, rodilla)** | JSONs | El ruido angular es absoluto: pesa 8× más en talones que en hombros — escala por ejercicio |
| `goodThreshold` | 0.7 | 0.6 | JSONs + default de captura | Corte Good/NeedsCorrection más laxo |
| `amplitudeThresholdRad` | 0.25 | 0.35 | `ErrorAttributor` | Hint de amplitud solo con ~20° de error medio |
| `tempoRatioThreshold` | 1.6 simétrico | **lento 3.0 / rápido 1.9** (sobre duración, no frames) | `ErrorAttributor` | Lento se tolera más que rápido |
| `SMALL_AMPLITUDE_SMOOTHING_ALPHA` | — | 0.5 si amp < 0.6 rad | `tunedFor` | EMA anti-ruido del detector en ejercicios chicos |
| `MAX_PROMINENCE_AMPLITUDE_FRACTION` | 0.6 | 0.5 | `tunedFor` | Talones cuenta con reps al 50% de la referencia |

Referencia numérica: overshoot leve (~15%, distancia DTW ~0.2) pasó de `exp(−0.4)×0.87 ≈
0.58` (corrección falsa) a `exp(−0.25)×1.0 ≈ 0.78` (Good). El caso guardia "llegó a la
mitad de amplitud" sigue castigado (ratio 0.5 → 0.625 < umbral).

Siguen siendo placeholders formales — la calibración con datos etiquetados está definida
en [calibration-protocol.md](calibration-protocol.md) (CSV por rep en
`files/metrics/rep-metrics.csv`, builds DEBUG).

## Checklist: agregar una referencia nueva

1. Grabar el video con la **misma orientación de cámara** que usará el paciente (los
   ángulos son signados; espejar invierte el signo).
2. Exportar schema v1 con el mismo modelo de assets. Gotchas: `metadata.version` string;
   nombre de archivo = `exerciseId` (así lo resuelve `LocalAssetReferenceRepository`);
   la trayectoria debe cubrir **exactamente un ciclo** (cualquier anclaje).
3. `scoring`: arrancar con `similarityScale 0.8 / goodThreshold 0.6` y ajustar por
   ejercicio con el CSV.
4. Si el patrón no existe en `MovementPatternCatalog`, agregarlo **con los mismos ángulos
   y pesos que el JSON** (consistencia con re-capturas in-app).
5. `.\gradlew testAwsDebugUnitTest` — el test golden valida el asset automáticamente.
6. En device: verificar en logcat la línea `Referencia '...' cargada: ... amplitud=X rad,
   rotada N frames al valle`.

## Limitaciones conocidas

- **Hints de fase en ejercicios peak-first**: "subida"/"bajada" refieren al ciclo rotado.
  Para la sentadilla coinciden con la intuición (subida = levantarse del fondo); para
  hombros quedan invertidos (la "subida" del ciclo es bajar los brazos). Si molesta, se
  encara en `CorrectionMessages` — no en el pipeline.
- **Entre series el segmenter no se resetea**: la primera rep de la serie 2+ puede
  arrancar de un segmento basura del descanso (se cuenta con status del momento). Aceptado
  por ahora.
- La regla "primera rep sin puntuar" es por sesión de `compare()`, no por serie.
