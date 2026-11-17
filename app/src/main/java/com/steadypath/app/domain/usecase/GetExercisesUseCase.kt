package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.Exercise
import com.steadypath.app.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetExercisesUseCase @Inject constructor(
    private val exerciseRepository: ExerciseRepository
) {
    operator fun invoke(): Flow<Result<List<Exercise>>> {
        return exerciseRepository.getExercises()
    }
}
