"""Consolida los resultados de PoseBenchmarkTest (Test Lab o corrida local).

Busca corridas (carpetas con dispositivo.csv + resumen.csv) bajo las rutas de entrada y
escribe en --salida:

  resumen_todos.csv              una fila por corrida x clip (todas las columnas del test)
  metricas_<codename>.csv        mismo esquema que metricas_a72.csv (muestra cada 30
                                 frames procesados, como el log `Inferencia: … ms`)
  metricas_<codename>_resumen.csv  mismo esquema que metricas_a72_resumen.csv
  rep_metrics_<codename>.csv     mismo esquema que rep-metrics.csv de las sesiones
  precision.csv                  desvío de keypoints vs escritorio y PCK vs GT de UCO
  energia_memoria.csv            una fila por corrida: memoria del modelo, pico de RSS, CPU y
                                 consumo estimado (componente `cpu` de batterystats, con una
                                 columna que dice si la estimación del equipo es válida)

Sólo usa la biblioteca estándar. Uso:

  python tools/benchmark/agregar_resultados.py
  python tools/benchmark/agregar_resultados.py --entrada <carpeta bajada de Test Lab>
"""

from __future__ import annotations

import argparse
import csv
import math
import re
import statistics
from collections import defaultdict
from pathlib import Path

APP_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_INPUTS = [
    APP_ROOT / "app" / "build" / "outputs",  # Test Lab (managedDevice) y AGP local
    Path(__file__).resolve().parent / "resultados",  # descargas manuales
]
BASELINE_DIR = Path(__file__).resolve().parent / "linea_base"
DEFAULT_OUTPUT = Path(__file__).resolve().parent / "resultados" / "consolidado"

LOG_EVERY = 30  # YoloPoseEstimator.LOG_EVERY_N_FRAMES: así se midió el A72 a mano
KPT_CONF = 0.5
UPRIGHT_ASPECT = 480 / 640  # ancho/alto del cuadro vertical: lleva dx a unidades de alto
COCO17 = [
    "nose", "left_eye", "right_eye", "left_ear", "right_ear", "left_shoulder",
    "right_shoulder", "left_elbow", "right_elbow", "left_wrist", "right_wrist", "left_hip",
    "right_hip", "left_knee", "right_knee", "left_ankle", "right_ankle",
]


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as fh:
        return list(csv.DictReader(fh))


def write_csv(path: Path, rows: list[dict], columns: list[str] | None = None) -> None:
    if not rows:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    # Unión ordenada: las filas UCO traen columnas de GT que las de la app no tienen.
    columns = columns or list(dict.fromkeys(k for row in rows for k in row))
    with path.open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=columns, extrasaction="ignore")
        w.writeheader()
        w.writerows(rows)
    print(f"  {path.relative_to(APP_ROOT) if path.is_relative_to(APP_ROOT) else path}")


def fnum(value: str | None) -> float | None:
    try:
        return float(value) if value not in (None, "") else None
    except ValueError:
        return None


def percentile(values: list[float], p: float) -> float:
    s = sorted(values)
    rank = p / 100 * (len(s) - 1)
    lo = int(rank)
    hi = min(lo + 1, len(s) - 1)
    return s[lo] + (s[hi] - s[lo]) * (rank - lo)


def find_runs(roots: list[Path]) -> dict[str, Path]:
    """Corridas únicas por nombre de carpeta (la misma puede aparecer copiada dos veces)."""
    runs: dict[str, Path] = {}
    for root in roots:
        if not root.exists():
            continue
        for summary in root.rglob("resumen.csv"):
            run = summary.parent
            if (run / "dispositivo.csv").exists() and (run / "frames.csv").exists():
                runs.setdefault(run.name, run)
    return runs


def device_info(run: Path) -> dict[str, str]:
    return {r["clave"]: r["valor"] for r in read_csv(run / "dispositivo.csv")}


