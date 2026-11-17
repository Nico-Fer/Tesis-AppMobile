package com.steadypath.app.presentation.screens.professional

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.usecase.SetToleranceUseCase
import com.steadypath.app.presentation.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdjustToleranceViewModel @Inject constructor(
    private val setToleranceUseCase: SetToleranceUseCase
) : ViewModel() {

    val anguloMinimo = MutableStateFlow("")
    val anguloMaximo = MutableStateFlow("")

    // Success(Unit) inicial = sin guardado en curso.
    private val _saveState = MutableStateFlow<UiState<Unit>>(UiState.Success(Unit))
    val saveState: StateFlow<UiState<Unit>> = _saveState.asStateFlow()

    private val _toleranceSaved = MutableStateFlow(false)
    val toleranceSaved: StateFlow<Boolean> = _toleranceSaved.asStateFlow()

    fun onAnguloMinimoChange(value: String) {
        anguloMinimo.value = value
    }

    fun onAnguloMaximoChange(value: String) {
        anguloMaximo.value = value
    }

    fun saveTolerance(idPaciente: String, idEjercicio: String) {
        val minimo = anguloMinimo.value.toDoubleOrNull()
        val maximo = anguloMaximo.value.toDoubleOrNull()
        if (minimo == null || maximo == null) {
            _saveState.value = UiState.Error("Ingresá los dos ángulos como números.")
            return
        }

        viewModelScope.launch {
            _saveState.value = UiState.Loading
            setToleranceUseCase(idPaciente, idEjercicio, minimo, maximo).collect { result ->
                result.fold(
                    onSuccess = {
                        _saveState.value = UiState.Success(Unit)
                        _toleranceSaved.value = true
                    },
                    onFailure = { error ->
                        _saveState.value = UiState.Error(error.message ?: "Error al guardar la tolerancia")
                    }
                )
            }
        }
    }
}
