package com.steadypath.app.presentation.screens.professional

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steadypath.app.presentation.components.DomiTopBar
import com.steadypath.app.presentation.components.SettingsToggleRow

@Composable
fun NotificationSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DomiTopBar(title = "Notificaciones", showBackArrow = true, onBackClick = onNavigateBack)

        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(
                text = "Elegí qué avisos querés recibir sobre tus pacientes.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            ) {
                SettingsToggleRow(
                    title = "Dolor alto reportado",
                    description = "Un paciente registra 7/10 o más en una sesión.",
                    checked = preferences.alertasDolorAlto,
                    onCheckedChange = viewModel::onAlertasDolorAltoChange
                )
                SettingsToggleRow(
                    title = "Pacientes inactivos",
                    description = "Un paciente lleva 5 días o más sin entrenar.",
                    checked = preferences.alertasPacientesInactivos,
                    onCheckedChange = viewModel::onAlertasPacientesInactivosChange
                )
                SettingsToggleRow(
                    title = "Resumen semanal",
                    description = "Un resumen de la actividad de tus pacientes cada semana.",
                    checked = preferences.resumenSemanal,
                    onCheckedChange = viewModel::onResumenSemanalChange,
                    showDivider = false
                )
            }
        }
    }
}
