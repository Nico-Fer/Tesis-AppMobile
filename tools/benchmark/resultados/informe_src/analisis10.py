"""Tablas y figuras del informe a partir de las corridas de Test Lab (scratch)."""
import csv
import json
import statistics
import sys
from collections import defaultdict
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402

RUNS_ROOT = Path(sys.argv[1])      # app/build/outputs
CONSOLIDADO = Path(sys.argv[2])    # salida de agregar_resultados.py
OUT = Path(sys.argv[3])            # carpeta del informe

# Rotulado para el informe (codename de Test Lab -> nombre comercial, SoC, GPU, rol).
EQUIPOS = {
    # codename: (equipo, SoC, GPU, rol, lote)
    "caprip": ("Motorola moto g30", "Snapdragon 662", "Adreno 610", "Piso de la gama media", 1),
    "SO-41A": ("Sony Xperia 10 II", "Snapdragon 665", "Adreno 610", "Segundo piso, época del Redmi Note 9 Pro", 2),
    "penang": ("Motorola moto g53 5G", "Snapdragon 480+", "Adreno 619", "Misma GPU que el g34, CPU más débil", 2),
    "fogos": ("Motorola moto g34 5G", "Snapdragon 695", "Adreno 619", "GPU ≈ Adreno 618 de los medidos", 1),
    "OP5958L1": ("OnePlus Nord CE3 Lite 5G", "Snapdragon 695", "Adreno 619", "Mismo SoC que el g34, otro fabricante", 1),
    "a52xq": ("Samsung Galaxy A52 5G", "Snapdragon 750G", "Adreno 619", "Hermano directo del Galaxy A72", 2),
    "redfin": ("Google Pixel 5", "Snapdragon 765G", "Adreno 620", "Snapdragon 7 de 2020", 1),
    "a52sxq": ("Samsung Galaxy A52s 5G", "Snapdragon 778G", "Adreno 642L", "Techo de la gama, familia Galaxy A", 1),
    "dubai": ("Motorola edge 30", "Snapdragon 778G+", "Adreno 642L", "Segundo techo, otro fabricante", 2),
    "a36xq": ("Samsung Galaxy A36 5G", "Snapdragon 6 Gen 3", "Adreno 710", "Gama media actual (2025)", 2),
}
ORDEN = list(EQUIPOS)  # por clase de GPU, de menor a mayor
EJERCICIOS = {
    "app_0001_elevacion_lateral": "Elevación lateral de hombros",
    "app_0002_sentadilla": "Sentadilla parcial",
    "app_0005_talones": "Elevación de talones",
    "app_0006_ext_rodilla": "Extensión de rodillas",
    "app_0006_ext_rodilla_heldout": "Extensión de rodillas (toma held-out)",
}


def read(path):
    with open(path, newline="", encoding="utf-8") as fh:
        return list(csv.DictReader(fh))


def pct(values, p):
    s = sorted(values)
    r = p / 100 * (len(s) - 1)
    lo = int(r)
    hi = min(lo + 1, len(s) - 1)
    return s[lo] + (s[hi] - s[lo]) * (r - lo)


def f(x):
    return float(x) if x not in (None, "") else None


runs = {}
for summary in RUNS_ROOT.rglob("resumen.csv"):
    run = summary.parent
    if not (run / "dispositivo.csv").exists():
        continue
    dev = {r["clave"]: r["valor"] for r in read(run / "dispositivo.csv")}
    if dev.get("codename") not in EQUIPOS:
        continue  # emulador u otras corridas locales
    runs.setdefault(dev["codename"], (run, dev))

missing = [c for c in ORDEN if c not in runs]
print("corridas:", {k: str(v[0].name) for k, v in runs.items()}, "faltan:", missing)
order = [c for c in ORDEN if c in runs]

precision = defaultdict(list)
if (CONSOLIDADO / "precision.csv").exists():
    for r in read(CONSOLIDADO / "precision.csv"):
        precision[r["codename"]].append(r)

