package com.steadypath.app.presentation.screens.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.Exercise
import com.steadypath.app.domain.usecase.GetExercisesUseCase
import com.steadypath.app.presentation.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExerciseListViewModel @Inject constructor(
    private val getExercisesUseCase: GetExercisesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<Exercise>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<Exercise>>> = _uiState.asStateFlow()

    init {
        loadExercises()
    }

    fun loadExercises() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            getExercisesUseCase().collect { result ->
                result.fold(
                    onSuccess = { exercises ->
                        _uiState.value = UiState.Success(exercises)
                    },
                    onFailure = { error ->
                        _uiState.value = UiState.Error(
                            error.message ?: "Error al cargar ejercicios"
                        )
                    }
                )
            }
        }
    }
}
