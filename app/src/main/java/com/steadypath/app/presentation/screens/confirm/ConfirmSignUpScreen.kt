package com.steadypath.app.presentation.screens.confirm

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.components.GradientButton
import com.steadypath.app.presentation.components.DomiTextField
import com.steadypath.app.presentation.components.DomiTopBar

@Composable
fun ConfirmSignUpScreen(
    onCompleted: (rol: String) -> Unit,
    onGoToLogin: () -> Unit,
    viewModel: ConfirmSignUpViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val infoMessage by viewModel.infoMessage.collectAsStateWithLifecycle()

    var code by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is ConfirmSignUpViewModel.NavigationEvent.Completed -> onCompleted(event.rol)
                ConfirmSignUpViewModel.NavigationEvent.GoToLogin -> onGoToLogin()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DomiTopBar(showLogo = true, showBackArrow = false)

        Column(
            modifier = Modifier
                .fillMaxSize()
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
                    text = "Confirmá tu email",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Te mandamos un código de verificación a ${viewModel.email}.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                DomiTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = "CÓDIGO DE VERIFICACIÓN",
                    placeholder = "123456",
                    keyboardType = KeyboardType.Number
                )

                Spacer(modifier = Modifier.height(32.dp))

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

                GradientButton(
                    text = "Confirmar →",
                    onClick = { viewModel.confirm(code) },
                    enabled = code.isNotBlank(),
                    isLoading = uiState is UiState.Loading,
                    contentDesc = "Confirmar código"
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = { viewModel.resendCode() },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = "Reenviar código",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
