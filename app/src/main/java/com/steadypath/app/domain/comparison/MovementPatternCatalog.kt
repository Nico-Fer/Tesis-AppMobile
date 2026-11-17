package com.steadypath.app.domain.comparison

import com.steadypath.app.domain.model.KeypointType

/**
 * Patrón de movimiento del catálogo: define QUÉ ángulos caracterizan un ejercicio,
 * independientemente del UUID que el backend le asigne. El UUID real se vincula recién
 * al capturar/exportar la referencia (ver [toExerciseDefinition]).
 *
 * Los ángulos son signados (ver [AngleExtractor]): una ejecución espejada invierte el
 * signo, por eso las variantes izquierda/derecha son patrones separados y la referencia
 * debe capturarse con la misma orientación de cámara que usará el paciente.
 */
data class MovementPattern(
    val slug: String,
    val displayName: String,
    val angleDefinitions: List<AngleDefinition>,
    val driverAngleIndex: Int,
    val notes: String = ""
) {
    init {
        require(slug.isNotBlank()) { "El slug del patrón no puede estar vacío" }
        require(angleDefinitions.isNotEmpty()) { "El patrón '$slug' no define ángulos" }
        require(driverAngleIndex in angleDefinitions.indices) {
            "driverAngleIndex=$driverAngleIndex fuera de rango para " +
                "${angleDefinitions.size} ángulos en '$slug'"
        }
    }

    /** Vincula el patrón con el UUID real del ejercicio del backend. */
    fun toExerciseDefinition(exerciseId: String): ExerciseDefinition = ExerciseDefinition(
        exerciseId = exerciseId,
        angleDefinitions = angleDefinitions,
        driverAngleIndex = driverAngleIndex
    )
}

/**
 * Catálogo de patrones angulares — alcance cerrado del proyecto: levantamiento de
 * brazos, flexión de rodilla, sentarse y levantarse de una silla, y extensión de
 * pierna en posición sentada.
 *
 * TODO: validar las ternas y pesos con la profesional (directora de tesis / fisio).
 */
object MovementPatternCatalog {

    // Peso menor para ángulos de control postural: informan pero no dominan la distancia.
    private const val SECONDARY_WEIGHT = 0.5f

    private fun hombro(side: String): AngleDefinition {
        val (hip, shoulder, elbow) = when (side) {
            "izq" -> Triple(KeypointType.LEFT_HIP, KeypointType.LEFT_SHOULDER, KeypointType.LEFT_ELBOW)
            else -> Triple(KeypointType.RIGHT_HIP, KeypointType.RIGHT_SHOULDER, KeypointType.RIGHT_ELBOW)
        }
        return AngleDefinition("hombro_$side", hip, shoulder, elbow)
    }

    private fun codo(side: String, weight: Float = 1f): AngleDefinition {
        val (shoulder, elbow, wrist) = when (side) {
            "izq" -> Triple(KeypointType.LEFT_SHOULDER, KeypointType.LEFT_ELBOW, KeypointType.LEFT_WRIST)
            else -> Triple(KeypointType.RIGHT_SHOULDER, KeypointType.RIGHT_ELBOW, KeypointType.RIGHT_WRIST)
        }
        return AngleDefinition("codo_$side", shoulder, elbow, wrist, weight)
    }

    private fun rodilla(side: String): AngleDefinition {
        val (hip, knee, ankle) = when (side) {
            "izq" -> Triple(KeypointType.LEFT_HIP, KeypointType.LEFT_KNEE, KeypointType.LEFT_ANKLE)
            else -> Triple(KeypointType.RIGHT_HIP, KeypointType.RIGHT_KNEE, KeypointType.RIGHT_ANKLE)
        }
        return AngleDefinition("rodilla_$side", hip, knee, ankle)
    }

    private fun cadera(side: String, weight: Float = SECONDARY_WEIGHT): AngleDefinition {
        val (shoulder, hip, knee) = when (side) {
            "izq" -> Triple(KeypointType.LEFT_SHOULDER, KeypointType.LEFT_HIP, KeypointType.LEFT_KNEE)
            else -> Triple(KeypointType.RIGHT_SHOULDER, KeypointType.RIGHT_HIP, KeypointType.RIGHT_KNEE)
        }
        return AngleDefinition("cadera_$side", shoulder, hip, knee, weight)
    }

    val patterns: List<MovementPattern> = listOf(
        MovementPattern(
            slug = "levantamiento_brazos",
            displayName = "Levantamiento de brazos",
            angleDefinitions = listOf(
                hombro("izq"),
                hombro("der"),
                codo("izq", SECONDARY_WEIGHT),
                codo("der", SECONDARY_WEIGHT)
            ),
            driverAngleIndex = 0,
            notes = "Bilateral, de frente. Codos con peso menor: controlan que los brazos vayan rectos."
        ),
        MovementPattern(
            slug = "flexion_rodilla_izq",
            displayName = "Flexión de rodilla (izq)",
            angleDefinitions = listOf(rodilla("izq"), cadera("izq")),
            driverAngleIndex = 0,
            notes = "De pie, perfil izquierdo. La cadera detecta compensación del tronco."
        ),
        MovementPattern(
            slug = "flexion_rodilla_der",
            displayName = "Flexión de rodilla (der)",
            angleDefinitions = listOf(rodilla("der"), cadera("der")),
            driverAngleIndex = 0,
            notes = "De pie, perfil derecho. La cadera detecta compensación del tronco."
        ),
        MovementPattern(
            slug = "sentarse_levantarse_silla",
            displayName = "Sentarse y levantarse de una silla",
            angleDefinitions = listOf(rodilla("izq"), rodilla("der"), cadera("izq")),
            driverAngleIndex = 0,
            notes = "De perfil. El lado ocluido se invalida por confianza y la distancia renormaliza sola."
        ),
        MovementPattern(
            slug = "sentadilla_parcial",
            displayName = "Sentadilla parcial",
            // Bilateral; pesos 1.0 en todos: replican la referencia shipped (c1000000-…-0002).
            angleDefinitions = listOf(cadera("izq", 1f), rodilla("izq"), cadera("der", 1f), rodilla("der")),
            driverAngleIndex = 0,
            notes = "De pie, bilateral. La cadera izquierda conduce; las rodillas controlan la profundidad."
        ),
        MovementPattern(
            slug = "extension_pierna_sentada_izq",
            displayName = "Extensión de rodilla sentado (izq)",
            // Solo rodilla: replica la referencia shipped (c1000000-…-0006).
            angleDefinitions = listOf(rodilla("izq")),
            driverAngleIndex = 0,
            notes = "Sentado, perfil izquierdo. El ángulo cruza ±π cerca de la extensión completa."
        ),
        MovementPattern(
            slug = "extension_pierna_sentada_der",
            displayName = "Extensión de pierna sentada (der)",
            angleDefinitions = listOf(rodilla("der"), cadera("der")),
            driverAngleIndex = 0,
            notes = "Sentado, perfil derecho. La cadera controla que no compense levantando el muslo."
        ),
        MovementPattern(
            slug = "elevacion_talones_izq",
            displayName = "Elevación de talones (sentado)",
            // Pesos 1.0 en ambos: replican la referencia shipped (c1000000-…-0005).
            angleDefinitions = listOf(rodilla("izq"), cadera("izq", 1f)),
            driverAngleIndex = 0,
            notes = "Sentado, perfil izquierdo. Excursión chica (~0.17 rad): el conteo depende del cap de prominencia."
        )
    )

    fun bySlug(slug: String): MovementPattern? = patterns.firstOrNull { it.slug == slug }
}
