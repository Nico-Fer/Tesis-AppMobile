# Guía para redactar la cobertura de pruebas en el informe (Cap. 7)

Carpeta autocontenida para escribir la subsección de cobertura de la tesis. Todo lo que
está acá fue medido el **2026-11-14** sobre el commit `a4c04d7` de `appMobile` (rama
`ImplementacionJacoco`, con `main` mergeado: incluye el modo sin conexión y el segmentador de
grabación de referencias) más los 9 casos de prueba y la exclusión de Room descritos en la
sección 5, que todavía no estaban commiteados al medir. La medición anterior fue del
2026-10-10 sobre `5d95bd3`. Si volvés a medir, regenerá con los comandos de la sección 8 y
reemplazá los archivos de `figuras/`, `tablas/` y `evidencia/`.

## Contenido de la carpeta

```
informe-cobertura/
├── GUIA_INFORME.md                  ← este archivo
├── figuras/
│   ├── resumen_cobertura.pdf        ← figura para LaTeX (vectorial)
│   └── resumen_cobertura.png        ← misma figura, para Word/preview
├── tablas/
│   ├── tabla_cobertura.tex          ← tabular por paquete, (a) y (b)   → \input
│   ├── tabla_cobertura_clases.tex   ← tabular por archivo de (b)       → \input
│   └── seccion_cobertura.tex        ← borrador completo de la subsección, con % TODO
└── evidencia/
    ├── medicion_2026-11-14.md       ← todas las tablas en Markdown (fuente de los números)
    ├── jacoco_global_a.csv          ← CSV crudo de JaCoCo, módulo completo (a)
    ├── jacoco_alcance_b.csv         ← CSV crudo de JaCoCo, alcance declarado (b)
    ├── jacoco_alcance_b.xml         ← XML crudo de (b) (por si la cátedra pide el reporte)
    └── reporte_html_alcance_b.zip   ← reporte HTML navegable de (b); abrir index.html
```

---

## 1. Qué corrigió la cátedra y qué responde esto

> "Documentan 15 clases de pruebas unitarias JVM para los algoritmos DTW, pero no miden ni
> reportan cobertura (no hay reporte JaCoCo ni % de líneas/ramas). Meta: 85 %."

Lo que ahora se puede afirmar, con evidencia en esta carpeta:

| Pedido | Respuesta | Evidencia |
|---|---|---|
| Reporte JaCoCo | JaCoCo 0.8.15, HTML + XML + CSV | `evidencia/` |
| % de líneas | **99,5 %** sobre el alcance declarado | `tablas/tabla_cobertura.tex` |
| % de ramas | **95,8 %** sobre el alcance declarado | ídem |
| Meta 85 % | Superada en ambas métricas, y exigida por el build | sección 6 |
| Reproducible | Dos comandos, sin dependencias externas | sección 8 |

---

## 2. Herramienta y entorno (qué escribir)

Escribir tal cual, son datos verificados:

- **Herramienta:** JaCoCo 0.8.15, mediante el plugin core `jacoco` de Gradle con tareas
  `JacocoReport` y `JacocoCoverageVerification` propias (no el modo nativo de AGP).
- **Entorno de build:** Gradle 8.13, Android Gradle Plugin 8.12.3, Kotlin 2.1.21,
  JVM target 11.
- **Variante instrumentada:** `awsDebug` (la cobertura es idéntica en `localDebug`; solo
  cambia `BuildConfig.API_BASE_URL`).
- **Framework de pruebas:** JUnit 4.13.2, pruebas unitarias JVM puras (sin Robolectric,
  sin mocks, sin dispositivo).
- **Casos:** **324 casos de prueba en 33 clases**, todos en verde (sin contar
  `ExampleUnitTest`, plantilla de Android Studio). De ellas, **19 clases y 228 casos**
  ejercitan la capa de comparación (b); el resto prueba lógica pura de otras capas (modo
  sin conexión, decodificador de pose, mensajes de corrección, etc.).

> **Corrección obligatoria al texto existente:** el capítulo dice "15 clases". El
> 2026-10-10 había **16** clases reales y se agregaron **4** para cerrar la brecha de
> ramas (20). Desde entonces entraron más clases con el segmentador de grabación y el modo
> sin conexión. Actualizar a 33 en total, o a 19 si el texto habla solo de las pruebas de
> los algoritmos de comparación, y listar las agregadas para la cobertura (sección 5).