def log_style_samples(run: Path, dev: dict[str, str]) -> list[dict]:
    """Reproduce el log de producción: una muestra cada 30 frames procesados, con los fps
    efectivos entre muestras (30 frames / tiempo virtual transcurrido)."""
    rows = []
    frames = [f for f in read_csv(run / "frames.csv") if f["warmup"] == "0"]
    by_clip: dict[tuple[str, str], list[dict]] = defaultdict(list)
    for f in frames:
        by_clip[(f["vuelta"], f["clip"])].append(f)
    for (_, clip), fs in by_clip.items():
        prev_t = None
        for i in range(LOG_EVERY - 1, len(fs), LOG_EVERY):
            f = fs[i]
            inf = float(f["inferencia_ms"])
            t = float(f["inicio_virtual_ms"])
            fps_eff = LOG_EVERY / ((t - prev_t) / 1000) if prev_t is not None else None
            prev_t = t
            rows.append({
                "dispositivo": f"{dev.get('fabricante', '')} {dev.get('modelo', '')}".strip(),
                "soc": dev.get("soc", ""),
                "android": dev.get("android", ""),
                "modelo": dev.get("modelo_tflite", ""),
                "delegate": dev.get("delegate_efectivo", ""),
                "fecha": dev.get("fecha", "")[:8],
                "hora": clip,  # el test no tiene reloj de pared por frame: se rotula el clip
                "inferencia_ms": f"{inf:.1f}",
                "fps_inferencia": f"{1000 / inf:.1f}",
                "fps_efectivo": f"{fps_eff:.1f}" if fps_eff else "",
            })
    return rows


def log_style_summary(samples: list[dict]) -> dict:
    inf = [float(s["inferencia_ms"]) for s in samples]
    eff = [float(s["fps_efectivo"]) for s in samples if s["fps_efectivo"]]
    first = samples[0]
    p50 = percentile(inf, 50)
    return {
        **{k: first[k] for k in ("dispositivo", "soc", "android", "modelo", "delegate")},
        "muestras": len(inf),
        "media_ms": f"{statistics.mean(inf):.1f}",
        "p50_ms": f"{p50:.1f}",
        "p90_ms": f"{percentile(inf, 90):.1f}",
        "min_ms": f"{min(inf):.1f}",
        "max_ms": f"{max(inf):.1f}",
        "fps_p50": f"{1000 / p50:.1f}",
        "fps_efectivo_p50": f"{percentile(eff, 50):.1f}" if eff else "",
        "fps_efectivo_min": f"{min(eff):.1f}" if eff else "",
        "fps_efectivo_max": f"{max(eff):.1f}" if eff else "",
    }


def load_keypoints(path: Path) -> dict[int, list[tuple[float, float, float]]]:
    out = {}
    for r in read_csv(path):
        if r.get("detectado", "1") == "0":
            continue
        out[int(r["frame"])] = [
            (float(r[f"{n}_x"]), float(r[f"{n}_y"]), float(r[f"{n}_c"])) for n in COCO17
        ]
    return out


def dist_h(ax: float, ay: float, bx: float, by: float) -> float:
    """Distancia en unidades de alto del cuadro vertical."""
    return math.hypot((ax - bx) * UPRIGHT_ASPECT, ay - by)


