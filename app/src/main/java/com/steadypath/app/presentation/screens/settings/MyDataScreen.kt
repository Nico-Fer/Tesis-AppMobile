package com.steadypath.app.presentation.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steadypath.app.domain.model.MisDatos
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.components.DomiTopBar

/**
 * "Mis datos": lo que el sistema guarda sobre la persona (derecho de acceso, Ley 25.326
 * art. 14). Resume y no vuelca: el detalle de sesiones y rutinas ya está en sus pantallas.
 */
@Composable
fun MyDataScreen(
    onNavigateBack: () -> Unit,
    viewModel: MyDataViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DomiTopBar(title = "Mis datos", showBackArrow = true, onBackClick = onNavigateBack)

        when (val s = state) {
            is UiState.Success -> MyDataContent(s.data)
            is UiState.Error -> Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = s.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = viewModel::cargar) {
                    Text("Reintentar", style = MaterialTheme.typography.titleMedium)
                }
            }
            else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun MyDataContent(datos: MisDatos) {
    val esPaciente = datos.rol.equals("Paciente", ignoreCase = true)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Esto es lo que guardamos sobre vos.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        Seccion("Tu cuenta") {
            Dato("Nombre", datos.nombre)
            Dato("Email", datos.email)
            datos.telefono?.let { Dato("Teléfono", it) }
            datos.edad?.let { Dato("Edad", "$it años") }
            datos.matricula?.let { Dato("Matrícula", it) }
            datos.institucion?.let { Dato("Institución", it) }
            Dato("Alta", fecha(datos.fechaRegistro))
        }

        if (esPaciente) {
            Seccion("Tu actividad") {
                Dato("Profesional", datos.profesionalAsignado ?: "Sin profesional")
                Dato("Sesiones", "${datos.sesiones}")
                Dato("Dolor registrado", "${datos.registrosDeDolor} veces")
                Dato("Rutinas", "${datos.rutinas}")
                Dato("Referencias de movimiento", "${datos.referenciasDeMovimiento}")
                Dato("Ángulos ajustados", "${datos.tolerancias}")
            }
        } else {
            Seccion("Tu actividad") {
                Dato("Pacientes a cargo", "${datos.pacientesACargo}")
                Dato("Rutinas guardadas", "${datos.plantillasDeRutina}")
            }
        }

        Seccion("Cuánto tiempo los guardamos") {
            Dato("Último uso", fecha(datos.ultimaActividad))
            Text(
                text = "Si no usás la app por 12 meses, borramos tu cuenta y tus datos el " +
                    "${fecha(datos.bajaPorInactividadDesde)}. Cada vez que la usás, ese plazo vuelve a empezar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Text(
            text = "Para corregir tus datos o borrar tu cuenta, entrá a Editar perfil.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Seccion(titulo: String, contenido: @Composable () -> Unit) {
    Text(
        text = titulo,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(8.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp)
    ) {
        contenido()
    }
    Spacer(modifier = Modifier.height(20.dp))
}

@Composable
private fun Dato(etiqueta: String, valor: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = valor,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** "2026-10-12T18:38:06.123Z" -> "12/10/2026". Si no tiene ese formato, se muestra tal cual. */
private fun fecha(iso: String): String {
    val partes = iso.take(10).split("-")
    return if (partes.size == 3) "${partes[2]}/${partes[1]}/${partes[0]}" else iso
}
