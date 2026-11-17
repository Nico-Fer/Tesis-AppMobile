# Benchmark de pose en Firebase Test Lab

Mide el pipeline de pose (preprocesado → YOLOv8-Pose → postprocesado) y la comparación DTW
sobre clips del dataset, en dispositivos físicos de Test Lab equivalentes a los que se
midieron a mano. Todo corre desde Android Studio como una tarea de Gradle.

## Qué mide y cómo

`PoseBenchmarkTest` (`app/src/androidTest/.../benchmark/`) reproduce cada clip **como si
fuera la cámara**:

- Los frames se decodifican a YUV_420_888 y entran por el mismo
  `PoseEstimator.estimate(ImageProxy, rotationDegrees)` que llama el analyzer de CameraX
  (`MediaImageProxy` adapta el `Image` del decoder). La conversión YUV→RGB es la de
  CameraX, así que el costo de preprocesado es el de producción.
- Un reloj virtual reproduce `STRATEGY_KEEP_ONLY_LATEST`: mientras el pipeline está
  ocupado, los frames que llegan se descartan y se procesa el último disponible. Los
  **fps efectivos** y la cadencia que ve el comparador son los que tendría un paciente con
  ese dispositivo. La decodificación no entra en ninguna medición.
- Las poses detectadas pasan por el `DtwMovementComparator` real contra la referencia
  empaquetada del ejercicio, igual que en `RealtimeExerciseViewModel`.
- Los clips tienen la geometría del analyzer: 640×480 apaisado + `rotationDegrees = 90`
  (480×640 vertical), con la persona llenando el encuadre. Si de pie no entra entera en
  3:4, se usa el alto completo con bandas negras laterales en vez de cortar cabeza o pies:
  en producción la pantalla de encuadre exige el cuerpo entero.

Por frame procesado queda el tiempo de cada etapa (vía `PoseTimingSink`, el único cambio
en código de producción: no-op fuera del test) y la pose cruda. Por clip queda un resumen
con fps efectivos, percentiles de inferencia, detección, reps detectadas y score medio.

### Batería y memoria

Se mide con `ResourceMonitor` (androidTest), siempre **fuera del bucle de frames**. La única
excepción es un muestreador de 2 Hz en su propio hilo, que lee `/proc` y el HAL de batería.

- **Memoria.** Se toma un snapshot de `Debug.getMemoryInfo` (PSS total, Java heap, native
  heap y `graphics`, que incluye los buffers del delegate de GPU) antes y después de cargar
  el modelo; la diferencia es lo que cuesta el modelo. Por clip quedan la memoria al
  terminar, el pico de RSS muestreado y `VmHWM` (el pico de RSS del proceso). Al cerrar se
  guarda `dumpsys meminfo` completo.
- **CPU.** Se toma `utime + stime` de `/proc/self/stat` alrededor de cada clip. Incluye todos
  los hilos del proceso: el demuxer del video, pero no el decoder, que corre en
  `media.swcodec`. No incluye la GPU.
- **Consumo.** En Test Lab el equipo **siempre está enchufado**, así que el nivel de batería
  no baja y la corriente no sirve. Se usa la estimación del sistema: `batterystats`, en mAh
  para el UID de la app, calculada con el `power_profile` del fabricante. El total del UID
  (`consumo_estimado_mah` en `resumen.csv`) incluye `video`, que es el decoder de los clips y
  no existe en producción: llegó al 87 % en el Xperia 10 II. Por eso `agregar_resultados.py`
  toma sólo el componente `cpu` de cada volcado.
  Para que acumule, cada clip corre dentro de una ventana `dumpsys battery unplug` →
  `batterystats --reset` → clip → volcado → `dumpsys battery reset`. La ventana se cierra
  antes de leer la temperatura, porque mientras dura el desenchufe el intent de batería se
  congela. La descarga real se midió a mano en el celular propio.
