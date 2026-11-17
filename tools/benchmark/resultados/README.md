# Resultados del benchmark de pose

Mediciones de rendimiento del pipeline de pose (YOLOv8n-Pose INT8) en dispositivos Android.
Cómo se generan: [docs/benchmark-firebase-test-lab.md](../../../docs/benchmark-firebase-test-lab.md).

```
resultados/
  Informe_benchmark_Test_Lab_10_dispositivos.docx   informe conjunto (2026-10-12/13)
  Figura1_*.png, Figura2_*.png                       figuras del informe
  Observaciones_bateria_memoria_Test_Lab.md          batería, memoria y CPU (2026-11-12/14)
  ftl_2026-10-12/        lote 1 de Test Lab, una carpeta por dispositivo (datos crudos)
  ftl_2026-10-13/        lote 2 de Test Lab
  ftl_consolidado_10/    los 10 consolidados con tools/benchmark/agregar_resultados.py
  ftl_2026-11-12/        lote 1 otra vez, con mediciones de batería y memoria
  ftl_2026-11-14/        lote 2 otra vez, con mediciones de batería y memoria
  ftl_bateria_memoria_10/  consolidado de las corridas de noviembre (incluye energia_memoria.csv)
  medidos_a_mano/        mediciones con la app en dispositivos reales (ver abajo)
  informe_src/           scripts que generan el informe (analisis10.py, build_docx10.js)
```

## Test Lab (10 dispositivos)

Cada carpeta `<fecha>_<codename>_<delegate>/` trae:

| Archivo | Contenido |
|---|---|
| `dispositivo.csv` | modelo, SoC, GPU, Android, RAM, delegate pedido/efectivo, carga del modelo |
| `resumen.csv` | una fila por clip: fps efectivos, inferencia p50/p90, pre/postproceso, detección, reps, score |
| `frames.csv` | una fila por frame procesado: tiempos por etapa |
| `poses.csv` | los 17 keypoints de cada frame detectado |
| `reps.csv` | métricas DTW por repetición (columnas de `rep-metrics.csv`) |
| `muestras.csv` | RSS, corriente y estado térmico cada 500 ms (corridas desde 2026-11) |
| `batterystats/`, `meminfo_fin.txt` | volcados crudos de consumo estimado y memoria (corridas desde 2026-11) |
| `junit.xml` | resultado de la prueba en Test Lab |

En `ftl_consolidado_10/`, `metricas_<codename>_<delegate>.csv` y `rep_metrics_*.csv` tienen el
mismo esquema que los archivos de `medidos_a_mano/`. `precision.csv` compara los keypoints
contra la línea de base de escritorio (`tools/benchmark/linea_base/`) y contra el GT de UCO.

## Medidos a mano (4 dispositivos)

| Archivo | Dispositivo | Qué es |
|---|---|---|
| `metricas_a72.csv`, `metricas_a72_resumen.csv` | Galaxy A72 (Snapdragon 720G) | **Medición real**: log `Inferencia: … ms` de la app, cada 30 frames |
| `rep_metrics_a72.csv` | Galaxy A72 | **Medición real**: métricas DTW por repetición en sesiones |
| `metricas_SamsungSM-g780g.csv` | Galaxy S20 FE (Snapdragon 865) | **Medición real**: métricas DTW por repetición (no tiene tiempos de inferencia) |
| `metricas_motog60*.csv` | Moto G60 (Snapdragon 732G)
| `metricas_xiaomi9pro*.csv` | Redmi Note 9 Pro (Snapdragon 720G)