Por qué JaCoCo y no Kover (si lo preguntan): es la herramienta que la cátedra nombró y la
que un evaluador reconoce; desde 0.8.13–0.8.15 filtra los artefactos del compilador de
Kotlin (`when` exhaustivos, argumentos por defecto, corrutinas, `inline`, safe-call,
Compose, serialización) y —verificado en el XML— también los métodos sintetizados de las
`data class`, así que el conteo de ramas corresponde a ramas escritas en el código.

---

## 3. Alcance y exclusiones (la parte que hay que defender)

### Dos números, siempre

Reportar **los dos**. No reportar solo (b): la cátedra podría pensar que se ocultó el
resto. No reportar solo (a): no mide lo que el capítulo evalúa.

| | Qué mide | Líneas | Ramas |
|---|---|---|---|
| **(a) Módulo completo** | Toda la app, excluyendo únicamente código generado | 11,4 % (1877/16512) | 19,2 % (929/4837) |
| **(b) Alcance declarado** | Capa de comparación de movimiento | **99,5 %** (1127/1133) | **95,8 %** (683/713) |

### Qué es el alcance (b)

Escribir que (b) es el núcleo algorítmico del trabajo y el objeto del plan de pruebas:

- Paquete `domain.comparison` completo (19 archivos), que incluye:
  - `dtw/Dtw.kt` — DTW multidimensional con banda de Sakoe-Chiba
  - `dtw/LocalDistance.kt` — distancia angular euclidiana ponderada con envolvimiento ±π
  - `dtw/ShapeSimilarity.kt` — similitud de forma (NCC + ratio de amplitud)
  - `PeakValleyRepetitionSegmenter.kt` — segmentación online de repeticiones (sesión en vivo)
  - `AdaptiveRestRepetitionSegmenter.kt` — segmentación al grabar la referencia: calibración
    de reposo con quietud, umbral por ruido y sin amplitud mínima (nuevo desde el 10/10)
  - `RepShapeGate.kt` — forma de la repetición candidata: NCC contra plantillas de Tukey y
    conteo de picos (nuevo desde el 10/10)
  - `ReferenceAlignment.kt` — rotación al valle y normalización a excursión
  - `ReferenceRepSelector.kt` — medoide DTW, descarte de outliers, cierre de ciclo, fps
  - `ErrorAttributor.kt` — atribución de errores por dimensión y fase
  - `DefaultAngleExtractor.kt`, `MovementPatternCatalog.kt`, modelos y contratos
- De `data.comparison`, solo las dos clases sin dependencias de Android:
  - `DtwMovementComparator.kt` — orquestador del pipeline
  - `ReferenceJson.kt` — parser y escritor del formato de referencias

### Exclusiones de (a): solo código generado

Cada patrón se verificó contra las clases compiladas reales. Escribir las cinco
categorías con su razón:

| Categoría | Patrones | Razón |
|---|---|---|
| Android Gradle Plugin | `R`, `R$*`, `BuildConfig`, `Manifest` | Generados por el build |
| Hilt / Dagger (KSP) | `Hilt_*`, `*_Factory`, `*_MembersInjector`, `*_GeneratedInjector`, `*_HiltModules*`, `*Module_Provide*Factory`, `Dagger*`, paquetes `dagger/` y `hilt_aggregated_deps/` | Generados por el procesador de anotaciones |
| Compilador de Compose | `ComposableSingletons$*` | Lambdas de composables sin captura, sintetizadas |
| kotlinx.serialization | `*$$serializer` | Generado por el plugin del compilador |
| Room (KSP) | `*_Impl`, `*_Impl$*` (`DomiDatabase_Impl`, `*Dao_Impl`) | Implementación de la base local del modo sin conexión, generada por el procesador de Room. Agregada el 14/11; ninguna clase escrita a mano lleva `_Impl` (los repositorios se llaman `*RepositoryImpl`) |

### Qué NO se excluye (y hay que decirlo)

**Nada escrito a mano.** La UI Compose (`presentation.*`), la integración CameraX/TFLite
(`data.vision`, salvo `PosePostprocessor` que sí tiene tests JVM), los módulos de DI
(`data.di`), los repositorios de red y el modo sin conexión (`data.local` —salvo el
código generado por Room—, `data.sync`) **quedan dentro de (a)** con cobertura baja y
aparecen desglosados en la tabla. Justificación a escribir:

- Su instrumento de verificación son las pruebas instrumentadas / manuales (referenciar
  la sección del capítulo que las describe), no las unitarias JVM.
