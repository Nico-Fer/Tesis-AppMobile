package com.steadypath.app.presentation.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Aviso permanente en el inicio mientras el consentimiento esté revocado (RF-48): sin él,
 * la persona se enteraría recién al tocar «Comenzar» de que la app no registra sesiones.
 */
@Composable
internal fun ConsentRevokedBanner(onPrivacyClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(20.dp)
    ) {
        Text(
            text = "Revocaste tu consentimiento",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Tus datos siguen guardados, pero tu profesional no los ve y la app no " +
                "registra sesiones nuevas. Para volver a ejercitar, volvé a dar tu consentimiento.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
        TextButton(onClick = onPrivacyClick) {
            Text(
                text = "Ir a Privacidad y datos",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
