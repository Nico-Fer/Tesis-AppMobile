package com.steadypath.app.data.datasource

import com.google.gson.Gson
import com.steadypath.app.data.dto.ErrorResponseDto
import com.steadypath.app.domain.model.NotProvisionedException
import com.steadypath.app.domain.model.PermissionDeniedException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Único punto de ejecución de llamadas a la API: parseo de `{message}`,
 * mapeo de 403/409 a excepciones tipadas y captura de errores de red.
 * Los repositorios no deben repetir este manejo.
 */
@Singleton
class SafeApiCaller @Inject constructor(
    private val gson: Gson,
    private val dispatcher: CoroutineDispatcher
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
                val body = response.body()
                if (body != null) {
                    Result.success(map(body))
                } else {
                    Result.failure(Exception("Respuesta vacía del servidor"))
                }
            } else {
                Result.failure(mapHttpError(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Error de conexión: ${e.localizedMessage}"))
        }
    }

    private fun mapHttpError(code: Int, errorBody: String?): Exception {
        val message = parseError(errorBody)
        return when (code) {
            403 -> PermissionDeniedException(message)
            409 -> NotProvisionedException()
            else -> Exception(message)
        }
    }

    private fun parseError(errorBody: String?): String = try {
        errorBody?.let { gson.fromJson(it, ErrorResponseDto::class.java).message }
            ?: "Error desconocido"
    } catch (_: Exception) {
        "Error de conexión"
    }
}
