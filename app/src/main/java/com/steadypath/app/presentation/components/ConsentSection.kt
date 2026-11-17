package com.steadypath.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.User
import com.steadypath.app.domain.usecase.ConsentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Revocar el consentimiento sin borrar la cuenta, y volver a darlo (RF-48). Trae su propio
 * estado, igual que [DeleteAccountSection], para que Privacidad siga siendo una pantalla
 * sin ViewModel.
 *
 * Revocar no es irreversible, pero corta el seguimiento del profesional: por eso pide
 * confirmación y explica qué pasa. Volver a darlo no la pide.
 */
@Composable
fun ConsentSection(
    modifier: Modifier = Modifier,
    viewModel: ConsentViewModel = hiltViewModel()
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    var mostrarConfirmacion by remember { mutableStateOf(false) }
    val revocado = (estado as? ConsentViewModel.Estado.Listo)?.revocado
    val ocupado = estado is ConsentViewModel.Estado.Cargando

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(20.dp)
    ) {
        Text(
            text = "Tu consentimiento",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (revocado == true) {
                "Revocaste tu consentimiento. Tus datos siguen guardados y los podés ver en " +
                    "Mis datos, pero tu profesional no los ve y la app no registra sesiones " +
                    "nuevas. Si querés volver a usarla, volvé a dar tu consentimiento."
            } else {
                "Lo diste al registrarte. Podés revocarlo sin borrar tu cuenta: tus datos " +
                    "quedan guardados, pero tu profesional deja de verlos y la app no registra " +
                    "sesiones nuevas hasta que lo vuelvas a dar."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        when (revocado) {
            true -> PrimaryButton(
                text = "Volver a dar mi consentimiento",
                onClick = viewModel::otorgar,
                isLoading = ocupado
            )
            false -> OutlinedButton(
                onClick = { mostrarConfirmacion = true },
                enabled = !ocupado,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Revocar mi consentimiento")
            }
            null -> CircularProgressIndicator(
                modifier = Modifier.height(24.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        (estado as? ConsentViewModel.Estado.Listo)?.error?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }

    if (mostrarConfirmacion) {
        AlertDialog(
            onDismissRequest = { mostrarConfirmacion = false },
            title = { Text("¿Revocar tu consentimiento?") },
            text = {
                Text(
                    text = "Tu cuenta y tus datos no se borran. Mientras no lo vuelvas a dar:\n\n" +
                        "• Tu profesional deja de ver tus datos.\n" +
                        "• La app no registra sesiones nuevas ni el dolor.\n" +
                        "• Vos seguís viendo tus datos en Mis datos.\n\n" +
                        "Lo podés volver a dar cuando quieras desde esta pantalla.",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mostrarConfirmacion = false
                        viewModel.revocar()
                    }
                ) {
                    Text("Revocar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfirmacion = false }) { Text("Cancelar") }
            }
        )
    }
}

@HiltViewModel
class ConsentViewModel @Inject constructor(
    private val consentUseCase: ConsentUseCase
) : ViewModel() {

    sealed interface Estado {
        data object Cargando : Estado
        data class Listo(val revocado: Boolean, val error: String? = null) : Estado
    }

    private val _estado = MutableStateFlow<Estado>(Estado.Cargando)
    val estado: StateFlow<Estado> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            _estado.value = Estado.Listo(revocado = consentUseCase.isRevoked())
        }
    }

    fun revocar() = ejecutar(revocadoAntes = false) { consentUseCase.revoke() }

    fun otorgar() = ejecutar(revocadoAntes = true) { consentUseCase.grant() }

    private fun ejecutar(revocadoAntes: Boolean, accion: suspend () -> Result<User>) {
        if (_estado.value is Estado.Cargando) return
        _estado.value = Estado.Cargando
        viewModelScope.launch {
            accion().fold(
                onSuccess = { user -> _estado.value = Estado.Listo(revocado = user.consentimientoRevocado) },
                onFailure = { error ->
                    _estado.value = Estado.Listo(
                        revocado = revocadoAntes,
                        error = error.message ?: "No se pudo actualizar tu consentimiento. Probá de nuevo."
                    )
                }
            )
        }
    }
}
