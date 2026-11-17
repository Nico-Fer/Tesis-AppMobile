# Batería, memoria y CPU en Firebase Test Lab (10 dispositivos)

Corridas del 2026-11-12 (lote 1) y 2026-11-14 (lote 2), con el benchmark de pose de la
rama `IntegracionFirebaseTestLab` (commit 8901da2, que agrega `ResourceMonitor`). Son los
mismos 10 equipos, clips y modelo (`yolov8n-pose_int8.tflite`) que el informe de rendimiento
de octubre. El código de producción no cambió entre las dos tandas: 8901da2 sólo toca el
test, el build y las herramientas.

## Resumen

- **Memoria.** Con el modelo cargado, el proceso queda en 173–205 MB de PSS en los 10 equipos.
  Cargar el modelo suma entre 45 y 84 MB. Esa diferencia sale sobre todo de la línea de base de
  cada equipo, no del modelo. Con el delegate de GPU se suman además ~22 MB de memoria gráfica.
- **CPU.** Durante la inferencia el proceso usa entre 43 % y 100 % de un núcleo, con
  148–314 ms de CPU por frame procesado. En reposo, con el modelo cargado, usa 2–6 %.
- **Consumo.** Sólo es una estimación del sistema (`batterystats`), no una medición. De los 10
  equipos, **6 dan una estimación coherente**: 1.9–3.1 mAh/min, es decir 19–31 mAh o
  **0.39–0.70 % de batería en una sesión de 10 minutos**. Es un **piso**: no incluye GPU,
  cámara, pantalla ni interfaz. El Xperia 10 II da un valor bastante menor (0.15 %) que no
  se pudo verificar, y **3 equipos no tienen una estimación utilizable**.
- **Térmico.** Ningún equipo pasó del estado térmico 0. La temperatura de batería quedó en
  23–33 °C, pero cada equipo corrió apenas ~4–5 minutos.
- **Las mediciones no alteran el benchmark.** La inferencia p50 de los 10 equipos difiere en
  ±3 ms de la de octubre y la detección sigue en 100 %.

## Cómo se midió

El test reproduce 9 clips (~195 s de video) por el mismo punto de entrada que usa la cámara
en producción. Todas las mediciones se toman fuera del bucle de frames, salvo un muestreador
a 2 Hz en un hilo propio que lee `/proc` y el HAL de batería.

- **Memoria.** Se toma `Debug.getMemoryInfo` (PSS total, heap Java, heap nativo y
  `graphics`) antes y después de crear el intérprete. La diferencia es lo que cuesta el
  modelo con su delegate. Después de cada clip se toma otro snapshot, y el muestreador lleva
  el pico de RSS.
- **CPU.** Se lee `utime + stime` de `/proc/self/stat` alrededor de cada clip. Se expresa
  como porcentaje de **un** núcleo, así que puede pasar de 100 % con varios hilos. La
  decodificación del video corre en otro proceso (`media.swcodec`, UID 1046) y **no** entra
  en esta cuenta.
- **Consumo.**
  - En Test Lab el equipo está siempre enchufado: la batería no baja y la corriente
    instantánea no refleja el consumo de la app (en varios equipos es 0). Por eso se usa la
    estimación de Android
    (`batterystats`), que multiplica el tiempo de CPU por los valores del `power_profile`
    del fabricante.
  - Para que acumule, cada clip corre dentro de una ventana `dumpsys battery unplug` →
    `batterystats --reset` → clip → volcado → `dumpsys battery reset`.
  - De cada volcado se toma **sólo el componente `cpu` del UID de la app**. El total del UID
    incluye además `video`, que es el decoder de los clips y no existe en producción. Ver la
    observación 2 de consumo.
- **Reposo.** Antes del primer clip hay 20 s con el modelo cargado y sin inferir. Es la línea
  de base de CPU y de consumo.
- **De la tasa del benchmark a una sesión real.** En el bucle del benchmark el pipeline está
  ocupado el 78–88 % del tiempo; el resto espera al decoder. En una sesión real, con
  `KEEP_ONLY_LATEST`, está ocupado casi todo el tiempo. La tasa se lleva a ocupación completa
  con `r = r_reposo + (r_clip − r_reposo) / ocupación`. Esta corrección supone que, fuera del
  pipeline, la app consume lo mismo que en reposo, lo cual es razonable porque el decoder
  corre en otro proceso. Se informan las dos cifras, con y sin corrección; la corrección sube
  el valor entre 16 % y 27 %.