- Excluirlas inflaría (a) artificialmente; se prefiere un (a) bajo y honesto con un (b)
  alto y acotado.

Dentro de `data.comparison`, quedan fuera de (b) pero dentro de (a):
`CsvRepMetricsSink`, `LocalAssetReferenceRepository`, `RemoteFirstReferenceRepository`
(dependen de `android.content.Context` / `Log`) y `NoOpMovementComparator` (sin lógica).

---

## 4. Resultados (qué tablas y figura poner)

### Tabla por paquete → `tablas/tabla_cobertura.tex`

Insertar con `\input{...}` dentro de un `table`. Muestra (a) desglosado por paquete y (b)
por subpaquete. Los números clave de (b):

| Paquete | Líneas | % | Ramas | % |
|---|---:|---:|---:|---:|
| data.comparison | 208/211 | 98,6 % | 72/78 | 92,3 % |
| domain.comparison | 742/745 | 99,6 % | 458/477 | 96,0 % |
| domain.comparison.dtw | 177/177 | 100,0 % | 153/158 | 96,8 % |
| **Total (b)** | **1127/1133** | **99,5 %** | **683/713** | **95,8 %** |

### Tabla por archivo → `tablas/tabla_cobertura_clases.tex`

Desglose de (b) por archivo fuente. Útil para mostrar que ningún archivo del núcleo quedó
por debajo del 85 % de líneas y que los de menor cobertura de ramas (`AngleExtractor.kt`
86,7 %, `DtwMovementComparator.kt` 91,7 %) igual superan la meta. El segmentador de
grabación, que entró después de la primera medición con 80,5 % de ramas, quedó en 97,7 %
(130/133) y 100 % de líneas. La columna "Ramas sint." vale 0 en todas las filas: confirma
que JaCoCo filtró los métodos sintetizados.

### Figura → `figuras/resumen_cobertura.pdf`

Barras de líneas y ramas por paquete de (b), más los totales de (b) y (a), con la línea
de la meta del 85 %. Pie sugerido:

> Cobertura de líneas y ramas del alcance declarado (b), por paquete, frente a la meta
> del 85 %. Las dos barras finales muestran los totales de (b) y del módulo completo (a).

### Antes / después (opcional, pero vale la pena)

Si el capítulo tiene espacio, mostrar la medición **antes** de cerrar la brecha, porque
demuestra que la cobertura se midió primero y se trabajó después:

| Momento | Casos | Líneas (b) | Ramas (b) |
|---|---:|---:|---:|
| 2026-10-10, medición base (16 clases originales) | 146 | 96,1 % | **76,9 %** |
| 2026-10-10, tras cerrar la brecha (20 clases) | 217 | 99,3 % | 95,3 % |
| 2026-11-14, re-medición con el segmentador de grabación (33 clases) | 315 | 98,7 % (1118/1133) | **92,4 %** (659/713) |
| 2026-11-14, tras cerrar la brecha del segmentador | 324 | 99,5 % (1127/1133) | 95,8 % (683/713) |

Las dos veces la brecha estuvo en **ramas**, no en líneas. El 10/10 faltaban las
precondiciones de constructores (`require`), los bordes de la banda DTW y los caminos
infactibles. El 14/11 el código nuevo del alcance (`AdaptiveRestRepetitionSegmenter`,
80,5 % de ramas) tenía sin probar sus precondiciones, el conductor inválido, la
calibración por timeout y el re-anclaje de una postura sostenida. La columna "Casos"
cuenta toda la suite JVM, no solo (b).

---

## 5. Pruebas agregadas para cerrar la brecha (qué verifican)

Escribir que se agregaron **71 casos** que verifican comportamiento, no relleno. Las
cuatro clases nuevas y los temas cubiertos por clase existente:

**Clases nuevas**
- `AngleDefinitionTest` — pesos no positivos y ternas degeneradas (extremo = vértice);
  `ExerciseDefinition` sin ángulos o con conductor fuera de rango.
- `AngleFrameTest` — máscara de validez de distinto tamaño que los ángulos.
- `ExerciseReferenceTest` — trayectoria vacía.
- `ReferenceProvenanceTest` — solo la referencia del profesional respalda juicio de
  corrección (RNF-17).

**Casos agregados a clases existentes**
- `DtwTest` — banda de Sakoe-Chiba: `windowRatio` 0 / negativo, ventana ≥ longitud
  (equivale al DTW completo), secuencia de un cuadro contra una larga, banda infactible
  con `computePath` (devuelve ∞ y sin camino).
