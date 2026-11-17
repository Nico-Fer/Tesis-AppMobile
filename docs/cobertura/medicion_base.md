# Medición de cobertura (JaCoCo)

Fuentes: `app\build\reports\jacoco\awsDebug\jacoco.xml` (a) y `app\build\reports\jacoco\comparison\jacoco.xml` (b).
Generado por `docs/cobertura/gen_cobertura.py`.

## (a) Módulo completo — solo excluido el código generado

| Paquete | Líneas | % | Ramas | % |
|---|---:|---:|---:|---:|
| (raíz) | 0/32 | 0.0 % | 0/2 | 0.0 % |
| data.auth | 0/102 | 0.0 % | 0/51 | 0.0 % |
| data.comparison | 208/368 | 56.5 % | 72/168 | 42.9 % |
| data.datasource | 2/45 | 4.4 % | 2/28 | 7.1 % |
| data.di | 0/76 | 0.0 % | 0/4 | 0.0 % |
| data.dto | 28/259 | 10.8 % | 0/0 | — |
| data.local | 23/288 | 8.0 % | 14/54 | 25.9 % |
| data.mapper | 42/190 | 22.1 % | 2/50 | 4.0 % |
| data.repository | 4/678 | 0.6 % | 0/326 | 0.0 % |
| data.session | 0/146 | 0.0 % | 0/30 | 0.0 % |
| data.sync | 41/269 | 15.2 % | 20/113 | 17.7 % |
| data.vision | 171/460 | 37.2 % | 42/190 | 22.1 % |
| domain.comparison | 919/922 | 99.7 % | 611/635 | 96.2 % |
| domain.model | 100/261 | 38.3 % | 0/8 | 0.0 % |
| domain.repository | 0/14 | 0.0 % | 0/0 | — |
| domain.usecase | 0/154 | 0.0 % | 0/14 | 0.0 % |
| presentation.common | 0/2 | 0.0 % | 0/0 | — |
| presentation.components | 161/1605 | 10.0 % | 23/524 | 4.4 % |
| presentation.navigation | 0/576 | 0.0 % | 0/2 | 0.0 % |
| presentation.screens | 139/9790 | 1.4 % | 134/2616 | 5.1 % |
| presentation.session | 39/40 | 97.5 % | 9/12 | 75.0 % |
| presentation.ui | 0/235 | 0.0 % | 0/10 | 0.0 % |
| **Total (a)** | 1877/16512 | 11.4 % | 929/4837 | 19.2 % |

## (b) Alcance declarado — capa de comparación

| Paquete | Líneas | % | Ramas | % |
|---|---:|---:|---:|---:|
| data.comparison | 208/211 | 98.6 % | 72/78 | 92.3 % |
| domain.comparison | 742/745 | 99.6 % | 458/477 | 96.0 % |
| domain.comparison.dtw | 177/177 | 100.0 % | 153/158 | 96.8 % |
| **Total (b)** | 1127/1133 | 99.5 % | 683/713 | 95.8 % |

Ramas en métodos sintetizados de `data class` dentro de (b): 0/0 cubiertas (0 sin cubrir).
Ramas de (b) excluyendo esos métodos: 683/713 (95.8 %).

### Por archivo fuente

| Paquete | Archivo | Líneas | % | Ramas | % | Ramas sint. sin cubrir |
|---|---|---:|---:|---:|---:|---:|
| data.comparison | DtwMovementComparator.kt | 112/113 | 99.1 % | 44/48 | 91.7 % | 0 |
| data.comparison | ReferenceJson.kt | 96/98 | 98.0 % | 28/30 | 93.3 % | 0 |
| domain.comparison | AdaptiveRestRepetitionSegmenter.kt | 207/207 | 100.0 % | 130/133 | 97.7 % | 0 |
| domain.comparison | AngleDefinition.kt | 24/24 | 100.0 % | 20/20 | 100.0 % | 0 |
| domain.comparison | AngleExtractor.kt | 29/29 | 100.0 % | 26/30 | 86.7 % | 0 |
| domain.comparison | AngleFrame.kt | 9/9 | 100.0 % | 4/4 | 100.0 % | 0 |
| domain.comparison | Comparisons.kt | 0/0 | — | 0/0 | — | 0 |
| domain.comparison | CorrectionHint.kt | 12/13 | 92.3 % | 0/0 | — | 0 |
| domain.comparison | ErrorAttributor.kt | 80/80 | 100.0 % | 54/58 | 93.1 % | 0 |
| domain.comparison | ExerciseReference.kt | 15/15 | 100.0 % | 4/4 | 100.0 % | 0 |
| domain.comparison | MovementComparator.kt | 13/13 | 100.0 % | 0/0 | — | 0 |
| domain.comparison | MovementPatternCatalog.kt | 90/91 | 98.9 % | 22/22 | 100.0 % | 0 |
| domain.comparison | PeakValleyRepetitionSegmenter.kt | 109/109 | 100.0 % | 63/66 | 95.5 % | 0 |
| domain.comparison | ReferenceAlignment.kt | 57/57 | 100.0 % | 42/44 | 95.5 % | 0 |
| domain.comparison | ReferenceProvenance.kt | 4/4 | 100.0 % | 2/2 | 100.0 % | 0 |
| domain.comparison | ReferenceRepSelector.kt | 49/50 | 98.0 % | 62/64 | 96.9 % | 0 |
| domain.comparison | RepMetricsSink.kt | 1/1 | 100.0 % | 0/0 | — | 0 |
| domain.comparison | RepShapeGate.kt | 39/39 | 100.0 % | 29/30 | 96.7 % | 0 |
| domain.comparison | RepetitionSegmenter.kt | 4/4 | 100.0 % | 0/0 | — | 0 |
| domain.comparison.dtw | Dtw.kt | 104/104 | 100.0 % | 94/98 | 95.9 % | 0 |
| domain.comparison.dtw | LocalDistance.kt | 19/19 | 100.0 % | 26/26 | 100.0 % | 0 |
| domain.comparison.dtw | ShapeSimilarity.kt | 54/54 | 100.0 % | 33/34 | 97.1 % | 0 |
