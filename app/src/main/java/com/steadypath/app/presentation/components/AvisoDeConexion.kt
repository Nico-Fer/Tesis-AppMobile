package com.steadypath.app.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.EstadoDeSincronizacion
import com.steadypath.app.domain.repository.SyncStatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AvisoDeConexionViewModel @Inject constructor(
    private val sincronizacion: SyncStatusRepository
) : ViewModel() {

    val estado: StateFlow<EstadoDeSincronizacion> = sincronizacion.estado.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        EstadoDeSincronizacion()
    )

    fun avisoDeRechazoVisto() = sincronizacion.avisoDeRechazoVisto()
}

/** Qué decirle a la persona, en orden de urgencia: lo que le pide hacer algo va primero. */
enum class TipoDeAviso { SESION_VENCIDA, RECHAZADOS, SIN_CONEXION, PENDIENTES }

data class Aviso(val tipo: TipoDeAviso, val texto: String)

/**
 * Texto del aviso para un estado, o `null` si no hay nada que decir.
 *
 * @param conDatosGuardados falso en las pantallas de ingreso: ahí no se está viendo nada
 *   guardado, así que solo corresponde decir que no hay conexión.
 */
fun avisoPara(estado: EstadoDeSincronizacion, conDatosGuardados: Boolean): Aviso? = when {
    // En el ingreso no aplica: ahí la persona ya está por iniciar sesión.
    estado.sesionVencida && conDatosGuardados -> Aviso(
        TipoDeAviso.SESION_VENCIDA,
        if (estado.pendientes > 0) {
            "Tu sesión venció. Tocá para iniciar sesión y sincronizar."
        } else {
            "Tu sesión venció. Tocá para iniciar sesión."
        }
    )
    estado.rechazadas > 0 -> Aviso(
        TipoDeAviso.RECHAZADOS,
        "No se ${if (estado.rechazadas == 1) "pudo" else "pudieron"} sincronizar " +
            "${cambios(estado.rechazadas)}. Tocá para cerrar."
    )
    !estado.enLinea && estado.pendientes > 0 -> Aviso(
        TipoDeAviso.SIN_CONEXION,
        "Sin conexión · ${cambios(estado.pendientes)} por sincronizar"
    )
    !estado.enLinea -> Aviso(
        TipoDeAviso.SIN_CONEXION,
        if (conDatosGuardados) "Sin conexión · Viendo datos guardados" else "Sin conexión a internet"
    )
    estado.pendientes > 0 -> Aviso(
        TipoDeAviso.PENDIENTES,
        "${cambios(estado.pendientes)} por sincronizar"
    )
    else -> null
}

private fun cambios(cantidad: Int): String = if (cantidad == 1) "1 cambio" else "$cantidad cambios"

/**
 * Franja que avisa que se están viendo datos guardados o que hay cosas hechas sin red que
 * todavía no subieron. Va una sola vez, arriba de toda la navegación: es un estado de la
 * aplicación, no de una pantalla.
 *
 * Sin bordes: se separa del contenido por color de fondo, como pide el sistema de diseño.
 */
@Composable
fun AvisoDeConexion(
    aviso: Aviso?,
    onIniciarSesion: () -> Unit,
    onCerrarAvisoDeRechazo: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = aviso != null,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier
    ) {
        if (aviso == null) return@AnimatedVisibility
        val esAlerta = aviso.tipo == TipoDeAviso.SESION_VENCIDA || aviso.tipo == TipoDeAviso.RECHAZADOS
        val fondo: Color
        val contenido: Color
        if (esAlerta) {
            fondo = MaterialTheme.colorScheme.errorContainer
            contenido = MaterialTheme.colorScheme.onErrorContainer
        } else {
            fondo = MaterialTheme.colorScheme.surfaceContainerHigh
            contenido = MaterialTheme.colorScheme.onSurface
        }
        val alTocar: (() -> Unit)? = when (aviso.tipo) {
            TipoDeAviso.SESION_VENCIDA -> onIniciarSesion
            TipoDeAviso.RECHAZADOS -> onCerrarAvisoDeRechazo
            else -> null
        }
        val icono: ImageVector = when (aviso.tipo) {
            TipoDeAviso.SIN_CONEXION -> Icons.Filled.CloudOff
            TipoDeAviso.PENDIENTES -> Icons.Filled.CloudSync
            else -> Icons.Filled.ErrorOutline
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(fondo)
                .then(if (alTocar != null) Modifier.clickable(onClick = alTocar) else Modifier)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                )
                // Los que se tocan respetan el mínimo de 48dp del sistema de diseño.
                .heightIn(min = if (alTocar != null) 48.dp else 40.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Icon(
                imageVector = icono,
                contentDescription = null,
                tint = contenido,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = aviso.texto,
                style = MaterialTheme.typography.titleMedium,
                color = contenido,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
