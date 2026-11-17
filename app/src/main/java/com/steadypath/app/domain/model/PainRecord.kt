package com.steadypath.app.domain.model

data class PainRecord(
    val idSesion: String,
    val fecha: String,
    val idEjercicio: String,
    val nombreEjercicio: String,
    val escalaEVA: Int
)
