package com.steadypath.app.presentation.components.bodydiagram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del mapeo zona → región del arte anatómico.
 *
 * Se tocan solo `id` y `pathData` (strings puros). Nada de `BodyRegion.path` ni
 * `BodyView.bounds`: eso parsea con `PathParser`, que necesita `android.graphics.Path`
 * y no existe en un unit test de JVM.
 */
class BodyZoneMappingTest {

    /** Ids de todas las regiones del arte. */
    private val artworkIds: Set<String> =
        (FRONT_REGIONS + BACK_REGIONS).map { it.id }.toSet()

    /**
     * Las 19 etiquetas que produce `DtoMappers.ZONAS` a partir del enum `ZonaCorporal`
     * del backend (0-18), con tilde y mayúscula tal como llegan a la UI.
     */
    private val backendLabels = listOf(
        "Hombros", "Brazos", "Lumbares", "Rodillas", "Piernas",
        "Pecho", "Abdominales", "Bíceps", "Antebrazos", "Cuádriceps",
        "Aductores", "Abductores", "Tibiales",
        "Trapecios", "Dorsales", "Tríceps", "Glúteos", "Isquiotibiales", "Gemelos"
    )

    @Test
    fun `el arte no tiene ids repetidos`() {
        val all = (FRONT_REGIONS + BACK_REGIONS).map { it.id }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun `ninguna region del arte tiene el path vacio`() {
        (FRONT_REGIONS + BACK_REGIONS).forEach { region ->
            assertTrue(
                "La región ${region.id} no tiene pathData",
                region.pathData.isNotBlank()
            )
        }
    }

    @Test
    fun `toda region mapeada existe en el arte`() {
        // Este es el test que importa: atrapa typos en BodyZoneMapping, que de otro
        // modo se manifiestan como un músculo que simplemente no se ilumina.
        KNOWN_ZONE_LABELS.forEach { label ->
            val huerfanas = regionsFor(listOf(label)) - artworkIds
            assertTrue(
                "La zona '$label' apunta a regiones inexistentes: $huerfanas",
                huerfanas.isEmpty()
            )
        }
    }

    @Test
    fun `las 19 zonas del backend resuelven a alguna region`() {
        backendLabels.forEach { label ->
            assertTrue(
                "La zona '$label' del enum ZonaCorporal no resalta nada",
                regionsFor(listOf(label)).isNotEmpty()
            )
        }
    }

    @Test
    fun `las tildes y mayusculas no afectan el matcheo`() {
        assertEquals(regionsFor(listOf("cuadriceps")), regionsFor(listOf("Cuádriceps")))
        assertEquals(regionsFor(listOf("gluteos")), regionsFor(listOf("  GLÚTEOS  ")))
        assertEquals(regionsFor(listOf("biceps")), regionsFor(listOf("Bíceps")))
    }

    @Test
    fun `una zona bilateral resalta ambos lados`() {
        assertEquals(setOf("quads-left", "quads-right"), regionsFor(listOf("Cuádriceps")))
    }

    @Test
    fun `la zona amplia Piernas expande a los seis grupos`() {
        val piernas = regionsFor(listOf("Piernas"))
        listOf(
            "quads-left", "adductors-left", "gluteus-medius-left",
            "tibialis-anterior-left", "hamstrings-medial-left", "calves-soleus-left"
        ).forEach {
            assertTrue("Piernas debería incluir $it", it in piernas)
        }
    }

    @Test
    fun `la zona amplia Brazos expande a biceps triceps y antebrazos`() {
        val brazos = regionsFor(listOf("Brazos"))
        assertTrue(regionsFor(listOf("Bíceps")).all { it in brazos })
        assertTrue(regionsFor(listOf("Tríceps")).all { it in brazos })
        assertTrue(regionsFor(listOf("Antebrazos")).all { it in brazos })
    }

    @Test
    fun `una zona desconocida se ignora sin romper`() {
        assertEquals(emptySet<String>(), regionsFor(listOf("Zona 42")))
        // Una etiqueta nueva del backend no debe tirar abajo el resto del resaltado.
        assertEquals(
            regionsFor(listOf("Gemelos")),
            regionsFor(listOf("Gemelos", "Zona 99"))
        )
    }

    @Test
    fun `varias zonas se acumulan sin duplicar`() {
        val combinado = regionsFor(listOf("Cuádriceps", "Gemelos", "Cuádriceps"))
        assertEquals(regionsFor(listOf("Cuádriceps")) + regionsFor(listOf("Gemelos")), combinado)
    }

    @Test
    fun `sin zonas no se resalta nada`() {
        assertEquals(emptySet<String>(), regionsFor(emptyList()))
    }
}