def precision_rows(run: Path, dev: dict[str, str]) -> list[dict]:
    rows = []
    device_poses: dict[str, dict[int, list]] = defaultdict(dict)
    processed: dict[str, set[int]] = defaultdict(set)
    for f in read_csv(run / "frames.csv"):
        processed[f["clip"]].add(int(f["frame"]))
    for r in read_csv(run / "poses.csv"):
        device_poses[r["clip"]][int(r["frame"])] = [
            (float(r[f"{n}_x"]), float(r[f"{n}_y"]), float(r[f"{n}_c"])) for n in COCO17
        ]
    for clip, frames in processed.items():
        desk_path = BASELINE_DIR / f"{clip}.escritorio.csv"
        if not desk_path.exists():
            continue
        desk = load_keypoints(desk_path)
        dev_kp = device_poses[clip]
        both = sorted(set(dev_kp) & set(desk))
        agree = sum((f in dev_kp) == (f in desk) for f in frames) / max(len(frames), 1)
        deviations = [
            dist_h(d[0], d[1], e[0], e[1])
            for f in both
            for d, e in zip(dev_kp[f], desk[f])
            if d[2] > KPT_CONF and e[2] > KPT_CONF
        ]
        row = {
            "codename": dev.get("codename", ""),
            "delegate": dev.get("delegate_efectivo", ""),
            "clip": clip,
            "frames_comparados": len(both),
            "acuerdo_deteccion": f"{agree:.3f}",
            "desvio_vs_escritorio_p50": f"{percentile(deviations, 50):.4f}" if deviations else "",
            "desvio_vs_escritorio_p90": f"{percentile(deviations, 90):.4f}" if deviations else "",
        }
        gt_path = BASELINE_DIR / f"{clip}.gt.csv"
        if gt_path.exists():
            gt: dict[int, dict[str, tuple[float, float]]] = defaultdict(dict)
            for g in read_csv(gt_path):
                gt[int(g["frame"])][g["keypoint"]] = (float(g["x"]), float(g["y"]))
            for name, poses in (("dispositivo", dev_kp), ("escritorio", desk)):
                # Mismos frames para los dos: los que procesó el dispositivo y tienen GT.
                errs = [
                    dist_h(*poses[f][COCO17.index(k)][:2], *xy)
                    for f in sorted(processed[clip])
                    if f in gt and f in dev_kp and f in desk
                    for k, xy in gt[f].items()
                ]
                row[f"pck05_{name}"] = f"{sum(e < 0.05 for e in errs) / len(errs):.3f}" if errs else ""
                row[f"pck10_{name}"] = f"{sum(e < 0.10 for e in errs) / len(errs):.3f}" if errs else ""
                row[f"error_gt_p50_{name}"] = f"{percentile(errs, 50):.4f}" if errs else ""
        rows.append(row)
    return rows


SESSION_MIN = 10  # duración de referencia para traducir mAh/min a % de batería
MIN_PLAUSIBLE_MAH = 1000  # por debajo, el contador de carga no es una batería de celular real
# mAh por segundo de CPU por debajo del cual el power_profile del equipo pone la CPU en ~0.
# En las corridas de 2026-11 los perfiles razonables dieron 0.013–0.057; moto g30 0.0012 y
# moto g53 0 (`cpu=0` con 22 s de CPU).
MIN_MAH_PER_CPU_S = 0.005


def uid_components(dump: str, uid: str) -> dict[str, float] | None:
    """Total y componentes (`cpu`, `video`, …) del UID en «Estimated power use».

    Los componentes van en la misma línea (`UID u0a1: 1.2 ( cpu=… video=… )`, API 30–34) o
    en las siguientes, indentadas (`video=0.24 (28s)`, Samsung API 35). `cpu:fgs=` es un
    desglose de `cpu=` y no se toma. En API 30 la línea sólo lista algunos componentes: el
    total puede ser mayor que la suma. `None` si el UID no figura (consumo despreciable).
    """
    _, sep, section = dump.partition("Estimated power use")
    if not sep or not uid:
        return None
    lines = section.splitlines()
    head = re.compile(rf"(?i)^\s*uid\s+{re.escape(uid)}\s*:\s*([\d.]+(?:[eE][-+]?\d+)?)(.*)$")
    for i, line in enumerate(lines):
        m = head.match(line)
        if not m:
            continue
        text = m.group(2)
        for nxt in lines[i + 1:]:
            if not nxt.strip() or re.match(r"(?i)^\s*uid\s", nxt):
                break
            text += " " + nxt
        comps = {"total": float(m.group(1))}
        for key, value in re.findall(r"(?<![\w:])(\w+)=([\d.]+(?:[eE][-+]?\d+)?)", text):
            comps.setdefault(key, float(value))
        return comps
    return None


