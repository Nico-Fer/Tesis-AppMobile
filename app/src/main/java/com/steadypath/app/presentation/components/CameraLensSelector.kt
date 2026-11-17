package com.steadypath.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.CameraRear
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.steadypath.app.domain.model.CameraLens

/**
 * Selector del lente con el que se abrirá la cámara.
 *
 * Deliberadamente **no** es un interruptor de un solo botón. Un toggle obliga a deducir si
 * el ícono representa el estado actual o aquel al que se cambiaría, ambigüedad que este
 * público no tiene por qué resolver. Acá las dos opciones están siempre a la vista, con su
 * etiqueta escrita, y la activa se distingue por color de fondo y una tilde ---no por un
 * borde fino, que el sistema de diseño descarta---.
 *
 * Cada opción supera los 48 dp de alto mínimos y el conjunto se expone como un grupo de
 * selección única para los lectores de pantalla.
 */
@Composable
fun CameraLensSelector(
    seleccionado: CameraLens,
    onSeleccionar: (CameraLens) -> Unit,
    modifier: Modifier = Modifier,
    titulo: String = "¿Con qué cámara?",
    descripcion: String? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (descripcion != null) {
            Text(
                text = descripcion,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        // Con la letra del sistema agrandada, dos opciones lado a lado partían «Cámara» a
        // mitad de palabra: se apilan y cada una toma el ancho completo.
        val grupo = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .selectableGroup()
        if (LocalDensity.current.fontScale > 1.3f) {
            Column(modifier = grupo, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CameraLens.entries.forEach { lente ->
                    OpcionLente(
                        lente = lente,
                        activo = lente == seleccionado,
                        onClick = { onSeleccionar(lente) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            Row(modifier = grupo, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CameraLens.entries.forEach { lente ->
                    OpcionLente(
                        lente = lente,
                        activo = lente == seleccionado,
                        onClick = { onSeleccionar(lente) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun OpcionLente(
    lente: CameraLens,
    activo: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fondo = if (activo) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contenido = if (activo) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(fondo)
            .selectable(
                selected = activo,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = when (lente) {
                CameraLens.FRONTAL -> Icons.Filled.CameraFront
                CameraLens.TRASERA -> Icons.Filled.CameraRear
            },
            contentDescription = null,
            tint = contenido
        )
        Text(
            text = lente.etiqueta,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (activo) FontWeight.SemiBold else FontWeight.Normal,
            color = contenido,
            modifier = Modifier.weight(1f)
        )
        if (activo) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = contenido
            )
        }
    }
}
