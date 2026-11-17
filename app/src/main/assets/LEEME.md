# Modelos de estimación de pose

La app carga el modelo activo desde este directorio (`PoseModelSpec.ACTIVE`; en esta
versión, `STUDENT_V3` → `student_pruned_ft_deploy_v3_int8.tflite`). Los `.tflite`
**no se versionan** (ver `.gitignore`):

- Descargalos del **Drive adjunto al correo de la entrega** (carpeta de modelos) o
  exportalos con el pipeline de `Tesis-ModelosCV`.
- Copiá el archivo con el nombre exacto que declara `PoseModelSpec.kt`.

La subcarpeta `references/` (trayectorias angulares del comparador DTW) sí está
versionada.
