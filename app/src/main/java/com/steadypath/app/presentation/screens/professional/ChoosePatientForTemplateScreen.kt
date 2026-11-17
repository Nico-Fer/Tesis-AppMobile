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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steadypath.app.domain.model.PatientSummary
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.components.DomiTextField
import com.steadypath.app.presentation.components.DomiTopBar
import com.steadypath.app.presentation.components.GradientButton

@Composable
fun ChoosePatientForTemplateScreen(
    templateId: String,
    templateName: String,
    onTemplateAssigned: (patientId: String, patientName: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ChoosePatientForTemplateViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val assignState by viewModel.assignState.collectAsStateWithLifecycle()
    val templateAssigned by viewModel.templateAssigned.collectAsStateWithLifecycle()
    var selectedPatient by remember { mutableStateOf<PatientSummary?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.loadPatients() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DomiTopBar(
            title = "Elegir Usuario",
            showBackArrow = true,
            onBackClick = onNavigateBack
        )

        Column(modifier = Modifier.weight(1f).padding(horizontal = 24.dp)) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Asignarle '$templateName' a:",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            DomiTextField(
                value = query,
                onValueChange = { query = it },
                label = "BUSCAR",
                placeholder = "Buscar paciente"
            )
            Spacer(modifier = Modifier.height(16.dp))

            when (val state = uiState) {
                is UiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                is UiState.Error -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                is UiState.Success -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    val filtrados = state.data.filter {
                        query.isBlank() || it.nombre.contains(query, ignoreCase = true)
                    }
                    items(filtrados, key = { it.idPaciente }) { patient ->
                        PatientRow(patient = patient, onClick = { selectedPatient = patient })
                    }
                }
            }
        }
    }

    selectedPatient?.let { patient ->
        ConfirmAssignDialog(
            patientName = patient.nombre,
            templateName = templateName,
            isLoading = assignState is UiState.Loading,
            onConfirm = { viewModel.assignTemplate(templateId, patient.idPaciente) },
            onDismiss = { selectedPatient = null }
        )
    }

    if (templateAssigned) {
        val patient = selectedPatient
        RoutineAssignedDialog(
            patientName = patient?.nombre.orEmpty(),
            templateName = templateName,
            onConfirm = {
                if (patient != null) onTemplateAssigned(patient.idPaciente, patient.nombre)
            }
        )
    }
}

/**
 * Cuando el paciente ya se conoce (flujo paciente → plantilla), se salta la
 * lista y se confirma directo, reusando el mismo ViewModel/diálogos.
 */
@Composable
fun ConfirmAssignTemplateScreen(
    templateId: String,
    templateName: String,
    patientId: String,
    patientName: String,
    onTemplateAssigned: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ChoosePatientForTemplateViewModel = hiltViewModel()
) {
    val assignState by viewModel.assignState.collectAsStateWithLifecycle()
    val templateAssigned by viewModel.templateAssigned.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DomiTopBar(title = "Asignar rutina", showBackArrow = true, onBackClick = onNavigateBack)
    }

    if (!templateAssigned) {
        ConfirmAssignDialog(
            patientName = patientName,
            templateName = templateName,
            isLoading = assignState is UiState.Loading,
            onConfirm = { viewModel.assignTemplate(templateId, patientId) },
            onDismiss = onNavigateBack
        )
    } else {
        RoutineAssignedDialog(patientName = patientName, templateName = templateName, onConfirm = onTemplateAssigned)
    }
}

@Composable
private fun PatientRow(patient: PatientSummary, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = patient.nombre,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            patient.edad?.let {
                Text(
                    text = "$it años",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun ConfirmAssignDialog(
    patientName: String,
    templateName: String,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Assignment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "¿Asignar esta rutina a $patientName?",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Vas a asignarle '$templateName' a $patientName.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            GradientButton(
                text = "Sí, asignar",
                onClick = onConfirm,
                isLoading = isLoading,
                contentDesc = "Sí, asignar la rutina"
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    }
}

@Composable
private fun RoutineAssignedDialog(patientName: String, templateName: String, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onConfirm) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "¡Rutina asignada!",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$patientName ya puede ver '$templateName' en su lista de rutinas.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            GradientButton(
                text = "Entendido",
                onClick = onConfirm,
                contentDesc = "Volver al detalle del paciente"
            )
        }
    }
}
