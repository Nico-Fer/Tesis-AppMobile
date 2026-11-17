// Informe del benchmark en Firebase Test Lab (.docx). Datos: datos_informe.json (analisis.py);
// prosa: texto.json (escrita a mano tras ver los resultados).
const fs = require("fs");
const path = require("path");
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell, ImageRun,
  HeadingLevel, AlignmentType, WidthType, ShadingType, BorderStyle, LevelFormat,
  Footer, PageNumber, TableLayoutType,
} = require("docx");

const dir = process.argv[2];
const out = process.argv[3];
const data = JSON.parse(fs.readFileSync(path.join(dir, "datos_informe10.json"), "utf8"));
const txt = JSON.parse(fs.readFileSync(path.join(dir, "texto10.json"), "utf8"));

const FONT = "Calibri";
const PAGE_W = 11906, MARGIN = 1134;               // A4, márgenes de 2 cm
const CONTENT_W = PAGE_W - 2 * MARGIN;             // 9638 DXA
const HEAD_FILL = "E8EEF7", BORDER = "BFBFBF";

const fmt = (v, d = 1) => (v === null || v === undefined || v === "" || Number.isNaN(v)) ? "—"
  : Number(v).toLocaleString("es-AR", { minimumFractionDigits: d, maximumFractionDigits: d });
const pct = (v, d = 0) => (v === null || v === undefined) ? "—" : fmt(v * 100, d) + " %";

function runs(text, opts = {}) {
  // **negrita** inline mínima.
  return text.split(/(\*\*[^*]+\*\*)/).filter(Boolean).map((t) =>
    t.startsWith("**") ? new TextRun({ text: t.slice(2, -2), bold: true, ...opts })
      : new TextRun({ text: t, ...opts }));
}
const p = (text, opts = {}) => new Paragraph({ children: runs(text), spacing: { after: 120 }, ...opts });
const h1 = (t) => new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun(t)] });
const h2 = (t) => new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun(t)] });
const bullet = (t) => new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: runs(t), spacing: { after: 60 } });
const caption = (t) => new Paragraph({ children: [new TextRun({ text: t, italics: true, size: 18, color: "52514E" })], spacing: { before: 60, after: 200 } });

function table(headers, rows, widths, opts = {}) {
  const total = widths.reduce((a, b) => a + b, 0);
  const border = { style: BorderStyle.SINGLE, size: 4, color: BORDER };
  const borders = { top: border, bottom: border, left: border, right: border };
  const cell = (text, i, head) => new TableCell({
    width: { size: widths[i], type: WidthType.DXA },
    borders,
    shading: head ? { fill: HEAD_FILL, type: ShadingType.CLEAR, color: "auto" } : undefined,
    margins: { top: 40, bottom: 40, left: 80, right: 80 },
    children: [new Paragraph({
      alignment: (i === 0 || (opts.leftCols || []).includes(i)) ? AlignmentType.LEFT : AlignmentType.RIGHT,
      children: [new TextRun({ text: String(text), bold: head, size: opts.size || 18 })],
    })],
  });
  return new Table({
    width: { size: total, type: WidthType.DXA },
    columnWidths: widths,
    layout: TableLayoutType.FIXED,
    rows: [
      new TableRow({ tableHeader: true, children: headers.map((h, i) => cell(h, i, true)) }),
      ...rows.map((r) => new TableRow({ children: r.map((c, i) => cell(c, i, false)) })),
    ],
  });
}

function figure(file, widthPx, heightPx, w = 600) {
  const h = Math.round(w * heightPx / widthPx);
  return new Paragraph({
    alignment: AlignmentType.CENTER,
    children: [new ImageRun({ type: "png", data: fs.readFileSync(path.join(dir, file)),
      transformation: { width: w, height: h },
      altText: { title: file, description: file, name: file } })],
  });
}

const D = data.devices;
const byCode = Object.fromEntries(D.map((d) => [d.codename, d]));
const order = D.map((d) => d.codename);
const short = (c) => byCode[c].equipo.replace("Samsung ", "").replace("Motorola ", "").replace("Google ", "").replace(" 5G", "");

const children = [];
children.push(new Paragraph({ heading: HeadingLevel.TITLE, children: [new TextRun(txt.titulo)] }));
children.push(new Paragraph({ children: [new TextRun({ text: txt.subtitulo, color: "52514E" })], spacing: { after: 240 } }));

children.push(h1("1. Objetivo"));
txt.objetivo.forEach((t) => children.push(p(t)));

children.push(h1("2. Metodología"));
children.push(h2("2.1 Entorno de prueba"));
txt.entorno.forEach((t) => children.push(bullet(t)));

