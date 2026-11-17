package com.steadypath.app.data.datasource

import android.util.Log
import com.google.gson.Gson
import com.steadypath.app.data.dto.ErrorResponseDto
import com.steadypath.app.data.sync.EstadoDeLaSesion
import com.steadypath.app.domain.model.ConflictoException
import com.steadypath.app.domain.model.NoAutenticadoException
import com.steadypath.app.domain.model.NoEncontradoException
import com.steadypath.app.domain.model.NotProvisionedException
import com.steadypath.app.domain.model.PermissionDeniedException
import com.steadypath.app.domain.model.ServidorNoDisponibleException
import com.steadypath.app.domain.model.SinConexionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Único punto de ejecución de llamadas a la API: parseo de `{message}`,
 * mapeo de 401/403/409/5xx a excepciones tipadas y captura de errores de red. Los tipos
 * permiten distinguir lo transitorio (sin red, servidor caído) de un rechazo definitivo.
 * Los repositorios no deben repetir este manejo.
 */
@Singleton
class SafeApiCaller @Inject constructor(
    private val gson: Gson,
    private val dispatcher: CoroutineDispatcher,
    private val sesion: EstadoDeLaSesion
) {

    fun <T : Any, R> flowCall(
        call: suspend () -> Response<T>,
        map: (T) -> R
    ): Flow<Result<R>> = flow { emit(execute(call, map)) }

    suspend fun <T : Any, R> execute(
        call: suspend () -> Response<T>,
        map: (T) -> R
    ): Result<R> = withContext(dispatcher) {
        try {
            val response = call()
            if (response.isSuccessful) {
                sesion.vigente()
                val body = response.body()
                if (body != null) {
                    Result.success(map(body))
                } else {
                    Result.failure(Exception("Respuesta vacía del servidor"))
                }
            } else {
                // El 401 llega acá recién después de que TokenAuthenticator intentó renovar.
                if (response.code() == 401) sesion.rechazada()
                Result.failure(mapHttpError(response.code(), response.errorBody()?.string()))
            }
        } catch (e: CancellationException) {
            // Una llamada interrumpida no es un error del servidor ni de la red: quien la
            // lanzó dejó de esperarla. Convertirla en un fallo común la haría pasar por un
            // rechazo definitivo ante la cola de salida, que borraría la operación.
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Sin conexión con el servidor", e)
            Result.failure(SinConexionException(MENSAJE_SIN_CONEXION))
        } catch (e: Exception) {
            Result.failure(Exception("Error de conexión: ${e.localizedMessage}"))
        }
    }

    private fun mapHttpError(code: Int, errorBody: String?): Exception {
        val message = parseError(errorBody)
        return when (code) {
            401 -> NoAutenticadoException(message)
            403 -> PermissionDeniedException(message)
            404 -> NoEncontradoException(message)
            409 -> excepcionPara409(message)
            in 500..599 -> ServidorNoDisponibleException(message)
            else -> Exception(message)
        }
    }

    private fun parseError(errorBody: String?): String = try {
        errorBody?.let { gson.fromJson(it, ErrorResponseDto::class.java).message }
            ?: "Error desconocido"
    } catch (_: Exception) {
        "Error de conexión"
    }

    private companion object {
        const val TAG = "SafeApiCaller"

        /**
         * Lo lee la persona en las acciones que necesitan red sí o sí. El detalle técnico
         * (host que no resuelve, tiempo agotado) no le sirve y queda en el log.
         */
        const val MENSAJE_SIN_CONEXION =
            "No hay conexión a internet. Probá de nuevo cuando vuelva."
    }
}

/**
 * El backend responde 409 por dos motivos distintos: la cuenta existe en Cognito pero no en
 * la base (UsuarioNoProvisionadoException, que la app resuelve provisionando) o el pedido
 * choca con los datos (ConflictoDeDatosException). Antes todo 409 era "no provisionado":
 * una invitación duplicada mostraba "El usuario no fue provisionado", y la cola de sesiones,
 * que reintenta ese caso, reintentaba para siempre una sesión rechazada por conflicto.
 * El backend no manda un código aparte, así que se distingue por su mensaje.
 */
internal fun excepcionPara409(message: String): Exception =
    if (message.contains("provisionado", ignoreCase = true)) NotProvisionedException()
    else ConflictoException(message)
