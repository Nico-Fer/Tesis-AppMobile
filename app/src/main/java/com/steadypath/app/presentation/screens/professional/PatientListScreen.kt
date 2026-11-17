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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steadypath.app.domain.model.Invitation
import com.steadypath.app.domain.model.PatientSummary
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.components.DomiTextField
import com.steadypath.app.presentation.components.GradientButton
import com.steadypath.app.presentation.components.PrimaryButton
import com.steadypath.app.presentation.components.ProfessionalBottomNavBar
import com.steadypath.app.presentation.components.DomiMainHeader
import com.steadypath.app.presentation.components.ProfessionalNavTab
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PatientListScreen(
    onPatientClick: (idPaciente: String, nombre: String, edad: Int?) -> Unit,
    onHomeClick: () -> Unit,
    onRoutinesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: PatientListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val inviteState by viewModel.inviteState.collectAsStateWithLifecycle()
    val nombreProfesional by viewModel.nombreProfesional.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var showManageSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadPatients()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DomiMainHeader(nombre = nombreProfesional, onSettingsClick = onSettingsClick)

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Mis Pacientes",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Revisa el progreso y las rutinas asignadas para la recuperación de tus pacientes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            when (val state = uiState) {
                is UiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                is UiState.Error -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                is UiState.Success -> {
                    val filtered = state.data.patients.filter {
                        query.isBlank() || it.nombre.contains(query, ignoreCase = true)
                    }
                    if (state.data.patients.isEmpty() && state.data.invitations.isEmpty()) {
                        EmptyPatients(onInviteClick = { showManageSheet = true })
                    } else {
                        PatientListContent(
                            patients = filtered,
                            cumplimientoPorPaciente = state.data.cumplimientoPorPaciente,
                            invitations = state.data.invitations,
                            query = query,
                            onQueryChange = { query = it },
                            onPatientClick = onPatientClick
                        )
                    }
                }
            }
        }

        ProfessionalBottomNavBar(
            selectedTab = ProfessionalNavTab.Patients,
            onTabSelected = { tab ->
                when (tab) {
                    ProfessionalNavTab.Home -> onHomeClick()
                    ProfessionalNavTab.Patients -> {}
                    ProfessionalNavTab.Routines -> onRoutinesClick()
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomEnd) {
        Box(
            modifier = Modifier
                .padding(bottom = 96.dp)
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable { showManageSheet = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Gestionar pacientes",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }

    if (showManageSheet) {
        val patients = (uiState as? UiState.Success)?.data?.patients.orEmpty()
        val invitations = (uiState as? UiState.Success)?.data?.invitations.orEmpty()
        ManagePatientsSheet(
            patients = patients,
            invitations = invitations,
            inviteState = inviteState,
            onInvite = { email -> viewModel.invitePatient(email) },
            onCancelInvitation = { viewModel.cancelInvitation(it) },
            onRemovePatient = { viewModel.removePatient(it) },
            onDismiss = { showManageSheet = false }
        )
    }
}

@Composable
private fun PatientListContent(
    patients: List<PatientSummary>,
    cumplimientoPorPaciente: Map<String, Int>,
    invitations: List<Invitation>,
    query: String,
    onQueryChange: (String) -> Unit,
    onPatientClick: (idPaciente: String, nombre: String, edad: Int?) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            DomiTextField(
                value = query,
                onValueChange = onQueryChange,
                label = "BUSCAR",
                placeholder = "Buscar paciente..."
            )
        }
        items(patients, key = { it.idPaciente }) { patient ->
            PatientCard(
                patient = patient,
                cumplimiento = cumplimientoPorPaciente[patient.idPaciente],
                onClick = {
                    // Sin consentimiento el detalle solo mostraría errores de acceso.
                    if (!patient.consentimientoRevocado) {
                        onPatientClick(patient.idPaciente, patient.nombre, patient.edad)
                    }
                }
            )
        }
        items(invitations, key = { it.idInvitacion }) { invitation ->
            PendingInvitationCard(invitation)
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun PatientCard(
    patient: PatientSummary,
    cumplimiento: Int?,
    onClick: () -> Unit
) {
    // Revocó el consentimiento: se ve el vínculo, no los datos (RF-48).
    val cumplimientoLabel = when {
        patient.consentimientoRevocado -> "Revocó su consentimiento"
        cumplimiento != null -> "Cumplimiento $cumplimiento%"
        else -> "Sin sesiones registradas"
    }
    val ultimaSesionLabel = if (patient.consentimientoRevocado) {
        "sus datos no están disponibles"
    } else {
        formatUltimaSesion(patient.fechaUltimaSesion)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(onClick = onClick)
            .padding(16.dp)
            .semantics {
                contentDescription = "Paciente ${patient.nombre}, $cumplimientoLabel, $ultimaSesionLabel"
                role = Role.Button
            }
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape)
                    .padding(2.dp)
                    .background(MaterialTheme.colorScheme.secondary, CircleShape)
            )
        }

        Spacer(modifier = Modifier.width(20.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = patient.nombre,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = cumplimientoLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = ultimaSesionLabel.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium,
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
private fun PendingInvitationCard(invitation: Invitation) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.7f))
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(color = MaterialTheme.colorScheme.surfaceContainer, shape = CircleShape),
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = invitation.email,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "PENDIENTE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = "Invitación enviada ${formatInvitacionFecha(invitation.fechaInvitacion)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManagePatientsSheet(
    patients: List<PatientSummary>,
    invitations: List<Invitation>,
    inviteState: UiState<Unit>?,
    onInvite: (String) -> Unit,
    onCancelInvitation: (String) -> Unit,
    onRemovePatient: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var patientToRemove by remember { mutableStateOf<PatientSummary?>(null) }
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(inviteState) {
        if (inviteState is UiState.Success) email = ""
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = "Gestionar pacientes",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Agregar paciente",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            DomiTextField(
                value = email,
                onValueChange = { email = it },
                label = "EMAIL",
                placeholder = "Ingresá el email del paciente"
            )
            Spacer(modifier = Modifier.height(12.dp))
            GradientButton(
                text = "Invitar",
                onClick = { onInvite(email.trim()) },
                enabled = email.isNotBlank(),
                isLoading = inviteState is UiState.Loading,
                contentDesc = "Invitar paciente"
            )
            if (inviteState is UiState.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = inviteState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Le enviaremos una invitación para sumarse a tu seguimiento.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Pacientes actuales",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                patients.forEach { patient ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = patient.nombre,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { patientToRemove = patient }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Eliminar a ${patient.nombre} de mis pacientes",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
                invitations.forEach { invitation ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = invitation.email,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "PENDIENTE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        IconButton(onClick = { onCancelInvitation(invitation.idInvitacion) }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Cancelar invitación a ${invitation.email}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onDismiss)
            ) {
                Text(
                    text = "Listo",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    patientToRemove?.let { patient ->
        Dialog(onDismissRequest = { patientToRemove = null }) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(24.dp)
            ) {
                Text(
                    text = "¿Eliminar a ${patient.nombre}?",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Dejará de estar en tu lista de pacientes. Su cuenta y su historial no se borran.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .clickable {
                            onRemovePatient(patient.idPaciente)
                            patientToRemove = null
                        }
                ) {
                    Text(
                        text = "Sí, eliminar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { patientToRemove = null }) {
                    Text("Cancelar")
                }
            }
        }
    }
}

@Composable
private fun EmptyPatients(onInviteClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Aún no tenés pacientes",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Invitá a un paciente para empezar a acompañar su rehabilitación.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        PrimaryButton(
            text = "Invitar paciente",
            onClick = onInviteClick,
            contentDesc = "Invitar un paciente nuevo"
        )
    }
}

// El backend guarda UTC; si el string no trae offset se asume UTC y se pasa a fecha local.
private fun parseToLocalDate(iso: String): LocalDate? = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
}.getOrNull()

private fun formatUltimaSesion(iso: String?): String {
    val date = iso?.let(::parseToLocalDate) ?: return "sin sesiones"
    val today = LocalDate.now()
    return when (date) {
        today -> "última sesión hoy"
        today.minusDays(1) -> "última sesión ayer"
        else -> "última sesión ${date.format(DateTimeFormatter.ofPattern("d MMM", Locale("es")))}"
    }
}

private fun formatInvitacionFecha(iso: String): String {
    val date = parseToLocalDate(iso) ?: return "recientemente"
    val today = LocalDate.now()
    return when (date) {
        today -> "hoy"
        today.minusDays(1) -> "ayer"
        else -> "el ${date.format(DateTimeFormatter.ofPattern("d MMM", Locale("es")))}"
    }
}
