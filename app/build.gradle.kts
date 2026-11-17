plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
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

    // Room — caché local y cola de salida del modo sin conexión
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // WorkManager — envío de la cola de salida con la aplicación cerrada
    implementation(libs.work.runtime.ktx)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}