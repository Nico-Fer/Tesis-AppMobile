package com.steadypath.app.domain.model

/**
 * Preferencia de tamaño de texto del paciente. Por ahora solo se guarda: todavía no
 * hay un reescalado real de la tipografía de la app conectado a este valor (queda
 * para una fase aparte que toque el tema global de Compose).
 */
enum class TextScale(val etiqueta: String) {
    NORMAL("Normal"),
    GRANDE("Grande"),
    MUY_GRANDE("Muy grande")
}