- `WrappedAngularDistanceTest` — sin pesos, peso cero/negativo, cuadros de dimensión
  incompatible.
- `ShapeSimilarityTest` — menos de 3 pares alineados (neutro), sin dimensiones con
  evidencia (neutro), referencia plana contra usuario con movimiento (castiga).
- `PeakValleyRepetitionSegmenterTest` — las 6 precondiciones del constructor,
  conductor fuera del vector de ángulos, `tunedFor` con amplitud no positiva.
- `ReferenceAlignmentTest` — lista vacía, dimensión nunca válida, trayectoria de un
  cuadro, `valleyIndex` fuera de rango, conductor fuera de rango, sin conductor válido.
- `ReferenceRepSelectorTest` — `estimateFps` con <2 frames y con timestamps no
  crecientes; `cycleClosureErrorRad` con ciclo cerrado, peor dimensión con envolvimiento
  en ±π, toma que no vuelve al reposo, lista vacía; reps con camino DTW infactible.
- `DefaultAngleExtractorTest` — baja confianza en vértice y en extremo C, extremo A
  superpuesto al vértice, frame con menos keypoints que los que pide la terna.
- `ErrorAttributorTest` — rep/referencia vacías, dimensión sin pares válidos, sin
  duración real en timestamps, fase sin frames en el camino.
- `MovementPatternCatalogTest` — variantes izq/der son patrones distintos (ángulos
  signados); precondiciones de `MovementPattern` (slug vacío, sin ángulos, conductor fuera
  de rango y negativo); `notes` opcional.
- `ReferenceJsonParserTest` — fps no positivo, `similarityScale` no positiva, peso por
  defecto, claves desconocidas ignoradas.
- `DtwMovementComparatorTest` — tolerancia marcada como calibrada pero sin ángulos,
  repositorio de tolerancia que falla, log de salud con mayoría de cuadros inválidos y con
  cuadros válidos.

**Re-medición del 2026-11-14: 9 casos más** para el código que entró al alcance después
de la primera medición:
- `AdaptiveRestRepetitionSegmenterTest` (8):
  - las precondiciones del constructor (8 parámetros inválidos);
  - perder el conductor en las pausas y en la calibración no corta la cuenta;
  - un conductor fuera del vector de ángulos nunca calibra;
  - un hueco de detección en medio de una repetición no la corta, y los cuadros sin
    conductor viajan dentro de la rep;
  - una ventana de calibración con pocas muestras espera a juntarlas;
  - si la persona nunca se queda quieta, calibra al vencer el timeout con el umbral
    topeado en `MAX_EXIT_RAD`;
  - una postura sostenida más de `maxRepFrames` pasa a ser el reposo y la rep siguiente
    cuenta desde ahí;
  - sin suavizado (α = 1) también cuenta.
- `RepShapeGateTest` (1): una serie de exactamente `SAMPLES` puntos se usa sin remuestrear.

Nota: el test existente "el umbral de salida escala con el ruido" dice que el caso
ruidoso "puede" calibrar recién en el timeout, pero con σ = 0,05 rad el umbral resultante
(~0,115) queda debajo del de quietud y calibra antes. Por eso el timeout tiene su propio
test, con σ = 0,2 rad.

### Las 30 ramas que quedan sin cubrir

Se revisaron una por una en el XML/HTML (`evidencia/reporte_html_alcance_b.zip`). Son las
26 del 10/10 más 4 del código nuevo, todas de la categoría 3. Escribir que corresponden a
tres categorías y que ninguna es comportamiento observable sin probar:

1. **Desempates internos de la recurrencia DTW** (`Dtw.rollingRows`/`fullMatrix`,
   4 ramas): con la distancia angular real no son alcanzables sin construir una métrica
   artificial.
2. **Operandos de condiciones compuestas** (`&&`/`||`) cuyo cortocircuito ya fue
   ejercitado por otro operando equivalente (`DefaultAngleExtractor`, `ErrorAttributor`,
   `ShapeSimilarity`, `DtwMovementComparator`).
