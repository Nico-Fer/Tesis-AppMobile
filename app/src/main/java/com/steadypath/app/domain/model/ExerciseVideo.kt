package com.steadypath.app.domain.model

/**
 * Video explicativo de un ejercicio. La URL viene firmada de S3 y vence a los
 * 15 minutos: no cachear, pedir de nuevo al entrar a la pantalla.
 */
data class ExerciseVideo(
    val nombreArchivo: String,
    val url: String
)
