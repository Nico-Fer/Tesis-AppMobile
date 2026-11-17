package com.steadypath.app.presentation.screens.professional

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steadypath.app.domain.model.PatientSummary
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.components.ProfessionalBottomNavBar
import com.steadypath.app.presentation.components.DomiMainHeader
import com.steadypath.app.presentation.components.ProfessionalNavTab

@Composable
fun ProfessionalHomeScreen(
    onPatientsClick: () -> Unit,
    onRoutinesClick: () -> Unit,
    onPatientClick: (idPaciente: String, nombre: String, edad: Int?) -> Unit,
    onSettingsClick: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: ProfessionalHomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadHome()
    }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                ProfessionalHomeViewModel.NavigationEvent.SignedOut -> onSignedOut()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val nombre = (uiState as? UiState.Success)?.data?.nombreProfesional.orEmpty()
        DomiMainHeader(nombre = nombre, onSettingsClick = onSettingsClick)

        when (val state = uiState) {
            is UiState.Loading -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            is UiState.Error -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }

            is UiState.Success -> ProfessionalHomeContent(
                data = state.data,
                onPatientClick = onPatientClick,
                onPatientsClick = onPatientsClick,
                modifier = Modifier.weight(1f)
            )
        }

        ProfessionalBottomNavBar(
            selectedTab = ProfessionalNavTab.Home,
            onTabSelected = { tab ->
                when (tab) {
                    ProfessionalNavTab.Home -> {}
                    ProfessionalNavTab.Patients -> onPatientsClick()
                    ProfessionalNavTab.Routines -> onRoutinesClick()
                }
            }
        )
    }
}

@Composable
private fun ProfessionalHomeContent(
    data: ProfessionalHomeData,
    onPatientClick: (idPaciente: String, nombre: String, edad: Int?) -> Unit,
    onPatientsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            StatCard(value = data.cantidadPacientes.toString(), label = "PACIENTES\nACTIVOS")
            StatCard(value = data.sesionesEstaSemana.toString(), label = "SESIONES\nESTA SEMANA")
            StatCard(
                value = data.cumplimientoPromedio?.let { "$it%" } ?: "—",
                label = "CUMPLIMIENTO\nPROMEDIO"
            )
        }

        if (data.pacientesQueRequierenAtencion.isNotEmpty()) {
            Spacer(modifier = Modifier.height(40.dp))
            Text(
                text = "Requieren tu atención",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(24.dp))
            data.pacientesQueRequierenAtencion.forEach { item ->
                AttentionCard(
                    item = item,
                    onClick = { onPatientClick(item.patient.idPaciente, item.patient.nombre, item.patient.edad) }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        if (data.invitacionesPendientes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(40.dp))
            Text(
                text = "Invitaciones pendientes",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(24.dp))
            data.invitacionesPendientes.forEach { invitacion ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Text(
                        text = invitacion.email,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "PENDIENTE",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onPatientsClick),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Ver todos mis pacientes",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(170.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(24.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AttentionCard(
    item: PatientAttentionItem,
    onClick: () -> Unit
) {
    val mensaje = when (val alert = item.alert) {
        is PatientAlert.HighPain -> "Reportó dolor ${alert.eva}/10 en su última sesión"
        is PatientAlert.Inactive -> "Sin actividad hace ${alert.dias} días"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.patient.nombre,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = if (item.alert is PatientAlert.HighPain) Icons.Filled.Warning else Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = mensaje,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
