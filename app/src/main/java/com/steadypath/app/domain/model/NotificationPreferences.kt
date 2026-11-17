package com.steadypath.app.domain.model

/** Preferencias de notificaciones del profesional. Se guardan solo en el dispositivo. */
data class NotificationPreferences(
    val alertasDolorAlto: Boolean = true,
    val alertasPacientesInactivos: Boolean = true,
    val resumenSemanal: Boolean = false
)
