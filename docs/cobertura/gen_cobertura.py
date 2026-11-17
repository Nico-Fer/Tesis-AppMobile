#!/usr/bin/env python3
"""Genera los artefactos de cobertura para la tesis a partir de los XML de JaCoCo.

Uso (desde la raíz de appMobile, tras correr las tareas Gradle):

    .\\gradlew :app:jacocoAwsDebugUnitTestReport :app:jacocoComparisonReport
    python docs/cobertura/gen_cobertura.py

Salidas (en docs/cobertura/ por defecto):
    tabla_cobertura.tex         tabular por paquete: global (a) y alcance (b)
    tabla_cobertura_clases.tex  tabular por archivo fuente del alcance (b)
    resumen_cobertura.png/.pdf  barras líneas/ramas por paquete, con la meta del 85 %
    medicion_base.md            misma información en Markdown + ramas en métodos sintetizados

Solo depende de la stdlib; matplotlib es opcional (sin él se omite la figura).
"""

from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
APP_PREFIX = "com/steadypath/app/"
THRESHOLD = 0.85

# Métodos que el compilador de Kotlin sintetiza para `data class`; JaCoCo no los
# filtra y sus ramas (comparaciones campo a campo) no corresponden a lógica escrita.
SYNTHETIC_METHOD = re.compile(r"^(equals|hashCode|toString|copy|copy\$default|component\d+)$")


@dataclass
class Counter:
    missed: int = 0
    covered: int = 0

    @property
    def total(self) -> int:
        return self.missed + self.covered

    @property
    def ratio(self) -> float | None:
        return None if self.total == 0 else self.covered / self.total

    def add(self, other: "Counter") -> None:
        self.missed += other.missed
        self.covered += other.covered


@dataclass
class Node:
    name: str
    line: Counter = field(default_factory=Counter)
    branch: Counter = field(default_factory=Counter)
    synthetic_branch: Counter = field(default_factory=Counter)

    def add(self, other: "Node") -> None:
        self.line.add(other.line)
        self.branch.add(other.branch)
        self.synthetic_branch.add(other.synthetic_branch)


def _counter(elem: ET.Element, kind: str) -> Counter:
    for c in elem.findall("counter"):
        if c.get("type") == kind:
            return Counter(int(c.get("missed", 0)), int(c.get("covered", 0)))
    return Counter()


def parse_report(path: Path) -> tuple[Node, dict[str, Node], dict[str, Node]]:
    """Devuelve (bundle, paquetes, archivos). Las claves son nombres JaCoCo con '/'."""
    root = ET.parse(path).getroot()
    bundle = Node(root.get("name", "bundle"), _counter(root, "LINE"), _counter(root, "BRANCH"))
    packages: dict[str, Node] = {}
    files: dict[str, Node] = {}
    for pkg in root.findall("package"):
        pname = pkg.get("name", "")
        packages[pname] = Node(pname, _counter(pkg, "LINE"), _counter(pkg, "BRANCH"))
        synthetic_by_file: dict[str, Counter] = defaultdict(Counter)
        for cls in pkg.findall("class"):
            src = cls.get("sourcefilename", "")
            for m in cls.findall("method"):
                if SYNTHETIC_METHOD.match(m.get("name", "")):
                    synthetic_by_file[src].add(_counter(m, "BRANCH"))
        for sf in pkg.findall("sourcefile"):
            fname = sf.get("name", "")
            key = f"{pname}/{fname}"
            files[key] = Node(
                key,
                _counter(sf, "LINE"),
                _counter(sf, "BRANCH"),
                synthetic_by_file.get(fname, Counter()),
            )
            packages[pname].synthetic_branch.add(files[key].synthetic_branch)
        bundle.synthetic_branch.add(packages[pname].synthetic_branch)
    return bundle, packages, files


def short_pkg(jacoco_name: str, depth: int | None) -> str:
    name = jacoco_name
    if name.startswith(APP_PREFIX):
        name = name[len(APP_PREFIX):]
    elif name == APP_PREFIX.rstrip("/"):
        name = "(raíz)"
    name = name.replace("/", ".")
    if depth is not None and name != "(raíz)":
        name = ".".join(name.split(".")[:depth])
    return name


def group_packages(packages: dict[str, Node], depth: int | None) -> list[Node]:
    grouped: dict[str, Node] = {}
    for pname, node in packages.items():
        key = short_pkg(pname, depth)
        grouped.setdefault(key, Node(key)).add(node)
    return sorted(grouped.values(), key=lambda n: n.name)


def pct(c: Counter, decimal: str) -> str:
    if c.ratio is None:
        return "—"
    return f"{c.ratio * 100:.1f}".replace(".", decimal) + r"\,\%"


def pct_md(c: Counter) -> str:
    return "—" if c.ratio is None else f"{c.ratio * 100:.1f} %"


def tex_escape(s: str) -> str:
    return s.replace("_", r"\_").replace("$", r"\$").replace("%", r"\%")


