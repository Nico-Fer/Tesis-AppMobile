"""
Genera los drawables de marca de DOMI.IA a partir de los PNG de Mocks/.

Los logos de origen vienen con fondo blanco horneado (RGB sin alpha). Este script
los convierte a PNG con transparencia real, recorta el margen sobrante y emite:

  app/src/main/res/drawable-nodpi/domi_logo.png          lockup completo (isotipo + wordmark)
  app/src/main/res/drawable-nodpi/domi_wordmark.png      solo el wordmark "DOMI.IA"
  app/src/main/res/drawable-night-nodpi/domi_wordmark.png  wordmark recoloreado para tema oscuro
  app/src/main/res/mipmap-*/ic_launcher_foreground.png   isotipo para el adaptive icon
  app/src/main/res/mipmap-*/ic_launcher.webp             icono legacy cuadrado
  app/src/main/res/mipmap-*/ic_launcher_round.webp       icono legacy redondo

Uso:  python tools/generate_brand_assets.py
"""

from __future__ import annotations

import colorsys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"

SRC_LOCKUP = ROOT / "Mocks" / "domi.ia_logo.png" / "screen.png"
SRC_WORDMARK = ROOT / "Mocks" / "domi.ia_frase.png" / "screen.png"

# Los PNG de origen son generados y su "blanco" no es exactamente 255.
WHITE_THRESHOLD = 248

# Token PrimaryDark de presentation/ui/theme/Color.kt — el navy #0044A1 es
# ilegible sobre SurfaceDark #11141A.
PRIMARY_DARK = (0xAA, 0xC7, 0xFF)

# Densidades Android: nombre -> multiplicador sobre mdpi.
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}

# Degradé del disco del launcher: los dos colores de la marca, teal arriba-izquierda
# hacia el navy abajo-derecha. Debe coincidir con drawable/ic_launcher_background.xml.
LOGO_TEAL = (0x16, 0xB8, 0xA8)
LOGO_NAVY = (0x00, 0x44, 0xA1)

# Diámetro de la zona visible del adaptive icon: 72dp sobre el lienzo de 108dp.
ADAPTIVE_VIEWPORT = 72 / 108

# Proporción del isotipo dentro del círculo visible.
ISOTYPE_IN_CIRCLE = 0.36


def white_to_alpha(img: Image.Image) -> Image.Image:
    """Convierte el fondo blanco en transparencia, sin dejar halo en los bordes.

    alpha = 255 - min(r, g, b): el blanco puro queda invisible y la tinta saturada
    opaca. Después hay que des-premultiplicar el color, porque cada pixel del borde
    antialiaseado viene mezclado con blanco; sin ese paso las letras quedan con un
    contorno lechoso al dibujarse sobre el surface de la app.
    """
    img = img.convert("RGB")
    px = img.load()
    out = Image.new("RGBA", img.size)
    out_px = out.load()

    for y in range(img.height):
        for x in range(img.width):
            r, g, b = px[x, y]
            m = min(r, g, b)
            if m >= WHITE_THRESHOLD:
                out_px[x, y] = (0, 0, 0, 0)
                continue
            a = 255 - m
            scale = 255.0 / a
            out_px[x, y] = (
                min(255, max(0, round((r - m) * scale))),
                min(255, max(0, round((g - m) * scale))),
                min(255, max(0, round((b - m) * scale))),
                a,
            )
    return out


def autocrop(img: Image.Image) -> Image.Image:
    """Recorta al bounding box de lo visible (el lockup trae un margen enorme)."""
    bbox = img.getchannel("A").getbbox()
    return img.crop(bbox) if bbox else img