def dump_components(run: Path, dev: dict[str, str], dump_name: str) -> dict[str, float] | None:
    path = run / "batterystats" / f"{dump_name}.txt"
    if not path.exists():
        return None
    return uid_components(path.read_text(encoding="utf-8", errors="replace"), dev.get("uid_batterystats", ""))


def parse_estimated_mah(dump: str, uid: str) -> float | None:
    """Mismo parseo que ResourceMonitor.parseEstimatedMah (API 30 `Uid …`, 31+ `UID …`)."""
    _, sep, section = dump.partition("Estimated power use")
    if not sep or not uid:
        return None
    m = re.search(rf"(?im)^\s*uid\s+{re.escape(uid)}\s*:\s*([\d.]+(?:[eE][-+]?\d+)?)", section)
    return float(m.group(1)) if m else None


def power_profile_capacity(run: Path) -> float | None:
    """`Capacity: N` de batterystats: la capacidad que declara el power_profile del fabricante."""
    for dump in sorted((run / "batterystats").glob("*.txt")):
        m = re.search(r"Capacity:\s*([\d.]+)", dump.read_text(encoding="utf-8", errors="replace"))
        if m:
            return float(m.group(1))
    return None


def clip_mah(run: Path, dev: dict[str, str], row: dict[str, str]) -> float | None:
    """mAh del clip: el valor del dispositivo o, si quedó vacío, el volcado crudo re-parseado."""
    value = fnum(row.get("consumo_estimado_mah"))
    if value is not None:
        return value
    dump = run / "batterystats" / f"{row['clip']}_v{row['vuelta']}.txt"
    if not dump.exists():
        return None
    return parse_estimated_mah(dump.read_text(encoding="utf-8", errors="replace"), dev.get("uid_batterystats", ""))


