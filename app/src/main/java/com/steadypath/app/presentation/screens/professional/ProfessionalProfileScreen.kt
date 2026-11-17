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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraRear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.components.DomiTopBar
import com.steadypath.app.presentation.components.LabeledField
import com.steadypath.app.presentation.components.SettingsNavRow
import com.steadypath.app.presentation.components.SignOutButton
import com.steadypath.app.presentation.components.SignOutConfirmDialog

@Composable
fun ProfessionalProfileScreen(
    onEditProfileClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onChangePasswordClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onCameraClick: () -> Unit,
    onSignedOut: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ProfessionalProfileViewModel = hiltViewModel()
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val signOutState by viewModel.signOutState.collectAsStateWithLifecycle()
    var showSignOutDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadProfile() }

    // Si falla, se cierra el diálogo para que se vea el error bajo el botón.
    LaunchedEffect(signOutState) {
        if (signOutState is UiState.Error) showSignOutDialog = false
    }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                ProfessionalProfileViewModel.NavigationEvent.SignedOut -> onSignedOut()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DomiTopBar(title = "Perfil y ajustes", showBackArrow = true, onBackClick = onNavigateBack)

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = user?.nombre.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                user?.tipoProfesional?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(20.dp)
            ) {
                LabeledField(label = "MATRÍCULA", value = user?.matricula)
                Spacer(modifier = Modifier.height(16.dp))
                LabeledField(label = "INSTITUCIÓN", value = user?.institucion)
                Spacer(modifier = Modifier.height(16.dp))
                LabeledField(label = "CORREO", value = user?.email)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable(onClick = onEditProfileClick)
                    .semantics { contentDescription = "Editar mi perfil" }
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Editar perfil",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Ajustes",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            ) {
                SettingsNavRow(
                    icon = Icons.Filled.Notifications,
                    label = "Notificaciones",
                    onClick = onNotificationsClick
                )
                SettingsNavRow(
                    icon = Icons.Filled.CameraRear,
                    label = "Cámara",
                    onClick = onCameraClick
                )
                SettingsNavRow(
                    icon = Icons.Filled.Lock,
                    label = "Cambiar contraseña",
                    onClick = onChangePasswordClick
                )
                SettingsNavRow(
                    icon = Icons.Filled.Shield,
                    label = "Privacidad y datos",
                    onClick = onPrivacyClick,
                    showDivider = false
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            SignOutButton(onClick = { showSignOutDialog = true })
            (signOutState as? UiState.Error)?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showSignOutDialog) {
        SignOutConfirmDialog(
            isLoading = signOutState is UiState.Loading,
            onConfirm = viewModel::signOut,
            onDismiss = { showSignOutDialog = false }
        )
    }
}