- **Reposo.** Antes del primer clip hay 20 s con el modelo cargado y sin inferir
  (`benchmark.reposoS`). Es la línea de base de CPU y consumo.

## Dispositivos

Equipos de gama media del catálogo, **distintos de los medidos a mano** (Galaxy A72,
Moto G60, Redmi Note 9 Pro, Galaxy S20 FE) pero de la misma clase: Snapdragon con GPU
Adreno, como todos los medidos. Van en lotes de 5 (`ftlLotes` en `app/build.gradle.kts`),
que es lo que entra en los 30 min diarios sin costo.

**Lote 1** (`ftlLote1`, corrido el 2026-10-12):

| Test Lab | Equipo | SoC / GPU | API | Rol en la muestra |
|---|---|---|---|---|
| `caprip` | moto g30 | Snapdragon 662 / Adreno 610 | 31 | Piso de la gama media |
| `fogos` | moto g34 5G | Snapdragon 695 / Adreno 619 | 34 | GPU ≈ Adreno 618 de A72, G60 y Redmi |
| `OP5958L1` | OnePlus Nord CE3 Lite | Snapdragon 695 / Adreno 619 | 34 | Mismo SoC que el g34, otro fabricante (como A72 y Redmi) |
| `redfin` | Pixel 5 | Snapdragon 765G / Adreno 620 | 30 | Snapdragon 7 de 2020, la generación del 720G/732G |
| `a52sxq` | Galaxy A52s 5G | Snapdragon 778G / Adreno 642L | 34 | Techo de la gama, familia Galaxy A |

**Lote 2** (`ftlLote2`, validado contra el catálogo vivo el 2026-10-12). Cada equipo es el
segundo fabricante de una clase de GPU del lote 1, o una clase nueva:

| Test Lab | Equipo | SoC / GPU | API | Rol en la muestra |
|---|---|---|---|---|
| `a52xq` | Galaxy A52 5G | Snapdragon 750G / Adreno 619 | 34 | Hermano directo del A72 (misma familia y año) |
| `penang` | moto g53 5G | Snapdragon 480+ / Adreno 619 | 34 | Misma GPU que el g34 con una CPU más débil: aísla el costo del preprocesado |
| `SO-41A` | Xperia 10 II | Snapdragon 665 / Adreno 610 | 31 | Segundo piso, de la época del Redmi Note 9 Pro |
| `dubai` | motorola edge 30 | Snapdragon 778G+ / Adreno 642L | 34 | Segundo techo, otro fabricante |
| `a36xq` | Galaxy A36 5G | Snapdragon 6 Gen 3 / Adreno 710 | 35 | Gama media actual, nueva generación de GPU |

Criterio: la inferencia corre en el delegate de GPU, así que manda la GPU. Entre los dos
lotes cada clase de GPU tiene dos equipos de fabricantes distintos, para separar el efecto
del chip del de la marca. El catálogo no tiene Snapdragon 720G/732G ni ningún Xiaomi de
gama media con API ≥ 29. Se descartaron los que repiten el Snapdragon 695 (A23, Nord CE2
Lite, Xperia 10 V) y los MediaTek, Exynos y Unisoc.

**Ojo con los datos de partida:** `metricas_motog60.csv` y `metricas_xiaomi9pro.csv` no son
mediciones. Son las muestras del A72 multiplicadas por un factor (0.9 y 1.0, columnas
`base`/`factor`).

Los dispositivos se declaran en `app/build.gradle.kts` (`ftlLotes`). El catálogo
cambia. La lista sale de una foto de febrero de 2026, así que conviene confirmarla antes de
correr:

```powershell
gcloud firebase test android models list --filter="PHYSICAL"
gcloud firebase test android models describe fogos
```

## Clips

Se generan desde `modelosCV` con `tools/benchmark/preparar_clips.py`. Los `.mp4` no se
versionan, igual que los `.tflite`. Lo que sí se versiona es el catálogo, `clips.json`.

