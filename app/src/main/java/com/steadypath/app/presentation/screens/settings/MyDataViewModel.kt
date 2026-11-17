package com.steadypath.app.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.MisDatos
import com.steadypath.app.domain.usecase.GetMyDataUseCase
import com.steadypath.app.presentation.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MyDataViewModel @Inject constructor(
    private val getMyData: GetMyDataUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<MisDatos>>(UiState.Loading)
    val state: StateFlow<UiState<MisDatos>> = _state.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            getMyData()
                .onSuccess { _state.value = UiState.Success(it) }
                .onFailure { _state.value = UiState.Error(it.message ?: "No se pudieron obtener tus datos") }
        }
    }
}
