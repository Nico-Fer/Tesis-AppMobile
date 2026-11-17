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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steadypath.app.domain.model.RoutineTemplate
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.components.DomiTextField
import com.steadypath.app.presentation.components.DomiTopBar
import com.steadypath.app.presentation.components.GradientButton
import com.steadypath.app.presentation.components.PrimaryButton
import com.steadypath.app.presentation.components.ProfessionalBottomNavBar
import com.steadypath.app.presentation.components.DomiMainHeader
import com.steadypath.app.presentation.components.ProfessionalNavTab

/** "Rutinas" del bottom nav: biblioteca de plantillas del profesional. */
@Composable
fun RoutineTemplateLibraryScreen(
    onTemplateClick: (templateId: String) -> Unit,
    onCreateTemplateClick: () -> Unit,
    onHomeClick: () -> Unit,
    onPatientsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: TemplateListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val nombreProfesional by viewModel.nombreProfesional.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadTemplates() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DomiMainHeader(nombre = nombreProfesional, onSettingsClick = onSettingsClick)

        Column(modifier = Modifier.weight(1f).padding(horizontal = 24.dp)) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Mis Rutinas",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Plantillas que podés asignar a tus pacientes.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            TemplateListBody(
                uiState = uiState,
                onTemplateClick = onTemplateClick,
                onCreateTemplateClick = onCreateTemplateClick
            )
        }

        ProfessionalBottomNavBar(
            selectedTab = ProfessionalNavTab.Routines,
            onTabSelected = { tab ->
                when (tab) {
                    ProfessionalNavTab.Home -> onHomeClick()
                    ProfessionalNavTab.Patients -> onPatientsClick()
                    ProfessionalNavTab.Routines -> {}
                }
            }
        )
    }
}

/** Mismo listado, sin bottom nav: se llega acá desde el detalle de un paciente. */
@Composable
fun ChooseTemplateForPatientScreen(
    patientName: String,
    onTemplateClick: (templateId: String, templateName: String) -> Unit,
    onCreateTemplateClick: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: TemplateListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadTemplates() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DomiTopBar(
            title = "Elegí una plantilla",
            showBackArrow = true,
            onBackClick = onNavigateBack
        )

        Column(modifier = Modifier.weight(1f).padding(horizontal = 24.dp)) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Elegí la rutina que querés asignarle a $patientName.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            TemplateListBody(
                uiState = uiState,
                onTemplateClick = { id -> onTemplateClick(id, templateName(uiState, id)) },
                onCreateTemplateClick = onCreateTemplateClick
            )
        }
    }
}

private fun templateName(uiState: UiState<List<RoutineTemplate>>, id: String): String =
    (uiState as? UiState.Success)?.data?.firstOrNull { it.idPlantilla == id }?.nombreRutina.orEmpty()

@Composable
private fun TemplateListBody(
    uiState: UiState<List<RoutineTemplate>>,
    onTemplateClick: (templateId: String) -> Unit,
    onCreateTemplateClick: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }

    when (uiState) {
        is UiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }

        is UiState.Error -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = uiState.message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        is UiState.Success -> if (uiState.data.isEmpty()) {
            EmptyTemplates(onCreateTemplateClick)
        } else {
            val filtradas = uiState.data.filter {
                query.isBlank() || it.nombreRutina.contains(query, ignoreCase = true)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                item {
                    DomiTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = "BUSCAR",
                        placeholder = "Buscar rutina..."
                    )
                }
                items(filtradas, key = { it.idPlantilla }) { template ->
                    TemplateCard(template = template, onClick = { onTemplateClick(template.idPlantilla) })
                }
                item {
                    GradientButton(
                        text = "Crear nueva rutina",
                        onClick = onCreateTemplateClick,
                        contentDesc = "Crear una plantilla de rutina nueva"
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(template: RoutineTemplate, onClick: () -> Unit) {
    val pacientesLabel = if (template.cantidadPacientesAsignados == 1) {
        "Asignada a 1 paciente"
    } else {
        "Asignada a ${template.cantidadPacientesAsignados} pacientes"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(onClick = onClick)
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = template.nombreRutina,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${template.ejercicios.size} ejercicios · $pacientesLabel",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyTemplates(onCreateTemplateClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Aún no tenés rutinas",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Creá tu primera plantilla de rutina para asignársela a tus pacientes.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        PrimaryButton(
            text = "Crear rutina",
            onClick = onCreateTemplateClick,
            contentDesc = "Crear una plantilla de rutina nueva"
        )
    }
}

