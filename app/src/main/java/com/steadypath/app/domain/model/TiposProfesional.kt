package com.steadypath.app.domain.model

/** Opciones del selector "Tipo de profesional" (registro y edición de perfil). */
object TiposProfesional {
    const val FAMILIAR = "Familiar"
    val OPCIONES = listOf(
        "Kinesiólogo/a",
        "Fisioterapeuta",
        "Médico/a",
        "Enfermero/a",
        "Terapeuta Ocupacional",
        FAMILIAR
    )
}
