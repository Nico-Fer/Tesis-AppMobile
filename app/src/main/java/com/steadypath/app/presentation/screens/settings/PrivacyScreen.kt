package com.steadypath.app.presentation.screens.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.steadypath.app.presentation.components.DomiTopBar
import com.steadypath.app.presentation.components.PrimaryButton

/**
 * Compartida entre paciente y profesional: mismo diseño, pero el copy de "quién ve tus
 * datos" cambia según quién mira la pantalla (no es simétrico: un profesional ve a sus
 * pacientes, un paciente ve a su profesional).
 */
/**
 * [onViewMyData] es null antes del registro (SignUpPrivacy): sin cuenta no hay datos que ver.
 */
@Composable
fun PrivacyScreen(
    onNavigateBack: () -> Unit,
    isPatient: Boolean = false,
    onViewMyData: (() -> Unit)? = null,
    consentSection: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DomiTopBar(title = "Privacidad y datos", showBackArrow = true, onBackClick = onNavigateBack)

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Qué datos usamos y para qué",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isPatient) {
                    "DOMI.IA solo usa la información necesaria para acompañar tu " +
                        "rehabilitación."
                } else {
                    "DOMI.IA solo usa la información necesaria para acompañar la " +
                        "rehabilitación de tus pacientes."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            PrivacyItem(
                icon = Icons.Filled.ManageAccounts,
                title = "Podés ver, corregir o borrar tus datos",
                description = "En Mis datos ves lo que guardamos sobre vos. Para corregir algo " +
                    "o borrar tu cuenta y todo lo asociado, entrá a Editar perfil: lo hacés " +
                    "vos, sin pedir permiso ni explicar por qué.\n\n" +
                    "Para cualquier otra consulta sobre tus datos, escribinos a " +
                    "$CONTACTO_PRIVACIDAD."
            )
            onViewMyData?.let {
                PrimaryButton(text = "Ver mis datos", onClick = it)
            }
            // Solo el paciente con cuenta: no antes del registro ni para el profesional.
            consentSection?.let {
                Spacer(modifier = Modifier.height(24.dp))
                it()
            }
            Spacer(modifier = Modifier.height(16.dp))
            PrivacyItem(
                icon = Icons.Filled.Gavel,
                title = "Quién responde por tus datos",
                description = "DOMI.IA es responsable del tratamiento de tus datos y los " +
                    "usa únicamente para el acompañamiento descripto acá. La Agencia de " +
                    "Acceso a la Información Pública es el organismo de control ante el " +
                    "que podés reclamar si considerás que no cumplimos."
            )
            Spacer(modifier = Modifier.height(16.dp))
            PrivacyItem(
                icon = Icons.Filled.Videocam,
                title = "Las sesiones no se graban",
                description = "El análisis de movimiento se procesa en el momento, en tu " +
                    "dispositivo. Ningún video se guarda ni se sube a ningún servidor."
            )
            PrivacyItem(
                icon = Icons.Filled.HealthAndSafety,
                title = "Datos clínicos",
                description = if (isPatient) {
                    "El dolor que reportás, tu cumplimiento y tu historial de sesiones " +
                        "se guardan para que tu profesional pueda seguir tu progreso."
                } else {
                    "El dolor reportado, el cumplimiento y el historial de " +
                        "sesiones de tus pacientes se guardan para que puedas seguir su progreso."
                }
            )
            PrivacyItem(
                icon = Icons.Filled.Groups,
                title = "Quién ve tus datos",
                description = if (isPatient) {
                    "Tus datos (dolor, cumplimiento, historial) solo los ve el " +
                        "profesional que tenés asignado. Tu profesional no ve los datos " +
                        "de otros pacientes que no sean suyos."
                } else {
                    "Tu perfil profesional (nombre, matrícula, institución) " +
                        "es visible para los pacientes que tenés a cargo. Los datos de un " +
                        "paciente solo los ve él y el profesional que lo tiene asignado."
                }
            )
            PrivacyItem(
                icon = Icons.Filled.Lock,
                title = "Seguridad de la cuenta",
                description = "El acceso se maneja con AWS Cognito. Podés cambiar tu " +
                    "contraseña cuando quieras desde Ajustes.",
                showDivider = false
            )
        }
    }
}

@Composable
private fun PrivacyItem(
    icon: ImageVector,
    title: String,
    description: String,
    showDivider: Boolean = true
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (showDivider) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
        ) {}
    }
}

/**
 * Canal de contacto por los datos personales (Ley 25.326, art. 14: respuesta en 10 días
 * corridos). Tiene que ser un correo que el equipo lea de verdad: cambiarlo acá y en el
 * informe a la vez.
 */
private const val CONTACTO_PRIVACIDAD = "domiia_privacidad@gmail.com"
