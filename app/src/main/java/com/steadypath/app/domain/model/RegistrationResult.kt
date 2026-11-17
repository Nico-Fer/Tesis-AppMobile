package com.steadypath.app.domain.model

sealed interface RegistrationResult {
    /** Ficha creada/vinculada y sesión iniciada. */
    data class Completed(val user: User) : RegistrationResult

    /** Cognito mandó un código al email; falta confirmarlo. */
    data object NeedsConfirmation : RegistrationResult

    /** Cuenta confirmada pero sin credenciales en memoria: pedir login manual. */
    data object SignInRequired : RegistrationResult
}
