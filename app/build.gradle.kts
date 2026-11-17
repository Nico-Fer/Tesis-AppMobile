plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    jacoco
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
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

// ---------------------------------------------------------------------------
// Cobertura (JaCoCo) sobre los unit tests JVM de la variante awsDebug.
//
// Dos reportes, siempre:
//   (a) jacocoAwsDebugUnitTestReport — todo el módulo, filtrando SOLO código generado.
//   (b) jacocoComparisonReport       — alcance declarado: capa de comparación de
//       movimiento (domain.comparison.** + DtwMovementComparator + ReferenceJson*).
// jacocoComparisonVerification hace fallar el build si (b) baja de 85 % en líneas o ramas.
// ---------------------------------------------------------------------------

// Código generado por herramientas (no escrito a mano). Cada patrón fue verificado
// contra las clases compiladas reales de awsDebug.
val coverageGeneratedExclusions = listOf(
    // AGP
    "**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*",
    // Hilt / Dagger (KSP)
    "**/Hilt_*.class", "**/Hilt_*$*.class",
    "**/*_GeneratedInjector.class",
    "**/*_Factory.class", "**/*_Factory$*.class",
    "**/*_MembersInjector.class",
    "**/*_HiltModules*.class",
    "**/*Module_Provide*Factory.class",
    "**/Dagger*.class",
    "**/*_ComponentTreeDeps.class",
    "**/dagger/**", "**/hilt_aggregated_deps/**",
    // Room (KSP): DomiDatabase_Impl y *Dao_Impl, más sus clases internas
    "**/*_Impl.class", "**/*_Impl$*.class",
    // Compose compiler (lambdas de composables sin captura)
    "**/ComposableSingletons$*.class",
    // kotlinx.serialization compiler plugin
    "**/*\$\$serializer.class",
)

// Alcance (b): la lógica de comparación pura (sin Android). Quedan fuera, a propósito,
// los adapters de I/O de data.comparison (CsvRepMetricsSink, *ReferenceRepository) y
// NoOpMovementComparator — se reportan dentro de (a).
val comparisonScopeIncludes = listOf(
    "com/steadypath/app/domain/comparison/**",
    "com/steadypath/app/data/comparison/DtwMovementComparator*.class",
    "com/steadypath/app/data/comparison/ReferenceJson*.class",
    "com/steadypath/app/data/comparison/*Dto*.class",
)

val coverageVariant = "awsDebug"
val coverageVariantCap = coverageVariant.replaceFirstChar { it.uppercase() }

fun coverageClassDirs(includes: List<String>): ConfigurableFileCollection {
    val kotlinClasses = layout.buildDirectory.dir("tmp/kotlin-classes/$coverageVariant")
    val javaClasses = layout.buildDirectory
        .dir("intermediates/javac/$coverageVariant/compile${coverageVariantCap}JavaWithJavac/classes")
    return files(
        listOf(kotlinClasses, javaClasses).map { dir ->
            fileTree(dir) {
                if (includes.isNotEmpty()) include(includes)
                exclude(coverageGeneratedExclusions)
            }
        }
    )
}

val coverageSourceDirs = files("src/main/java", "src/$coverageVariant/java")
val coverageExecData = layout.buildDirectory.file("jacoco/test${coverageVariantCap}UnitTest.exec")
val coverageTestTask = "test${coverageVariantCap}UnitTest"

tasks.withType<Test>().configureEach {
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

fun JacocoReport.configureCoverageReport(reportName: String, includes: List<String>) {
    group = "verification"
    dependsOn(coverageTestTask)
    classDirectories.setFrom(coverageClassDirs(includes))
    sourceDirectories.setFrom(coverageSourceDirs)
    executionData.setFrom(coverageExecData)
    reports {
        val outDir = layout.buildDirectory.dir("reports/jacoco/$reportName")
        html.required.set(true)
        html.outputLocation.set(outDir.map { it.dir("html") })
        xml.required.set(true)
        xml.outputLocation.set(outDir.map { it.file("jacoco.xml") })
        csv.required.set(true)
        csv.outputLocation.set(outDir.map { it.file("jacoco.csv") })
    }
}

// (a) Cobertura global del módulo.
tasks.register<JacocoReport>("jacoco${coverageVariantCap}UnitTestReport") {
    description = "Cobertura de unit tests JVM ($coverageVariant), todo el módulo, sin código generado."
    configureCoverageReport(coverageVariant, includes = emptyList())
}

// (b) Cobertura del alcance declarado.
tasks.register<JacocoReport>("jacocoComparisonReport") {
    description = "Cobertura de la capa de comparación de movimiento (domain.comparison + DTW comparator)."
    configureCoverageReport("comparison", includes = comparisonScopeIncludes)
}

tasks.register<JacocoCoverageVerification>("jacocoComparisonVerification") {
    group = "verification"
    description = "Falla si la capa de comparación baja de 85 % en líneas o ramas."
    dependsOn(coverageTestTask)
    classDirectories.setFrom(coverageClassDirs(comparisonScopeIncludes))
    sourceDirectories.setFrom(coverageSourceDirs)
    executionData.setFrom(coverageExecData)
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
        }
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