| id | Fuente | Para qué |
|---|---|---|
| `app_0001_elevacion_lateral` | `data/references/videos/C1000000-…-0001.mp4` | Control positivo: es la fuente de la referencia |
| `app_0002_sentadilla` | `…-0002.MOV` (desde 1.9 s, como la referencia) | Control positivo |
| `app_0005_talones` | `…-0005.MOV` | Control positivo |
| `app_0006_ext_rodilla` | `…-0006_SinManos.MOV` | Control positivo |
| `app_0006_ext_rodilla_heldout` | `…-0006_CpnManos.MOV` (desde 21 s) | **Held-out**: la toma que perdió como referencia |
| `uco_s4_e01_cam3` | UCO sujeto 4, ej. 01, cam 3 | Precisión contra GT, rodilla izq. |
| `uco_s14_e05_cam0` | UCO sujeto 14, ej. 05, cam 0 | Precisión contra GT, rodilla der. |
| `uco_s4_e13_cam2` | UCO sujeto 4, ej. 13, cam 2 | Precisión contra GT, brazo der. |
| `uco_s11_e09_cam4` | UCO sujeto 11, ej. 09, cam 4 | Precisión contra GT, brazo izq. |

- Los **`app`** son el dominio exacto de producción (celular, frontal, vertical) y los
  únicos con ejercicio y referencia DTW. Rinden fps y métricas por rep comparables con los
  `rep-metrics.csv` de las sesiones reales. `repsEsperadas` es lo que contó el generador
  de referencias (README de `data/references`), no un conteo manual.
- Los **`uco`** son sujetos del split de validación del fold 0 (4, 11, 14, 19, 20, 23), así
  que ningún student entrenado en ese fold los vio. Se recortan a 24 s y a una ventana 3:4
  alrededor del sujeto. La cámara se eligió con un survey del mismo `.tflite` contra el GT
  (16 cuadros por cámara, error mediano / altura de la caja). **Supino (ej. 03/04) quedó
  afuera**: el modelo falla ahí incluso en escritorio, con error mediano de 0.18–0.35 de la
  altura de la caja contra ~0.03–0.06 sentado, y la app no lo usa.

El script también deja en `tools/benchmark/linea_base/` (versionado) las poses del
**mismo `.tflite` en escritorio** sobre los clips ya codificados, y el GT de UCO llevado al
cuadro recortado. Contra eso se mide la precisión en cada dispositivo.

```powershell
# venv-torch de modelosCV (cv2 + tensorflow). Hace falta ffmpeg con libx264: en el PATH,
# con --ffmpeg <exe> o con `pip install imageio-ffmpeg`.
..\modelosCV\venv-torch\Scripts\python.exe tools\benchmark\preparar_clips.py --modelos-cv ..\modelosCV
```

Como no están en git, una limpieza del repo (`git clean -X`, un clon nuevo) los borra. Las tareas `ftl*` fallan entonces antes de subir nada (`verificarClipsBenchmark`), con la lista de clips faltantes. Si no se cortara ahí, Test Lab correría igual y fallaría clip por clip consumiendo cuota.

Hay que regenerar si cambia el modelo activo, porque la línea de base depende de él. El
test compara la detección contra escritorio sólo si `modeloEscritorio` coincide con
`PoseModelSpec.ACTIVE`.

## Puesta en marcha (una vez)

1. En la consola de Google Cloud del proyecto de Firebase, habilitar **Cloud Testing API**
   y **Cloud Tool Results API**.
2. **Plan Blaze.** Sin cuenta de facturación vinculada, Cloud Storage rechaza la subida del
   APK al bucket de Test Lab (`403 The billing account for the owning project is disabled`)
   y no corre nada. El plan Spark sólo sirve desde la consola web.
