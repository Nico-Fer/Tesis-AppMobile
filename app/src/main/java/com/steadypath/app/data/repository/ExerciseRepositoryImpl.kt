package com.steadypath.app.data.repository

import com.steadypath.app.data.datasource.RehabApiService
import com.steadypath.app.data.datasource.SafeApiCaller
import com.steadypath.app.data.local.EjercicioDao
import com.steadypath.app.data.local.LecturaConCache
import com.steadypath.app.data.local.aDominio
import com.steadypath.app.data.local.aEntidad
import com.steadypath.app.data.mapper.toDomain
import com.steadypath.app.domain.model.Exercise
import com.steadypath.app.domain.model.ExerciseVideo
import com.steadypath.app.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ExerciseRepositoryImpl @Inject constructor(
    private val apiService: RehabApiService,
    private val caller: SafeApiCaller,
    private val lectura: LecturaConCache,
    private val ejercicioDao: EjercicioDao
) : ExerciseRepository {

    /**
     * El catálogo se guarda entero: sin red alimenta el detalle del ejercicio, el armado de
     * rutinas y los nombres de las sesiones hechas sin conexión. Un catálogo vacío no
     * cuenta como copia (no existe uno real sin ejercicios).
     */
    override fun getExercises(): Flow<Result<List<Exercise>>> = lectura.flujo(
        leer = { ejercicioDao.todos().takeIf { it.isNotEmpty() }?.map { it.aDominio() } },
        llamar = { caller.execute({ apiService.getExercises() }) { dtos -> dtos.map { it.toDomain() } } },
        guardar = { ejercicios ->
            ejercicioDao.reemplazar(ejercicios.mapIndexed { indice, ejercicio -> ejercicio.aEntidad(indice) })
        },
        aDominio = { it }
    )

    /** Sin copia local a propósito: las URLs firmadas vencen a los minutos. */
    override fun getExerciseVideos(idEjercicio: String): Flow<Result<List<ExerciseVideo>>> =
        caller.flowCall(
            call = { apiService.getExerciseVideos(idEjercicio) },
            map = { dtos -> dtos.map { it.toDomain() } }
        )
}
