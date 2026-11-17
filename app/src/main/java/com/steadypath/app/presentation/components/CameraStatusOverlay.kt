package com.steadypath.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.steadypath.app.data.vision.EstadoEstimador

/**
 * Capa sobre el visor de cámara que dice en qué está el arranque: cámara, modelo, o un
 * error de alguno de los dos. Sin esto, un bind fallido o un modelo que no carga se ven
 * igual que una cámara que todavía no emitió el primer frame: un recuadro negro.
 */
@Composable
fun CameraStatusOverlay(
    cameraReady: Boolean,
    cameraError: Throwable?,
    estadoModelo: EstadoEstimador,
    modifier: Modifier = Modifier
) {
    val errorMessage = when {
        cameraError != null -> cameraError.message?.takeIf { it.isNotBlank() }
            ?: "No se pudo iniciar la cámara."
        estadoModelo is EstadoEstimador.Error -> "No se pudo cargar el modelo de pose: ${estadoModelo.mensaje}"
        else -> null
    }
    val loadingMessage = when {
        errorMessage != null -> null
        !cameraReady -> "Preparando cámara…"
        estadoModelo !is EstadoEstimador.Listo -> "Preparando modelo…"
        else -> null
    }
    if (errorMessage == null && loadingMessage == null) return

    Column(
        modifier = modifier
            .fillMaxSize()
            // Sobre la cámara ya encendida el fondo es translúcido para no taparla del todo.
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (cameraReady) 0.6f else 1f))
            .padding(24.dp)
            .semantics { contentDescription = errorMessage ?: loadingMessage.orEmpty() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (errorMessage != null) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        } else {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = loadingMessage.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