- **Porcentaje de batería.** Se calcula con la capacidad que reporta el equipo (contador de
  carga / nivel).

Toda la consolidación la hace `tools/benchmark/agregar_resultados.py` (salida en
`ftl_bateria_memoria_10/energia_memoria.csv`).

## Equipos

| Equipo (Test Lab) | SoC / GPU | API | Delegate | Estimación de consumo |
|---|---|---|---|---|
| Pixel 5 (`redfin`) | SD765G / Adreno 620 | 30 | GPU | válida |
| moto g34 5G (`fogos`) | SD695 / Adreno 619 | 34 | GPU | válida |
| Nord CE3 Lite (`OP5958L1`) | SD695 / Adreno 619 | 34 | GPU | válida |
| Galaxy A52s (`a52sxq`) | SD778G / Adreno 642L | 34 | GPU | válida |
| Galaxy A52 5G (`a52xq`) | SD750G / Adreno 619 | 34 | GPU | válida |
| edge 30 (`dubai`) | SD778G+ / Adreno 642L | 34 | GPU | válida |
| Xperia 10 II (`SO-41A`) | SD665 / Adreno 610 | 31 | GPU | válida, pero baja y sin verificar |
| moto g30 (`caprip`) | SD662 / Adreno 610 | 31 | GPU | **no utilizable** |
| moto g53 5G (`penang`) | SD480+ / Adreno 619 | 34 | GPU | **no utilizable** |
| Galaxy A36 5G (`a36xq`) | SD 6 Gen 3 / Adreno 710 | 35 | **CPU** (XNNPACK) | **no utilizable** |

El Galaxy A36 volvió a caer en CPU en vez de GPU, igual que en octubre: el modelo cargó en
62 ms, contra 2–4 s cuando se crea el delegate de GPU. Sus cifras no se comparan con las de
los demás.

## Memoria

Valores en MB. «Δ» es la diferencia entre antes y después de cargar el modelo. La columna
«PSS en inferencia» es la mediana de los snapshots al final de cada clip, con el rango entre
paréntesis.

| Equipo | RAM | PSS antes | PSS con modelo | Δ PSS | Δ nativo | Δ gráficos | PSS en inferencia | Pico RSS |
|---|---|---|---|---|---|---|---|---|
| Pixel 5 | 7457 | 119 | 180 | +62 | +20 | +22 | 264 (212–443) | 652 |
| moto g34 | 3472 | 115 | 193 | +77 | +28 | +22 | 228 (201–248) | 418 |
| Nord CE3 Lite | 7397 | 129 | 204 | +75 | +26 | +22 | 224 (202–259) | 464 |
| Galaxy A52s | 5371 | 145 | 190 | +45 | +4 | +22 | 226 (194–253) | 406 |
| Galaxy A52 | 5425 | 143 | 190 | +47 | +7 | +22 | 224 (200–248) | 418 |
| edge 30 | 7382 | 145 | 200 | +55 | +5 | +22 | 358 (226–402) | 583 |
| Xperia 10 II | 3637 | 121 | 205 | +84 | +31 | +22 | 240 (224–265) | 454 |
| moto g30 | 3722 | 120 | 203 | +83 | +31 | +0.2 | 235 (222–266) | 463 |
| moto g53 | 3472 | 115 | 195 | +79 | +28 | +22 | 236 (206–244) | 433 |
| Galaxy A36 (CPU) | 7280 | 124 | 173 | +49 | +40 | 0 | 277 (226–310) | 500 |

Observaciones:

1. **El dato más robusto es el PSS con el modelo cargado: 173–205 MB en los 10 equipos.**
   El costo del modelo (Δ PSS) va de 45 a 84 MB, pero la diferencia viene sobre todo de la
   línea de base. Entre los equipos con GPU, los dos Samsung y el edge 30 arrancan en
   143–145 MB y suman 45–55; los otros seis arrancan en 115–129 MB y suman 62–84. En esos tres
   equipos el heap nativo casi no
   crece (+4 a +7 MB, contra +20 a +31 en los demás). No es por la clase de GPU: el Galaxy
   A52 y el moto g34 tienen la misma Adreno 619. **La causa no se investigó.**
