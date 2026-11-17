package com.steadypath.app.presentation.screens.calibration

/**
 * Decide qué aviso hablado dar mientras la checklist de encuadre no se cumple. Es pura (el
 * reloj entra por parámetro) para testearla en JVM.
 *
 * Reglas:
 * - Se nombra un solo problema por vez, en el orden de la checklist: cuerpo, luz, estabilidad.
 *   Arreglar el encuadre suele mover el teléfono, así que no tiene sentido pedir que lo dejen
 *   quieto antes de que la persona entre en cuadro.
 * - El problema tiene que sostenerse [esperaMs] seguidos: "cuerpo visible" parpadea mientras
 *   la persona se acomoda y no hay que hablarle en cada parpadeo.
 * - El mismo aviso no se repite antes de [repeticionMs], y entre dos avisos distintos pasan al
 *   menos [silencioMinimoMs], para dar tiempo a corregir sin aturdir.
 */
class GuiaDeEncuadre(
    private val esperaMs: Long = ESPERA_MS,
    private val repeticionMs: Long = REPETICION_MS,
    private val silencioMinimoMs: Long = SILENCIO_MINIMO_MS
) {
    enum class Problema(val aviso: String) {
        CUERPO("No se ve todo tu cuerpo. Alejate un poco o acomodá el teléfono."),
        LUZ("Hay poca luz. Prendé una luz o buscá un lugar más claro."),
        MOVIMIENTO("El teléfono se mueve. Dejalo quieto.")
    }

    private var problemaActual: Problema? = null
    private var desdeMs = 0L
    private var ultimoDicho: Problema? = null
    private var ultimoAvisoMs: Long? = null

    /** Devuelve el aviso a decir ahora, o `null` si no corresponde hablar. */
    fun aviso(cuerpoVisible: Boolean, buenaIluminacion: Boolean, camaraEstable: Boolean, ahoraMs: Long): String? {
        val problema = when {
            !cuerpoVisible -> Problema.CUERPO
            !buenaIluminacion -> Problema.LUZ
            !camaraEstable -> Problema.MOVIMIENTO
            else -> null
        }
        if (problema != problemaActual) {
            problemaActual = problema
            desdeMs = ahoraMs
        }
        if (problema == null || ahoraMs - desdeMs < esperaMs) return null

        val ultimo = ultimoAvisoMs
        if (ultimo != null) {
            val pausa = if (problema == ultimoDicho) repeticionMs else silencioMinimoMs
            if (ahoraMs - ultimo < pausa) return null
        }
        ultimoDicho = problema
        ultimoAvisoMs = ahoraMs
        return problema.aviso
    }

    /** Olvida el problema en curso (por ejemplo, al abrir la ayuda); no el historial de avisos. */
    fun pausar() {
        problemaActual = null
    }

    companion object {
        const val ESPERA_MS = 3_000L
        const val REPETICION_MS = 10_000L
        const val SILENCIO_MINIMO_MS = 4_000L
    }
}
