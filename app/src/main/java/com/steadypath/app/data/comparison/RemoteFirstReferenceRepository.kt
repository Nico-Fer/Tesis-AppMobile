package com.steadypath.app.data.comparison

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.steadypath.app.data.datasource.RehabApiService
import com.steadypath.app.data.datasource.SafeApiCaller
import com.steadypath.app.data.dto.SaveOwnReferenceRequestDto
import com.steadypath.app.data.dto.SavePatientReferenceRequestDto
import com.steadypath.app.data.session.UserScopedCache
import com.steadypath.app.data.sync.ColaDeOperaciones
import com.steadypath.app.data.sync.EnvioDePendientes
import com.steadypath.app.data.sync.MonitorDeConectividad
import com.steadypath.app.data.sync.OperacionPendiente
import com.steadypath.app.data.sync.TipoDeOperacion
import com.steadypath.app.domain.comparison.ExerciseReference
import com.steadypath.app.domain.comparison.ReferenceProvenance
import com.steadypath.app.domain.repository.MovementReferenceRepository
import com.steadypath.app.domain.repository.ReferenceRepository
import com.steadypath.app.domain.repository.UserSessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Referencias con prioridad remota: la referencia del paciente en el backend (por
 * paciente+ejercicio) manda sobre la genérica empaquetada en assets.
 *
 * Orden de resolución de [getReference]:
 * 0. Subida pendiente — una referencia grabada sin red que todavía no llegó al servidor
 *    manda sobre lo que el servidor diga: es más nueva que cualquier cosa que tenga.
 * 1. Backend (`GET /api/pacientes/referencias/{idEjercicio}`) — si responde, es autoritativo:
 *    con referencia se cachea a disco; sin referencia se saltea el cache de disco (estaría
 *    obsoleto) y se cae directo a assets. Si se sabe que no hay red, ni se intenta.
 * 2. Cache de disco (`filesDir/references/`) — última referencia remota conocida, para
 *    sesiones offline. [precargar] lo llena por adelantado.
 * 3. Assets ([LocalAssetReferenceRepository]) — referencia genérica de fábrica.
 *
 * Una sola clase implementa lectura y subida para que [uploadOwn] pueda refrescar sus
 * propios caches sin coordinación entre singletons.
 */