2. **El delegate de GPU suma ~22 MB de memoria gráfica** (`Gfx dev` en `dumpsys meminfo`) en
   todos los equipos que la reportan. El moto g30 no reporta `Gfx dev`: sus 0.2 MB son un
   hueco del reporte, no ausencia de memoria gráfica. En CPU, el A36 no suma memoria gráfica
   y el heap nativo crece +40 MB, que es donde XNNPACK guarda los pesos.
3. **El PSS durante la inferencia varía mucho de un clip a otro en el mismo equipo**: 212–443
   MB en el Pixel 5 y 226–402 MB en el edge 30. Lo mueve el heap nativo (48–267 MB en el
   Pixel 5). Sube y baja sin tendencia a lo largo de los clips, así que no hay indicio de una
   fuga, pero **la causa no se aisló**. Puede venir de buffers del propio benchmark: frames
   decodificados y listas de resultados que se acumulan por clip.
4. **Los valores absolutos no son la memoria de la app en producción.** Incluyen la
   infraestructura del test y el manejo del decoder, y no incluyen Compose, la cámara ni el
   preview. El pico de RSS (405–652 MB) cuenta además páginas compartidas con otros procesos
   (bibliotecas), así que sobreestima la memoria propia.
5. Ningún equipo tuvo problemas de memoria durante la corrida, incluidos los de 3.4 GB de RAM.
   No hubo cierres ni reintentos. Esto no prueba nada sobre sesiones más largas.

## CPU

| Equipo | Delegate | CPU en reposo | CPU en los clips (mediana) | ms de CPU por frame | Ocupación del pipeline |
|---|---|---|---|---|---|
| Pixel 5 | GPU | 3.3 % | 68 % | 227 | 86 % |
| moto g34 | GPU | 3.2 % | 73 % | 210 | 83 % |
| Nord CE3 Lite | GPU | 4.7 % | 73 % | 195 | 83 % |
| Galaxy A52s | GPU | 3.4 % | 76 % | 148 | 86 % |
| Galaxy A52 | GPU | 5.6 % | 77 % | 256 | 82 % |
| edge 30 | GPU | 4.1 % | 80 % | 163 | 86 % |
| Xperia 10 II | GPU | 5.6 % | 49 % | 314 | 78 % |
| moto g30 | GPU | 3.0 % | 43 % | 271 | 80 % |
| moto g53 | GPU | 4.4 % | 72 % | 250 | 83 % |
| Galaxy A36 | CPU | 1.8 % | 100 % | 301 | 88 % |

Los porcentajes son de un núcleo.

- **Aun con GPU, la CPU trabaja buena parte del tiempo.** Hace el preprocesado (YUV → RGB →
  letterbox), el postprocesado, la comparación DTW y el trabajo del driver de GPU. Esto es
  coherente con el informe de octubre, donde el preprocesado superaba a la inferencia en
  varios equipos.
- **Los equipos con Adreno 610 (Xperia 10 II, moto g30) usan menos CPU.** Su GPU es la más
  lenta (~350 ms por inferencia) y la CPU queda esperándola. Por eso su porcentaje de CPU es
  el más bajo, aunque sus ms de CPU por frame están entre los más altos (314 y 271).
- **El A36 en CPU queda cerca de un núcleo.** Hace 301 ms de CPU por frame, con ~265 ms de
  pipeline por frame. Aunque el intérprete se configura con 4 hilos, el paralelismo efectivo
  parece bajo. **No se investigó.**
- **El reposo incluye al propio muestreador** (2 lecturas por segundo de `/proc` y del HAL de
  batería). Es una cota superior de lo que consume la app en reposo con el modelo cargado.

## Consumo estimado

Sólo los equipos con una estimación válida. La columna «mAh por s de CPU» mide cuánto cobra
el `power_profile` del equipo por cada segundo de CPU de la app.

