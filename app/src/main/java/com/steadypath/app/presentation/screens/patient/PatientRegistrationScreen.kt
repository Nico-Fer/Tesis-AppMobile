package com.steadypath.app.presentation.screens.patient

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.components.DomiLogoLockup
import com.steadypath.app.presentation.components.DomiTextField
import com.steadypath.app.presentation.components.DomiTopBar
import com.steadypath.app.presentation.components.PrimaryButton

@Composable
fun PatientRegistrationScreen(
    onRegistrationSuccess: () -> Unit,
    onNeedsConfirmation: (email: String) -> Unit,
    onLoginClick: () -> Unit,
    onBackClick: () -> Unit,
    onPrivacyClick: () -> Unit = {},
    viewModel: PatientRegistrationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val privacidadAceptada by viewModel.privacidadAceptada.collectAsStateWithLifecycle()
    val nombre by viewModel.nombre.collectAsStateWithLifecycle()
    val email by viewModel.email.collectAsStateWithLifecycle()
    val contrasena by viewModel.contrasena.collectAsStateWithLifecycle()
    val emailProfesional by viewModel.emailProfesional.collectAsStateWithLifecycle()
    val isFormValid by viewModel.isFormValid.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                PatientRegistrationViewModel.NavigationEvent.RegistrationCompleted ->
                    onRegistrationSuccess()
                is PatientRegistrationViewModel.NavigationEvent.NeedsConfirmation ->
                    onNeedsConfirmation(event.email)
                PatientRegistrationViewModel.NavigationEvent.GoToLogin -> onLoginClick()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DomiTopBar(
            showBackArrow = true,
            onBackClick = onBackClick,
            trailing = {
                TextButton(onClick = onLoginClick) {
                    Text(
                        text = "Iniciar Sesión",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            DomiLogoLockup(size = 112.dp)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Creá tu cuenta",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Comenzá tu camino de recuperación.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            DomiTextField(
                value = nombre,
                onValueChange = viewModel::onNombreChange,
                label = "NOMBRE COMPLETO",
                placeholder = "Juan Pérez"
            )

            Spacer(modifier = Modifier.height(16.dp))

            DomiTextField(
                value = email,
                onValueChange = viewModel::onEmailChange,
                label = "CORREO ELECTRÓNICO",
                placeholder = "juan@ejemplo.com",
                keyboardType = KeyboardType.Email
            )

            Spacer(modifier = Modifier.height(16.dp))

            DomiTextField(
                value = contrasena,
                onValueChange = viewModel::onContrasenaChange,
                label = "ELEGÍ UNA CONTRASEÑA",
                placeholder = "••••••••",
                isPassword = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            DomiTextField(
                value = emailProfesional,
                onValueChange = viewModel::onEmailProfesionalChange,
                label = "EMAIL DE TU PROFESIONAL (OPCIONAL)",
                placeholder = "medico@ejemplo.com",
                keyboardType = KeyboardType.Email
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "* Solo podés vincularte con profesionales ya registrados en DOMI.IA. " +
                    "Si no tenés uno ahora, podés agregarlo más tarde.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.semantics {
                    contentDescription = "Aceptar la política de privacidad"
                }
            ) {
                Checkbox(
                    checked = privacidadAceptada,
                    onCheckedChange = viewModel::onPrivacidadAceptadaChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary
                    )
                )
                Text(
                    text = buildAnnotatedString {
                        append("Leí y acepto la ")
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        ) {
                            append("Política de Privacidad")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 8.dp, top = 12.dp)
                        .clickable { onPrivacyClick() }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (uiState is UiState.Error) {
                Text(
                    text = (uiState as UiState.Error).message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            PrimaryButton(
                text = "Crear cuenta",
                onClick = viewModel::register,
                enabled = isFormValid,
                isLoading = uiState is UiState.Loading,
                contentDesc = "Crear cuenta de paciente"
            )


            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "© 2024 DOMI.IA Health. Todos los derechos reservados. " +
                    "Socios Profesionales de Rehabilitación.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
