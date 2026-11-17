package com.steadypath.app.presentation.screens.professional

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.RoutineTemplate
import com.steadypath.app.domain.repository.UserSessionRepository
import com.steadypath.app.domain.usecase.GetTemplatesUseCase
import com.steadypath.app.presentation.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TemplateListViewModel @Inject constructor(
    private val getTemplatesUseCase: GetTemplatesUseCase,
    private val userSessionRepository: UserSessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<RoutineTemplate>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<RoutineTemplate>>> = _uiState.asStateFlow()

    private val _nombreProfesional = MutableStateFlow("")
    val nombreProfesional: StateFlow<String> = _nombreProfesional.asStateFlow()

    init {
        viewModelScope.launch {
            _nombreProfesional.value = userSessionRepository.get()?.nombre.orEmpty()
        }
    }

    fun loadTemplates() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            getTemplatesUseCase().collect { result ->
                result.fold(
                    onSuccess = { templates -> _uiState.value = UiState.Success(templates) },
                    onFailure = { error ->
                        _uiState.value = UiState.Error(error.message ?: "Error al cargar las plantillas")
                    }
                )
            }
        }
    }
}
