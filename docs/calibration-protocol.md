# Protocolo de calibración de umbrales

Todos los umbrales del pipeline de comparación son **placeholders sin calibrar** (marcados
`// TODO: calibrar con datos` en el código). Este documento define el protocolo para
elegirlos con datos reales. La ejecución requiere grabaciones etiquetadas — queda
pendiente de la campaña de captura con la fisioterapeuta.

## Inventario de umbrales a calibrar

| Parámetro | Valor actual | Archivo | Qué controla |
|---|---|---|---|
| `confidenceThreshold` | 0.5 | `DefaultAngleExtractor` | Keypoint confiable → dimensión válida |
| `minProminenceRad` | max(0.25×amp, 0.15) cap 0.6×amp | `PeakValleyRepetitionSegmenter.tunedFor` | Prominencia mínima de un pico para contar rep |
| `hysteresisRad` | 0.25×amp en [0.05, 0.15] | `PeakValleyRepetitionSegmenter.tunedFor` | Anti-rebote de la detección pico/valle |
| `minPeakGapMs` | 800 | `PeakValleyRepetitionSegmenter` | Separación temporal mínima entre reps |
| `windowRatio` | 0.2 | `Dtw` | Semiancho de la banda Sakoe-Chiba |
| `similarityScale` | 0.8 | JSON de referencia (`scoring`) | Escala del mapa distancia → similitud |
| `goodThreshold` | 0.6 | JSON de referencia (`scoring`) | Corte binario Good / NeedsCorrection |
| `amplitudeThresholdRad` | 0.35 | `ErrorAttributor` | Error medio de amplitud que dispara un hint |
| `slowTempoRatioThreshold` | 3.0 | `ErrorAttributor` | Ratio de duración (ms) que dispara TOO_SLOW — asimétrico, lento se tolera más |
| `fastTempoRatioThreshold` | 1.9 | `ErrorAttributor` | Ratio de duración (ms) que dispara TOO_FAST |
| `peakWindowFraction` | 0.15 | `ErrorAttributor` | Ancho de la fase PICO alrededor del extremo |
| `AMPLITUDE_TOLERANCE` | 0.2 | `ShapeSimilarity` | Desvío de amplitud tolerado sin castigo |
| `SMALL_AMPLITUDE_SMOOTHING_ALPHA` | 0.5 (amp < 0.6 rad) | `PeakValleyRepetitionSegmenter.tunedFor` | EMA del conductor en ejercicios de excursión chica |
| `outlierFactor` | 2.0 | `ReferenceRepSelector` | Descarte de reps outlier al capturar referencia |
| `NOISE_SIGMAS` / `MIN_EXIT_RAD` / `MAX_EXIT_RAD` | 4σ / 0.08 / 0.25 | `AdaptiveRestRepetitionSegmenter` | Umbral de salida del reposo al grabar referencia (σ = MAD×1.4826 de 1.5 s de reposo). Es el único piso de amplitud: a propósito no hay mínimo, la referencia registra lo que la persona puede hacer |
| `STILL_EXIT_RAD` / `CALIBRATION_TIMEOUT_MS` | 0.12 / 6000 | `AdaptiveRestRepetitionSegmenter` | Calibración aceptada solo con la persona quieta; pasado el timeout se acepta lo que haya |
| `MIN_REP_DURATION_MS` | 600 | `AdaptiveRestRepetitionSegmenter` | Duración mínima de una rep al grabar |
| `NOISE_MARGIN_FACTOR` | 1.5× salida | `AdaptiveRestRepetitionSegmenter` | Excursión breve por debajo de esto = ruido, se descarta sin avisar (no es mínimo para contar) |
| `MIN_SHAPE_NCC` / `PEAK_PROMINENCE_FRACTION` | 0.6 / 0.5 | `AdaptiveRestRepetitionSegmenter` / `RepShapeGate` | Mejor NCC contra coseno/Tukey (acepta pausa en el extremo) y un solo pico |
| `RETURN_FRACTION` / `NEAR_RETURN_FRACTION` / `SETTLE_MS` | 0.5 / 0.3 / 500 | `AdaptiveRestRepetitionSegmenter` | Cierre por valle o al asentarse cerca del reposo (relativo al extremo) |
| `SUPERSEDE_FACTOR` | 3× | `AdaptiveRestRepetitionSegmenter` | Una rep 3× más grande que lo aceptado descarta lo anterior como acomodos |
| `REST_DRIFT_ALPHA` | 0.005 | `AdaptiveRestRepetitionSegmenter` | Deriva de la media de reposo (más rápida se come excursiones lentas y chicas) |
| `MIN_SPEECH_GAP_MS` | 4000 | `FeedbackEffects` | Rate-limit del TTS |

