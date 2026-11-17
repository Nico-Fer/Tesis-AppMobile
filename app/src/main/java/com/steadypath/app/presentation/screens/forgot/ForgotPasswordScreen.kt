package com.steadypath.app.presentation.screens.forgot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.components.DomiTextField
import com.steadypath.app.presentation.components.DomiTopBar
import com.steadypath.app.presentation.components.GradientButton

@Composable
fun ForgotPasswordScreen(
    onBackToLogin: () -> Unit,
    viewModel: ForgotPasswordViewModel = hiltViewModel()
) {
    val paso by viewModel.paso.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val infoMessage by viewModel.infoMessage.collectAsStateWithLifecycle()
    val email by viewModel.email.collectAsStateWithLifecycle()
    val code by viewModel.code.collectAsStateWithLifecycle()
    val newPassword by viewModel.newPassword.collectAsStateWithLifecycle()
    val confirmPassword by viewModel.confirmPassword.collectAsStateWithLifecycle()
    val cargando = uiState is UiState.Loading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DomiTopBar(showLogo = true, showBackArrow = true, onBackClick = onBackToLogin)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(24.dp)
            ) {
                Text(
                    text = if (paso == ForgotPasswordViewModel.Paso.LISTO) "Contraseña cambiada"
                    else "Recuperar contraseña",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when (paso) {
                        ForgotPasswordViewModel.Paso.EMAIL ->
                            "Escribí el email de tu cuenta y te mandamos un código para elegir una contraseña nueva."
                        ForgotPasswordViewModel.Paso.CODIGO ->
                            "Te mandamos un código a $email. Escribilo junto con tu contraseña nueva."
                        ForgotPasswordViewModel.Paso.LISTO ->
                            "Ya podés iniciar sesión con tu contraseña nueva."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                when (paso) {
                    ForgotPasswordViewModel.Paso.EMAIL -> {
                        DomiTextField(
                            value = email,
                            onValueChange = viewModel::onEmailChange,
                            label = "EMAIL",
                            placeholder = "nombre@correo.com",
                            keyboardType = KeyboardType.Email
                        )
                    }
                    ForgotPasswordViewModel.Paso.CODIGO -> {
                        DomiTextField(
                            value = code,
                            onValueChange = viewModel::onCodeChange,
                            label = "CÓDIGO DE VERIFICACIÓN",
                            placeholder = "123456",
                            keyboardType = KeyboardType.Number
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        DomiTextField(
                            value = newPassword,
                            onValueChange = viewModel::onNewPasswordChange,
                            label = "CONTRASEÑA NUEVA",
                            placeholder = "••••••••",
                            isPassword = true
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        DomiTextField(
                            value = confirmPassword,
                            onValueChange = viewModel::onConfirmPasswordChange,
                            label = "REPETIR CONTRASEÑA NUEVA",
                            placeholder = "••••••••",
                            isPassword = true
                        )
                    }
                    ForgotPasswordViewModel.Paso.LISTO -> Unit
                }

                if (paso != ForgotPasswordViewModel.Paso.LISTO) {
                    Spacer(modifier = Modifier.height(32.dp))
                }

                if (uiState is UiState.Error) {
                    Text(
                        text = (uiState as UiState.Error).message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                infoMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                when (paso) {
                    ForgotPasswordViewModel.Paso.EMAIL -> GradientButton(
                        text = "Enviar código →",
                        onClick = viewModel::requestCode,
                        enabled = email.isNotBlank(),
                        isLoading = cargando,
                        contentDesc = "Enviar código de recuperación"
                    )
                    ForgotPasswordViewModel.Paso.CODIGO -> {
                        GradientButton(
                            text = "Cambiar contraseña →",
                            onClick = viewModel::confirm,
                            enabled = code.isNotBlank() && newPassword.isNotBlank() &&
                                confirmPassword.isNotBlank(),
                            isLoading = cargando,
                            contentDesc = "Cambiar contraseña"
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                            onClick = viewModel::requestCode,
                            enabled = !cargando,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(
                                text = "Reenviar código",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        TextButton(
                            onClick = viewModel::changeEmail,
                            enabled = !cargando,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(
                                text = "Cambiar el email",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    ForgotPasswordViewModel.Paso.LISTO -> GradientButton(
                        text = "Iniciar sesión →",
                        onClick = onBackToLogin,
                        contentDesc = "Volver a iniciar sesión"
                    )
                }
            }
        }
    }
}