def row(c_line: Counter, c_branch: Counter, decimal: str) -> str:
    return (
        f"{c_line.covered}/{c_line.total} & {pct(c_line, decimal)} & "
        f"{c_branch.covered}/{c_branch.total} & {pct(c_branch, decimal)}"
    )


def write_tabla_paquetes(
    out: Path, a_bundle: Node, a_pkgs: list[Node], b_bundle: Node, b_pkgs: list[Node], decimal: str
) -> None:
    lines = [
        "% Generado por docs/cobertura/gen_cobertura.py — no editar a mano.",
        "% Requiere \\usepackage{booktabs}.",
        r"\begin{tabular}{@{}lrrrr@{}}",
        r"\toprule",
        r"\textbf{Paquete} & \textbf{Líneas} & \textbf{\%} & \textbf{Ramas} & \textbf{\%} \\",
        r"\midrule",
        r"\multicolumn{5}{@{}l}{\emph{(a) Módulo completo, excluido solo el código generado}} \\",
    ]
    for p in a_pkgs:
        lines.append(f"\\quad {tex_escape(p.name)} & {row(p.line, p.branch, decimal)} \\\\")
    lines.append(r"\midrule")
    lines.append(
        f"\\textbf{{Total (a)}} & {row(a_bundle.line, a_bundle.branch, decimal)} \\\\"
    )
    lines.append(r"\midrule")
    lines.append(
        r"\multicolumn{5}{@{}l}{\emph{(b) Alcance declarado: capa de comparación de movimiento}} \\"
    )
    for p in b_pkgs:
        lines.append(f"\\quad {tex_escape(p.name)} & {row(p.line, p.branch, decimal)} \\\\")
    lines.append(r"\midrule")
    lines.append(
        f"\\textbf{{Total (b)}} & {row(b_bundle.line, b_bundle.branch, decimal)} \\\\"
    )
    lines += [r"\bottomrule", r"\end{tabular}", ""]
    out.write_text("\n".join(lines), encoding="utf-8")


def write_tabla_clases(out: Path, b_files: dict[str, Node], decimal: str) -> None:
    lines = [
        "% Generado por docs/cobertura/gen_cobertura.py — no editar a mano.",
        "% Requiere \\usepackage{booktabs}. Última columna: ramas no cubiertas que pertenecen",
        "% a métodos sintetizados por el compilador de Kotlin (equals/hashCode/toString/copy/componentN).",
        r"\begin{tabular}{@{}llrrrrr@{}}",
        r"\toprule",
        r"\textbf{Paquete} & \textbf{Archivo} & \textbf{Líneas} & \textbf{\%} & \textbf{Ramas} & \textbf{\%} & \textbf{Ramas sint.} \\",
        r"\midrule",
    ]
    for key in sorted(b_files):
        n = b_files[key]
        pkg, fname = key.rsplit("/", 1)
        lines.append(
            f"{tex_escape(short_pkg(pkg, None))} & {tex_escape(fname)} & "
            f"{row(n.line, n.branch, decimal)} & {n.synthetic_branch.missed} \\\\"
        )
    lines += [r"\bottomrule", r"\end{tabular}", ""]
    out.write_text("\n".join(lines), encoding="utf-8")


def write_markdown(
    out: Path,
    a_bundle: Node,
    a_pkgs: list[Node],
    b_bundle: Node,
    b_pkgs: list[Node],
    b_files: dict[str, Node],
    xml_a: Path,
    xml_b: Path,
) -> None:
    def md_row(name: str, n: Node, extra: str = "") -> str:
        return (
            f"| {name} | {n.line.covered}/{n.line.total} | {pct_md(n.line)} | "
            f"{n.branch.covered}/{n.branch.total} | {pct_md(n.branch)} |{extra}"
        )

    hdr = "| Paquete | Líneas | % | Ramas | % |\n|---|---:|---:|---:|---:|"
    lines = [
        "# Medición de cobertura (JaCoCo)",
        "",
        f"Fuentes: `{xml_a.relative_to(REPO)}` (a) y `{xml_b.relative_to(REPO)}` (b).",
        "Generado por `docs/cobertura/gen_cobertura.py`.",
        "",
        "## (a) Módulo completo — solo excluido el código generado",
        "",
        hdr,
    ]
    lines += [md_row(p.name, p) for p in a_pkgs]
    lines.append(md_row("**Total (a)**", a_bundle))
    lines += ["", "## (b) Alcance declarado — capa de comparación", "", hdr]
    lines += [md_row(p.name, p) for p in b_pkgs]
    lines.append(md_row("**Total (b)**", b_bundle))
    real = Counter(
        b_bundle.branch.missed - b_bundle.synthetic_branch.missed,
        b_bundle.branch.covered - b_bundle.synthetic_branch.covered,
    )
    lines += [
        "",
        f"Ramas en métodos sintetizados de `data class` dentro de (b): "
        f"{b_bundle.synthetic_branch.covered}/{b_bundle.synthetic_branch.total} cubiertas "
        f"({b_bundle.synthetic_branch.missed} sin cubrir).",
        f"Ramas de (b) excluyendo esos métodos: {real.covered}/{real.total} ({pct_md(real)}).",
        "",
        "### Por archivo fuente",
        "",
        "| Paquete | Archivo | Líneas | % | Ramas | % | Ramas sint. sin cubrir |",
        "|---|---|---:|---:|---:|---:|---:|",
    ]
    for key in sorted(b_files):
        n = b_files[key]
        pkg, fname = key.rsplit("/", 1)
        lines.append(
            f"| {short_pkg(pkg, None)} | {fname} | {n.line.covered}/{n.line.total} | {pct_md(n.line)} | "
            f"{n.branch.covered}/{n.branch.total} | {pct_md(n.branch)} | {n.synthetic_branch.missed} |"
        )
    lines.append("")
    out.write_text("\n".join(lines), encoding="utf-8")


