package com.steadypath.app.data.repository

import com.steadypath.app.data.datasource.RehabApiService
import com.steadypath.app.data.datasource.SafeApiCaller
import com.steadypath.app.data.mapper.toDomain
import com.steadypath.app.domain.model.Exercise
import com.steadypath.app.domain.model.ExerciseVideo
import com.steadypath.app.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ExerciseRepositoryImpl @Inject constructor(
    private val apiService: RehabApiService,
    private val caller: SafeApiCaller
) : ExerciseRepository {

    override fun getExercises(): Flow<Result<List<Exercise>>> = caller.flowCall(
        call = { apiService.getExercises() },
        map = { dtos -> dtos.map { it.toDomain() } }
    )

    override fun getExerciseVideos(idEjercicio: String): Flow<Result<List<ExerciseVideo>>> =
        caller.flowCall(
            call = { apiService.getExerciseVideos(idEjercicio) },
            map = { dtos -> dtos.map { it.toDomain() } }
        )
}
