package com.steadypath.app.domain.comparison

/**
 * Repetición canónica de referencia contra la que se compara la ejecución del usuario.
 *
 * La referencia se **carga** (asset local hoy, backend mañana), no se genera en la app
 * (la construcción por DBA es parte del pipeline offline). [trajectory] debe cubrir
 * exactamente un ciclo valle → pico → valle del ángulo conductor — la misma convención
 * de segmentación que produce [PeakValleyRepetitionSegmenter] en vivo.
 *
 * Invariante del mismo-pipeline: [definition] viene DEL archivo de referencia y es la
 * fuente autoritativa con la que se configura el extractor en vivo, de modo que ambas
 * secuencias comparten definiciones, orden y unidades por construcción.
 */
class ExerciseReference(
    val definition: ExerciseDefinition,
    /** [T] cuadros de [D] ángulos, todos válidos, timestamps sintéticos según fps. */
    val trajectory: List<AngleFrame>,
    val metadata: ReferenceMetadata,
    val scoring: ScoringConfig
) {
    init {
        require(trajectory.isNotEmpty()) { "La trayectoria de referencia está vacía" }
    }
}

data class ReferenceMetadata(
    val fps: Float,
    val version: String,
    val source: String
)

/**
 * Parámetros de scoring por ejercicio.
 * TODO: calibrar con datos etiquetados — los valores actuales son placeholder.
 */
data class ScoringConfig(
    /** Escala de `similarityScore = exp(−normalizedDistance / scale)`. */
    val similarityScale: Double,
    /** similarityScore ≥ este umbral → [MovementStatus.Good]. */
    val goodThreshold: Float
)
