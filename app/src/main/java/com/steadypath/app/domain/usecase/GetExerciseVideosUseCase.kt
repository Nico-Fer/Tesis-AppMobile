package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.ExerciseVideo
import com.steadypath.app.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetExerciseVideosUseCase @Inject constructor(
    private val exerciseRepository: ExerciseRepository
) {
    operator fun invoke(idEjercicio: String): Flow<Result<List<ExerciseVideo>>> =
        exerciseRepository.getExerciseVideos(idEjercicio)
}
