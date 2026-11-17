package com.steadypath.app.data.comparison

import com.steadypath.app.domain.comparison.AngleDefinition
import com.steadypath.app.domain.comparison.AngleFrame
import com.steadypath.app.domain.comparison.ExerciseDefinition
import com.steadypath.app.domain.comparison.ExerciseReference
import com.steadypath.app.domain.comparison.ReferenceMetadata
import com.steadypath.app.domain.comparison.ScoringConfig
import com.steadypath.app.domain.model.KeypointType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Esquema JSON (v1) de una referencia de ejercicio:
 *
 * ```json
 * {
 *   "schemaVersion": 1,
 *   "exerciseId": "uuid",
 *   "angleDefinitions": [
 *     { "name": "codo_izq", "pointA": "LEFT_SHOULDER", "vertex": "LEFT_ELBOW",
 *       "pointC": "LEFT_WRIST", "weight": 1.0 }
 *   ],
 *   "driverAngleIndex": 0,
 *   "referenceTrajectory": [[3.14, 3.10], [3.01, 2.98]],
 *   "metadata": { "fps": 20.0, "version": "1.0", "source": "captura-offline" },
 *   "scoring": { "similarityScale": 0.5, "goodThreshold": 0.7 }
 * }
 * ```
 *
 * Los nombres de keypoint son los de [KeypointType]; los ángulos van en radianes, en el
 * mismo pipeline de features que produce [com.steadypath.app.domain.comparison.DefaultAngleExtractor].
 * El archivo incluye las `angleDefinitions` justamente para poder validar la invariante
 * del mismo-pipeline en carga.
 */
@Serializable
data class ReferenceFileDto(
    val schemaVersion: Int,
    val exerciseId: String,
    val angleDefinitions: List<AngleDefinitionDto>,
    val driverAngleIndex: Int,
    val referenceTrajectory: List<List<Float>>,
    val metadata: MetadataDto,
    val scoring: ScoringDto
)

@Serializable
data class AngleDefinitionDto(
    val name: String,
    val pointA: String,
    val vertex: String,
    val pointC: String,
    val weight: Float = 1f
)

@Serializable
data class MetadataDto(
    val fps: Float,
    val version: String,
    val source: String
)

@Serializable
data class ScoringDto(
    val similarityScale: Double,
    val goodThreshold: Float
)

/**
 * Parsea y valida una referencia. Toda inconsistencia falla fuerte con
 * [IllegalArgumentException] y mensaje accionable: una referencia inválida comparada en
 * silencio produce distancias sin sentido, que es el peor modo de falla posible.
 */
object ReferenceJsonParser {

    const val SUPPORTED_SCHEMA_VERSION = 1

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(jsonText: String): ExerciseReference {
        val dto = json.decodeFromString<ReferenceFileDto>(jsonText)

        require(dto.schemaVersion == SUPPORTED_SCHEMA_VERSION) {
            "schemaVersion=${dto.schemaVersion} no soportada (esperaba $SUPPORTED_SCHEMA_VERSION)"
        }
        require(dto.metadata.fps > 0f) { "metadata.fps debe ser > 0 (era ${dto.metadata.fps})" }
        require(dto.scoring.similarityScale > 0.0) {
            "scoring.similarityScale debe ser > 0 (era ${dto.scoring.similarityScale})"
        }
        require(dto.referenceTrajectory.isNotEmpty()) {
            "referenceTrajectory vacía en '${dto.exerciseId}'"
        }

        val definitions = dto.angleDefinitions.map { it.toDomain(dto.exerciseId) }
        // Valida driverAngleIndex en rango y lista no vacía (init de ExerciseDefinition).
        val definition = ExerciseDefinition(
            exerciseId = dto.exerciseId,
            angleDefinitions = definitions,
            driverAngleIndex = dto.driverAngleIndex
        )

        val d = definition.dimensions
        val frameMs = 1000.0 / dto.metadata.fps
        val trajectory = dto.referenceTrajectory.mapIndexed { i, row ->
            require(row.size == d) {
                "referenceTrajectory[$i] tiene ${row.size} ángulos pero hay $d " +
                    "angleDefinitions en '${dto.exerciseId}' — pipelines desalineados"
            }
            require(row.all { it.isFinite() }) {
                "referenceTrajectory[$i] contiene valores no finitos en '${dto.exerciseId}'"
            }
            AngleFrame(
                angles = row.toFloatArray(),
                valid = BooleanArray(d) { true },
                timestampMs = (i * frameMs).toLong()
            )
        }

        return ExerciseReference(
            definition = definition,
            trajectory = trajectory,
            metadata = ReferenceMetadata(
                fps = dto.metadata.fps,
                version = dto.metadata.version,
                source = dto.metadata.source
            ),
            scoring = ScoringConfig(
                similarityScale = dto.scoring.similarityScale,
                goodThreshold = dto.scoring.goodThreshold
            )
        )
    }

    private fun AngleDefinitionDto.toDomain(exerciseId: String): AngleDefinition {
        fun keypoint(name: String, field: String): KeypointType =
            runCatching { KeypointType.valueOf(name) }.getOrElse {
                throw IllegalArgumentException(
                    "Keypoint '$name' ($field del ángulo '${this.name}' en '$exerciseId') " +
                        "no existe en KeypointType — nombres válidos: " +
                        KeypointType.entries.joinToString()
                )
            }
        return AngleDefinition(
            name = name,
            pointA = keypoint(pointA, "pointA"),
            vertex = keypoint(vertex, "vertex"),
            pointC = keypoint(pointC, "pointC"),
            weight = weight
        )
    }
}

/**
 * Serializa una referencia al esquema v1 usando los MISMOS DTOs que [ReferenceJsonParser]:
 * la simetría write→parse queda garantizada por construcción (ver test de round-trip).
 */
object ReferenceJsonWriter {

    private val json = Json { prettyPrint = true }

    fun write(reference: ExerciseReference): String {
        reference.trajectory.forEachIndexed { i, frame ->
            // El esquema no tiene máscara de validez: el parser marca todo como válido.
            require(frame.valid.all { it }) {
                "trajectory[$i] tiene dimensiones inválidas — una referencia debe " +
                    "exportarse solo desde frames con todos los keypoints confiables"
            }
        }
        val dto = ReferenceFileDto(
            schemaVersion = ReferenceJsonParser.SUPPORTED_SCHEMA_VERSION,
            exerciseId = reference.definition.exerciseId,
            angleDefinitions = reference.definition.angleDefinitions.map {
                AngleDefinitionDto(
                    name = it.name,
                    pointA = it.pointA.name,
                    vertex = it.vertex.name,
                    pointC = it.pointC.name,
                    weight = it.weight
                )
            },
            driverAngleIndex = reference.definition.driverAngleIndex,
            referenceTrajectory = reference.trajectory.map { it.angles.toList() },
            metadata = MetadataDto(
                fps = reference.metadata.fps,
                version = reference.metadata.version,
                source = reference.metadata.source
            ),
            scoring = ScoringDto(
                similarityScale = reference.scoring.similarityScale,
                goodThreshold = reference.scoring.goodThreshold
            )
        )
        return json.encodeToString(ReferenceFileDto.serializer(), dto)
    }
}
