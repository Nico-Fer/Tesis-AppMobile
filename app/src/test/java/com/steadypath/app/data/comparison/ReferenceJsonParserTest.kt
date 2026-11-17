package com.steadypath.app.data.comparison

import com.steadypath.app.domain.comparison.ReferenceAlignment
import com.steadypath.app.domain.model.KeypointType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ReferenceJsonParserTest {

    private fun referenceJson(
        schemaVersion: Int = 1,
        exerciseId: String = "ex-1",
        angleDefinitions: String = """
            [
              {"name": "codo_izq", "pointA": "LEFT_SHOULDER", "vertex": "LEFT_ELBOW", "pointC": "LEFT_WRIST"},
              {"name": "codo_der", "pointA": "RIGHT_SHOULDER", "vertex": "RIGHT_ELBOW", "pointC": "RIGHT_WRIST", "weight": 2.0}
            ]
        """.trimIndent(),
        driverAngleIndex: Int = 0,
        trajectory: String = "[[0.5, 1.0], [0.7, 1.2], [0.5, 1.0]]",
        fps: Float = 20f
    ): String = """
        {
          "schemaVersion": $schemaVersion,
          "exerciseId": "$exerciseId",
          "angleDefinitions": $angleDefinitions,
          "driverAngleIndex": $driverAngleIndex,
          "referenceTrajectory": $trajectory,
          "metadata": {"fps": $fps, "version": "1.0", "source": "test"},
          "scoring": {"similarityScale": 0.5, "goodThreshold": 0.7}
        }
    """.trimIndent()

    private fun assertFailsWithMessage(fragment: String, block: () -> Unit) {
        try {
            block()
            fail("Esperaba IllegalArgumentException con '$fragment'")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "Mensaje '${e.message}' no contiene '$fragment'",
                e.message.orEmpty().contains(fragment)
            )
        }
    }

    // ── Camino feliz ─────────────────────────────────────────────────────────

    @Test
    fun `parsea una referencia valida completa`() {
        val ref = ReferenceJsonParser.parse(referenceJson())

        assertEquals("ex-1", ref.definition.exerciseId)
        assertEquals(2, ref.definition.dimensions)
        assertEquals(0, ref.definition.driverAngleIndex)
        assertEquals(KeypointType.LEFT_ELBOW, ref.definition.angleDefinitions[0].vertex)
        assertEquals(2f, ref.definition.angleDefinitions[1].weight, 0f)
        assertEquals(1f, ref.definition.angleDefinitions[0].weight, 0f) // default
        assertEquals(3, ref.trajectory.size)
        assertEquals(0.7f, ref.trajectory[1].angles[0], 1e-6f)
        assertTrue(ref.trajectory.all { frame -> frame.valid.all { it } })
        assertEquals(0.5, ref.scoring.similarityScale, 0.0)
        assertEquals(0.7f, ref.scoring.goodThreshold, 0f)
        assertEquals(20f, ref.metadata.fps, 0f)
    }

    @Test
    fun `timestamps sinteticos derivados del fps`() {
        val ref = ReferenceJsonParser.parse(referenceJson(fps = 20f))

        assertEquals(0L, ref.trajectory[0].timestampMs)
        assertEquals(50L, ref.trajectory[1].timestampMs)
        assertEquals(100L, ref.trajectory[2].timestampMs)
    }

    // ── Validación ruidosa ───────────────────────────────────────────────────

    @Test
    fun `version de esquema no soportada falla`() {
        assertFailsWithMessage("schemaVersion=2") {
            ReferenceJsonParser.parse(referenceJson(schemaVersion = 2))
        }
    }

    @Test
    fun `fila con dimension distinta a las definiciones falla`() {
        // Invariante del mismo-pipeline: 3 ángulos por fila vs 2 definiciones.
        assertFailsWithMessage("pipelines desalineados") {
            ReferenceJsonParser.parse(
                referenceJson(trajectory = "[[0.5, 1.0], [0.7, 1.2, 0.9], [0.5, 1.0]]")
            )
        }
    }

    @Test
    fun `nombre de keypoint desconocido falla con mensaje accionable`() {
        assertFailsWithMessage("LEFT_ELBOWW") {
            ReferenceJsonParser.parse(
                referenceJson(
                    angleDefinitions = """
                        [{"name": "codo", "pointA": "LEFT_SHOULDER", "vertex": "LEFT_ELBOWW", "pointC": "LEFT_WRIST"}]
                    """.trimIndent(),
                    trajectory = "[[0.5], [0.7]]"
                )
            )
        }
    }

    @Test
    fun `driverAngleIndex fuera de rango falla`() {
        assertFailsWithMessage("driverAngleIndex") {
            ReferenceJsonParser.parse(referenceJson(driverAngleIndex = 5))
        }
    }

    @Test
    fun `trayectoria vacia falla`() {
        assertFailsWithMessage("vacía") {
            ReferenceJsonParser.parse(referenceJson(trajectory = "[]"))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `valores no finitos en la trayectoria fallan`() {
        // kotlinx ya rechaza no-finitos al decodificar (SerializationException es
        // IllegalArgumentException); el require del parser es la segunda defensa.
        ReferenceJsonParser.parse(
            referenceJson(trajectory = "[[0.5, 1.0], [1e39, 1.2], [0.5, 1.0]]")
        )
    }

    @Test(expected = Exception::class)
    fun `json malformado falla`() {
        ReferenceJsonParser.parse("{ esto no es json valido")
    }

    // ── Asset de ejemplo ─────────────────────────────────────────────────────

    @Test
    fun `el asset de ejemplo del repo es valido`() {
        // Falla acá antes que en runtime si alguien rompe el JSON de ejemplo.
        val stream = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("references/sample-exercise.json")
                ?: run {
                    // Fallback: leer directo del árbol de assets del módulo.
                    val file = java.io.File("src/main/assets/references/sample-exercise.json")
                    if (file.exists()) file.inputStream() else null
                }
        ) { "No se encontró el asset de ejemplo" }

        val ref = stream.bufferedReader().use { it.readText() }.let(ReferenceJsonParser::parse)

        assertEquals("sample-exercise", ref.definition.exerciseId)
        assertEquals(2, ref.definition.dimensions)
        assertEquals(41, ref.trajectory.size)
        // Valle→pico→valle sobre el conductor, como recorta el segmentador.
        val driver = ref.trajectory.map { it.angles[0] }
        assertTrue(driver.first() < driver[20] && driver.last() < driver[20])
    }

    @Test
    fun `todos los assets de referencias del repo son validos`() {
        // Red de seguridad: una referencia mal copiada rompe la build, no el runtime.
        val dir = java.io.File("src/main/assets/references")
        val files = dir.listFiles { f -> f.extension == "json" }.orEmpty()
        assertTrue("No hay referencias en ${dir.absolutePath}", files.isNotEmpty())

        for (file in files) {
            val ref = try {
                ReferenceJsonParser.parse(file.readText())
            } catch (e: IllegalArgumentException) {
                fail("Referencia inválida '${file.name}': ${e.message}")
                return
            }
            assertTrue(
                "El nombre del archivo debe ser el exerciseId (así lo busca el repositorio); " +
                    "archivo='${file.nameWithoutExtension}' vs exerciseId='${ref.definition.exerciseId}'",
                file.nameWithoutExtension.equals(ref.definition.exerciseId, ignoreCase = true)
            )
            // Un ciclo del conductor con excursión útil, en cualquier anclaje: el
            // comparador rota al valle en carga (ReferenceAlignment), así que acá solo
            // importa que tras alinear quede un valle→pico→valle con prominencia real.
            val profile = ReferenceAlignment.profileOf(ref.trajectory, ref.definition.driverAngleIndex)
            assertTrue(
                "'${file.name}': amplitud del conductor al nivel del ruido de pose " +
                    "(${profile.amplitudeRad} rad)",
                profile.amplitudeRad > 0.1f
            )
            val aligned = ReferenceAlignment.alignToDriverValley(ref.trajectory, profile.valleyIndex)
            // Des-envuelto: un conductor que cruza ±π (extensión de rodilla) invalida
            // cualquier aritmética sobre los valores crudos.
            val driver = ReferenceAlignment.unwrappedDriver(aligned, ref.definition.driverAngleIndex)
                .filter { !it.isNaN() }
            val peak = driver.max()
            val prominence = peak - maxOf(driver.first(), driver.last())
            // Relativa a la amplitud del ejercicio: el ruido de meseta en referencias
            // reales deja los extremos algo por encima del mínimo global.
            assertTrue(
                "'${file.name}': el conductor alineado no describe un ciclo valle→pico→valle " +
                    "(primero=${driver.first()}, pico=$peak, último=${driver.last()})",
                prominence > 0.5f * profile.amplitudeRad
            )
        }
    }
}
