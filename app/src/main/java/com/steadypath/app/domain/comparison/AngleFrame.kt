package com.steadypath.app.domain.comparison

/**
 * Vector de ángulos articulares extraído de un único [com.steadypath.app.domain.model.PoseFrame].
 *
 * [angles] está en radianes, en (−π, π], una entrada por [AngleDefinition] y en el mismo
 * orden que la [ExerciseDefinition] que lo produjo. [valid] es la máscara de validez por
 * dimensión: `false` cuando algún keypoint de la terna estuvo por debajo del umbral de
 * confianza — esa dimensión no debe participar de la distancia local.
 *
 * Clase plana (no `data class`): los arrays harían que `equals`/`hashCode` comparen por
 * referencia y eso invita a bugs silenciosos.
 */
class AngleFrame(
    val angles: FloatArray,
    val valid: BooleanArray,
    val timestampMs: Long
) {
    init {
        require(angles.size == valid.size) {
            "angles (${angles.size}) y valid (${valid.size}) deben tener el mismo tamaño"
        }
    }

    /** `true` si al menos una dimensión es utilizable. */
    fun hasValidDimensions(): Boolean = valid.any { it }
}
