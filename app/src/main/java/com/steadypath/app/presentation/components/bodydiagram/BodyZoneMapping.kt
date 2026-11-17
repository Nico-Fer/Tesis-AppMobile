package com.steadypath.app.presentation.components.bodydiagram

import java.text.Normalizer

/**
 * Traduce las zonas corporales del dominio a las regiones del arte anatómico.
 *
 * Las etiquetas llegan tal como las produce `DtoMappers.ZONAS` a partir del enum
 * `ZonaCorporal` del backend (0-18) — o sea, con tildes ("Bíceps", "Cuádriceps").
 * Todo el matcheo se hace sobre la forma normalizada, así que el acento no importa.
 */

/** Genera los ids izquierdo y derecho de una región bilateral. */
private fun sides(vararg bases: String): Set<String> =
    bases.flatMapTo(LinkedHashSet()) { listOf("$it-left", "$it-right") }

// ── Músculos específicos (enum ZonaCorporal 5-18) ────────────────────────────
private val HOMBROS = sides("shoulder-front", "shoulder-side", "deltoid-rear")
private val PECHO = sides("chest-upper", "chest-lower")
private val ABDOMINALES = sides("abs-upper", "abs-lower", "obliques")
private val BICEPS = sides("biceps")
private val ANTEBRAZOS = sides("forearm", "forearm-flexors", "forearm-extensors")
private val CUADRICEPS = sides("quads")
private val ADUCTORES = sides("adductors")

/**
 * El abductor principal de la cadera es el glúteo medio, que en este arte solo
 * existe en la vista posterior. Es la aproximación más cercana disponible.
 */
private val ABDUCTORES = sides("gluteus-medius")
private val TIBIALES = sides("tibialis-anterior")
private val TRAPECIOS = sides("traps-upper", "traps-mid", "traps-lower")
private val DORSALES = sides("lats-upper", "lats-mid", "lats-lower")
private val TRICEPS = sides("triceps-long", "triceps-lateral")
private val GLUTEOS = sides("gluteus-maximus")
private val ISQUIOTIBIALES = sides("hamstrings-medial", "hamstrings-lateral")
private val GEMELOS = sides("calves-gastroc-medial", "calves-gastroc-lateral", "calves-soleus")

// ── Zonas amplias legadas (enum ZonaCorporal 0-4) ────────────────────────────
private val LUMBARES = sides("lower-back-erectors", "lower-back-ql")
private val RODILLAS = sides("knee", "knee-back")
private val BRAZOS = BICEPS + TRICEPS + ANTEBRAZOS
private val PIERNAS = CUADRICEPS + ADUCTORES + ABDUCTORES + TIBIALES + ISQUIOTIBIALES + GEMELOS

/** Etiqueta normalizada (minúsculas, sin tildes) → regiones a resaltar. */
private val ZONE_REGIONS: Map<String, Set<String>> = mapOf(
    // Zonas amplias legadas (0-4)
    "hombros" to HOMBROS,
    "brazos" to BRAZOS,
    "lumbares" to LUMBARES,
    "rodillas" to RODILLAS,
    "piernas" to PIERNAS,
    // Músculos específicos — frente (5-12)
    "pecho" to PECHO,
    "abdominales" to ABDOMINALES,
    "biceps" to BICEPS,
    "antebrazos" to ANTEBRAZOS,
    "cuadriceps" to CUADRICEPS,
    "aductores" to ADUCTORES,
    "abductores" to ABDUCTORES,
    "tibiales" to TIBIALES,
    // Músculos específicos — espalda (13-18)
    "trapecios" to TRAPECIOS,
    "dorsales" to DORSALES,
    "triceps" to TRICEPS,
    "gluteos" to GLUTEOS,
    "isquiotibiales" to ISQUIOTIBIALES,
    "gemelos" to GEMELOS
)

/** Minúsculas y sin diacríticos, para que "Cuádriceps" y "cuadriceps" matcheen igual. */
internal fun normalizeLabel(label: String): String =
    Normalizer.normalize(label.trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

/**
 * Regiones del arte que corresponden a [zonas].
 *
 * Función pura: las etiquetas desconocidas se ignoran en silencio — el backend
 * puede sumar valores nuevos al enum y la pantalla no debe romperse por eso.
 */
internal fun regionsFor(zonas: List<String>): Set<String> =
    zonas.flatMapTo(LinkedHashSet()) { ZONE_REGIONS[normalizeLabel(it)].orEmpty() }

/** Solo para tests: todas las etiquetas de zona reconocidas. */
internal val KNOWN_ZONE_LABELS: Set<String> get() = ZONE_REGIONS.keys
