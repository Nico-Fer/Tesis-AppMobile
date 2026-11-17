# Cobertura de pruebas unitarias (JaCoCo)

Artefactos para el Cap. 7 de la tesis. Nada de esta carpeta se edita a mano salvo
`seccion_cobertura.tex` (borrador) y este README.

## Regenerar todo

```powershell
# desde la raíz de appMobile
.\gradlew :app:jacocoAwsDebugUnitTestReport :app:jacocoComparisonReport
python docs/cobertura/gen_cobertura.py
```

La primera línea corre `testAwsDebugUnitTest` y deja HTML/XML/CSV en
`app/build/reports/jacoco/awsDebug/` (a: módulo completo) y
`app/build/reports/jacoco/comparison/` (b: capa de comparación). La segunda lee ambos
`jacoco.xml` y escribe acá:

| Archivo | Uso |
|---|---|
| `tabla_cobertura.tex` | `\input` en la tesis: tabular por paquete, (a) y (b) |
| `tabla_cobertura_clases.tex` | `\input`: tabular por archivo fuente de (b) |
| `resumen_cobertura.pdf` / `.png` | figura: barras líneas/ramas con la meta del 85 % |
| `medicion_base.md` | misma información en Markdown, para leer sin compilar LaTeX |
| `seccion_cobertura.tex` | borrador de la subsección; buscar `% TODO` |

Las tablas requieren `\usepackage{booktabs}`; la figura, `graphicx`.

## Umbral

```powershell
.\gradlew :app:jacocoComparisonVerification
```

Falla el build si (b) baja de 85 % en líneas o en ramas. El alcance y las exclusiones
están declarados (y comentados) en `app/build.gradle.kts`, bloque "Cobertura (JaCoCo)".