3. **Ramas defensivas** que protegen invariantes ya garantizadas por el llamador o por una
   validación previa (`PeakValleyRepetitionSegmenter`, `ReferenceAlignment`,
   `ReferenceRepSelector`, y la segunda comprobación de valores no finitos en
   `ReferenceJsonParser`, que kotlinx.serialization rechaza antes). Desde el 14/11 se
   suman:
   - `AdaptiveRestRepetitionSegmenter`, 3 ramas. El cuadro donde cierra una repetición
     siempre tiene el conductor válido, así que nunca se dan la serie de desviaciones vacía
     (`maxOrNull() ?: 0f`), el reposo nuevo `NaN` ni el rango vacío en `deviationSeries`.
   - `RepShapeGate`, 1 rama: el caso de colección vacía del `maxOf` en línea de la
     biblioteca estándar. Las plantillas son una lista constante de 3.

---

## 6. Cumplimiento del umbral (qué escribir)

La meta no es un número reportado sino **exigido por el build**:

- Tarea `jacocoComparisonVerification` en `app/build.gradle.kts`, regla de JaCoCo sobre
  el alcance (b), elemento `BUNDLE`, contadores `LINE` y `BRANCH`, `COVEREDRATIO`
  mínimo **0,85** en ambos.
- Si cualquiera de los dos baja, el build falla.
- **Se probó el mecanismo**: elevando temporalmente el mínimo de ramas a 0,99 la tarea
  falló con `Rule violated for bundle app: branches covered ratio is 0.95, but expected
  minimum is 0.99`. Luego se restauró a 0,85.
- En la re-medición del 2026-11-14 la tarea pasa con 0,995 de líneas y 0,958 de ramas.

Mencionar como trabajo futuro (no está implementado): un job de integración continua que
ejecute `gradlew testAwsDebugUnitTest jacocoComparisonVerification` y publique el HTML
como artefacto; las pruebas son JVM puras, así que no requiere emulador, el modelo
`.tflite` ni credenciales.

---

## 7. Estructura sugerida de la subsección

El borrador completo ya está escrito en `tablas/seccion_cobertura.tex`; buscar `% TODO`.
Orden:

1. **Herramienta y reproducción** (sección 2 de esta guía) — un párrafo + bloque de
   comandos.
2. **Alcance y exclusiones** (sección 3) — `description` con (a) y (b), `itemize` con
   las 4 categorías de exclusión, párrafo de "qué no se excluye".
3. **Resultados** (sección 4) — tabla por paquete, figura, párrafo con los dos totales,
   tabla por archivo, párrafo de las ramas restantes y nota sobre `data class`.
4. **Cumplimiento del umbral** (sección 6) — párrafo + comando.

`% TODO` que tenés que resolver vos:
- Fecha y commit de la medición (hoy: 2026-11-14, `a4c04d7` más los tests de la sección 5;
  si se commitean, poner ese hash).
- Rutas de `\input` e `\includegraphics` relativas a `main_tesis.tex`.
- Referencia cruzada a la sección de pruebas instrumentadas/manuales.
- Actualizar "15 clases" → 33 (o 19 si se habla solo de la capa de comparación) en la
  introducción del capítulo.
- Decidir si se menciona CI como trabajo futuro.

Preámbulo necesario: `\usepackage{booktabs}` y `\usepackage{graphicx}`.

---

## 8. Reproducir la medición

Desde la raíz de `appMobile`:

```powershell
.\gradlew :app:jacocoAwsDebugUnitTestReport :app:jacocoComparisonReport
python docs/cobertura/gen_cobertura.py
.\gradlew :app:jacocoComparisonVerification
```

Salidas: `app/build/reports/jacoco/{awsDebug,comparison}/{html,jacoco.xml,jacoco.csv}` y
los `.tex`/`.pdf`/`.md` regenerados en `docs/cobertura/`. Para actualizar esta carpeta,
copiar de ahí a `figuras/`, `tablas/` y `evidencia/` y actualizar fecha y commit arriba.

---

## 9. Inventario de los otros dos repositorios (por si el capítulo los menciona)

No se tocó ninguno; es solo lo que hay, para decidir el alcance del capítulo.

| Repo | Stack | Pruebas | Cobertura hoy |
|---|---|---|---|
| `tesisApi` (backend) | .NET 8, xUnit 2.9.2, Moq, EF InMemory | ~132 casos en 10 clases; CI corre `dotnet test` | Ninguna reportada, pero `coverlet.collector` 6.0.2 **ya está referenciado** en el `.csproj`: `dotnet test --collect:"XPlat Code Coverage"` da Cobertura XML sin agregar nada |
| `modelosCV` (pipeline) | Python 3.11, PyTorch/Ultralytics + TF | 95 funciones `test_*` en 12 archivos, patrón `main()` (pytest **no** instalado) | Ninguna; requeriría instalar `pytest-cov` |
