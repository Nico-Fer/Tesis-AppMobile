"""Genera los clips del benchmark de Firebase Test Lab a partir del dataset de modelosCV.

Salidas:
  app/src/androidTest/assets/benchmark/<id>.mp4   clips con la geometría del analyzer
  app/src/androidTest/assets/benchmark/clips.json catálogo que lee PoseBenchmarkTest
  tools/benchmark/linea_base/<id>.escritorio.csv  poses del MISMO .tflite en escritorio
  tools/benchmark/linea_base/<id>.gt.csv          GT de UCO llevado al cuadro recortado

Cada clip se lleva a lo que entrega CameraX en producción: frame YUV 640x480 apaisado
(orientación de sensor) + rotationDegrees=90 -> 480x640 vertical, con la persona
llenando el encuadre. Los clips no tienen B-frames, así que el orden de decodificación
del test coincide con el de presentación.

Correr con el venv-torch de modelosCV (cv2 + tensorflow) y un ffmpeg con libx264
(PATH, --ffmpeg, o `pip install imageio-ffmpeg`):

  ..\\modelosCV\\venv-torch\\Scripts\\python.exe tools\\benchmark\\preparar_clips.py ^
      --modelos-cv ..\\modelosCV
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import shutil
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path

import cv2
import numpy as np

APP_ROOT = Path(__file__).resolve().parents[2]
ASSETS_DIR = APP_ROOT / "app" / "src" / "androidTest" / "assets" / "benchmark"
BASELINE_DIR = Path(__file__).resolve().parent / "linea_base"
DEFAULT_MODEL = APP_ROOT / "app" / "src" / "main" / "assets" / "yolov8n-pose_int8.tflite"

# Geometría del analyzer de CameraX en la app (CameraPreview.kt: 4:3, default 640x480).
UPRIGHT_W, UPRIGHT_H = 480, 640
ROTATION_DEGREES = 90
INPUT_SIZE = 640
DET_CONF = 0.25  # PoseModelSpec.DEFAULT_CONFIDENCE_THRESHOLD
KPT_CONF = 0.5  # mismo umbral de keypoint confiable que el comparador
PROBE_STRIDE = 5  # cuadros entre muestras al decidir el recorte

COCO17 = [
    "nose", "left_eye", "right_eye", "left_ear", "right_ear", "left_shoulder",
    "right_shoulder", "left_elbow", "right_elbow", "left_wrist", "right_wrist", "left_hip",
    "right_hip", "left_knee", "right_knee", "left_ankle", "right_ankle",
]
# Columnas de camN_p2d.txt por ejercicio UCO (orden de dataset_2d.json, ver uco_to_yolo.py).
UCO_JOINTS = {
    **dict.fromkeys(["01", "02", "03", "04"], ["left_hip", "left_knee", "left_ankle"]),
    **dict.fromkeys(["05", "06", "07", "08"], ["right_hip", "right_knee", "right_ankle"]),
    **dict.fromkeys(["09", "12"], ["left_shoulder", "left_elbow", "left_wrist"]),
    **dict.fromkeys(["13", "14", "15", "16"], ["right_shoulder", "right_elbow", "right_wrist"]),
}


@dataclass
class ClipSpec:
    id: str
    grupo: str  # "app" | "uco"
    fuente: str  # relativo a la raíz de modelosCV
    notas: str
    exercise_id: str | None = None
    inicio_s: float = 0.0
    fin_s: float | None = None
    reps_esperadas: int | None = None
    uco: tuple[str, str, int] | None = None  # (sujeto, ejercicio, cámara)
    extra: dict = field(default_factory=dict)


# ---------------------------------------------------------------------------------------
# Catálogo. Criterios en docs/benchmark-firebase-test-lab.md.
#
# "app": las grabaciones propias de las que salen las referencias DTW empaquetadas —
#   celular, vista frontal, vertical: el dominio exacto de producción. Rinden fps reales
#   y métricas por rep comparables con los rep-metrics.csv de las sesiones. La toma
#   _CpnManos NO generó ninguna referencia: es la única ejecución held-out del catálogo.
# "uco": sujetos del split de validación del fold 0 (4, 11, 14, 19, 20, 23), así ningún
#   modelo student entrenado en ese fold los vio. Traen GT de 3 articulaciones por
#   cuadro, para medir precisión en el dispositivo. Cámara elegida por el survey de error
#   contra GT del mismo .tflite; supino (ej. 03/04) excluido: el modelo falla ahí incluso
#   en escritorio (error mediano 0.18-0.35 de la altura de la caja) y la app no lo usa.
# ---------------------------------------------------------------------------------------
CLIPS: list[ClipSpec] = [
    ClipSpec(
        id="app_0001_elevacion_lateral",
        grupo="app",
        fuente="data/references/videos/C1000000-0000-0000-0000-000000000001.mp4",
        exercise_id="C1000000-0000-0000-0000-000000000001",
        reps_esperadas=2,
        notas="Sentado, frontal, bilateral. Fuente de la referencia (control positivo).",
    ),
    ClipSpec(
        id="app_0002_sentadilla",
        grupo="app",
        fuente="data/references/videos/c1000000-0000-0000-0000-000000000002.MOV",
        exercise_id="C1000000-0000-0000-0000-000000000002",
        inicio_s=1.9,  # mismo trimStartS que la referencia: el clip arranca en sentadilla
        reps_esperadas=4,
        notas="De pie, frontal, bilateral. Fuente de la referencia (control positivo).",
    ),
    ClipSpec(
        id="app_0005_talones",
        grupo="app",
        fuente="data/references/videos/c1000000-0000-0000-0000-000000000005.MOV",
        exercise_id="C1000000-0000-0000-0000-000000000005",
        reps_esperadas=7,
        notas="De pie, frontal. Señal = artefacto del detector (ver README de referencias).",
    ),
    ClipSpec(
        id="app_0006_ext_rodilla",
        grupo="app",
        fuente="data/references/videos/c1000000-0000-0000-0000-000000000006_SinManos.MOV",
        exercise_id="C1000000-0000-0000-0000-000000000006",
        reps_esperadas=3,
        notas="Sentado, frontal, pierna izquierda. Fuente de la referencia.",
    ),
    ClipSpec(
        id="app_0006_ext_rodilla_heldout",
        grupo="app",
        fuente="data/references/videos/c1000000-0000-0000-0000-000000000006_CpnManos.MOV",
        exercise_id="C1000000-0000-0000-0000-000000000006",
        inicio_s=21.0,  # ~21 s de preámbulo en otra postura (README de referencias)
        notas="Toma descartada como referencia (manos sobre la rodilla): ejecución held-out.",
    ),
    # Los clips UCO se completan tras el survey de cámaras (ver más abajo).
]

UCO_MAX_S = 24.0


def uco_clip(subject: str, exercise: str, cam: int, notas: str) -> ClipSpec:
    return ClipSpec(
        id=f"uco_s{subject}_e{exercise}_cam{cam}",
        grupo="uco",
        fuente=f"data/raw/uco/clips_mp4/{subject}/{exercise}/cam{cam}.mp4",
        fin_s=UCO_MAX_S,
        uco=(subject, exercise, cam),
        notas=notas,
    )


# Survey de escritorio (16 cuadros por cámara, error mediano contra GT / altura de caja):
# s4/e01/cam3 0.031, s14/e05/cam0 0.075, s4/e13/cam2 0.028, s11/e09/cam4 0.038.
CLIPS += [
    uco_clip("4", "01", 3, "Sentado, extensión de rodilla izquierda (≈ ejercicio 0006)."),
    uco_clip("14", "05", 0, "Sentado, extensión de rodilla derecha; otro sujeto y lado."),
    uco_clip("4", "13", 2, "Sentado, elevación de brazo derecho (miembro superior)."),
    uco_clip("11", "09", 4, "Sentado, elevación de brazo izquierdo; tercer sujeto."),
]


# ---------------------------------------------------------------------------------------
# Modelo: letterbox + decode iguales a los de la app (PosePreprocessor/PosePostprocessor).
# ---------------------------------------------------------------------------------------
class AppModel:
    def __init__(self, path: Path, threads: int) -> None:
        import tensorflow as tf  # diferido: sólo hace falta en venv-torch

        self.interpreter = tf.lite.Interpreter(model_path=str(path), num_threads=threads)
        self.interpreter.allocate_tensors()
        self.inp = self.interpreter.get_input_details()[0]
        self.out = self.interpreter.get_output_details()[0]

    def __call__(self, upright_bgr: np.ndarray) -> dict | None:
        """Mejor persona en coordenadas normalizadas al cuadro vertical (como PoseFrame)."""
        h, w = upright_bgr.shape[:2]
        r = min(INPUT_SIZE / w, INPUT_SIZE / h)
        nw, nh = round(w * r), round(h * r)
        canvas = np.full((INPUT_SIZE, INPUT_SIZE, 3), 114, np.uint8)
        px, py = (INPUT_SIZE - nw) / 2, (INPUT_SIZE - nh) / 2
        ix, iy = round(px), round(py)
        canvas[iy : iy + nh, ix : ix + nw] = cv2.resize(upright_bgr, (nw, nh))
        x = cv2.cvtColor(canvas, cv2.COLOR_BGR2RGB).astype(np.float32)[None] / 255.0
        self.interpreter.set_tensor(self.inp["index"], x)
        self.interpreter.invoke()
        y = np.squeeze(self.interpreter.get_tensor(self.out["index"]), 0)  # (56, 8400)
        best = int(np.argmax(y[4]))
        score = float(y[4, best])
        if score < DET_CONF:
            return None
        to_x = lambda v: ((v * INPUT_SIZE - px) / r) / w  # noqa: E731
        to_y = lambda v: ((v * INPUT_SIZE - py) / r) / h  # noqa: E731
        cx, cy, bw, bh = y[0:4, best]
        return {
            "score": score,
            "box": (to_x(cx - bw / 2), to_y(cy - bh / 2), to_x(cx + bw / 2), to_y(cy + bh / 2)),
            "kx": to_x(y[5::3, best]),
            "ky": to_y(y[6::3, best]),
            "kc": y[7::3, best],
        }


# ---------------------------------------------------------------------------------------
# Video
# ---------------------------------------------------------------------------------------
def open_video(path: Path) -> tuple[cv2.VideoCapture, int, float, int]:
    cap = cv2.VideoCapture(str(path))
    if not cap.isOpened():
        sys.exit(f"No se pudo abrir {path}")
    cap.set(cv2.CAP_PROP_ORIENTATION_AUTO, 0)
    rotation = int(cap.get(cv2.CAP_PROP_ORIENTATION_META)) % 360
    return cap, rotation, cap.get(cv2.CAP_PROP_FPS), int(cap.get(cv2.CAP_PROP_FRAME_COUNT))


def to_upright(frame: np.ndarray, rotation: int) -> np.ndarray:
    """Rota el cuadro almacenado `rotation` grados horarios (semántica del contenedor)."""
    codes = {90: cv2.ROTATE_90_CLOCKWISE, 180: cv2.ROTATE_180, 270: cv2.ROTATE_90_COUNTERCLOCKWISE}
    return cv2.rotate(frame, codes[rotation]) if rotation else frame


def check_rotation_convention(path: Path) -> None:
    """La rotación propia tiene que coincidir con la que aplica OpenCV desde la metadata."""
    cap, rotation, _, _ = open_video(path)
    ok, stored = cap.read()
    cap.release()
    auto = cv2.VideoCapture(str(path))
    auto.set(cv2.CAP_PROP_ORIENTATION_AUTO, 1)
    ok2, expected = auto.read()
    auto.release()
    mine = to_upright(stored, rotation)
    if not (ok and ok2) or mine.shape != expected.shape or np.abs(
        mine.astype(int) - expected.astype(int)
    ).mean() > 2:
        sys.exit(f"Convención de rotación inconsistente en {path} (meta={rotation})")


def frame_range(spec: ClipSpec, fps: float, count: int) -> range:
    start = round(spec.inicio_s * fps)
    end = count if spec.fin_s is None else min(count, round(spec.fin_s * fps))
    return range(start, end)


def read_upright(cap: cv2.VideoCapture, rotation: int, frames: range):
    cap.set(cv2.CAP_PROP_POS_FRAMES, frames.start)
    for idx in frames:
        ok, frame = cap.read()
        if not ok:
            return
        yield idx, to_upright(frame, rotation)


# ---------------------------------------------------------------------------------------
# Encuadre: ventana 3:4 vertical que contiene a la persona en todo el clip
# ---------------------------------------------------------------------------------------
def crop_window(spec: ClipSpec, model: AppModel, path: Path) -> tuple[int, int, int, int]:
    cap, rotation, fps, count = open_video(path)
    frames = frame_range(spec, fps, count)
    xs, ys = [], []
    for idx, upright in read_upright(cap, rotation, frames):
        if (idx - frames.start) % PROBE_STRIDE:
            continue
        det = model(upright)
        if det is None:
            continue
        h, w = upright.shape[:2]
        x0, y0, x1, y1 = det["box"]
        good = det["kc"] > KPT_CONF
        # Caja de la persona ∪ keypoints confiables (la caja sola recorta manos en alto).
        xs += [x0 * w, x1 * w, *(det["kx"][good] * w)]
        ys += [y0 * h, y1 * h, *(det["ky"][good] * h)]
    cap.release()
    if not xs:
        sys.exit(f"{spec.id}: el modelo no detectó a nadie al decidir el encuadre")
    h, w = upright.shape[:2]
    bx0, bx1 = np.percentile(xs, 1), np.percentile(xs, 99)
    by0, by1 = np.percentile(ys, 1), np.percentile(ys, 99)
    margin = 0.08 * max(bx1 - bx0, by1 - by0)
    need_w, need_h = bx1 - bx0 + 2 * margin, by1 - by0 + 2 * margin
    # Ventana 3:4 mínima que contiene a la persona. Si no entra a lo alto del cuadro se
    # usa el alto completo; si a lo ancho se pasa del cuadro, se rellena con bandas
    # negras laterales (extract_window) en vez de cortar cabeza o pies: en producción la
    # pantalla de encuadre exige el cuerpo entero.
    win_h = min(max(need_h, need_w * 4 / 3), h)
    win_w = win_h * 3 / 4
    if need_h > h or need_w > w:
        print(f"  [aviso] {spec.id}: la persona no entra entera en el cuadro; se corta")
    cx, cy = (bx0 + bx1) / 2, (by0 + by1) / 2
    y = int(np.clip(cy - win_h / 2, 0, h - win_h))
    if win_w <= w:
        x = int(np.clip(cx - win_w / 2, 0, w - win_w))
    else:
        x = int(round((w - win_w) / 2))  # negativo: bandas simétricas
        print(f"  [aviso] {spec.id}: bandas laterales de {(win_w - w) / 2:.0f} px")
    return x, y, int(win_w), int(win_h)


def extract_window(upright: np.ndarray, window) -> np.ndarray:
    """Recorte de la ventana; lo que cae fuera del cuadro queda negro."""
    x, y, ww, wh = window
    h, w = upright.shape[:2]
    out = np.zeros((wh, ww, 3), np.uint8)
    sx0, sy0 = max(x, 0), max(y, 0)
    sx1, sy1 = min(x + ww, w), min(y + wh, h)
    out[sy0 - y : sy1 - y, sx0 - x : sx1 - x] = upright[sy0:sy1, sx0:sx1]
    return out


# ---------------------------------------------------------------------------------------
# Codificación
# ---------------------------------------------------------------------------------------
def find_ffmpeg(explicit: str | None) -> str:
    if explicit:
        return explicit
    on_path = shutil.which("ffmpeg")
    if on_path:
        return on_path
    try:
        import imageio_ffmpeg

        return imageio_ffmpeg.get_ffmpeg_exe()
    except ImportError:
        sys.exit("Falta ffmpeg: ponelo en el PATH, pasá --ffmpeg o `pip install imageio-ffmpeg`")


def encode(spec: ClipSpec, path: Path, window, out: Path, ffmpeg: str) -> tuple[float, int]:
    cap, rotation, fps, count = open_video(path)
    frames = frame_range(spec, fps, count)
    x, y, ww, wh = window
    raw = out.with_suffix(".tmp.mp4")
    # Baseline sin B-frames, un keyframe por segundo; el remux agrega la rotación (-90 en
    # display matrix = 90° horarios, la misma metadata que traen las grabaciones de celular).
    enc = subprocess.Popen(
        [ffmpeg, "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "bgr24",
         "-s", f"{UPRIGHT_H}x{UPRIGHT_W}", "-r", f"{fps}", "-i", "-",
         "-c:v", "libx264", "-preset", "slow", "-crf", "18", "-profile:v", "baseline",
         "-bf", "0", "-g", f"{round(fps)}", "-pix_fmt", "yuv420p", "-threads", "2", str(raw)],
        stdin=subprocess.PIPE,
    )
    written = 0
    for _, upright in read_upright(cap, rotation, frames):
        crop = extract_window(upright, window)
        crop = cv2.resize(crop, (UPRIGHT_W, UPRIGHT_H), interpolation=cv2.INTER_AREA
                          if ww > UPRIGHT_W else cv2.INTER_LINEAR)
        # Orientación de sensor: girado 90° antihorario, así rotationDegrees=90 lo endereza.
        enc.stdin.write(cv2.rotate(crop, cv2.ROTATE_90_COUNTERCLOCKWISE).tobytes())
        written += 1
    cap.release()
    enc.stdin.close()
    if enc.wait() != 0:
        sys.exit(f"ffmpeg falló codificando {spec.id}")
    subprocess.run(
        [ffmpeg, "-v", "error", "-y", "-display_rotation:v:0", "-90", "-i", str(raw),
         "-c", "copy", "-movflags", "+faststart", str(out)],
        check=True,
    )
    raw.unlink()
    return fps, written


def desktop_baseline(spec: ClipSpec, out_mp4: Path, model: AppModel) -> float:
    """Poses del .tflite sobre el clip YA codificado (mismos píxeles que el dispositivo)."""
    cap = cv2.VideoCapture(str(out_mp4))
    cap.set(cv2.CAP_PROP_ORIENTATION_AUTO, 0)
    rows, detected, idx = [], 0, 0
    while True:
        ok, stored = cap.read()
        if not ok:
            break
        det = model(to_upright(stored, ROTATION_DEGREES))
        row = {"frame": idx, "detectado": int(det is not None)}
        if det is not None:
            detected += 1
            row["score"] = f"{det['score']:.4f}"
            for k, name in enumerate(COCO17):
                row[f"{name}_x"] = f"{det['kx'][k]:.5f}"
                row[f"{name}_y"] = f"{det['ky'][k]:.5f}"
                row[f"{name}_c"] = f"{det['kc'][k]:.4f}"
        rows.append(row)
        idx += 1
    cap.release()
    cols = ["frame", "detectado", "score"] + [f"{n}_{a}" for n in COCO17 for a in "xyc"]
    with (BASELINE_DIR / f"{spec.id}.escritorio.csv").open("w", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=cols)
        w.writeheader()
        w.writerows(rows)
    return detected / max(idx, 1)


def write_gt(spec: ClipSpec, modelos: Path, window, fps: float, count: int) -> None:
    """GT de UCO (px del cuadro original) → normalizado al cuadro recortado del clip."""
    subject, exercise, cam = spec.uco
    names = UCO_JOINTS[exercise]
    lines = (modelos / "data/raw/uco/clips_mp4" / subject / exercise / f"cam{cam}_p2d.txt")
    gt = [l.split() for l in lines.read_text().splitlines() if l.strip()]
    x, y, ww, wh = window
    frames = frame_range(spec, fps, count)
    with (BASELINE_DIR / f"{spec.id}.gt.csv").open("w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["frame", "keypoint", "x", "y"])
        for out_idx, src_idx in enumerate(frames):
            if src_idx >= len(gt) or len(gt[src_idx]) != 2 * len(names):
                continue
            for j, name in enumerate(names):
                gx, gy = float(gt[src_idx][2 * j]), float(gt[src_idx][2 * j + 1])
                if gx <= 0 or gy <= 0:  # fuera de cuadro en UCO
                    continue
                w.writerow([out_idx, name, f"{(gx - x) / ww:.5f}", f"{(gy - y) / wh:.5f}"])


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--modelos-cv", type=Path, required=True, help="raíz del repo modelosCV")
    parser.add_argument("--modelo", type=Path, default=DEFAULT_MODEL, help="mismo .tflite que la app")
    parser.add_argument("--ffmpeg", help="ejecutable de ffmpeg con libx264")
    parser.add_argument("--solo", help="ids separados por coma (regenera sólo esos)")
    # Pocos hilos por defecto: no pelear CPU con un entrenamiento que esté corriendo.
    parser.add_argument("--hilos", type=int, default=min(4, os.cpu_count() or 1))
    args = parser.parse_args()

    ffmpeg = find_ffmpeg(args.ffmpeg)
    ASSETS_DIR.mkdir(parents=True, exist_ok=True)
    BASELINE_DIR.mkdir(parents=True, exist_ok=True)
    model = AppModel(args.modelo, args.hilos)
    model_md5 = hashlib.md5(args.modelo.read_bytes()).hexdigest()

    manifest_path = ASSETS_DIR / "clips.json"
    previous = {}
    if manifest_path.exists():
        previous = {c["id"]: c for c in json.loads(manifest_path.read_text("utf-8"))["clips"]}
    wanted = set(args.solo.split(",")) if args.solo else None

    entries = []
    for spec in CLIPS:
        if wanted is not None and spec.id not in wanted:
            if spec.id in previous:
                entries.append(previous[spec.id])
            continue
        src = args.modelos_cv / spec.fuente
        print(f"== {spec.id} <- {spec.fuente}")
        check_rotation_convention(src)
        window = crop_window(spec, model, src)
        out_mp4 = ASSETS_DIR / f"{spec.id}.mp4"
        fps, written = encode(spec, src, window, out_mp4, ffmpeg)
        detection = desktop_baseline(spec, out_mp4, model)
        if spec.uco:
            _, _, _, count = open_video(src)
            write_gt(spec, args.modelos_cv, window, fps, count)
        print(f"   {written} cuadros @ {fps:.2f} fps, ventana {window}, detección {detection:.3f}, "
              f"{out_mp4.stat().st_size / 1e6:.1f} MB")
        entries.append({
            "id": spec.id,
            "archivo": out_mp4.name,
            "grupo": spec.grupo,
            "exerciseId": spec.exercise_id,
            "rotationDegrees": ROTATION_DEGREES,
            "fps": round(fps, 3),
            "frames": written,
            "duracionS": round(written / fps, 2),
            "repsEsperadas": spec.reps_esperadas,
            "deteccionEscritorio": round(detection, 4),
            "fuente": spec.fuente,
            "recorte": {"inicioS": spec.inicio_s, "finS": spec.fin_s,
                        "ventanaVerticalPx": list(window)},
            "notas": spec.notas,
        })

    manifest = {
        "version": 1,
        "modeloEscritorio": args.modelo.name,
        "md5Modelo": model_md5,
        "geometria": f"{UPRIGHT_H}x{UPRIGHT_W} YUV + rotationDegrees={ROTATION_DEGREES}",
        "clips": entries,
    }
    manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", "utf-8")
    total = sum(e["duracionS"] for e in entries)
    print(f"\n{len(entries)} clips, {total:.0f} s de video -> {manifest_path}")


if __name__ == "__main__":
    main()