3. Credenciales, una de dos:
   - **Cuenta de servicio** (recomendado por Google): con rol *Editor*, que se agrega con
     «+ Agregar otro rol» para conservar los roles que ya tenga. Bajar el JSON **fuera del
     repo** y agregar a `local.properties`
     `ftl.credenciales=C:/ruta/a/ftl-credenciales.json` (barras normales: en un
     `.properties` la `\` es escape), o usar la variable de entorno `FTL_CREDENCIALES`. El
     proyecto se toma del propio JSON. El permiso sobre el bucket de Test Lab tarda unos
     minutos en propagarse después de dar el rol: un `storage.objects.create denied`
     inmediato se resuelve reintentando.
   - **Tu usuario**, con el [gcloud CLI](https://cloud.google.com/sdk/docs/install):
     `gcloud config set project <ID>`, `gcloud auth application-default login` y
     `gcloud auth application-default set-quota-project <ID>`.
4. Generar los clips (sección anterior).

## Correr

En Android Studio: panel **Gradle → app → Tasks → verification →
`ftlLote2GroupAwsDebugAndroidTest`** (o `ftlLote1…`), que corre los 5 dispositivos del lote
en paralelo. Para uno solo está `<nombre>AwsDebugAndroidTest` (p. ej.
`ftlGalaxyA52AwsDebugAndroidTest`). Doble clic crea la run configuration.

Por terminal, agregando `-P` para parametrizar:

```powershell
.\gradlew ftlLote2GroupAwsDebugAndroidTest --continue
.\gradlew ftlPixel5AwsDebugAndroidTest -Pbenchmark.delegate=cpu      # GPU vs CPU
.\gradlew ftlGalaxyA52sAwsDebugAndroidTest -Pbenchmark.vueltas=3     # sesión larga / throttling
.\gradlew ftlMotoG34AwsDebugAndroidTest -Pbenchmark.clips=app_0002_sentadilla,app_0005_talones
```

| Propiedad | Default | Efecto |
|---|---|---|
| `benchmark.delegate` | `gpu` | `cpu` fuerza CPU/XNNPACK (producción siempre intenta GPU) |
| `benchmark.vueltas` | `1` | Pasadas completas sobre los clips. Con el A72 a mano, la inferencia pasó de ~125 a ~160 ms tras ~10 min |
| `benchmark.clips` | todos | ids de `clips.json`, separados por coma |
| `benchmark.reposoS` | `20` | Segundos de reposo con el modelo cargado antes del primer clip. `0` lo omite |

En local (emulador o celular por USB) es lo mismo con
`connectedAwsDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.steadypath.app.benchmark.PoseBenchmarkTest`.
Los CSV quedan en `app/build/outputs/connected_android_test_additional_output/`.

Los resultados de Test Lab quedan anidados tan hondo bajo `app/build/outputs/` que pasan
el límite de 260 caracteres de Windows, y Python no los abre. Hay que copiarlos antes a
una carpeta corta (`cp -r` de Git Bash sí llega) y apuntar ahí el script:
`agregar_resultados.py --entrada tools/benchmark/resultados/<corrida>`.

**Tiempo y costo:** una pasada son ~195 s de video. Medido el 2026-10-12: 237–265 s de
ejecución por dispositivo, y los 5 corren en paralelo (8 min de punta a punta con la cola).
Test Lab cobra sólo la ejecución, por minuto. En Blaze hay 30 min/día físicos sin costo
(5 dispositivos ≈ 21 min) y después USD 5 por hora y dispositivo. Test Lab
[se apaga el 30/09/2027](https://firebase.google.com/docs/test-lab/usage-quotas-pricing).

## Resultados

El test escribe en `/sdcard/Android/media/com.steadypath.app/benchmark/<fecha>_<codename>_<delegate>/`.
El plugin los baja solo, debajo de `app/build/outputs/androidTest-results/managedDevice/`
(el script de abajo busca en todo `app/build/outputs`), y también quedan en el bucket del
proyecto (consola de Firebase → Test Lab → la corrida).

| Archivo | Contenido |
|---|---|
| `dispositivo.csv` | Modelo, codename, SoC, GPU (`GL_RENDERER`), Android, RAM, delegate pedido/efectivo, tiempo de carga del modelo. También memoria antes y después de cargarlo (`*_antes_modelo_mb`, `*_con_modelo_mb`), cargador, capacidad estimada de la batería y la línea de base en reposo (`reposo_*`) |
| `resumen.csv` | Una fila por clip: fps efectivos, inferencia (media/p50/p90/min/max), pre/postproceso p50, detección, reps, score medio, temperatura de batería y estado térmico. Además, CPU (`cpu_ms`, `uso_cpu_pct`, `cpu_ms_por_frame`), ocupación del pipeline, consumo estimado (`consumo_estimado_mah`, `consumo_mah_por_min`) y memoria (`pss_fin_mb`, `*_heap_fin_mb`, `graficos_fin_mb`, `rss_max_mb`, `vmhwm_mb`) |
| `muestras.csv` | Cada 500 ms: RSS, `VmHWM`, corriente, contador de carga, nivel y estado térmico, rotulados por fase (`reposo` o el clip) |
| `batterystats/` | Volcado crudo de `dumpsys batterystats` de cada clip y del reposo |
| `meminfo_fin.txt` | `dumpsys meminfo` del proceso al terminar, con el modelo cargado |
| `frames.csv` | Una fila por frame procesado: tiempos por etapa, instante virtual, detección |
| `poses.csv` | Los 17 keypoints de cada frame detectado |
| `reps.csv` | Métricas DTW por rep, con las columnas de `rep-metrics.csv` |

Para consolidar todas las corridas en los formatos que ya usás:

```powershell
python tools\benchmark\agregar_resultados.py   # o --entrada <carpeta bajada del bucket>
```

Escribe en `tools/benchmark/resultados/consolidado/`:

- `metricas_<codename>_<delegate>.csv` y `..._resumen.csv`, mismo esquema que
  `metricas_a72.csv`: una muestra cada 30 frames, como el log `Inferencia: … ms`. La
  columna `hora` lleva el id del clip, porque el test no tiene reloj de pared por frame.
- `rep_metrics_<codename>_<delegate>.csv`.
- `resumen_todos.csv`.
- `energia_memoria.csv`: una fila por corrida con estos datos:
  - lo que cuesta el modelo en memoria (PSS, native y gráficos);
  - el pico de RSS;
  - CPU en reposo y en inferencia;
  - consumo estimado en mAh/min, en reposo y en inferencia;
  - mAh y **% de batería por sesión de 10 min**, con la capacidad estimada como
    `CHARGE_COUNTER / nivel`.

  El consumo sale del componente `cpu` de cada volcado de `batterystats/` (columnas
  `*_cpu`). Las columnas `*_total_uid` usan el total del UID, que incluye el decoder, y
  quedan sólo como referencia. `consumo_cpu_valido` dice si la estimación del equipo sirve:
  la CPU tiene que figurar en todos los clips y el `power_profile` tiene que cobrar al menos
  0.005 mAh por segundo de CPU (`mah_por_s_cpu`). El bucle del benchmark también
  decodifica, así que la tasa de inferencia se lleva a ocupación completa con
  `r_reposo + (r_clip − r_reposo) / ocupación`, como en una sesión real con
  `KEEP_ONLY_LATEST`.
- `precision.csv`: desvío de keypoints contra escritorio (en altura del cuadro) y, en los
  clips UCO, PCK@0.05/0.10 contra GT **del dispositivo y de escritorio sobre los mismos
  frames**. Una diferencia ahí es numérica del delegate, no del modelo.

Además de escribir los CSV, el test **falla** si un dispositivo detecta 15 puntos menos que
escritorio en algún clip. Así un delegate que devuelve basura no pasa como "rápido".

## Limitaciones conocidas

- No están la cámara, el preview ni Compose compitiendo por CPU/GPU, así que los fps
  efectivos son un **techo** de los de producción. La inferencia sí es comparable 1 a 1
  con el log de producción.
- Corre el build **debug**, como las mediciones a mano. ART optimiza menos el código Kotlin
  del preprocesado (el bucle de normalización) en debug: medir release cambiaría el
  preprocesado, no la inferencia.
- El consumo de Test Lab es una **estimación** (`batterystats`), no una medición. Sale del
  tiempo de CPU y del `power_profile` de cada fabricante, así que no ve la GPU y subestima
  al delegate de GPU. Tampoco incluye cámara, pantalla ni preview: es un piso del consumo de
  una sesión real. La descarga real está en las mediciones a mano.
- Test Lab no deja fijar temperatura ni brillo. El estado térmico queda registrado por clip
  para interpretar outliers.
- Que el lente frontal y el trasero entreguen la misma orientación al analyzer sigue sin
  verificarse en un dispositivo (ver `CLAUDE.md`). Los clips simulan el sensor
  (`rotationDegrees = 90`), sin espejado.

## Validación en emulador (2026-10-12)

Pixel 9 Pro XL AVD, API 36. Sin GPU compatible, así que cae en CPU/XNNPACK, el fallback
de producción. Catálogo completo: 9/9 tests, 4 min.

| | Valor |
|---|---|
| fps efectivos | 7.3–8.8 |
| Inferencia p50 | 78–82 ms (formato log: p50 79.5, p90 92.7) |
| Preproceso p50 | 33–35 ms (≈ 30 % del frame) |
| Total por frame p50 | 112–120 ms |
| Detección | 100 % en los 9 clips (igual que escritorio) |
| Desvío de keypoints contra escritorio, p50 | 0.0015–0.0035 del alto del cuadro |
| PCK@0.10 contra GT (UCO), dispositivo / escritorio | 0.93–1.00 / 0.93–1.00 |

Reps detectadas contra las del generador: 0001 2/2, 0002 4/4, 0005 6/7, 0006 **8/3**. En
0006 la rodilla izquierda tiene 4 extensiones reales (≥ 0.6 rad en la línea de base de
escritorio), así que el segmentador de la app cuenta de más en este clip a ~8.5 fps. El
framing del benchmark no lo explica: escala uniforme y traslación preservan los ángulos.
Vale revisarlo aparte, y coincide con las sesiones reales de 0006 que llegan a repIndex 17.
Otro dato: 0002 puntúa 0.27 de score medio aun siendo el clip fuente de su referencia.

## Primera corrida en Test Lab (2026-10-12)

Lote 1, delegate de GPU en todos y 10/10 pruebas aprobadas por equipo. Los datos crudos
están en `tools/benchmark/resultados/ftl_2026-10-12/`; el informe conjunto de los dos lotes,
en la sección siguiente.

| Equipo | GPU | Inferencia p50 | Preproceso p50 | Total p50 | fps efectivos |
|---|---|---|---|---|---|
| moto g30 (`caprip`) | Adreno 610 | 351 ms | 141 ms | 496 ms | 2.1 |
| moto g34 5G (`fogos`) | Adreno 619 | 117 ms | 152 ms | 274 ms | 3.7 |
| Nord CE3 Lite (`OP5958L1`) | Adreno 619 | 119 ms | 102 ms | 226 ms | 4.5 |
| Pixel 5 (`redfin`) | Adreno 620 | 117 ms | 182 ms | 301 ms | 3.5 |
| Galaxy A52s (`a52sxq`) | Adreno 642L | 74 ms | 90 ms | 168 ms | 5.9 |

- Coherente con el Galaxy A72 medido a mano (Adreno 618): 130 ms de inferencia y 3.5 fps.
- El preprocesado supera a la inferencia en 3 de los 5 equipos. Los dos Snapdragon 695 sólo
  difieren ahí (152 contra 102 ms).
- Detección del 100 % en todos los equipos. El desvío de keypoints contra escritorio es de
  ~0.0017 del alto del cuadro y la PCK@0.10 es la misma que en escritorio.
- Las reps de 0006 crecen con los fps efectivos: 3 a 2.1 fps, 4 a 3.5–3.7, 6 a 4.5 y 7 a
  5.9, con ~4 extensiones reales. El segmentador es sensible a la cadencia.
- No hubo throttling en ~4 min: deriva de la inferencia ≤ 0.3 % y estado térmico 0.

## Segunda corrida en Test Lab (2026-10-13)

Lote 2: 10/10 pruebas aprobadas por equipo. El informe conjunto de los 10, los datos crudos,
los consolidados y las mediciones a mano están en `tools/benchmark/resultados/`; su
`README.md` explica cada archivo.

| Equipo | GPU | Delegate | Inferencia p50 | Preproceso p50 | Total p50 | fps efectivos |
|---|---|---|---|---|---|---|
| Xperia 10 II (`SO-41A`) | Adreno 610 | GPU | 362 ms | 122 ms | 490 ms | 2.1 |
| moto g53 5G (`penang`) | Adreno 619 | GPU | 140 ms | 160 ms | 305 ms | 3.3 |
| Galaxy A52 5G (`a52xq`) | Adreno 619 | GPU | 119 ms | 144 ms | 268 ms | 3.8 |
| edge 30 (`dubai`) | Adreno 642L | GPU | 71 ms | 100 ms | 175 ms | 5.7 |
| Galaxy A36 5G (`a36xq`) | Adreno 710 | **CPU** | 176 ms | 88 ms | 265 ms | 3.8 |

- Cada clase de GPU da lo mismo en los dos fabricantes que la tienen: Adreno 610 351/362 ms
  y Adreno 642L 74/71 ms. La excepción es la Adreno 619 del Snapdragon 480+ (g53), que
  corre a menor frecuencia: 140 ms contra 117–119 ms.
- **El Galaxy A36 no usó la GPU.** El modelo cargó en 64 ms, contra 2.0–4.2 s con GPU, así
  que ni se intentó el delegate. Lo más probable es que `CompatibilityList` de LiteRT
  1.0.1 no apruebe ese equipo. Queda por verificar forzando el delegate o actualizando
  LiteRT.
- Las reps de 0006 siguen la cadencia en los 10 equipos: 3 a 2.1 fps, 4 a 3.3–3.8, 6 a
  4.5–5.7 y 7 a 5.9.
- Antes de esta corrida los `.mp4` se habían borrado del disco. La primera pasada de hoy
  falló por eso; de ahí sale `verificarClipsBenchmark`. Los clips regenerados son
  idénticos: `clips.json` igual al commiteado y la precisión del lote 1 da exactamente
  los mismos valores.

## Batería y memoria (2026-11-12 y 2026-11-14)

Se repitieron los dos lotes con `ResourceMonitor`, y los 10 equipos aprobaron 10/10. El
análisis completo, con todas las salvedades, está en
[`tools/benchmark/resultados/Observaciones_bateria_memoria_Test_Lab.md`](../tools/benchmark/resultados/Observaciones_bateria_memoria_Test_Lab.md).
En corto:

- Con el modelo cargado, el proceso queda en 173–205 MB de PSS. El delegate de GPU suma
  ~22 MB de memoria gráfica.
- En los 6 equipos con estimación coherente, una sesión de 10 minutos gasta 0.39–0.70 % de
  la batería. Es un piso: sólo cuenta la CPU, sin GPU, cámara ni pantalla.
- La estimación no sirve en el moto g30, el moto g53 y el Galaxy A36, por cómo el
  fabricante configuró `batterystats`. El Galaxy A36 volvió a caer en CPU.
- La inferencia coincide con octubre en ±3 ms: la medición no perturba el benchmark.
- Antes de la corrida, los `.mp4` se habían borrado otra vez. Se regeneraron idénticos con
  el ffmpeg 9.0.2 de `imageio-ffmpeg`.