`similarityScale` y `goodThreshold` viven en el JSON de la referencia: se calibran **por
ejercicio** sin recompilar la app.

## Herramienta de recolección (ya implementada)

En builds **DEBUG**, `CsvRepMetricsSink` appendea una fila por repetición comparada a:

```
Android/data/com.steadypath.app/files/metrics/rep-metrics.csv
```

Columnas: `timestampMs, exerciseId, repIndex, normalizedDistance, dtwSimilarity,
shapeScore, finalScore`. Extracción:

```powershell
adb pull /sdcard/Android/data/com.steadypath.app/files/metrics/rep-metrics.csv
```

## Protocolo de grabación

Por cada ejercicio del alcance (levantamiento de brazos, flexión de rodilla, sentarse y
levantarse de una silla, extensión de pierna sentada):

1. **Referencia**: capturar con la pantalla debug (≥5 reps del fisio, exportar la mediana).
2. **Ejecuciones etiquetadas**, con la misma orientación de cámara que la referencia.
   Mínimo sugerido por clase (más es mejor; idealmente ≥3 sujetos):
   - `correcta` ×10 — ejecución supervisada por el fisio.
   - `amplitud-insuficiente` ×10 — se queda corto deliberadamente (~50% del rango).
   - `muy-rapida` ×5 y `muy-lenta` ×5.
   - `compensación` ×5 — error típico del ejercicio según el fisio (p. ej. tronco).
3. Registrar a mano el mapeo `(timestamp aproximado / repIndex) → etiqueta` de cada tanda;
   el CSV no conoce la etiqueta.

## Análisis y elección de umbrales

1. Unir CSV + etiquetas (notebook Python / hoja de cálculo).
2. **`goodThreshold` y `similarityScale`** (por ejercicio): graficar la distribución de
   `finalScore` por clase (`correcta` vs. resto).
   - Separación limpia → elegir el corte por percentiles (p. ej. p05 de `correcta` y
     p95 del resto, punto medio).
   - Solapamiento → curva ROC sobre `finalScore` y elegir el punto que prioriza
     **sensibilidad a errores** (falso "Good" es peor que falso "NeedsCorrection" en
     rehabilitación) con FPR aceptable.
   - Si `finalScore` no separa pero `normalizedDistance` o `shapeScore` sí lo hacen por
     separado, reconsiderar la composición (producto vs. media geométrica ponderada).
3. **Umbrales del `ErrorAttributor`**: sobre las tandas etiquetadas por tipo de error,
   verificar que el hint principal nombra el ángulo/dirección esperados. Ajustar
   `amplitudeThresholdRad` y `tempoRatioThreshold` hasta maximizar la precisión del primer
   hint (matriz de confusión hint-esperado × hint-emitido).
4. **Segmentador**: contar reps detectadas vs. reales en todas las tandas. Falsos
   positivos → subir `minProminenceRad`; reps perdidas → bajarla. `minPeakGapMs` según la
   cadencia real más rápida observada.
5. **`windowRatio`**: verificar que ninguna rep etiquetada `correcta` dé banda infactible
   (`rawDistance = ∞`); subir sólo lo mínimo necesario.
6. Documentar los valores elegidos y la fecha/dataset de calibración en este archivo.

## Criterio de éxito

- ≥90% de las reps `correcta` clasifican Good; ≥90% de las reps con error clasifican
  NeedsCorrection (por ejercicio).
- El primer hint coincide con el error inducido en ≥80% de las tandas de error dirigidas.
- Cero reps correctas con banda DTW infactible.
