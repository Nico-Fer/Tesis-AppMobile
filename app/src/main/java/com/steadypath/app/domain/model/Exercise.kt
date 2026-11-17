package com.steadypath.app.domain.model

data class Exercise(
    val idEjercicio: String,
    val nombre: String,
    val descripcion: String,
    val urlVideoExplicativo: String?,
    val zonasAfectadas: List<String>,
    val nivelDificultad: String,
    val duracionMinutos: Int,
    // Slug de MovementPatternCatalog; null = sin patrón ⇒ no se puede grabar referencia.
    val patronMovimiento: String? = null
)
