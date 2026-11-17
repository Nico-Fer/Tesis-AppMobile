package com.steadypath.app.domain.comparison

import com.steadypath.app.domain.model.PoseFrame
import kotlinx.coroutines.flow.Flow

/**
 * Seam de la capa de comparación de movimiento.
 *
 * Consume el stream de [PoseFrame] producido por el estimador de pose y emite un
 * [ComparisonResult] por cada repetición evaluada. [exerciseId] permite a la
 * implementación resolver su referencia (definiciones de ángulos + trayectoria canónica)
 * sin que el ViewModel conozca ese detalle.
 *
 * El contrato es deliberadamente un transformador de flujos `Flow<PoseFrame> ->
 * Flow<ComparisonResult>` para que la lógica con estado (extracción de ángulos,
 * segmentación de repeticiones, alineación DTW) viva dentro de la implementación sin
 * filtrar detalles al ViewModel.
 */
interface MovementComparator {
    fun compare(exerciseId: String, poses: Flow<PoseFrame>): Flow<ComparisonResult>
}

/**
 * Resultado de comparar la ejecución en vivo contra el movimiento de referencia.
 *
 * Los campos de distancia son nullables: solo se completan cuando la emisión corresponde
 * al cierre de una repetición comparada con DTW. Estructurado para que la similitud de
 * forma (NCC) se agregue después como campo adicional sin romper la interfaz.
 *
 * @property status calidad del movimiento en el instante evaluado.
 * @property repDelta incremento de repeticiones detectado en esta emisión (0 si no hubo).
 * @property repIndex número de repetición (1-based) al que refieren las métricas.
 * @property rawDistance costo DTW acumulado; [Double.POSITIVE_INFINITY] si la banda
 *   volvió infactible el alineamiento.
 * @property normalizedDistance costo por paso del camino, comparable entre repeticiones
 *   de distinta duración.
 * @property similarityScore score final compuesto (similitud DTW × forma) en `[0,1]`
 *   (1 = idéntico).
 * @property shapeScore similitud de forma (NCC × ratio de amplitud) en `[0,1]`; castiga
 *   los déficits de amplitud que el warping del DTW absorbe.
 * @property hints correcciones accionables atribuidas a la repetición (vacío si la
 *   ejecución fue buena o no hay evidencia suficiente para atribuir).
 */
data class ComparisonResult(
    val status: MovementStatus,
    val repDelta: Int = 0,
    val repIndex: Int? = null,
    val rawDistance: Double? = null,
    val normalizedDistance: Double? = null,
    val similarityScore: Float? = null,
    val shapeScore: Float? = null,
    val hints: List<CorrectionHint> = emptyList()
)

/** Estado de la comparación, independiente de la capa de UI. */
enum class MovementStatus {
    Unknown,
    Good,
    NeedsCorrection
}
