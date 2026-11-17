package com.steadypath.app.domain.repository

import com.steadypath.app.domain.model.Exercise
import com.steadypath.app.domain.model.ExerciseVideo
import kotlinx.coroutines.flow.Flow

interface ExerciseRepository {

    fun getExercises(): Flow<Result<List<Exercise>>>

    /** URLs firmadas de corta vida: pedir al momento de reproducir, no cachear. */
    fun getExerciseVideos(idEjercicio: String): Flow<Result<List<ExerciseVideo>>>
}