def energy_memory_row(name: str, run: Path, dev: dict[str, str]) -> dict | None:
    """Recursos de una corrida. `None` para corridas anteriores a estas mediciones."""
    clips = read_csv(run / "resumen.csv")
    if not clips or "cpu_ms" not in clips[0]:
        return None

    def med(key: str) -> float | None:
        vals = [v for v in (fnum(r.get(key)) for r in clips) if v is not None]
        return statistics.median(vals) if vals else None

    def peak(key: str) -> float | None:
        vals = [v for v in (fnum(r.get(key)) for r in clips) if v is not None]
        return max(vals) if vals else None

    def delta(key: str) -> float | None:
        before, after = fnum(dev.get(f"{key}_antes_modelo_mb")), fnum(dev.get(f"{key}_con_modelo_mb"))
        return after - before if before is not None and after is not None else None

    occupancy = med("ocupacion_pct")

    def to_full_occupancy(clip_rate: float | None, idle_rate: float | None) -> float | None:
        # En una sesión real el pipeline está ocupado ~100 % del tiempo (KEEP_ONLY_LATEST).
        # El bucle del benchmark además decodifica, así que se lleva la tasa a ocupación
        # completa: r_inferencia = r_reposo + (r_clip - r_reposo) / ocupación.
        if clip_rate is None or not occupancy:
            return None
        base = idle_rate if idle_rate is not None else 0.0
        return base + (clip_rate - base) / (occupancy / 100)

    # Total del UID (lo que reporta el test). Incluye `video`: el decoder de los clips, que
    # en producción no existe. Queda como referencia; el consumo que vale es el de CPU.
    rates = []
    for r in clips:
        mah, loop_s = clip_mah(run, dev, r), fnum(r.get("duracion_bucle_s"))
        if mah is not None and loop_s:
            rates.append(mah / (loop_s / 60))
    clip_rate = statistics.median(rates) if rates else None
    idle_rate = fnum(dev.get("reposo_mah_por_min"))
    infer_rate = to_full_occupancy(clip_rate, idle_rate)

    # Sólo el componente `cpu` del UID: lo que batterystats calcula con el tiempo de CPU y el
    # power_profile. Es lo único atribuible al pipeline de pose (la GPU no se estima).
    cpu_rates, cpu_per_s, non_cpu_shares = [], [], []
    with_dump = with_cpu = 0
    for r in clips:
        comps = dump_components(run, dev, f"{r['clip']}_v{r['vuelta']}")
        if not comps:
            continue
        with_dump += 1
        with_cpu += comps.get("cpu") is not None
        if comps["total"] > 0:
            non_cpu_shares.append(1 - (comps.get("cpu") or 0.0) / comps["total"])
        cpu_mah, loop_s, cpu_ms = comps.get("cpu"), fnum(r.get("duracion_bucle_s")), fnum(r.get("cpu_ms"))
        if cpu_mah is None:
            continue
        if loop_s:
            cpu_rates.append(cpu_mah / (loop_s / 60))
        if cpu_ms:
            cpu_per_s.append(cpu_mah / (cpu_ms / 1000))
    mah_per_cpu_s = statistics.median(cpu_per_s) if cpu_per_s else None
    if mah_per_cpu_s is None:
        validity = "no: batterystats no desglosa CPU para la app"
    elif with_cpu < with_dump:
        # Galaxy A36 (API 35): la CPU aparece en algunos clips y en otros no, con el mismo uso.
        validity = f"no: batterystats atribuyó CPU en {with_cpu} de {with_dump} clips"
    elif mah_per_cpu_s < MIN_MAH_PER_CPU_S:
        validity = f"no: power_profile con CPU ~0 ({mah_per_cpu_s:.4f} mAh por s de CPU)"
    else:
        validity = "si"
    valid = validity == "si"
    idle = dump_components(run, dev, "reposo")
    idle_s = fnum(dev.get("reposo_s"))
    idle_cpu_rate = idle["cpu"] / (idle_s / 60) if idle and idle.get("cpu") is not None and idle_s else None
    clip_cpu_rate = statistics.median(cpu_rates) if cpu_rates and valid else None
    infer_cpu_rate = to_full_occupancy(clip_cpu_rate, idle_cpu_rate) if valid else None

    # El contador de carga da la capacidad actual; si no es plausible (emuladores, equipos
    # que no lo exponen) se usa la declarada en el power_profile.
    counter_capacity = fnum(dev.get("bateria_capacidad_estimada_mah"))
    profile_capacity = power_profile_capacity(run)
    capacity = counter_capacity if counter_capacity and counter_capacity >= MIN_PLAUSIBLE_MAH else profile_capacity
    session_mah = infer_rate * SESSION_MIN if infer_rate is not None else None
    session_cpu_mah = infer_cpu_rate * SESSION_MIN if infer_cpu_rate is not None else None

    def f(v: float | None, digits: int = 2) -> str:
        return "" if v is None else f"{v:.{digits}f}"

    def pct(mah: float | None) -> str:
        return f(100 * mah / capacity if mah is not None and capacity else None, 2)

    return {
        "corrida": name,
        "dispositivo": f"{dev.get('fabricante', '')} {dev.get('modelo', '')}".strip(),
        "codename": dev.get("codename", ""),
        "soc": dev.get("soc", ""),
        "delegate": dev.get("delegate_efectivo", ""),
        "ram_mb": dev.get("ram_mb", ""),
        "cargador_inicio": dev.get("cargador_inicio", ""),
        "pss_antes_modelo_mb": dev.get("pss_antes_modelo_mb", ""),
        "pss_con_modelo_mb": dev.get("pss_con_modelo_mb", ""),
        "costo_modelo_pss_mb": f(delta("pss"), 1),
        "costo_modelo_native_mb": f(delta("native_heap"), 1),
        "costo_modelo_graficos_mb": f(delta("graficos"), 1),
        "pss_inferencia_mediana_mb": f(med("pss_fin_mb"), 1),
        "rss_max_mb": f(peak("rss_max_mb"), 1),
        "vmhwm_mb": f(peak("vmhwm_mb"), 1),
        "reposo_cpu_pct": dev.get("reposo_cpu_pct", ""),
        "uso_cpu_pct_mediana": f(med("uso_cpu_pct"), 1),
        "cpu_ms_por_frame_mediana": f(med("cpu_ms_por_frame"), 1),
        "ocupacion_pct_mediana": f(occupancy, 1),
        "consumo_cpu_valido": validity,
        "mah_por_s_cpu": f(mah_per_cpu_s, 4),
        "uid_fuera_de_cpu_pct": f(100 * statistics.median(non_cpu_shares) if non_cpu_shares else None, 1),
        "reposo_mah_por_min_cpu": f(idle_cpu_rate if valid else None, 4),
        "clips_mah_por_min_cpu_mediana": f(clip_cpu_rate, 4),
        "inferencia_mah_por_min_cpu": f(infer_cpu_rate, 4),
        f"mah_sesion_{SESSION_MIN}min_cpu": f(session_cpu_mah, 2),
        f"pct_bateria_sesion_{SESSION_MIN}min_cpu": pct(session_cpu_mah),
        "reposo_mah_por_min_total_uid": f(idle_rate, 4),
        "clips_mah_por_min_mediana_total_uid": f(clip_rate, 4),
        "inferencia_mah_por_min_total_uid": f(infer_rate, 4),
        f"mah_sesion_{SESSION_MIN}min_total_uid": f(session_mah, 2),
        f"pct_bateria_sesion_{SESSION_MIN}min_total_uid": pct(session_mah),
        "bateria_capacidad_contador_mah": dev.get("bateria_capacidad_estimada_mah", ""),
        "bateria_capacidad_power_profile_mah": f(profile_capacity, 0),
        "bateria_capacidad_usada_mah": f(capacity, 0),
        "temp_bateria_max_c": f(peak("temp_bateria_fin_c"), 1),
        "estado_termico_max": f(peak("estado_termico_fin"), 0),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--entrada", type=Path, action="append", help="carpetas donde buscar corridas")
    parser.add_argument("--salida", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    runs = find_runs(args.entrada or DEFAULT_INPUTS)
    if not runs:
        raise SystemExit("No se encontraron corridas (carpetas con dispositivo.csv + resumen.csv)")
    print(f"{len(runs)} corridas:")
    for name, path in sorted(runs.items()):
        print(f"  {name}  ({path})")

    print("\nEscribiendo:")
    all_summaries, all_precision, all_resources = [], [], []
    samples_by_key: dict[str, list[dict]] = defaultdict(list)
    reps_by_key: dict[str, list[dict]] = defaultdict(list)
    for name, run in sorted(runs.items()):
        dev = device_info(run)
        key = f"{dev.get('codename', 'desconocido')}_{dev.get('delegate_efectivo', '').lower()}"
        all_summaries += [{"corrida": name, **r} for r in read_csv(run / "resumen.csv")]
        samples_by_key[key] += log_style_samples(run, dev)
        if (run / "reps.csv").exists():
            reps_by_key[key] += read_csv(run / "reps.csv")
        all_precision += [{"corrida": name, **r} for r in precision_rows(run, dev)]
        resources = energy_memory_row(name, run, dev)
        if resources:
            all_resources.append(resources)

    out = args.salida
    write_csv(out / "resumen_todos.csv", all_summaries)
    for key, samples in samples_by_key.items():
        if samples:
            write_csv(out / f"metricas_{key}.csv", samples)
            write_csv(out / f"metricas_{key}_resumen.csv", [log_style_summary(samples)])
    rep_cols = ["timestampMs", "exerciseId", "repIndex", "normalizedDistance",
                "dtwSimilarity", "shapeScore", "finalScore"]
    for key, reps in reps_by_key.items():
        write_csv(out / f"rep_metrics_{key}.csv", reps, rep_cols)
    write_csv(out / "precision.csv", all_precision)
    if all_resources:
        write_csv(out / "energia_memoria.csv", all_resources)
    else:
        print("  (ninguna corrida trae mediciones de batería/memoria: no se escribe energia_memoria.csv)")


if __name__ == "__main__":
    main()
