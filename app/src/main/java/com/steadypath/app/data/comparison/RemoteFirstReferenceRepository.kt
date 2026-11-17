package com.steadypath.app.data.comparison

import android.content.Context
import android.util.Log
import com.steadypath.app.data.datasource.RehabApiService
import com.steadypath.app.data.datasource.SafeApiCaller
import com.steadypath.app.data.dto.SaveOwnReferenceRequestDto
import com.steadypath.app.data.dto.SavePatientReferenceRequestDto
import com.steadypath.app.data.session.UserScopedCache
import com.steadypath.app.domain.comparison.ExerciseReference
import com.steadypath.app.domain.comparison.ReferenceProvenance
import com.steadypath.app.domain.repository.MovementReferenceRepository
import com.steadypath.app.domain.repository.ReferenceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Referencias con prioridad remota: la referencia del paciente en el backend (por
 * paciente+ejercicio) manda sobre la genérica empaquetada en assets.
 *
 * Orden de resolución de [getReference]:
 * 1. Backend (`GET /api/pacientes/referencias/{idEjercicio}`) — si responde, es autoritativo:
 *    con referencia se cachea a disco; sin referencia se saltea el cache de disco (estaría
 *    obsoleto) y se cae directo a assets.
 * 2. Cache de disco (`filesDir/references/`) — última referencia remota conocida, para
 *    sesiones offline.
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
        val loaded = resolve(exerciseId)
        mutex.withLock { cache[key] = loaded }
        return loaded
    }

    override suspend fun uploadOwn(exerciseId: String, referenceJson: String): Result<Unit> {
        val result = caller.execute(
            call = { api.saveOwnReference(SaveOwnReferenceRequestDto(exerciseId, referenceJson)) },
            map = { }
        )
        if (result.isSuccess) {
            // La recién subida es la nueva verdad local: se pisa el cache sin re-descargar.
            withContext(ioDispatcher) { writeDiskCache(exerciseId, referenceJson) }
            val parsed = parseOrNull(referenceJson, exerciseId, origen = "subida propia")
            mutex.withLock {
                cache[exerciseId.lowercase()] = parsed
                provenance[exerciseId] = ReferenceProvenance.PROPIA
            }
        }
        return result
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

    private suspend fun resolve(exerciseId: String): ExerciseReference? {
        val remote = caller.execute(
            call = { api.getOwnReference(exerciseId) },
            map = { it }
        ).getOrNull()

        if (remote != null) {
            if (!remote.tieneReferencia || remote.referenciaJson.isNullOrBlank()) {
                Log.i(TAG, "Sin referencia remota para '$exerciseId' — se usa la de assets")
                return assetFallback.getReference(exerciseId)
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
                return parsed
            }
            return assetFallback.getReference(exerciseId)
        }

        // Sin red (o 403 de un rol sin referencias propias): última remota conocida, o assets.
        val cached = withContext(ioDispatcher) { readDiskCache(exerciseId) }
        if (cached != null) {
            val parsed = parseOrNull(cached, exerciseId, origen = "cache de disco")
            if (parsed != null) {
                Log.i(TAG, "Referencia de '$exerciseId' servida del cache de disco (offline)")
                mutex.withLock { provenance[exerciseId] = ReferenceProvenance.PROPIA }
                return parsed
            }
        }
        return assetFallback.getReference(exerciseId)
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