| Equipo | mAh por s de CPU | Reposo (mAh/min) | Clips, sin corregir (mAh/min) | Inferencia, corregida (mAh/min) | Sesión de 10 min | % batería en 10 min (sin corregir) | Capacidad |
|---|---|---|---|---|---|---|---|
| Pixel 5 | 0.046 | 0.042 | 1.87 | 2.18 | 21.8 mAh | 0.53 % (0.45 %) | 4131 mAh |
| moto g34 | 0.038 | 0.037 | 1.69 | 2.03 | 20.3 mAh | 0.40 % (0.33 %) | 5051 mAh |
| Nord CE3 Lite | 0.037 | 0.043 | 1.60 | 1.92 | 19.2 mAh | 0.39 % (0.33 %) | 4880 mAh |
| Galaxy A52s | 0.052 | 0.032 | 2.37 | 2.74 | 27.4 mAh | 0.61 % (0.53 %) | 4500 mAh |
| Galaxy A52 | 0.055 | 0.023 | 2.56 | 3.14 | 31.4 mAh | 0.70 % (0.57 %) | 4500 mAh |
| edge 30 | 0.035 | 0.011 | 1.64 | 1.92 | 19.2 mAh | 0.47 % (0.40 %) | 4075 mAh |
| Xperia 10 II | 0.014 | 0.032 | 0.40 | 0.51 | 5.1 mAh | 0.15 % (0.12 %) | 3287 mAh |

Observaciones:

1. **En los 6 equipos coherentes, una sesión de 10 minutos estima 19–31 mAh: entre 0.39 % y
   0.70 % de la batería** (0.33–0.57 % sin la corrección de ocupación). En ellos el
   `power_profile` cobra 0.035–0.055 mAh por segundo de CPU. Dentro de cada equipo varía
   menos de ±10 % entre clips, con una excepción: el primer clip del moto g34 da +22 %.
2. **Hay que descartar el total que `batterystats` atribuye a la app.** El total del UID
   incluye `video`: un costo fijo por el tiempo que la app tiene abierto un decoder de video,
   y en producción no hay decoder. La proporción del total que no es CPU es:
   - 87 % en el Xperia 10 II;
   - 18 % en el Pixel 5, donde el formato de API 30 no la desglosa;
   - 100 % en 6 de los 9 clips del Galaxy A36;
   - 0.1–1.1 % en los otros cinco equipos válidos.

   Con el total, el Xperia 10 II parecía gastar 1.2 % en 10 minutos; con sólo la CPU gasta
   0.15 %. Por eso no hay que usar la columna `consumo_estimado_mah` de `resumen.csv`, que es
   el total.
3. **El Xperia 10 II queda muy por debajo del resto y no se pudo verificar.** Su perfil cobra
   0.014 mAh por segundo de CPU, unas 3 veces menos que los demás, de forma estable en los 9
   clips. Puede ser una CPU más eficiente o un perfil que subestima. Sin una medición física
   no hay forma de distinguirlo.
4. **Tres equipos no tienen una estimación utilizable**, por cómo el fabricante configuró
   `batterystats` y no por el código:
   - **moto g30**: el perfil cobra 0.0012 mAh por segundo de CPU, unas 30 veces menos que el
     resto, de forma estable en los 9 clips. Con eso, la sesión de 10 minutos daría 0.01 %.
   - **moto g53**: atribuye `cpu=0` a la app con 22 s de CPU medidos en un clip.
   - **Galaxy A36 (API 35)**: atribuye CPU a la app en 3 de los 9 clips y en los otros 6 sólo
     `video`, aunque la app usó 97–101 % de CPU en los 9. Además corrió en CPU y no se compara
     con los demás.

   El criterio de validez está en `agregar_resultados.py` (columna `consumo_cpu_valido`). La
   CPU tiene que figurar en todos los clips y el perfil tiene que cobrar al menos 0.005 mAh
   por segundo de CPU.
5. **Es un piso, y probablemente bastante por debajo del consumo de una sesión real.**
   - **No incluye la GPU.** En los equipos con delegate de GPU, la inferencia misma, la
     mayor parte del trabajo del modelo, no está estimada.
   - **No incluye cámara, pantalla, preview ni interfaz.** Como orden de magnitud, en los
     mismos volcados `batterystats` estima la pantalla encendida en 1.2–3.1 mAh/min, del
     mismo orden que la app. El brillo de Test Lab no está controlado.