def write_figure(out_base: Path, b_pkgs: list[Node], b_bundle: Node, a_bundle: Node) -> bool:
    try:
        import matplotlib

        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ImportError:
        return False

    labels = [p.name for p in b_pkgs] + ["Total (b)", "Total (a)"]
    nodes = b_pkgs + [b_bundle, a_bundle]
    line_vals = [(n.line.ratio or 0) * 100 for n in nodes]
    branch_vals = [(n.branch.ratio or 0) * 100 for n in nodes]

    x = range(len(labels))
    width = 0.38
    fig, ax = plt.subplots(figsize=(max(6, 1.6 * len(labels)), 4.2))
    ax.bar([i - width / 2 for i in x], line_vals, width, label="Líneas", color="#0044a1")
    ax.bar([i + width / 2 for i in x], branch_vals, width, label="Ramas", color="#006b5f")
    ax.axhline(THRESHOLD * 100, color="#b3261e", linestyle="--", linewidth=1, label="Meta 85 %")
    for i, (lv, bv) in enumerate(zip(line_vals, branch_vals)):
        ax.text(i - width / 2, lv + 1, f"{lv:.0f}", ha="center", va="bottom", fontsize=8)
        ax.text(i + width / 2, bv + 1, f"{bv:.0f}", ha="center", va="bottom", fontsize=8)
    ax.set_ylim(0, 112)
    ax.set_ylabel("Cobertura (%)")
    ax.set_xticks(list(x))
    ax.set_xticklabels(labels, rotation=20, ha="right", fontsize=9)
    ax.legend(loc="upper center", ncol=3, fontsize=9, frameon=False, bbox_to_anchor=(0.5, 1.02))
    ax.spines[["top", "right"]].set_visible(False)
    fig.tight_layout()
    fig.savefig(out_base.with_suffix(".png"), dpi=200)
    fig.savefig(out_base.with_suffix(".pdf"))
    plt.close(fig)
    return True


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument(
        "--xml-global",
        type=Path,
        default=REPO / "app/build/reports/jacoco/awsDebug/jacoco.xml",
        help="jacoco.xml del reporte (a)",
    )
    ap.add_argument(
        "--xml-alcance",
        type=Path,
        default=REPO / "app/build/reports/jacoco/comparison/jacoco.xml",
        help="jacoco.xml del reporte (b)",
    )
    ap.add_argument("--out", type=Path, default=HERE, help="carpeta de salida")
    ap.add_argument(
        "--depth",
        type=int,
        default=2,
        help="niveles de paquete bajo com.steadypath.app para agrupar (a); 0 = sin agrupar",
    )
    ap.add_argument("--decimal", default=",", help="separador decimal en los .tex")
    args = ap.parse_args(argv)

    for p in (args.xml_global, args.xml_alcance):
        if not p.exists():
            print(f"No existe {p}. Corré primero las tareas Gradle de JaCoCo.", file=sys.stderr)
            return 1
    args.out.mkdir(parents=True, exist_ok=True)

    a_bundle, a_pkgs_raw, _ = parse_report(args.xml_global)
    b_bundle, b_pkgs_raw, b_files = parse_report(args.xml_alcance)
    a_pkgs = group_packages(a_pkgs_raw, args.depth or None)
    b_pkgs = group_packages(b_pkgs_raw, None)

    write_tabla_paquetes(args.out / "tabla_cobertura.tex", a_bundle, a_pkgs, b_bundle, b_pkgs, args.decimal)
    write_tabla_clases(args.out / "tabla_cobertura_clases.tex", b_files, args.decimal)
    write_markdown(
        args.out / "medicion_base.md", a_bundle, a_pkgs, b_bundle, b_pkgs, b_files, args.xml_global, args.xml_alcance
    )
    has_fig = write_figure(args.out / "resumen_cobertura", b_pkgs, b_bundle, a_bundle)

    print(f"(a) líneas {pct_md(a_bundle.line)}  ramas {pct_md(a_bundle.branch)}")
    print(f"(b) líneas {pct_md(b_bundle.line)}  ramas {pct_md(b_bundle.branch)}")
    print(f"Escrito en {args.out}" + ("" if has_fig else " (sin figura: falta matplotlib)"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
