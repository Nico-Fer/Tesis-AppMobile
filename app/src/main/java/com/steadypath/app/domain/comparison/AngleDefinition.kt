package com.steadypath.app.domain.comparison

import com.steadypath.app.domain.model.KeypointType

/**
 * Define un ángulo articular como la terna de keypoints COCO-17 (A, vértice, C):
 * el ángulo se mide en [vertex] entre los vectores (A−vértice) y (C−vértice).
 *
 * @property name identificador legible (p. ej. "codo_izq"); se usa para validar que la
 *   referencia cargada y el extractor en vivo comparten exactamente el mismo pipeline.
 * @property weight peso relativo de este ángulo en la distancia local del DTW.
 */
data class AngleDefinition(
    val name: String,
    val pointA: KeypointType,
    val vertex: KeypointType,
    val pointC: KeypointType,
    val weight: Float = 1f
) {
    init {
        require(weight > 0f) { "El peso del ángulo '$name' debe ser positivo (era $weight)" }
        require(pointA != vertex && pointC != vertex) {
            "El ángulo '$name' es degenerado: un extremo coincide con el vértice"
        }
    }
}

/**
 * Conjunto de ángulos que caracterizan un ejercicio.
 *
 * El orden de [angleDefinitions] es significativo: define el orden de las dimensiones en
 * los vectores de features y debe coincidir 1:1 con el de la referencia contra la que se
 * compara (invariante del mismo-pipeline).
 *
 * @property driverAngleIndex índice del "ángulo conductor": el que usa el segmentador de
 *   repeticiones para detectar picos/valles del ciclo del ejercicio.
 */
data class ExerciseDefinition(
    val exerciseId: String,
    val angleDefinitions: List<AngleDefinition>,
    val driverAngleIndex: Int
) {
    init {
        require(angleDefinitions.isNotEmpty()) {
            "El ejercicio '$exerciseId' no define ningún ángulo"
        }
        require(driverAngleIndex in angleDefinitions.indices) {
            "driverAngleIndex=$driverAngleIndex fuera de rango para " +
                "${angleDefinitions.size} ángulos en '$exerciseId'"
        }
    }

    /** Cantidad de dimensiones D del vector de features. */
    val dimensions: Int get() = angleDefinitions.size
}
