package com.steadypath.app.data.comparison

import android.content.Context
import android.util.Log
import com.steadypath.app.domain.comparison.ExerciseReference
import com.steadypath.app.domain.comparison.ReferenceProvenance
import com.steadypath.app.domain.repository.ReferenceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Referencias desde `assets/references/{exerciseId}.json`. Offline-first por construcción:
 * los assets están empaquetados en el APK y se cachean en memoria (incluidos los misses,
 * para no re-tocar el filesystem por cada sesión del mismo ejercicio).
 *
 * TODO: BackendReferenceSource — descargar/cachear referencias al asignar la rutina.
 */
@Singleton
class LocalAssetReferenceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ioDispatcher: CoroutineDispatcher
) : ReferenceRepository {

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, ExerciseReference?>()

    /** De fábrica y genérica: no es de esta persona ni la vio un profesional. */
    override suspend fun provenanceOf(exerciseId: String): ReferenceProvenance =
        ReferenceProvenance.EMPAQUETADA

    override suspend fun getReference(exerciseId: String): ExerciseReference? {
        // Los GUID de .NET viajan en minúscula: clave normalizada para no duplicar entradas.
        val key = exerciseId.lowercase()
        mutex.withLock {
            if (cache.containsKey(key)) return cache[key]
        }
        val loaded = withContext(ioDispatcher) { loadFromAssets(exerciseId) }
        mutex.withLock { cache[key] = loaded }
        return loaded
    }

    private fun loadFromAssets(exerciseId: String): ExerciseReference? {
        val fileName = findAssetFileName(exerciseId)
        if (fileName == null) {
            Log.w(TAG, "Sin referencia local para '$exerciseId' ($ASSET_DIR/) — comparación deshabilitada")
            return null
        }
        val path = "$ASSET_DIR/$fileName"
        val text = try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            Log.w(TAG, "No se pudo leer '$path' — comparación deshabilitada", e)
            return null
        }
        val reference = ReferenceJsonParser.parse(text)
        require(reference.definition.exerciseId.equals(exerciseId, ignoreCase = true)) {
            "El archivo $path declara exerciseId='${reference.definition.exerciseId}' " +
                "pero se pidió '$exerciseId'"
        }
        return reference
    }

    /** Los assets de Android son case-sensitive; el nombre se matchea ignorando mayúsculas. */
    private fun findAssetFileName(exerciseId: String): String? {
        val wanted = "$exerciseId.json"
        val names = try {
            context.assets.list(ASSET_DIR).orEmpty()
        } catch (e: IOException) {
            return null
        }
        return names.firstOrNull { it.equals(wanted, ignoreCase = true) }
    }

    companion object {
        private const val TAG = "ReferenceRepo"
        const val ASSET_DIR = "references"
    }
}
