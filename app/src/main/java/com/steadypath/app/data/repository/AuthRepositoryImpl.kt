package com.steadypath.app.data.repository

import android.util.Log
import com.google.gson.Gson
import com.steadypath.app.data.auth.CognitoAuthService
import com.steadypath.app.data.auth.CognitoSignInResult
import com.steadypath.app.data.datasource.RehabApiService
import com.steadypath.app.data.datasource.SafeApiCaller
import com.steadypath.app.data.dto.AssignedProfessionalDto
import com.steadypath.app.data.dto.CalibrateOwnToleranceRequestDto
import com.steadypath.app.data.dto.ErrorResponseDto
import com.steadypath.app.data.dto.ProvisionRequestDto
import com.steadypath.app.data.dto.UpdatePatientProfileRequestDto
import com.steadypath.app.data.dto.UpdateProfessionalProfileRequestDto
import com.steadypath.app.data.local.LecturaConCache
import com.steadypath.app.data.mapper.toDomain
import com.steadypath.app.data.session.PendingRegistrationHolder
import com.steadypath.app.data.sync.ColaDeOperaciones
import com.steadypath.app.data.sync.EnvioDePendientes
import com.steadypath.app.data.sync.EstadoDeLaSesion
import com.steadypath.app.data.sync.MonitorDeConectividad
import com.steadypath.app.data.sync.OperacionPendiente
import com.steadypath.app.data.sync.Sincronizador
import com.steadypath.app.data.sync.TipoDeOperacion
import com.steadypath.app.data.session.UserScopedCache
import com.steadypath.app.domain.model.AccountAlreadyExistsException
import com.steadypath.app.domain.model.AssignedProfessional
import com.steadypath.app.domain.model.Invitation
import com.steadypath.app.domain.model.MisDatos
import com.steadypath.app.domain.model.NotProvisionedException
import com.steadypath.app.domain.model.PatientTolerance
import com.steadypath.app.domain.model.ProvisionData
import com.steadypath.app.domain.model.RegistrationResult
import com.steadypath.app.domain.model.SinConexionException
import com.steadypath.app.domain.model.User
import com.steadypath.app.domain.model.UserNotConfirmedException
import com.steadypath.app.domain.repository.AuthRepository
import com.steadypath.app.domain.repository.UserSessionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val cognito: CognitoAuthService,
    private val apiService: RehabApiService,
    private val sessionRepository: UserSessionRepository,
    private val pendingRegistration: PendingRegistrationHolder,
    private val userCaches: Set<@JvmSuppressWildcards UserScopedCache>,
    private val sincronizador: Sincronizador,
    private val caller: SafeApiCaller,
    private val cola: ColaDeOperaciones,
    private val monitor: MonitorDeConectividad,
    private val lectura: LecturaConCache,
    private val sesion: EstadoDeLaSesion,
    private val dispatcher: CoroutineDispatcher,
    private val gson: Gson
) : AuthRepository {

    override suspend fun signIn(email: String, contrasena: String): Result<User> =
        withContext(dispatcher) {
            runCatching {
                // Identidad que había antes de entrar: queda una cuando la sesión venció
                // sin que nadie la cerrara, y puede no ser la de quien entra ahora.
                val anterior = sessionRepository.get()?.idUsuario
                when (cognito.signIn(email, contrasena)) {
                    CognitoSignInResult.NeedsConfirmation -> throw UserNotConfirmedException()
                    CognitoSignInResult.NotSignedIn ->
                        throw Exception("No se pudo iniciar sesión")
                    CognitoSignInResult.SignedIn -> fetchAndStoreMe().also { user ->
                        if (anterior != null && anterior != user.idUsuario) vaciarCachesDelUsuario()
                        sincronizador.alIniciarSesion()
                    }
                }
            }.recoverCatching { error ->
                // Iniciar sesión necesita red sí o sí: sin sesión guardada no hay contra qué
                // validar la contraseña. El monitor de red es solo una pista, por eso se
                // intenta igual y recién ante el fallo se explica el motivo en vez de
                // mostrar el error técnico de Cognito.
                val esDeLaCuenta = error is UserNotConfirmedException || error is NotProvisionedException
                throw if (!esDeLaCuenta && !monitor.hayRed()) {
                    SinConexionException(MENSAJE_INGRESO_SIN_RED)
                } else {
                    error
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

    /**
     * Con una sesión guardada en el teléfono se entra aunque no haya red: la identidad
     * local alcanza para mostrar lo guardado y lo que se haga queda en la cola de salida.
     * No se valida contraseña (no se guarda ni ella ni un derivado): lo que habilita el
     * ingreso es que esa persona ya inició sesión acá y no la cerró.
     */
    override suspend fun restoreSession(): User? = withContext(dispatcher) {
        if (!cognito.isSignedIn()) return@withContext null
        val local = sessionRepository.get()
        if (local != null && !monitor.hayRed()) return@withContext local
        // /me refresca la identidad; si la red falla, vale la copia local. Con copia no se
        // deja a la persona mirando la pantalla de inicio más de unos segundos.
        val respuesta = if (local != null) {
            withTimeoutOrNull(ESPERA_DE_IDENTIDAD_MS) { runCatching { fetchAndStoreMe() } }
                ?: return@withContext local
        } else {
            runCatching { fetchAndStoreMe() }
        }
        respuesta.getOrElse { e ->
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
                local
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
     * a que las dos divergieran. Lo único que no comparte es el último envío de las sesiones
     * pendientes, que no tiene sentido sobre una cuenta que ya no existe.
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
            .mapCatching { cerrarSesionLocal().getOrThrow() }
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

    /**
     * Antes de perder el token se intenta enviar una última vez lo que quedó sin enviar. Si
     * no hay red, esas sesiones se descartan con el resto de los datos locales del usuario:
     * dejarlas en el teléfono expondría datos de salud a quien entre después.
     */
    override suspend fun signOut(): Result<Unit> {
        withTimeoutOrNull(ENVIO_ANTES_DE_SALIR_MS) {
            runCatching { sincronizador.enviarPendientes() }
                .onFailure { Log.w(TAG, "No se pudieron enviar las sesiones pendientes", it) }
        }
        return cerrarSesionLocal()
    }

    private suspend fun cerrarSesionLocal(): Result<Unit> = withContext(dispatcher) {
        runCatching {
            cognito.signOut()
            pendingRegistration.clear()
            sessionRepository.clear()
            vaciarCachesDelUsuario()
            sesion.vigente()
        }.onFailure { Log.e(TAG, "signOut() falló", it) }
    }

    /** Un caché que falla no debe dejar al usuario a medio desloguear. */
    private suspend fun vaciarCachesDelUsuario() {
        userCaches.forEach { cache ->
            runCatching { cache.clear() }
                .onFailure { Log.w(TAG, "No se pudo limpiar ${cache::class.simpleName}", it) }
        }
    }

    override suspend fun updatePassword(oldPassword: String, newPassword: String): Result<Unit> =
        withContext(dispatcher) {
            runCatching { cognito.updatePassword(oldPassword, newPassword) }
                .onFailure { Log.e(TAG, "updatePassword() falló", it) }
        }

    override suspend fun requestPasswordReset(email: String): Result<Unit> =
        withContext(dispatcher) {
            runCatching { cognito.resetPassword(email) }
                .onFailure { Log.e(TAG, "requestPasswordReset() falló", it) }
        }

    override suspend fun confirmPasswordReset(
        email: String,
        code: String,
        newPassword: String
    ): Result<Unit> = withContext(dispatcher) {
        runCatching { cognito.confirmResetPassword(email, code, newPassword) }
            .onFailure { Log.e(TAG, "confirmPasswordReset() falló", it) }
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

    /**
     * Sin red el cambio se aplica a la identidad guardada en el teléfono y queda en la cola
     * de salida. Pasa por [SafeApiCaller] para distinguir un corte (se encola) de un
     * rechazo del servidor (se informa).
     */
    override suspend fun updatePatientProfile(nombre: String, telefono: String?): Result<User> =
        withContext(dispatcher) {
            val solicitud = UpdatePatientProfileRequestDto(nombre, telefono)
            val resultado = caller.execute({ apiService.updatePatientProfile(solicitud) }) { it.toDomain() }
            val error = resultado.exceptionOrNull()
            if (error == null) {
                resultado.onSuccess { sessionRepository.save(it) }
                return@withContext resultado
            }
            Log.e(TAG, "updatePatientProfile() falló", error)
            val local = sessionRepository.get()
            if (!EnvioDePendientes.esReintentable(error) || local == null) return@withContext resultado

            val editado = local.copy(nombre = nombre, telefono = telefono)
            sessionRepository.save(editado)
            cola.agregar(
                OperacionPendiente(
                    id = UUID.randomUUID().toString(),
                    idUsuario = local.idUsuario,
                    tipo = TipoDeOperacion.PERFIL_PACIENTE,
                    destino = DESTINO_PERFIL,
                    cargaJson = gson.toJson(solicitud)
                )
            )
            Result.success(editado)
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

    override suspend fun revokeConsent(): Result<User> =
        updateConsent("revokeConsent") { apiService.revokeConsent() }

    override suspend fun grantConsent(): Result<User> =
        updateConsent("grantConsent") { apiService.grantConsent() }

    /** Guarda el usuario devuelto: el inicio y el detalle del ejercicio leen el estado de ahí. */
    private suspend fun updateConsent(
        nombre: String,
        call: suspend () -> retrofit2.Response<com.steadypath.app.data.dto.UserResponseDto>
    ): Result<User> = withContext(dispatcher) {
        runCatching {
            val response = call()
            if (!response.isSuccessful) {
                throw Exception(parseError(response.errorBody()?.string()))
            }
            val user = response.body()?.toDomain()
                ?: throw Exception("Respuesta vacía del servidor")
            sessionRepository.save(user)
            user
        }.onFailure { Log.e(TAG, "$nombre() falló", it) }
    }

    /** Con copia local: el perfil del paciente lo muestra y no debe quedar en error sin red. */
    override suspend fun getMyProfessional(): Result<AssignedProfessional> = withContext(dispatcher) {
        val idUsuario = sessionRepository.get()?.idUsuario.orEmpty()
        val clave = "miProfesional:$idUsuario"
        val guardado = runCatching {
            lectura.leerInstantanea<AssignedProfessionalDto>(clave, AssignedProfessionalDto::class.java)
        }.getOrNull()
        if (guardado != null && !monitor.hayRed()) return@withContext Result.success(guardado.toDomain())

        val respuesta = caller.execute({ apiService.getMyProfessional() }) { it }
        respuesta.fold(
            onSuccess = { dto ->
                runCatching { lectura.guardarInstantanea(clave, dto) }
                Result.success(dto.toDomain())
            },
            onFailure = { error ->
                Log.e(TAG, "getMyProfessional() falló", error)
                if (guardado != null && EnvioDePendientes.esReintentable(error)) {
                    Result.success(guardado.toDomain())
                } else {
                    Result.failure(error)
                }
            }
        )
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
        val delServidor = response.body()?.toDomain()
            ?: throw Exception("Respuesta vacía del servidor")
        val user = conEdicionPendiente(delServidor)
        sessionRepository.save(user)
        return user
    }

    /**
     * Si la persona editó su perfil sin red y eso todavía no subió, el servidor devuelve
     * los datos viejos: se conservan los que ella escribió hasta que la cola los envíe,
     * para que no vea su cambio deshacerse al volver la conexión.
     */
    private suspend fun conEdicionPendiente(user: User): User {
        val carga = runCatching {
            cola.cargaPendiente(user.idUsuario, TipoDeOperacion.PERFIL_PACIENTE, DESTINO_PERFIL)
        }.getOrNull() ?: return user
        val pendiente = runCatching {
            gson.fromJson(carga, UpdatePatientProfileRequestDto::class.java)
        }.getOrNull() ?: return user
        return user.copy(nombre = pendiente.nombre, telefono = pendiente.telefono)
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
        /** Tope del último envío al cerrar sesión: sin red, no demora la salida. */
        private const val ENVIO_ANTES_DE_SALIR_MS = 5_000L

        /** Cuánto se espera a /me al arrancar antes de entrar con la identidad guardada. */
        private const val ESPERA_DE_IDENTIDAD_MS = 5_000L

        /** El perfil es uno solo por usuario: todas las ediciones recaen sobre lo mismo. */
        private const val DESTINO_PERFIL = "perfil"

        private const val MENSAJE_INGRESO_SIN_RED =
            "Necesitás conexión a internet para iniciar sesión en este dispositivo."
    }
}
