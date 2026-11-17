package com.steadypath.app.domain.repository

import com.steadypath.app.domain.model.AssignedProfessional
import com.steadypath.app.domain.model.Invitation
import com.steadypath.app.domain.model.MisDatos
import com.steadypath.app.domain.model.PatientTolerance
import com.steadypath.app.domain.model.ProvisionData
import com.steadypath.app.domain.model.RegistrationResult
import com.steadypath.app.domain.model.User

interface AuthRepository : PatientToleranceRepository {

    /**
     * Sign-in contra Cognito + GET /me + persistencia de la identidad.
     * Falla con [com.steadypath.app.domain.model.NotProvisionedException] si la
     * cuenta no tiene ficha en el backend, y con
     * [com.steadypath.app.domain.model.UserNotConfirmedException] si falta
     * confirmar el código de email.
     */
    suspend fun signIn(email: String, contrasena: String): Result<User>

    /**
     * Registro completo: sign-up en Cognito + provisión en el backend.
     * Si la cuenta ya existía en Cognito, intenta sign-in con la contraseña
     * dada y provisiona (cubre a los usuarios de prueba sin ficha).
     */
    suspend fun register(
        email: String,
        contrasena: String,
        data: ProvisionData
    ): Result<RegistrationResult>

    /**
     * Confirma el código de email y, si hay credenciales del registro en
     * memoria, completa sign-in + provisión.
     */
    suspend fun confirmRegistration(email: String, code: String): Result<RegistrationResult>

    suspend fun resendCode(email: String): Result<Unit>

    /** Identidad al arrancar: null si no hay sesión Cognito válida. */
    suspend fun restoreSession(): User?

    /**
     * Cierra la sesión Cognito (revoca el refresh token) y borra la identidad y los
     * cachés del usuario. Falla si los tokens locales no se pudieron borrar: en ese
     * caso la sesión sigue abierta y no hay que navegar al login.
     */
    /**
     * Da de baja la cuenta y limpia la sesión local.
     *
     * Es irreversible y el backend la trata como todo o nada: si no puede dar de baja
     * también la identidad de acceso, no borra nada y devuelve error.
     */
    suspend fun deleteAccount(): Result<Unit>

    /** Resumen de los datos que el sistema guarda sobre el usuario autenticado. */
    suspend fun getMyData(): Result<MisDatos>

    suspend fun signOut(): Result<Unit>

    /** Cambia la contraseña del usuario autenticado; Cognito exige la actual para validarla. */
    suspend fun updatePassword(oldPassword: String, newPassword: String): Result<Unit>

    /** Edita nombre y datos profesionales del profesional autenticado. */
    suspend fun updateProfessionalProfile(
        nombre: String,
        tipoProfesional: String?,
        matricula: String?,
        institucion: String?
    ): Result<User>

    /** Edita nombre y teléfono del paciente autenticado. */
    suspend fun updatePatientProfile(nombre: String, telefono: String?): Result<User>

    /** Desvincula al paciente autenticado de su profesional asignado. */
    suspend fun unlinkProfessional(): Result<User>

    /** El profesional asignado al paciente autenticado, si tiene uno. */
    suspend fun getMyProfessional(): Result<AssignedProfessional>

    /** Invitaciones pendientes dirigidas al email del paciente autenticado. */
    suspend fun getMyInvitations(): Result<List<Invitation>>

    /** Acepta una invitación: recién ahí queda vinculado al profesional que invitó. */
    suspend fun acceptInvitation(idInvitacion: String): Result<Invitation>

    /** Rechaza una invitación. No cambia el profesional asignado. */
    suspend fun rejectInvitation(idInvitacion: String): Result<Invitation>

    /** Guarda el rango de ángulo que el paciente calibró en vivo para un ejercicio. */
    suspend fun calibrateOwnTolerance(
        idEjercicio: String,
        anguloMinimo: Double,
        anguloMaximo: Double
    ): Result<PatientTolerance>
}