children.push(h2("2.2 Dispositivos"));
children.push(p(txt.dispositivos_intro));
children.push(table(
  ["Equipo (Test Lab)", "SoC", "GPU", "Android", "RAM", "Lote", "Rol en la muestra"],
  D.map((d) => [`${d.equipo} (${d.codename})`, d.soc, d.gpu, `${d.android} (API ${d.api})`,
    `${fmt(Number(d.ram_mb) / 1024, 1)} GB`, String(d.lote), d.rol]),
  [2250, 1250, 1050, 1050, 700, 550, 2788], { leftCols: [1, 2, 3, 6] }));
children.push(caption("Tabla 1. Dispositivos físicos de Firebase Test Lab. SoC y GPU según especificación del fabricante; Android y RAM, reportados por el propio dispositivo."));

children.push(h2("2.3 Clips de prueba"));
children.push(p(txt.clips_intro));
children.push(table(
  ["Clip", "Origen", "Duración", "Uso"],
  txt.clips.map((c) => [c.id, c.origen, c.duracion, c.uso]),
  [2900, 2700, 900, 3138], { leftCols: [1, 3] }));
children.push(caption("Tabla 2. Clips de prueba. Todos en 640×480 con rotación de 90° (480×640 vertical), la geometría que entrega CameraX a la app."));

children.push(h2("2.4 Procedimiento y métricas"));
txt.procedimiento.forEach((t) => children.push(p(t)));
txt.metricas.forEach((t) => children.push(bullet(t)));

children.push(h1("3. Resultados"));
children.push(h2("3.1 Rendimiento"));
children.push(p(txt.rendimiento_intro));
children.push(table(
  ["Equipo", "Delegate", "Inferencia p50", "Inferencia p90", "Preproc. p50", "Postproc. p50", "Total p50", "fps efectivos", "Carga modelo"],
  D.map((d) => [short(d.codename), d.delegate === "GPU" ? "GPU" : "CPU", `${fmt(d.inf_p50)} ms`, `${fmt(d.inf_p90)} ms`,
    `${fmt(d.pre_p50)} ms`, `${fmt(d.post_p50)} ms`, `${fmt(d.tot_p50)} ms`, fmt(d.fps_efectivo, 1), `${fmt(d.init_ms, 0)} ms`]),
  [1500, 950, 1050, 1050, 1000, 1000, 1000, 900, 1188]));
children.push(caption(`Tabla 3. Tiempos por frame (mediana y percentil 90 sobre todos los frames procesados de los 9 clips, sin los ${txt.warmup} primeros de calentamiento) y fps efectivos (frames analizados / duración del video).`));
txt.rendimiento_obs.forEach((t) => children.push(bullet(t)));
children.push(figure("fig1_10_tiempo_por_etapa.png", 6.5, 5.4, 470));
children.push(caption("Figura 1. Mediana del tiempo de cada etapa del pipeline; la barra es la suma de las tres medianas."));
children.push(figure("fig2_10_fps_efectivos.png", 6.5, 4.8, 470));
children.push(caption("Figura 2. Cuadros que el pipeline alcanza a analizar por segundo de video, con la estrategia KEEP_ONLY_LATEST de CameraX."));

children.push(h2("3.2 Estabilidad térmica"));
children.push(p(txt.termico_intro));
children.push(table(
  ["Equipo", "Inferencia p50, primer tercio", "Inferencia p50, último tercio", "Variación", "Temp. batería", "Estado térmico máx."],
  D.map((d) => [short(d.codename), `${fmt(d.inf_p50_inicio)} ms`, `${fmt(d.inf_p50_final)} ms`,
    `${d.inf_p50_final >= d.inf_p50_inicio ? "+" : ""}${fmt((d.inf_p50_final / d.inf_p50_inicio - 1) * 100, 1)} %`,
    `${fmt(d.temp_min, 1)}–${fmt(d.temp_max, 1)} °C`, String(d.termico_max)]),
  [1900, 1700, 1700, 1100, 1600, 1638]));
children.push(caption("Tabla 4. Deriva de la inferencia entre el primer y el último tercio de la corrida (~4 min de uso continuo). Estado térmico: PowerManager.THERMAL_STATUS (0 = sin limitación)."));
children.push(p(txt.termico_obs));

children.push(h2("3.3 Precisión en el dispositivo"));
children.push(p(txt.precision_intro));
children.push(table(
  ["Equipo", "Detección", "Desvío vs escritorio (p50)", "Desvío p50, peor clip", "PCK@0,10 dispositivo", "PCK@0,10 escritorio"],
  D.map((d) => [short(d.codename), pct(d.deteccion, 1), fmt(d.desvio_p50, 4), fmt(d.desvio_max, 4),
    fmt(d.pck10_dev, 3), fmt(d.pck10_desk, 3)]),
  [1900, 1100, 1800, 1700, 1600, 1538]));
