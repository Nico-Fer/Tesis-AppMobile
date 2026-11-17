package com.steadypath.app.domain.repository

import com.steadypath.app.domain.comparison.ExerciseReference
import com.steadypath.app.domain.comparison.ReferenceProvenance

/**
 * Fuente de la repetición de referencia por ejercicio. Offline-first: la referencia debe
 * estar disponible localmente antes del ejercicio — nunca se accede a red durante la
 * captura.
 *
 * TODO: BackendReferenceSource — cuando el backend exponga referencias por asignación de
 * rutina, una implementación remota las descargará y cacheará al asignar; esta interfaz
 * no cambia.
 */
interface ReferenceRepository {
    /** `null` si no hay referencia para el ejercicio (el comparador degrada a no-op). */
    suspend fun getReference(exerciseId: String): ExerciseReference?

    /**
     * Procedencia de la referencia que devolvería [getReference].
     *
     * Se expone aparte y no dentro de [ExerciseReference] para no ensuciar el modelo del
     * comparador, que deliberadamente no sabe de dónde salió la referencia: el único
     * consumidor es la capa que redacta la devolución al usuario.
     */
    suspend fun provenanceOf(exerciseId: String): ReferenceProvenance
}
