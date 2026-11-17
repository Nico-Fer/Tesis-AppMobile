# Tesis-AppMobile

App Android (Kotlin + Jetpack Compose) del sistema de asistencia para la rehabilitación
en adultos mayores: captura de ejercicios con cámara, estimación de pose on-device
(YOLOv8-pose → TFLite INT8), comparación de movimiento contra referencias (DTW) y
sincronización con el backend [`TesisApi`](https://github.com/Nico-Fer/TesisApi).

> ⚠️ **Repositorio de entrega académica**
>
> Esta es una copia pública creada para la entrega a la universidad. **No es el
> repositorio original del trabajo**: el original permanece privado. En esta copia:
>
> - Los valores del despliegue en AWS se reemplazaron por marcadores
>   (`TU_API_GATEWAY`, `TU_USER_POOL_ID`, `TU_APP_CLIENT_ID`): el flavor `aws` de
>   `app/build.gradle.kts` y `app/src/main/res/raw/amplifyconfiguration.json` apuntan
>   a un despliegue propio, no al laboratorio de los autores.
> - Los clips de la prueba instrumentada no se distribuyen (ver
>   `app/src/androidTest/assets/benchmark/LEEME.md`).
> - Los modelos `.tflite` no se versionan: bajalos del **Drive adjunto al correo de
>   la entrega** (ver `app/src/main/assets/LEEME.md`).
> - La carpeta `Mocks/` (diseño de interfaz) no se incluye en esta copia.
>
> Ramas incluidas: `main`, `ImplementacionJacoco` (medición de cobertura de código)
> e `IntegracionFirebaseTestLab` (pruebas instrumentadas en Firebase Test Lab).

## Construir

1. Copiá el modelo activo a `app/src/main/assets/` (ver el LEEME de esa carpeta).
2. Compilá con Android Studio o por línea de comandos:
   - `./gradlew assembleLocalDebug` — flavor `local` contra el backend en `http://10.0.2.2:5264/`
   - `./gradlew assembleAwsDebug` — flavor `aws` (antes, reemplazá los marcadores de configuración)
3. Tests: `./gradlew test` · `./gradlew connectedAndroidTest`