children.push(caption("Tabla 5. Detección: frames con persona / frames procesados. Desvío: distancia mediana entre los keypoints del dispositivo y los del mismo modelo en escritorio (CPU) sobre el mismo frame, en fracción del alto del cuadro. PCK@0,10: proporción de articulaciones anotadas en UCO a menos del 10 % del alto del cuadro, sobre los mismos frames en dispositivo y escritorio."));
children.push(p(txt.precision_obs));

children.push(h2("3.4 Conteo de repeticiones y puntaje DTW"));
children.push(p(txt.reps_intro));
const clipsReps = [...new Set(data.reps.map((r) => r.clip))];
const repHead = clipsReps.map((c) => {
  const r = data.reps.find((x) => x.clip === c);
  return `${txt.ejercicios_corto[c]} (${r.esperadas || "—"})`;
});
children.push(table(
  ["Equipo", "fps efectivos", ...repHead],
  [...order].sort((a, b) => byCode[a].fps_efectivo - byCode[b].fps_efectivo).map((code) => [short(code), fmt(byCode[code].fps_efectivo, 1), ...clipsReps.map((c) => {
    const r = data.reps.find((x) => x.clip === c && x.codename === code);
    return r ? `${r.detectadas} · ${fmt(r.score, 2)}` : "—";
  })]),
  [1900, 1000, ...clipsReps.map(() => Math.floor((CONTENT_W - 2900) / clipsReps.length))]));
children.push(caption("Tabla 6. Repeticiones detectadas por el segmentador de la app · puntaje final medio (similitud DTW × forma) de las repeticiones puntuadas. Entre paréntesis, el conteo del generador de referencias (no es un conteo manual)."));
txt.reps_obs.forEach((t) => children.push(bullet(t)));

children.push(h1("4. Comparación con la medición en un dispositivo real"));
txt.comparacion.forEach((t) => children.push(p(t)));
children.push(table(
  ["Fuente", "Inferencia p50", "Inferencia p90", "fps efectivos"],
  txt.comparacion_tabla,
  [4038, 1800, 1800, 2000]));
children.push(caption("Tabla 7. Galaxy A72 (Snapdragon 720G) medido a mano en la app, contra el rango de los dispositivos de Test Lab."));

children.push(h1("5. Conclusiones"));
txt.conclusiones.forEach((t) => children.push(bullet(t)));

children.push(h1("6. Limitaciones"));
txt.limitaciones.forEach((t) => children.push(bullet(t)));

children.push(h1("Anexo A. Resultados por clip"));
children.push(p(txt.anexo_intro));
const clipIds = [...new Set(data.clips.map((c) => c.clip))];
[1, 2].forEach((lote, k) => {
  const codes = order.filter((c) => byCode[c].lote === lote);
  children.push(table(
    ["Clip", ...codes.map(short)],
    clipIds.map((id) => [id, ...codes.map((code) => {
      const c = data.clips.find((x) => x.codename === code && x.clip === id);
      return c ? `${fmt(Number(c.inferencia_p50_ms), 0)} ms · ${fmt(Number(c.fps_efectivo), 1)}` : "—";
    })]),
    [2638, ...codes.map(() => 1400)], { size: 16 }));
  children.push(caption(`Tabla A${k + 1}. Lote ${lote}: inferencia p50 · fps efectivos por clip, en el orden de ejecución.`));
});

const doc = new Document({
  creator: "Benchmark DOMI.IA",
  title: txt.titulo,
  styles: {
    default: { document: { run: { font: FONT, size: 22 } } },
    paragraphStyles: [
      { id: "Title", name: "Title", basedOn: "Normal", run: { size: 36, bold: true, font: FONT, color: "1F3864" }, paragraph: { spacing: { after: 80 } } },
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 28, bold: true, font: FONT, color: "1F3864" }, paragraph: { spacing: { before: 320, after: 120 }, outlineLevel: 0, keepNext: true, keepLines: true } },
      { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 24, bold: true, font: FONT, color: "2F5496" }, paragraph: { spacing: { before: 200, after: 100 }, outlineLevel: 1, keepNext: true, keepLines: true } },
    ],
  },
  numbering: { config: [{ reference: "bullets", levels: [{ level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT,
    style: { paragraph: { indent: { left: 540, hanging: 270 } } } }] }] },
  sections: [{
    properties: { page: { size: { width: PAGE_W, height: 16838 }, margin: { top: MARGIN, bottom: MARGIN, left: MARGIN, right: MARGIN } } },
    footers: { default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER,
      children: [new TextRun({ children: [PageNumber.CURRENT], size: 18, color: "52514E" })] })] }) },
    children,
  }],
});

Packer.toBuffer(doc).then((buf) => { fs.writeFileSync(out, buf); console.log("ok", out); });