6. **Depende de un perfil del fabricante que nadie validó.** Los tres equipos descartados
   muestran cuánto puede fallar un perfil. Los 6 coherentes coinciden entre sí, pero no se
   contrastaron con una descarga medida. **Las cifras absolutas son indicativas.** Sirven para
   comparar equipos y para acotar el orden de magnitud, no como consumo real.

## Térmico y duración

Cada equipo corrió una pasada: ~4–5 minutos, con 20 s de reposo y 9 clips de 11–30 s de
video, cada uno precedido por el reset de `batterystats`. La temperatura de batería quedó en 23–33 °C y
el estado térmico en 0 (sin throttling) en todos. Una sesión real de 10 minutos o más, con
cámara y pantalla, calienta más. En el Galaxy A72 medido a mano, la inferencia pasó de ~125
a ~160 ms después de ~10 minutos. Estas corridas no cubren ese régimen.

## Control: la medición no perturba el benchmark

| Equipo | Inferencia p50, oct → nov (ms) | Preprocesado p50, oct → nov (ms) | fps efectivos, oct → nov |
|---|---|---|---|
| Pixel 5 | 117 → 117 | 182 → 184 | 3.50 → 3.50 |
| moto g34 | 117 → 116 | **153 → 100** | 3.71 → 4.25 |
| Nord CE3 Lite | 118 → 118 | 102 → 100 | 4.49 → 4.54 |
| Galaxy A52s | 74 → 74 | 90 → 90 | 5.96 → 6.00 |
| Galaxy A52 | 119 → 119 | 142 → 143 | 3.75 → 3.79 |
| edge 30 | 71 → 71 | 100 → 100 | 5.75 → 5.79 |
| Xperia 10 II | 363 → 360 | 122 → 122 | 2.12 → 2.12 |
| moto g30 | 351 → 352 | 141 → 136 | 2.08 → 2.12 |
| moto g53 | 140 → 140 | **163 → 131** | 3.29 → 3.50 |
| Galaxy A36 (CPU) | 176 → 177 | 87 → 87 | 3.83 → 3.83 |

- La inferencia coincide en ±3 ms y la detección sigue en 100 % en todos los equipos. El
  muestreador y las ventanas de `batterystats` no alteran lo que mide el benchmark.
- **Dos Motorola preprocesaron bastante más rápido que en octubre:** moto g34 −35 % y moto
  g53 −20 %. El código de producción no cambió y el preprocesado corre en CPU, así que lo
  más probable es que haya cambiado algo del lado del equipo (otra unidad física, firmware o
  política de frecuencias). **No se pudo verificar.** Esto hace que el preprocesado de esos
  dos equipos sea el dato menos reproducible del benchmark.
- Las repeticiones detectadas en el clip `app_0006_ext_rodilla` variaron ±1–2 entre tandas.
  Es coherente con la sensibilidad del segmentador a la cadencia que ya se señaló en octubre.

## Limitaciones

- Una sola pasada por equipo y una sola unidad física de cada modelo. No hay medida de
  variabilidad entre corridas salvo la comparación con octubre, que sólo cubre los tiempos.
- El consumo es una estimación de `batterystats`, no una medición. Ver las observaciones 4 a
  6 de consumo.
- No están la cámara, el preview ni la interfaz. La memoria y la CPU son las del pipeline más
  la infraestructura del test, no las de la app completa.
- Corre el build debug, igual que las mediciones de octubre y las hechas a mano.
- Test Lab no permite fijar brillo ni temperatura. La temperatura queda registrada por clip
  para interpretar valores atípicos.

## Datos

- Crudos: `ftl_2026-11-12/` (lote 1) y `ftl_2026-11-14/` (lote 2). Cada equipo tiene
  `dispositivo.csv`, `resumen.csv`, `muestras.csv`, `meminfo_fin.txt` y `batterystats/`
  (volcado de cada clip y del reposo).
- Consolidado: `ftl_bateria_memoria_10/`. `energia_memoria.csv` trae una fila por equipo;
  las columnas `*_cpu` son las que usa este documento y las `*_total_uid` incluyen `video`.
- Se regenera con:

  ```powershell
  python tools\benchmark\agregar_resultados.py --entrada tools\benchmark\resultados\ftl_2026-11-12 --entrada tools\benchmark\resultados\ftl_2026-11-14 --salida tools\benchmark\resultados\ftl_bateria_memoria_10
  ```