devices, clips_rows, reps_rows, stage = [], [], [], {}
for code in order:
    run, dev = runs[code]
    frames = read(run / "frames.csv")
    summary = read(run / "resumen.csv")
    steady = [fr for fr in frames if fr["warmup"] == "0"]
    inf = [float(fr["inferencia_ms"]) for fr in steady]
    pre = [float(fr["preproceso_ms"]) for fr in steady]
    post = [float(fr["postproceso_ms"]) for fr in steady]
    tot = [float(fr["total_ms"]) for fr in steady]
    dur = sum(float(s["duracion_s"]) for s in summary)
    processed = sum(int(s["frames_procesados"]) for s in summary)
    detected = sum(int(fr["detectado"]) for fr in frames)
    prec = precision.get(code, [])
    desv = [f(p["desvio_vs_escritorio_p50"]) for p in prec if f(p["desvio_vs_escritorio_p50"]) is not None]
    pck_dev = [f(p.get("pck10_dispositivo")) for p in prec if f(p.get("pck10_dispositivo")) is not None]
    pck_desk = [f(p.get("pck10_escritorio")) for p in prec if f(p.get("pck10_escritorio")) is not None]
    temps = [f(s["temp_bateria_inicio_c"]) for s in summary] + [f(s["temp_bateria_fin_c"]) for s in summary]
    temps = [t for t in temps if t is not None]
    nombre, soc, gpu, rol, lote = EQUIPOS[code]
    row = {
        "codename": code, "equipo": nombre, "soc": soc, "gpu": gpu, "rol": rol, "lote": lote,
        "gpu_reportada": dev.get("gpu", ""), "soc_reportado": dev.get("soc", ""),
        "android": dev.get("android", ""), "api": dev.get("api", ""), "ram_mb": dev.get("ram_mb", ""),
        "delegate": dev.get("delegate_efectivo", ""), "init_ms": f(dev.get("init_modelo_ms")),
        "frames": processed, "duracion_s": dur,
        "fps_efectivo": processed / dur,
        "inf_p50": pct(inf, 50), "inf_p90": pct(inf, 90), "inf_media": statistics.mean(inf),
        "inf_min": min(inf), "inf_max": max(inf),
        "pre_p50": pct(pre, 50), "post_p50": pct(post, 50),
        "tot_p50": pct(tot, 50), "tot_p90": pct(tot, 90),
        "fps_inferencia": 1000 / pct(inf, 50), "fps_pipeline": 1000 / pct(tot, 50),
        "deteccion": detected / len(frames),
        "desvio_p50": statistics.median(desv) if desv else None,
        "desvio_max": max(desv) if desv else None,
        "pck10_dev": statistics.mean(pck_dev) if pck_dev else None,
        "pck10_desk": statistics.mean(pck_desk) if pck_desk else None,
        "temp_min": min(temps) if temps else None, "temp_max": max(temps) if temps else None,
        "termico_max": max(int(s["estado_termico_fin"]) for s in summary),
        "fecha": dev.get("fecha", ""),
    }
    # Deriva térmica: primer vs último tercio de los frames del run (orden de ejecución).
    third = max(len(inf) // 3, 1)
    row["inf_p50_inicio"] = pct(inf[:third], 50)
    row["inf_p50_final"] = pct(inf[-third:], 50)
    devices.append(row)
    stage[code] = (row["pre_p50"], row["inf_p50"], row["post_p50"])
    for s in summary:
        clips_rows.append({"codename": code, **s})
        if s["clip"] in EJERCICIOS:
            reps_rows.append({
                "codename": code, "clip": s["clip"], "ejercicio": EJERCICIOS[s["clip"]],
                "esperadas": s["reps_esperadas"], "detectadas": s["reps_detectadas"],
                "puntuadas": s["reps_puntuadas"], "score": f(s["score_medio"]),
                "fps": f(s["fps_efectivo"]),
            })

OUT.mkdir(parents=True, exist_ok=True)
json.dump({"devices": devices, "clips": clips_rows, "reps": reps_rows,
           "precision": {k: v for k, v in precision.items()}},
          open(OUT / "datos_informe10.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)

# ------------------------------------------------------------------ figuras (impresión)
SURFACE, INK, INK2, GRID = "#fcfcfb", "#0b0b0b", "#52514e", "#e4e3df"
SERIES = ["#2a78d6", "#eb6834", "#1baf7a"]  # slots 1-3 de la paleta de referencia
plt.rcParams.update({
    "font.family": "DejaVu Sans", "font.size": 9, "axes.edgecolor": GRID,
    "axes.labelcolor": INK2, "xtick.color": INK2, "ytick.color": INK2,
    "axes.titlecolor": INK, "figure.facecolor": SURFACE, "axes.facecolor": SURFACE,
})
delegate_de = {d["codename"]: d["delegate"] for d in devices}
labels = [f"{EQUIPOS[c][0]}\n{EQUIPOS[c][1]} · "
          + (EQUIPOS[c][2] if delegate_de[c] == "GPU" else "en CPU") for c in order]

# Figura 1: tiempo por frame (p50) por etapa, barras horizontales apiladas.
fig, ax = plt.subplots(figsize=(6.5, 5.4), dpi=200)
y = range(len(order))[::-1]
left = [0.0] * len(order)
names = ["Preprocesado", "Inferencia", "Postprocesado"]
for k, name in enumerate(names):
    vals = [stage[c][k] for c in order]
    ax.barh(list(y), vals, left=left, color=SERIES[k], height=0.55, label=name,
            edgecolor=SURFACE, linewidth=1.5)
    left = [l + v for l, v in zip(left, vals)]
for yi, c, total in zip(y, order, left):
    ax.text(total + 3, yi, f"{total:.0f} ms", va="center", color=INK, fontsize=8)
ax.set_yticks(list(y), labels, fontsize=8)
ax.tick_params(axis="y", length=0)
ax.set_xlabel("Tiempo por frame, mediana (ms)")
ax.set_xlim(0, max(left) * 1.15)
ax.grid(axis="x", color=GRID, linewidth=0.6)
ax.set_axisbelow(True)
for s in ("top", "right", "left"):
    ax.spines[s].set_visible(False)
ax.legend(ncol=3, frameon=False, loc="lower center", bbox_to_anchor=(0.45, 1.0), fontsize=8)
fig.tight_layout()
fig.savefig(OUT / "fig1_10_tiempo_por_etapa.png")
plt.close(fig)

# Figura 2: fps efectivos por dispositivo (una serie, un color).
fig, ax = plt.subplots(figsize=(6.5, 4.8), dpi=200)
vals = [next(d["fps_efectivo"] for d in devices if d["codename"] == c) for c in order]
ax.barh(list(y), vals, color=SERIES[0], height=0.55)
for yi, v in zip(y, vals):
    ax.text(v + 0.15, yi, f"{v:.1f} fps".replace(".", ","), va="center", color=INK, fontsize=8)
ax.set_yticks(list(y), labels, fontsize=8)
ax.tick_params(axis="y", length=0)
ax.set_xlabel("Cuadros analizados por segundo (fps efectivos, video a 25–30 fps)")
ax.set_xlim(0, max(vals) * 1.18)
ax.grid(axis="x", color=GRID, linewidth=0.6)
ax.set_axisbelow(True)
for s in ("top", "right", "left"):
    ax.spines[s].set_visible(False)
fig.tight_layout()
fig.savefig(OUT / "fig2_10_fps_efectivos.png")
plt.close(fig)

for d in devices:
    print(f"{d['codename']:9s} {d['delegate']:12s} inf p50 {d['inf_p50']:6.1f} p90 {d['inf_p90']:6.1f} "
          f"pre {d['pre_p50']:5.1f} post {d['post_p50']:4.1f} tot {d['tot_p50']:6.1f} fps_eff {d['fps_efectivo']:5.2f} "
          f"det {d['deteccion']:.3f} desv {d['desvio_p50']} pck {d['pck10_dev']}/{d['pck10_desk']} "
          f"temp {d['temp_min']}-{d['temp_max']} term {d['termico_max']} init {d['init_ms']} "
          f"drift {d['inf_p50_inicio']:.1f}->{d['inf_p50_final']:.1f} gpu={d['gpu_reportada']}")
