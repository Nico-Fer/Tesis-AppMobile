package com.steadypath.app.presentation.screens.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import com.steadypath.app.presentation.components.TipoProfesionalDropdown
import com.steadypath.app.presentation.components.PrimaryButton

@Composable
fun DoctorRegistrationScreen(
    onRegistrationSuccess: () -> Unit,
    onNeedsConfirmation: (email: String) -> Unit,
    onLoginClick: () -> Unit,
    onBackClick: () -> Unit,
    viewModel: DoctorRegistrationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val nombre by viewModel.nombre.collectAsStateWithLifecycle()
    val tipoProfesional by viewModel.tipoProfesional.collectAsStateWithLifecycle()
    val matricula by viewModel.matricula.collectAsStateWithLifecycle()
    val institucion by viewModel.institucion.collectAsStateWithLifecycle()
    val email by viewModel.email.collectAsStateWithLifecycle()
    val contrasena by viewModel.contrasena.collectAsStateWithLifecycle()
    val certChecked by viewModel.certChecked.collectAsStateWithLifecycle()
    val requiereMatricula by viewModel.requiereMatricula.collectAsStateWithLifecycle()
    val isFormValid by viewModel.isFormValid.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                DoctorRegistrationViewModel.NavigationEvent.RegistrationCompleted ->
                    onRegistrationSuccess()
                is DoctorRegistrationViewModel.NavigationEvent.NeedsConfirmation ->
                    onNeedsConfirmation(event.email)
                DoctorRegistrationViewModel.NavigationEvent.GoToLogin -> onLoginClick()
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
                text = "Registro de profesional",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Creá tu cuenta profesional",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            TipoProfesionalDropdown(
                value = tipoProfesional,
                onValueChange = viewModel::onTipoProfesionalChange
            )

            Spacer(modifier = Modifier.height(16.dp))

            DomiTextField(
                value = nombre,
                onValueChange = viewModel::onNombreChange,
                label = "NOMBRE COMPLETO",
                placeholder = "Juan Pérez"
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (requiereMatricula) {
                Text(
                    text = "REQUERIDO PARA TU PROFESIÓN",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                DomiTextField(
                    value = matricula,
                    onValueChange = viewModel::onMatriculaChange,
                    label = "NÚMERO DE MATRÍCULA",
                    placeholder = "MP-12345678"
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.medium
                        )
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Para tu tipo de perfil no hace falta matrícula.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            DomiTextField(
                value = institucion,
                onValueChange = viewModel::onInstitucionChange,
                label = "INSTITUCIÓN (OPCIONAL)",
                placeholder = "Dónde trabajás o atendés"
            )

            Spacer(modifier = Modifier.height(16.dp))

            DomiTextField(
                value = email,
                onValueChange = viewModel::onEmailChange,
                label = "CORREO ELECTRÓNICO",
                placeholder = "juan@hospital.org",
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

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.semantics {
                    contentDescription = "Certificar credenciales válidas"
                }
            ) {
                Checkbox(
                    checked = certChecked,
                    onCheckedChange = viewModel::onCertCheckedChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary
                    )
                )
                Text(
                    text = buildAnnotatedString {
                        append("Certifico que los datos ingresados son correctos y acepto los ")
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        ) {
                            append("Términos de Servicio")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (uiState is UiState.Error) {
                Text(
                    text = (uiState as UiState.Error).message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            PrimaryButton(
                text = "Registrarme",
                onClick = viewModel::register,
                enabled = isFormValid,
                isLoading = uiState is UiState.Loading,
                contentDesc = "Registrarme como profesional"
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "© 2024 DOMI.IA Health. Todos los derechos reservados.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
