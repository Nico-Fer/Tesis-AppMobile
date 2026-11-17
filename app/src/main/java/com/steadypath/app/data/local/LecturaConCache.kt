package com.steadypath.app.data.local

import android.util.Log
import com.google.gson.Gson
import com.steadypath.app.data.datasource.SafeApiCaller
import com.steadypath.app.data.sync.MonitorDeConectividad
import com.steadypath.app.domain.model.NoEncontradoException
import com.steadypath.app.domain.model.PermissionDeniedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.Response
import java.lang.reflect.Type
import javax.inject.Inject
import javax.inject.Singleton

/**
 * El servidor dijo que eso no existe o que este usuario ya no puede verlo. A diferencia de
 * un corte de red, no hay que seguir mostrando la copia guardada: un paciente que revocó
 * el consentimiento o se desvinculó no debe seguir visible desde el teléfono del profesional.
 */
internal fun invalidaLaCopia(error: Throwable): Boolean =
    error is PermissionDeniedException || error is NoEncontradoException

/**
 * Política de lectura del modo sin conexión, separada de Room y de Retrofit para poder
 * probarla en la JVM.
 *
 * 1. Si hay copia en el teléfono, se emite ya: la pantalla no espera a la red.
 * 2. Si hay copia y se sabe que no hay red, termina ahí, sin esperar el vencimiento de una
 *    llamada perdida. Sin copia se intenta igual: el monitor de red es solo una pista.
 * 3. Si el servidor responde, se guarda y se emite lo nuevo.
 * 4. Si falla habiendo copia, la pantalla se queda con ella, salvo que el fallo la
 *    invalide ([invalidaLaCopia]): entonces se borra y se emite el error.
 * 5. Si falla sin copia, se emite el error: no hay nada que mostrar.
 *
 * Emite una o dos veces, nunca cero: quien recolecta siempre termina con un estado.
 *
 * @param D lo que devuelve la llamada. @param R lo que consume la pantalla.
 * @param aDominio convierte la respuesta ya guardada; puede releer la base cuando el
 *   resultado mezcla lo del servidor con lo que el teléfono tiene pendiente de subir.
 */
internal fun <D, R> flujoConCache(
    hayRed: () -> Boolean,
    leer: suspend () -> R?,
    llamar: suspend () -> Result<D>,
    guardar: suspend (D) -> Unit,
    borrar: suspend () -> Unit,
    aDominio: suspend (D) -> R
): Flow<Result<R>> = flow {
    val local = intentar { leer() }
    if (local != null) {
        emit(Result.success(local))
        if (!hayRed()) return@flow
    }
    llamar().fold(
        onSuccess = { respuesta ->
            intentar { guardar(respuesta) }
            emit(Result.success(aDominio(respuesta)))
        },
        onFailure = { error ->
            if (invalidaLaCopia(error)) {
                intentar { borrar() }
                emit(Result.failure(error))
            } else if (local == null) {
                emit(Result.failure(error))
            }
        }
    )
}

/** Un fallo de la base local no debe impedir usar la red, ni al revés. */
private suspend fun <T> intentar(bloque: suspend () -> T): T? = try {
    bloque()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Log.w("LecturaConCache", "Falló el acceso a la base local", e)
    null
}

/**
 * Lecturas con copia local para los repositorios. [flujo] sirve para lo que tiene tabla
 * propia; [instantanea] para lo que se guarda como llegó, por clave.
 */
@Singleton
class LecturaConCache @Inject constructor(
    private val monitor: MonitorDeConectividad,
    private val caller: SafeApiCaller,
    private val instantaneas: InstantaneaDao,
    private val gson: Gson
) {

    fun <D, R> flujo(
        leer: suspend () -> R?,
        llamar: suspend () -> Result<D>,
        guardar: suspend (D) -> Unit,
        borrar: suspend () -> Unit = {},
        aDominio: suspend (D) -> R
    ): Flow<Result<R>> = flujoConCache(monitor::hayRed, leer, llamar, guardar, borrar, aDominio)

    /**
     * Guarda el DTO tal cual llegó y lo convierte con el mismo mapeo que usa la respuesta
     * en vivo, así la copia nunca difiere de lo que se habría mostrado con red.
     *
     * @param tipo tipo del DTO para Gson; con listas, `TypeToken<List<X>>(){}.type`.
     */
    fun <D : Any, R> instantanea(
        clave: String,
        tipo: Type,
        call: suspend () -> Response<D>,
        map: (D) -> R
    ): Flow<Result<R>> = flujoConCache(
        hayRed = monitor::hayRed,
        leer = { leerInstantanea<D>(clave, tipo)?.let(map) },
        llamar = { caller.execute(call) { it } },
        guardar = { dto -> guardarInstantanea(clave, dto) },
        borrar = { instantaneas.borrar(clave) },
        aDominio = { dto -> map(dto) }
    )

    suspend fun <D : Any> leerInstantanea(clave: String, tipo: Type): D? {
        val json = instantaneas.obtener(clave)?.json?.takeIf { it.isNotEmpty() } ?: return null
        // Copia de una versión anterior con otro formato: se descarta y se vuelve a pedir.
        return runCatching { gson.fromJson<D>(json, tipo) }.getOrNull()
    }

    suspend fun guardarInstantanea(clave: String, dto: Any) {
        instantaneas.guardar(InstantaneaEntity(clave, gson.toJson(dto), System.currentTimeMillis()))
    }

    /** Deja constancia de que esa lista ya se sincronizó, aunque haya venido vacía. */
    suspend fun marcar(clave: String) {
        instantaneas.guardar(InstantaneaEntity(clave, "", System.currentTimeMillis()))
    }

    suspend fun estaMarcada(clave: String): Boolean = instantaneas.obtener(clave) != null

    /**
     * Borra todo lo guardado que cita ese identificador (las claves lo llevan adentro).
     * Los UUID no traen `%` ni `_`, así que el patrón no necesita escaparse.
     */
    suspend fun olvidar(id: String) {
        instantaneas.borrarLasQueCoinciden("%$id%")
    }
}