def lighten_blues(img: Image.Image) -> Image.Image:
    """Sube la luminosidad del navy para la variante -night, dejando el teal intacto.

    Se trabaja en HLS y se filtra por hue: el azul del wordmark cae en ~210-230°,
    el teal en ~170-180°. Solo el primero se lleva a la luminosidad de PRIMARY_DARK.
    """
    target_l = colorsys.rgb_to_hls(*[c / 255 for c in PRIMARY_DARK])[1]
    img = img.convert("RGBA")
    px = img.load()

    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            # hue normalizado 0-1; 200°-250° => 0.555-0.694
            if 0.545 <= h <= 0.70 and l < target_l:
                nr, ng, nb = colorsys.hls_to_rgb(h, target_l, s)
                px[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
    return img


def fit_height(img: Image.Image, height: int) -> Image.Image:
    width = round(img.width * height / img.height)
    return img.resize((width, height), Image.LANCZOS)


def fit_box(img: Image.Image, box: int) -> Image.Image:
    """Escala manteniendo proporción para entrar en un cuadrado de lado `box`."""
    scale = min(box / img.width, box / img.height)
    return img.resize((round(img.width * scale), round(img.height * scale)), Image.LANCZOS)


def max_radius(img: Image.Image) -> float:
    """Distancia del pixel opaco más lejano al centro de la imagen."""
    a = img.getchannel("A").load()
    cx, cy = img.width / 2, img.height / 2
    best = 0.0
    for y in range(img.height):
        for x in range(img.width):
            if a[x, y] > 16:
                r = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
                best = max(best, r)
    return best


def fit_to_radius(img: Image.Image, radius: float) -> Image.Image:
    """Escala `img` para que quepa dentro de un círculo de `radius`.

    Las máscaras de los launchers son circulares, así que la zona segura de un
    adaptive icon es un círculo de 72dp sobre el lienzo de 108dp — no un cuadrado.
    Encajar por bounding box cuadrado deja las esquinas fuera y el launcher las
    recorta: con este isotipo en forma de arco, se comían las patas.
    """
    scale = radius / max_radius(img)
    return img.resize((round(img.width * scale), round(img.height * scale)), Image.LANCZOS)


def centered_on(img: Image.Image, size: int, bg: Image.Image | None = None) -> Image.Image:
    """Pega `img` centrada sobre `bg` (o sobre transparente) en un cuadrado de `size`."""
    canvas = bg.copy() if bg is not None else Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.alpha_composite(img, ((size - img.width) // 2, (size - img.height) // 2))
    return canvas


def knockout(img: Image.Image, color: tuple = (255, 255, 255)) -> Image.Image:
    """Reemplaza el color de `img` conservando su alpha — el isotipo calado en blanco."""
    out = Image.new("RGBA", img.size, color + (0,))
    out.putalpha(img.getchannel("A"))
    return out


def gradient_square(size: int) -> Image.Image:
    """Degradé lineal a 135° (teal arriba-izquierda -> navy abajo-derecha)."""
    img = Image.new("RGBA", (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            t = (x + y) / (2 * (size - 1))
            px[x, y] = tuple(
                round(a + (b - a) * t) for a, b in zip(LOGO_TEAL, LOGO_NAVY)
            ) + (255,)
    return img


def circle_mask(size: int) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    return mask


def save(img: Image.Image, rel: str) -> None:
    path = RES / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    print(f"  {rel}  ({img.width}x{img.height})")


def extract_isotype(lockup: Image.Image) -> Image.Image:
    """Aísla el isotipo (arco + figura) descartando el wordmark de abajo.

    El lockup ya viene recortado, así que el corte se hace por proporción: el
    wordmark ocupa aproximadamente el tercio inferior.
    """
    cut = round(lockup.height * 0.64)
    return autocrop(lockup.crop((0, 0, lockup.width, cut)))


def main() -> None:
    for src in (SRC_LOCKUP, SRC_WORDMARK):
        if not src.exists():
            raise SystemExit(f"No encontré el logo de origen: {src}")

    print("Procesando lockup...")
    lockup = autocrop(white_to_alpha(Image.open(SRC_LOCKUP)))
    print("Procesando wordmark...")
    wordmark = autocrop(white_to_alpha(Image.open(SRC_WORDMARK)))

    print("\nDrawables:")
    save(fit_box(lockup, 512), "drawable-nodpi/domi_logo.png")
    save(wordmark, "drawable-nodpi/domi_wordmark.png")
    save(lighten_blues(wordmark.copy()), "drawable-night-nodpi/domi_wordmark.png")

    print("\nAdaptive icon (isotipo calado en blanco; el fondo es el degradé XML):")
    isotype = extract_isotype(lockup)
    white_isotype = knockout(isotype)
    for name, mult in DENSITIES.items():
        canvas = round(108 * mult)
        # El isotipo se dimensiona contra el círculo VISIBLE (72dp de los 108dp),
        # no contra el lienzo, o queda desproporcionado respecto de la máscara.
        radius = canvas * ADAPTIVE_VIEWPORT * ISOTYPE_IN_CIRCLE
        save(centered_on(fit_to_radius(white_isotype, radius), canvas),
             f"mipmap-{name}/ic_launcher_foreground.png")

    print("\nIconos legacy (disco completo, ya recortado en círculo):")
    for name, mult in DENSITIES.items():
        size = round(48 * mult)
        art = fit_to_radius(white_isotype, size * ISOTYPE_IN_CIRCLE)
        badge = centered_on(art, size, gradient_square(size))
        badge.putalpha(circle_mask(size))
        for fname in ("ic_launcher.webp", "ic_launcher_round.webp"):
            badge.save(RES / f"mipmap-{name}" / fname, lossless=True)
            print(f"  mipmap-{name}/{fname}  ({size}x{size})")

    print("\nListo.")


if __name__ == "__main__":
    main()
