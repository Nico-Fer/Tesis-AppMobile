package com.steadypath.app.presentation.screens.settings

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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.components.DomiTextField
import com.steadypath.app.presentation.components.DomiTopBar
import com.steadypath.app.presentation.components.GradientButton

/** Compartida entre paciente y profesional: es el mismo cambio de contraseña de Cognito para los dos. */
@Composable
fun ChangePasswordScreen(
    onPasswordChanged: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ChangePasswordViewModel = hiltViewModel()
) {
    val currentPassword by viewModel.currentPassword.collectAsStateWithLifecycle()
    val newPassword by viewModel.newPassword.collectAsStateWithLifecycle()
    val confirmPassword by viewModel.confirmPassword.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val passwordChanged by viewModel.passwordChanged.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DomiTopBar(title = "Cambiar contraseña", showBackArrow = true, onBackClick = onNavigateBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Text(
                text = "Por tu seguridad, ingresá tu contraseña actual y elegí una nueva.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            DomiTextField(
                value = currentPassword,
                onValueChange = viewModel::onCurrentPasswordChange,
                label = "CONTRASEÑA ACTUAL",
                placeholder = "••••••••",
                isPassword = true
            )
            Spacer(modifier = Modifier.height(16.dp))
            DomiTextField(
                value = newPassword,
                onValueChange = viewModel::onNewPasswordChange,
                label = "NUEVA CONTRASEÑA",
                placeholder = "Mínimo 8 caracteres",
                isPassword = true
            )
            Spacer(modifier = Modifier.height(16.dp))
            DomiTextField(
                value = confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                label = "CONFIRMAR NUEVA CONTRASEÑA",
                placeholder = "Repetí la nueva contraseña",
                isPassword = true
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
                text = "Cambiar contraseña",
                onClick = viewModel::changePassword,
                enabled = viewModel.esFormularioValido(),
                isLoading = saveState is UiState.Loading,
                contentDesc = "Cambiar mi contraseña"
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (passwordChanged) {
        PasswordChangedDialog(onConfirm = onPasswordChanged)
    }
}

@Composable
private fun PasswordChangedDialog(onConfirm: () -> Unit) {
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
                text = "Contraseña actualizada",
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
