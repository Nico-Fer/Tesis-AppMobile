import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.firebase.testlab)
}

android {
    namespace = "com.steadypath.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.steadypath.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Parámetros de PoseBenchmarkTest (-Pbenchmark.delegate=cpu, -Pbenchmark.clips=a,b,
        // -Pbenchmark.vueltas=3). Van por defaultConfig porque es lo que el plugin de Test Lab
        // reenvía al dispositivo.
        mapOf(
            "benchmark.delegate" to "benchmarkDelegate",
            "benchmark.clips" to "benchmarkClips",
            "benchmark.vueltas" to "benchmarkVueltas",
            "benchmark.reposoS" to "benchmarkReposoS"
        ).forEach { (property, argument) ->
            providers.gradleProperty(property).orNull?.let {
                testInstrumentationRunnerArguments[argument] = it
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Backend conmutable: aws (API Gateway) o local (Kestrel en la máquina host).
    flavorDimensions += "backend"
    productFlavors {
        create("aws") {
            dimension = "backend"
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"https://TU_API_GATEWAY.execute-api.us-east-1.amazonaws.com/\""
            )
        }
        create("local") {
            dimension = "backend"
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:5264/\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // Requerido por Amplify (usa java.time y otras APIs de JDK moderno)
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        // BuildConfig.DEBUG gatea los logs de diagnóstico del pipeline de pose.
        buildConfig = true
    }
    androidResources {
        // No comprimir el modelo .tflite para permitir memory-mapping en runtime
        noCompress += "tflite"
    }
    testOptions {
        // android.util.Log en tests JVM devuelve 0 en vez de tirar "not mocked".
        unitTests.isReturnDefaultValues = true
    }
}

/**
 * Dispositivos de Firebase Test Lab: gama media Snapdragon/Adreno como los medidos a mano
 * (A72, Moto G60, Redmi Note 9 Pro, S20 FE), sin repetir esos modelos, en lotes de 5
 * (la cuota diaria sin costo de Blaze es de 30 min físicos). La elección está justificada en
 * docs/benchmark-firebase-test-lab.md. Lote → nombre de Gradle → (MODEL_ID, API). Cada
 * dispositivo genera `<nombre>AwsDebugAndroidTest` y cada lote `<lote>GroupAwsDebugAndroidTest`.
 */
val ftlLotes = linkedMapOf(
    // Corrido el 2026-10-12.
    "ftlLote1" to linkedMapOf(
        "ftlMotoG30" to ("caprip" to 31),        // SD662 / Adreno 610: piso de la gama media
        "ftlMotoG34" to ("fogos" to 34),         // SD695 / Adreno 619 ≈ 618 del A72/G60/Redmi
        "ftlNordCe3Lite" to ("OP5958L1" to 34),  // mismo SD695 que el g34, otro fabricante
        "ftlPixel5" to ("redfin" to 30),         // SD765G / Adreno 620: Snapdragon 7 de 2020
        "ftlGalaxyA52s" to ("a52sxq" to 34)      // SD778G / Adreno 642L: Galaxy A, gama media-alta
    ),
    // Cada uno es el segundo fabricante de una clase de GPU del lote 1, o una clase nueva.
    "ftlLote2" to linkedMapOf(
        "ftlGalaxyA52" to ("a52xq" to 34),       // SD750G / Adreno 619: hermano directo del A72
        "ftlMotoG53" to ("penang" to 34),        // SD480+ / Adreno 619: misma GPU que el g34, CPU más débil
        "ftlXperia10II" to ("SO-41A" to 31),     // SD665 / Adreno 610: segundo piso, época del Redmi Note 9 Pro
        "ftlEdge30" to ("dubai" to 34),          // SD778G+ / Adreno 642L: segundo techo, otro fabricante
        "ftlGalaxyA36" to ("a36xq" to 35)        // SD 6 Gen 3 / Adreno 710: gama media actual
    )
)

// JSON de cuenta de servicio, fuera del repo: `ftl.credenciales=<ruta>` en local.properties
// o la variable FTL_CREDENCIALES. Sin ninguno, el plugin usa las Application Default
// Credentials (`gcloud auth application-default login`).
val ftlCredenciales: String? = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use { load(it) }
}.getProperty("ftl.credenciales") ?: System.getenv("FTL_CREDENCIALES")

firebaseTestLab {
    ftlCredenciales?.let { serviceAccountCredentials.set(file(it)) }

    managedDevices {
        ftlLotes.values.flatMap { it.entries }.forEach { (name, model) ->
            create(name) {
                device = model.first
                apiLevel = model.second
            }
        }
    }

    testOptions {
        fixture {
            grantedPermissions = "all"
        }
        execution {
            // Una pasada del catálogo tarda ~5 min en gama media; el máximo físico es 45.
            timeoutMinutes = 30
            // Re-ejecutar un benchmark fallido duplicaría filas en los CSV.
            maxTestReruns = 0
        }
        results {
            resultsHistoryName = "benchmark-pose"
            // Lo que escribe PoseBenchmarkTest; el plugin lo baja solo a
            // app/build/outputs/androidTest-results/managedDevice/.
            directoriesToPull.addAll("/sdcard/Android/media/com.steadypath.app/benchmark")
            // Grabar pantalla o muestrear métricas compite por CPU/GPU con lo que se mide.
            recordVideo = false
            performanceMetrics = false
        }
    }
}

ftlLotes.forEach { (lote, dispositivos) ->
    android.testOptions.managedDevices.groups.create(lote) {
        dispositivos.keys.forEach {
            targetDevices.add(android.testOptions.managedDevices.allDevices.getByName(it))
        }
    }
}

// Los .mp4 del benchmark no se versionan (sólo clips.json). Si faltan, Test Lab instala y
// corre igual, falla clip por clip con FileNotFoundException y consume cuota: se corta acá,
// antes de subir nada.
val verificarClipsBenchmark by tasks.registering {
    val clipsDir = layout.projectDirectory.dir("src/androidTest/assets/benchmark")
    doLast {
        val manifest = clipsDir.file("clips.json").asFile
        check(manifest.exists()) { "Falta ${manifest.path}" }
        val faltantes = Regex("\"archivo\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(manifest.readText())
            .map { it.groupValues[1] }
            .filterNot { clipsDir.file(it).asFile.exists() }
            .toList()
        check(faltantes.isEmpty()) {
            "Faltan ${faltantes.size} clips del benchmark en ${clipsDir.asFile.path} " +
                "(${faltantes.joinToString()}). Regenerarlos con tools/benchmark/preparar_clips.py " +
                "(ver docs/benchmark-firebase-test-lab.md)."
        }
    }
}
tasks.matching { it.name.startsWith("ftl") && it.name.endsWith("AndroidTest") }.configureEach {
    dependsOn(verificarClipsBenchmark)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.activity)
    debugImplementation(libs.compose.ui.tooling)

    // Lifecycle
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Image loading
    implementation(libs.coil.compose)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // AWS Amplify — autenticación contra Cognito
    implementation(libs.amplify.core.kotlin)
    implementation(libs.amplify.auth.cognito)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Media3 / ExoPlayer — videos de ejercicios
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    // LiteRT (TensorFlow Lite) — inferencia del modelo YOLO Pose
    implementation(libs.litert)
    implementation(libs.litert.gpu)
    implementation(libs.litert.gpu.api)
    implementation(libs.litert.support)

    // CameraX — captura y análisis de frames
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // DataStore
    implementation(libs.datastore.preferences)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}