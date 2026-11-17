package com.steadypath.app.presentation.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steadypath.app.presentation.components.CameraLensSelector
import com.steadypath.app.presentation.components.DomiTopBar

/**
 * Ajuste de la cámara predeterminada. Sirve a los dos roles: el ViewModel resuelve cuál
 * preferencia leer y escribir a partir del usuario de la sesión, de modo que no hacen falta
 * dos pantallas casi idénticas.
 *
 * Lo que se elige acá es el punto de partida de cada flujo. La pantalla previa a la cámara
 * permite apartarse de él para una vez puntual sin modificarlo.
 */
@Composable
fun CameraPreferenceScreen(
    onNavigateBack: () -> Unit,
    viewModel: CameraPreferenceViewModel = hiltViewModel()
) {
    val lente by viewModel.lente.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DomiTopBar(title = "Cámara", showBackArrow = true, onBackClick = onNavigateBack)

        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(
                text = "Elegí con qué cámara querés que arranquen los ejercicios. " +
                    "Vas a poder cambiarla antes de cada uno.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            CameraLensSelector(
                seleccionado = lente,
                onSeleccionar = viewModel::elegir,
                titulo = "Cámara predeterminada",
                descripcion = "La frontal te deja ver la pantalla mientras te movés. " +
                    "La trasera sirve cuando otra persona sostiene el teléfono."
            )
        }
    }
}
