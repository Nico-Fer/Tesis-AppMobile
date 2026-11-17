package com.steadypath.app.presentation.screens.forgot

/**
 * Validaciones del formulario de recuperación que se pueden resolver sin ir a
 * Cognito. Pura, para testearla en JVM.
 *
 * Solo se exige el largo mínimo y que las dos contraseñas coincidan, igual que
 * en el cambio de contraseña. El resto de la política (mayúscula, número,
 * símbolo) la aplica Cognito y su rechazo ya llega traducido.
 */
object ValidacionRecuperacion {

    const val LONGITUD_MINIMA = 8

    fun emailValido(email: String): Boolean {
        val limpio = email.trim()
        val arroba = limpio.indexOf('@')
        return arroba > 0 && limpio.indexOf('.', arroba) > arroba + 1 && !limpio.endsWith(".")
    }

    /** Devuelve el problema a mostrar, o null si se puede enviar. */
    fun problema(codigo: String, nueva: String, confirmacion: String): String? = when {
        codigo.isBlank() -> "Ingresá el código que te llegó por email"
        nueva.length < LONGITUD_MINIMA -> "La contraseña nueva debe tener al menos $LONGITUD_MINIMA caracteres"
        nueva != confirmacion -> "Las contraseñas nuevas no coinciden"
        else -> null
    }
}
