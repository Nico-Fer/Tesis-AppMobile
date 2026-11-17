package com.steadypath.app.domain.model

/** Preferencias de notificaciones del paciente. Se guardan solo en el dispositivo. */
data class PatientNotificationPreferences(
    val recordatorioRutinaDiaria: Boolean = true,
    val mensajesDelProfesional: Boolean = true,
    val resumenSemanal: Boolean = false
)
