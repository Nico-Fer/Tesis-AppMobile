package com.steadypath.app.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steadypath.app.domain.model.CameraLens
import com.steadypath.app.domain.repository.SessionCameraRepository
import com.steadypath.app.domain.usecase.GetEffectiveCameraLensUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Selector de cámara para usar antes de entrar a un flujo que abre la cámara.
 *
 * Trae su propio estado a propósito: la pantalla que lo muestra ---detalle del ejercicio,
 * elección del ejercicio a grabar--- no tiene por qué cargar la preferencia de cámara en su
 * ViewModel solo para ofrecer esta opción.
 *
 * Lo que elige el usuario acá **no modifica la preferencia guardada**: vale para este flujo
 * y se descarta al terminarlo. El predeterminado se cambia desde Ajustes.
 */
@Composable
fun CameraLensPicker(
    modifier: Modifier = Modifier,
    titulo: String = "¿Con qué cámara?",
    descripcion: String? = null,
    viewModel: CameraLensPickerViewModel = hiltViewModel()
) {
    val lente by viewModel.lente.collectAsStateWithLifecycle()
    CameraLensSelector(
        seleccionado = lente,
        onSeleccionar = viewModel::elegir,
        modifier = modifier,
        titulo = titulo,
        descripcion = descripcion
    )
}

@HiltViewModel
class CameraLensPickerViewModel @Inject constructor(
    getEffectiveCameraLens: GetEffectiveCameraLensUseCase,
    private val sessionCamera: SessionCameraRepository
) : ViewModel() {

    val lente: StateFlow<CameraLens> = getEffectiveCameraLens()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CameraLens.PREDETERMINADO_PACIENTE
        )

    fun elegir(lens: CameraLens) = sessionCamera.elegir(lens)
}
