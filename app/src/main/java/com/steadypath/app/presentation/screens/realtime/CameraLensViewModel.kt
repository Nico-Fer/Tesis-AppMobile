package com.steadypath.app.presentation.screens.realtime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.CameraLens
import com.steadypath.app.domain.usecase.GetEffectiveCameraLensUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Expone el lente con el que corresponde abrir la cámara, resolviendo la elección puntual
 * del flujo contra la preferencia persistida del rol.
 *
 * Es deliberadamente mínimo: lo consume la sobrecarga de `PoseCameraView` que no recibe
 * lente, de modo que una pantalla que no tenga nada particular que decidir sobre la cámara
 * no necesita cargar esto en su propio ViewModel.
 *
 * `null` hasta que la resolución real llega (un frame después): abrir con un lente
 * provisorio y cambiarlo enseguida sería peor que esperar, porque fuerza un rebind de
 * cámara y la pantalla ya muestra su propio indicador de carga mientras tanto.
 */
@HiltViewModel
class CameraLensViewModel @Inject constructor(
    getEffectiveCameraLens: GetEffectiveCameraLensUseCase
) : ViewModel() {

    val lente: StateFlow<CameraLens?> = getEffectiveCameraLens()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )
}