@Singleton
class RemoteFirstReferenceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: RehabApiService,
    private val caller: SafeApiCaller,
    private val assetFallback: LocalAssetReferenceRepository,
    private val cola: ColaDeOperaciones,
    private val usuario: UserSessionRepository,
    private val monitor: MonitorDeConectividad,
    private val gson: Gson,
    private val ioDispatcher: CoroutineDispatcher
) : ReferenceRepository, MovementReferenceRepository, UserScopedCache {

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, ExerciseReference?>()

    /**
     * Procedencia de la última referencia resuelta por ejercicio. Se registra en
     * [resolve] en lugar de consultarse aparte para no repetir la llamada de red ni
     * arriesgar que las dos respuestas discrepen.
     */
    private val provenance = mutableMapOf<String, ReferenceProvenance>()

    /** Las referencias son del paciente pero la clave es solo el ejercicio: al logout se descartan. */
    /**
     * El cache de disco no guarda la procedencia: una referencia servida sin red se
     * reporta como propia, que es la lectura conservadora ---no afirma un respaldo
     * profesional que no se puede comprobar en ese momento---.
     */
    override suspend fun provenanceOf(exerciseId: String): ReferenceProvenance =
        mutex.withLock { provenance[exerciseId] } ?: ReferenceProvenance.EMPAQUETADA

    override suspend fun clear() {
        mutex.withLock { cache.clear(); provenance.clear() }
        withContext(ioDispatcher) {
            runCatching { File(context.filesDir, DISK_DIR).deleteRecursively() }
                .onFailure { Log.w(TAG, "No se pudo borrar el cache de referencias en disco", it) }
        }
    }

    override suspend fun getReference(exerciseId: String): ExerciseReference? {
        val key = exerciseId.lowercase()
        mutex.withLock {
            if (cache.containsKey(key)) return cache[key]
        }
        val resuelta = resolve(exerciseId)
        // Lo que salió de assets porque no hubo respuesta ni copia no se memoriza: con red
        // la próxima consulta puede encontrar la referencia propia que hoy no se vio.
        if (resuelta.esDefinitiva) mutex.withLock { cache[key] = resuelta.referencia }
        return resuelta.referencia
    }

    /**
     * Baja y guarda en disco las referencias de esos ejercicios, pisando lo memorizado.
     * Lo llama la precarga con red: deja el teléfono listo para ejercitar sin conexión
     * ejercicios que la persona nunca abrió.
     */
    suspend fun precargar(exerciseIds: List<String>) {
        for (exerciseId in exerciseIds) {
            val resuelta = resolve(exerciseId)
            if (resuelta.esDefinitiva) {
                mutex.withLock { cache[exerciseId.lowercase()] = resuelta.referencia }
            }
        }
    }

    /**
     * Sin red la referencia queda igual de lista para usar: se guarda en disco y en
     * memoria como si hubiera subido y la subida va a la cola de salida. Se devuelve
     * éxito porque para la persona ya está hecho: puede empezar el ejercicio, que es para
     * lo que grabó. Perder la toma por un corte la obligaría a repetir cinco repeticiones.
     */
    override suspend fun uploadOwn(exerciseId: String, referenceJson: String): Result<Unit> {
        val solicitud = SaveOwnReferenceRequestDto(exerciseId, referenceJson)
        val result = caller.execute(call = { api.saveOwnReference(solicitud) }, map = { })
        val error = result.exceptionOrNull()
        if (error == null) {
            // La recién subida es la nueva verdad local: se pisa el cache sin re-descargar.
            adoptarComoPropia(exerciseId, referenceJson, origen = "subida propia")
            return result
        }

        val idUsuario = usuario.get()?.idUsuario
        if (!EnvioDePendientes.esReintentable(error) || idUsuario == null) return result
        // Una referencia que no se puede leer tampoco la aceptaría el servidor después.
        if (!adoptarComoPropia(exerciseId, referenceJson, origen = "grabación sin red")) return result
        cola.agregar(
            OperacionPendiente(
                id = UUID.randomUUID().toString(),
                idUsuario = idUsuario,
                tipo = TipoDeOperacion.REFERENCIA_PROPIA,
                destino = exerciseId.lowercase(),
                cargaJson = gson.toJson(solicitud)
            )
        )
        return Result.success(Unit)
    }

    /** @return si el JSON es una referencia válida para ese ejercicio. */
    private suspend fun adoptarComoPropia(
        exerciseId: String,
        referenceJson: String,
        origen: String
    ): Boolean {
        val parsed = parseOrNull(referenceJson, exerciseId, origen) ?: return false
        withContext(ioDispatcher) { writeDiskCache(exerciseId, referenceJson) }
        mutex.withLock {
            cache[exerciseId.lowercase()] = parsed
            provenance[exerciseId] = ReferenceProvenance.PROPIA
        }
        return true
    }

    override suspend fun uploadForPatient(
        idPaciente: String,
        exerciseId: String,
        referenceJson: String
    ): Result<Unit> = caller.execute(
        call = {
            api.savePatientReference(
                SavePatientReferenceRequestDto(idPaciente, exerciseId, referenceJson)
            )
        },
        map = { }
    )

    /**
     * @property esDefinitiva hay con qué responder con fundamento (el servidor contestó, hay
     *   copia en disco o una subida pendiente). Si no, [referencia] es el recurso de assets
     *   al que se cayó por no saber nada, y conviene volver a preguntar la próxima vez.
     */
    private data class Resuelta(val referencia: ExerciseReference?, val esDefinitiva: Boolean)

    private suspend fun resolve(exerciseId: String): Resuelta {
        if (tieneSubidaPendiente(exerciseId)) {
            leerDeDisco(exerciseId, motivo = "subida pendiente")?.let { return Resuelta(it, true) }
        }

        // Sin red no se espera el vencimiento de una llamada que se sabe perdida.
        val remote = if (monitor.hayRed()) {
            caller.execute(call = { api.getOwnReference(exerciseId) }, map = { it }).getOrNull()
        } else {
            null
        }

        if (remote != null) {
            if (!remote.tieneReferencia || remote.referenciaJson.isNullOrBlank()) {
                Log.i(TAG, "Sin referencia remota para '$exerciseId' — se usa la de assets")
                return Resuelta(assetFallback.getReference(exerciseId), true)
            }
            val parsed = parseOrNull(remote.referenciaJson, exerciseId, origen = "backend")
            if (parsed != null) {
                val origen = if (remote.creadaPorProfesional == true) {
                    ReferenceProvenance.PROFESIONAL
                } else {
                    ReferenceProvenance.PROPIA
                }
                mutex.withLock { provenance[exerciseId] = origen }
                withContext(ioDispatcher) { writeDiskCache(exerciseId, remote.referenciaJson) }
                return Resuelta(parsed, true)
            }
            return Resuelta(assetFallback.getReference(exerciseId), true)
        }

        // Sin red (o 403 de un rol sin referencias propias): última remota conocida, o assets.
        leerDeDisco(exerciseId, motivo = "offline")?.let { return Resuelta(it, true) }
        return Resuelta(assetFallback.getReference(exerciseId), false)
    }

    private suspend fun tieneSubidaPendiente(exerciseId: String): Boolean {
        val idUsuario = usuario.get()?.idUsuario ?: return false
        return runCatching {
            cola.cargaPendiente(idUsuario, TipoDeOperacion.REFERENCIA_PROPIA, exerciseId.lowercase())
        }.getOrNull() != null
    }

    private suspend fun leerDeDisco(exerciseId: String, motivo: String): ExerciseReference? {
        val cached = withContext(ioDispatcher) { readDiskCache(exerciseId) } ?: return null
        val parsed = parseOrNull(cached, exerciseId, origen = "cache de disco") ?: return null
        Log.i(TAG, "Referencia de '$exerciseId' servida del cache de disco ($motivo)")
        mutex.withLock { provenance[exerciseId] = ReferenceProvenance.PROPIA }
        return parsed
    }

    private fun parseOrNull(json: String, exerciseId: String, origen: String): ExerciseReference? =
        runCatching {
            val reference = ReferenceJsonParser.parse(json)
            require(reference.definition.exerciseId.equals(exerciseId, ignoreCase = true)) {
                "exerciseId='${reference.definition.exerciseId}' no coincide con '$exerciseId'"
            }
            reference
        }.onFailure {
            Log.w(TAG, "Referencia inválida de $origen para '$exerciseId'", it)
        }.getOrNull()

    private fun diskFile(exerciseId: String): File =
        File(File(context.filesDir, DISK_DIR), "${exerciseId.lowercase()}.json")

    private fun writeDiskCache(exerciseId: String, json: String) {
        runCatching {
            diskFile(exerciseId).apply { parentFile?.mkdirs() }.writeText(json)
        }.onFailure { Log.w(TAG, "No se pudo cachear la referencia de '$exerciseId'", it) }
    }

    private fun readDiskCache(exerciseId: String): String? =
        runCatching { diskFile(exerciseId).takeIf { it.isFile }?.readText() }.getOrNull()

    companion object {
        private const val TAG = "RemoteReferenceRepo"
        const val DISK_DIR = "references"
    }
}
