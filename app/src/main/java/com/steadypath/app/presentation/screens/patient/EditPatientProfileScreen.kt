package com.steadypath.app.presentation.screens.patient

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.components.DomiTextField
import com.steadypath.app.presentation.components.DomiTopBar
import com.steadypath.app.presentation.components.DeleteAccountSection
import com.steadypath.app.presentation.components.GradientButton

@Composable
fun EditPatientProfileScreen(
    onProfileSaved: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: EditPatientProfileViewModel = hiltViewModel(),
    onAccountDeleted: () -> Unit = {}
) {
    val nombre by viewModel.nombre.collectAsStateWithLifecycle()
    val telefono by viewModel.telefono.collectAsStateWithLifecycle()
    val email by viewModel.email.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val profileSaved by viewModel.profileSaved.collectAsStateWithLifecycle()
    var showConfirmDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadProfile() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DomiTopBar(title = "Editar perfil", showBackArrow = true, onBackClick = onNavigateBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(128.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            DomiTextField(
                value = nombre,
                onValueChange = viewModel::onNombreChange,
                label = "NOMBRE COMPLETO",
                placeholder = "Tu nombre"
            )
            Spacer(modifier = Modifier.height(24.dp))
            DomiTextField(
                value = telefono,
                onValueChange = viewModel::onTelefonoChange,
                label = "TELÉFONO",
                placeholder = "11 5555-5555",
                keyboardType = KeyboardType.Phone
            )
            Spacer(modifier = Modifier.height(24.dp))
            DomiTextField(
                value = email,
                onValueChange = {},
                label = "CORREO ELECTRÓNICO",
                placeholder = "tu@email.com",
                enabled = false
            )

            if (saveState is UiState.Error) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = (saveState as UiState.Error).message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            GradientButton(
                text = "Guardar cambios",
                onClick = { showConfirmDialog = true },
                enabled = nombre.isNotBlank(),
                isLoading = saveState is UiState.Loading,
                contentDesc = "Guardar cambios del perfil"
            )

            Spacer(modifier = Modifier.height(32.dp))

            DeleteAccountSection(onAccountDeleted = onAccountDeleted)

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showConfirmDialog) {
        ConfirmSaveDialog(
            onConfirm = {
                showConfirmDialog = false
                viewModel.saveProfile()
            },
            onDismiss = { showConfirmDialog = false }
        )
    }

    if (profileSaved) {
        ProfileSavedDialog(onConfirm = onProfileSaved)
    }
}

@Composable
private fun ConfirmSaveDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
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
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "¿Confirmás los cambios?",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Vas a actualizar los datos de tu perfil.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            GradientButton(
                text = "Sí, guardar cambios",
                onClick = onConfirm,
                contentDesc = "Sí, guardar los cambios del perfil"
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    }
}

@Composable
private fun ProfileSavedDialog(onConfirm: () -> Unit) {
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
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Perfil actualizado",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            GradientButton(
                text = "Entendido",
                onClick = onConfirm,
                contentDesc = "Volver al perfil"
            )
        }
    }
}
