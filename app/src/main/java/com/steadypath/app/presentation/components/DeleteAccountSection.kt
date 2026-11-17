package com.steadypath.app.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steadypath.app.domain.usecase.DeleteAccountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Baja de la cuenta propia. Sirve a los dos roles y trae su propio estado, de modo que la
 * pantalla que lo muestra no necesita cargar nada de esto en su ViewModel.
 *
 * Vive dentro de «Editar perfil» y no en el listado de ajustes porque es una acción
 * destructiva e irreversible: conviene que haya que entrar a editar el perfil para
 * encontrarla, en lugar de que quede a un toque de distancia del resto de las opciones.
 *
 * El color de error de la paleta marca la acción sin gritar ---contorno rojo sobre fondo
 * claro, no un bloque rojo lleno---, y la confirmación obliga a leer qué se pierde antes de
 * seguir.
 */
@Composable
fun DeleteAccountSection(
    onAccountDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeleteAccountViewModel = hiltViewModel()
) {
    var mostrarConfirmacion by remember { mutableStateOf(false) }
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { mostrarConfirmacion = true },
            enabled = estado !is DeleteAccountViewModel.Estado.Borrando,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            if (estado is DeleteAccountViewModel.Estado.Borrando) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text("Eliminar mi cuenta")
            }
        }

        (estado as? DeleteAccountViewModel.Estado.Error)?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error.mensaje,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }

    if (mostrarConfirmacion) {
        AlertDialog(
            onDismissRequest = { mostrarConfirmacion = false },
            title = { Text("¿Eliminar tu cuenta?") },
            text = {
                Text(
                    text = "Se van a borrar tus datos, tus rutinas y el historial de tus " +
                        "sesiones. También se da de baja tu usuario, así que no vas a poder " +
                        "volver a entrar con este correo.\n\n" +
                        "Esto no se puede deshacer.",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mostrarConfirmacion = false
                        viewModel.eliminar(onAccountDeleted)
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfirmacion = false }) { Text("Cancelar") }
            }
        )
    }
}

@HiltViewModel
class DeleteAccountViewModel @Inject constructor(
    private val deleteAccount: DeleteAccountUseCase
) : ViewModel() {

    sealed interface Estado {
        data object Inicial : Estado
        data object Borrando : Estado
        data class Error(val mensaje: String) : Estado
    }

    private val _estado = MutableStateFlow<Estado>(Estado.Inicial)
    val estado: StateFlow<Estado> = _estado.asStateFlow()

    fun eliminar(onDeleted: () -> Unit) {
        if (_estado.value is Estado.Borrando) return
        _estado.value = Estado.Borrando
        viewModelScope.launch {
            deleteAccount()
                .onSuccess { onDeleted() }
                .onFailure {
                    // El backend trata la baja como todo o nada, así que un fallo significa
                    // que no se borró nada: conviene decirlo para que nadie quede con la duda
                    // de si sus datos siguen existiendo.
                    _estado.value = Estado.Error(
                        it.message ?: "No se pudo eliminar la cuenta. No se borró ningún dato."
                    )
                }
        }
    }
}
