package com.steadypath.app.data.repository

import android.util.Log
import com.google.gson.Gson
import com.steadypath.app.data.auth.CognitoAuthService
import com.steadypath.app.data.auth.CognitoSignInResult
import com.steadypath.app.data.datasource.RehabApiService
import com.steadypath.app.data.dto.CalibrateOwnToleranceRequestDto
import com.steadypath.app.data.dto.ErrorResponseDto
import com.steadypath.app.data.dto.ProvisionRequestDto
import com.steadypath.app.data.dto.UpdatePatientProfileRequestDto
import com.steadypath.app.data.dto.UpdateProfessionalProfileRequestDto
import com.steadypath.app.data.mapper.toDomain
import com.steadypath.app.data.session.PendingRegistrationHolder
import com.steadypath.app.data.session.UserScopedCache
import com.steadypath.app.domain.model.AccountAlreadyExistsException
import com.steadypath.app.domain.model.AssignedProfessional
import com.steadypath.app.domain.model.Invitation
import com.steadypath.app.domain.model.MisDatos
import com.steadypath.app.domain.model.NotProvisionedException
import com.steadypath.app.domain.model.PatientTolerance
import com.steadypath.app.domain.model.ProvisionData
import com.steadypath.app.domain.model.RegistrationResult
import com.steadypath.app.domain.model.User
import com.steadypath.app.domain.model.UserNotConfirmedException
import com.steadypath.app.domain.repository.AuthRepository
import com.steadypath.app.domain.repository.UserSessionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val cognito: CognitoAuthService,
    private val apiService: RehabApiService,
    private val sessionRepository: UserSessionRepository,
    private val pendingRegistration: PendingRegistrationHolder,
    private val userCaches: Set<@JvmSuppressWildcards UserScopedCache>,
    private val dispatcher: CoroutineDispatcher,
    private val gson: Gson
) : AuthRepository {

    override suspend fun signIn(email: String, contrasena: String): Result<User> =
        withContext(dispatcher) {
            runCatching {
                when (cognito.signIn(email, contrasena)) {
                    CognitoSignInResult.NeedsConfirmation -> throw UserNotConfirmedException()
                    CognitoSignInResult.NotSignedIn ->
                        throw Exception("No se pudo iniciar sesión")
                    CognitoSignInResult.SignedIn -> fetchAndStoreMe()
                }
            }.onFailure { Log.e(TAG, "signIn() falló", it) }
        }

    override suspend fun register(
        email: String,
        contrasena: String,
        data: ProvisionData
    ): Result<RegistrationResult> = withContext(dispatcher) {
        runCatching {
            try {
                cognito.signUp(email, contrasena, data.nombre)
                pendingRegistration.store(email, contrasena, data)
                RegistrationResult.NeedsConfirmation
            } catch (e: AccountAlreadyExistsException) {
                // Cuenta ya en Cognito (p. ej. usuario de prueba): sign-in + provisión.
                signInAndProvision(email, contrasena, data)
            }
        }.onFailure { Log.e(TAG, "register() falló", it) }
    }

    override suspend fun confirmRegistration(
        email: String,
        code: String
    ): Result<RegistrationResult> = withContext(dispatcher) {
        runCatching {
            cognito.confirmSignUp(email, code)
            val password = pendingRegistration.password
            val data = pendingRegistration.provisionData
            if (pendingRegistration.email != email || password == null || data == null) {
                // Proceso reiniciado: sin credenciales en memoria, login manual.
                RegistrationResult.SignInRequired
            } else {
                signInAndProvision(email, password, data).also {
                    pendingRegistration.clear()
                }
            }
        }.onFailure { Log.e(TAG, "confirmRegistration() falló", it) }
    }

    override suspend fun resendCode(email: String): Result<Unit> =
        withContext(dispatcher) {
            runCatching { cognito.resendSignUpCode(email) }
                .onFailure { Log.e(TAG, "resendCode() falló", it) }
        }

    override suspend fun restoreSession(): User? = withContext(dispatcher) {
        if (!cognito.isSignedIn()) return@withContext null
        // /me refresca la identidad; si la red falla, vale la copia local.
        runCatching { fetchAndStoreMe() }.getOrElse { e ->
            if (e is NotProvisionedException) {
                // Autenticada en Cognito pero sin perfil en la base. No alcanza
                // con devolver null: habría que dejar una sesión abierta a la que
                // la pantalla de login no puede loguearse encima, y la persona
                // quedaría trabada. Se cierra para que el login (o "Registrate")
                // funcione.
                runCatching { cognito.signOut() }
                    .onFailure { Log.w(TAG, "No se pudo cerrar la sesión sin perfil", it) }
                null
            } else {
                sessionRepository.get()
            }
        }
    }

    /**
     * Baja de cuenta: primero en el servidor, después la limpieza local.
     *
     * El orden no es intercambiable. Si se limpiara la sesión antes, un fallo del servidor
     * dejaría a la persona deslogueada y con la cuenta intacta, sin manera de reintentar
     * desde el mismo lugar. Con este orden, un fallo la deja exactamente como estaba.
     *
     * La limpieza local reutiliza la de [signOut] a propósito: una baja exitosa deja la
     * aplicación en el mismo estado que un cierre de sesión, y duplicar esa lógica invitaba
     * a que las dos divergieran.
     */
    override suspend fun deleteAccount(): Result<Unit> = withContext(dispatcher) {
        runCatching {
            val respuesta = apiService.deleteOwnAccount()
            if (!respuesta.isSuccessful) {
                error(
                    "El servidor rechazó la baja de cuenta (${respuesta.code()}). " +
                        "No se eliminó ningún dato."
                )
            }
        }.onFailure { Log.e(TAG, "deleteAccount() falló", it) }
            .mapCatching { signOut().getOrThrow() }
    }

    override suspend fun getMyData(): Result<MisDatos> = withContext(dispatcher) {
        runCatching {
            val respuesta = apiService.getMisDatos()
            val dto = respuesta.body()
            if (!respuesta.isSuccessful || dto == null) {
                error("No se pudieron obtener tus datos (${respuesta.code()}).")
            }
            MisDatos(
                nombre = dto.perfil.nombre,
                email = dto.perfil.email,
                rol = dto.perfil.rol,
                telefono = dto.perfil.telefono,
                edad = dto.perfil.edad,
                matricula = dto.perfil.matricula,
                institucion = dto.perfil.institucion,
                fechaRegistro = dto.perfil.fechaRegistro,
                ultimaActividad = dto.ultimaActividad,
                bajaPorInactividadDesde = dto.bajaPorInactividadDesde,
                profesionalAsignado = dto.profesionalAsignado,
                sesiones = dto.sesiones,
                registrosDeDolor = dto.registrosDeDolor,
                rutinas = dto.rutinas,
                tolerancias = dto.tolerancias,
                referenciasDeMovimiento = dto.referenciasDeMovimiento,
                pacientesACargo = dto.pacientesACargo,
                plantillasDeRutina = dto.plantillasDeRutina
            )
        }.onFailure { Log.e(TAG, "getMyData() falló", it) }
    }

    override suspend fun signOut(): Result<Unit> = withContext(dispatcher) {
        runCatching {
            cognito.signOut()
            pendingRegistration.clear()
            sessionRepository.clear()
            // Un caché que falla no debe dejar al usuario a medio desloguear.
            userCaches.forEach { cache ->
                runCatching { cache.clear() }
                    .onFailure { Log.w(TAG, "No se pudo limpiar ${cache::class.simpleName}", it) }
            }
        }.onFailure { Log.e(TAG, "signOut() falló", it) }
    }

    override suspend fun updatePassword(oldPassword: String, newPassword: String): Result<Unit> =
        withContext(dispatcher) {
            runCatching { cognito.updatePassword(oldPassword, newPassword) }
                .onFailure { Log.e(TAG, "updatePassword() falló", it) }
        }

    override suspend fun updateProfessionalProfile(
        nombre: String,
        tipoProfesional: String?,
        matricula: String?,
        institucion: String?
    ): Result<User> = withContext(dispatcher) {
        runCatching {
            val response = apiService.updateProfessionalProfile(
                UpdateProfessionalProfileRequestDto(nombre, tipoProfesional, matricula, institucion)
            )
            if (!response.isSuccessful) {
                throw Exception(parseError(response.errorBody()?.string()))
            }
            val user = response.body()?.toDomain()
                ?: throw Exception("Respuesta vacía del servidor")
            sessionRepository.save(user)
            user
        }.onFailure { Log.e(TAG, "updateProfessionalProfile() falló", it) }
    }

    override suspend fun updatePatientProfile(nombre: String, telefono: String?): Result<User> =
        withContext(dispatcher) {
            runCatching {
                val response = apiService.updatePatientProfile(
                    UpdatePatientProfileRequestDto(nombre, telefono)
                )
                if (!response.isSuccessful) {
                    throw Exception(parseError(response.errorBody()?.string()))
                }
                val user = response.body()?.toDomain()
                    ?: throw Exception("Respuesta vacía del servidor")
                sessionRepository.save(user)
                user
            }.onFailure { Log.e(TAG, "updatePatientProfile() falló", it) }
        }

    override suspend fun unlinkProfessional(): Result<User> = withContext(dispatcher) {
        runCatching {
            val response = apiService.unlinkProfessional()
            if (!response.isSuccessful) {
                throw Exception(parseError(response.errorBody()?.string()))
            }
            val user = response.body()?.toDomain()
                ?: throw Exception("Respuesta vacía del servidor")
            sessionRepository.save(user)
            user
        }.onFailure { Log.e(TAG, "unlinkProfessional() falló", it) }
    }

    override suspend fun getMyProfessional(): Result<AssignedProfessional> = withContext(dispatcher) {
        runCatching {
            val response = apiService.getMyProfessional()
            if (!response.isSuccessful) {
                throw Exception(parseError(response.errorBody()?.string()))
            }
            response.body()?.toDomain() ?: throw Exception("Respuesta vacía del servidor")
        }.onFailure { Log.e(TAG, "getMyProfessional() falló", it) }
    }

    override suspend fun getMyInvitations(): Result<List<Invitation>> = withContext(dispatcher) {
        runCatching {
            val response = apiService.getMyInvitations()
            if (!response.isSuccessful) {
                throw Exception(parseError(response.errorBody()?.string()))
            }
            response.body()?.map { it.toDomain() } ?: throw Exception("Respuesta vacía del servidor")
        }.onFailure { Log.e(TAG, "getMyInvitations() falló", it) }
    }

    override suspend fun acceptInvitation(idInvitacion: String): Result<Invitation> = withContext(dispatcher) {
        runCatching {
            val response = apiService.acceptInvitation(idInvitacion)
            if (!response.isSuccessful) {
                throw Exception(parseError(response.errorBody()?.string()))
            }
            response.body()?.toDomain() ?: throw Exception("Respuesta vacía del servidor")
        }.onFailure { Log.e(TAG, "acceptInvitation() falló", it) }
    }

    override suspend fun rejectInvitation(idInvitacion: String): Result<Invitation> = withContext(dispatcher) {
        runCatching {
            val response = apiService.rejectInvitation(idInvitacion)
            if (!response.isSuccessful) {
                throw Exception(parseError(response.errorBody()?.string()))
            }
            response.body()?.toDomain() ?: throw Exception("Respuesta vacía del servidor")
        }.onFailure { Log.e(TAG, "rejectInvitation() falló", it) }
    }

    override suspend fun getOwnTolerance(idEjercicio: String): Result<PatientTolerance> =
        withContext(dispatcher) {
            runCatching {
                val response = apiService.getOwnTolerance(idEjercicio)
                if (!response.isSuccessful) {
                    throw Exception(parseError(response.errorBody()?.string()))
                }
                response.body()?.toDomain() ?: throw Exception("Respuesta vacía del servidor")
            }.onFailure { Log.e(TAG, "getOwnTolerance() falló", it) }
        }

    override suspend fun calibrateOwnTolerance(
        idEjercicio: String,
        anguloMinimo: Double,
        anguloMaximo: Double
    ): Result<PatientTolerance> = withContext(dispatcher) {
        runCatching {
            val response = apiService.calibrateOwnTolerance(
                CalibrateOwnToleranceRequestDto(idEjercicio, anguloMinimo, anguloMaximo)
            )
            if (!response.isSuccessful) {
                throw Exception(parseError(response.errorBody()?.string()))
            }
            response.body()?.toDomain() ?: throw Exception("Respuesta vacía del servidor")
        }.onFailure { Log.e(TAG, "calibrateOwnTolerance() falló", it) }
    }

    private suspend fun signInAndProvision(
        email: String,
        password: String,
        data: ProvisionData
    ): RegistrationResult {
        when (cognito.signIn(email, password)) {
            CognitoSignInResult.NeedsConfirmation -> {
                pendingRegistration.store(email, password, data)
                cognito.resendSignUpCode(email)
                return RegistrationResult.NeedsConfirmation
            }
            CognitoSignInResult.NotSignedIn ->
                throw Exception("No se pudo iniciar sesión")
            CognitoSignInResult.SignedIn -> Unit
        }
        return RegistrationResult.Completed(provision(data))
    }

    private suspend fun provision(data: ProvisionData): User {
        val response = apiService.provisionUser(
            ProvisionRequestDto(
                rol = data.rol,
                nombre = data.nombre,
                edad = data.edad,
                idProfesionalAsignado = data.idProfesionalAsignado,
                especialidad = data.especialidad,
                tipoProfesional = data.tipoProfesional,
                matricula = data.matricula,
                institucion = data.institucion
            )
        )
        if (!response.isSuccessful) {
            throw Exception(parseError(response.errorBody()?.string()))
        }
        val user = response.body()?.toDomain()
            ?: throw Exception("Respuesta vacía del servidor")
        sessionRepository.save(user)
        return user
    }

    private suspend fun fetchAndStoreMe(): User {
        val response = apiService.getMe()
        if (response.code() == 409) throw NotProvisionedException()
        if (!response.isSuccessful) {
            throw Exception(parseError(response.errorBody()?.string()))
        }
        val user = response.body()?.toDomain()
            ?: throw Exception("Respuesta vacía del servidor")
        sessionRepository.save(user)
        return user
    }

    private fun parseError(errorBody: String?): String {
        return try {
            errorBody?.let {
                gson.fromJson(it, ErrorResponseDto::class.java).message
            } ?: "Error desconocido"
        } catch (e: Exception) {
            Log.e(TAG, "parseError() no se pudo parsear el body: $errorBody", e)
            "Error de conexión"
        }
    }

    companion object {
        private const val TAG = "AuthRepository"
    }
}
