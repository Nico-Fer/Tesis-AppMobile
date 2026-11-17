package com.steadypath.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class ProfessionalNavTab { Home, Patients, Routines }

/**
 * Bottom nav propio del rol profesional (Inicio/Pacientes/Rutinas): no comparte
 * tabs con [DomiBottomNavBar], que es fija a las 3 secciones del paciente.
 */
@Composable
fun ProfessionalBottomNavBar(
    selectedTab: ProfessionalNavTab,
    onTabSelected: (ProfessionalNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavBarItem(
                icon = Icons.Filled.Home,
                label = "Inicio",
                isSelected = selectedTab == ProfessionalNavTab.Home,
                onClick = { onTabSelected(ProfessionalNavTab.Home) }
            )
            NavBarItem(
                icon = Icons.Filled.Group,
                label = "Pacientes",
                isSelected = selectedTab == ProfessionalNavTab.Patients,
                onClick = { onTabSelected(ProfessionalNavTab.Patients) }
            )
            NavBarItem(
                icon = Icons.Filled.FitnessCenter,
                label = "Rutinas",
                isSelected = selectedTab == ProfessionalNavTab.Routines,
                onClick = { onTabSelected(ProfessionalNavTab.Routines) }
            )
        }
    }
}

@Composable
private fun NavBarItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = if (isSelected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant
    val background = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val horizontalPadding = if (isSelected) 32.dp else 24.dp
    val verticalPadding = if (isSelected) 12.dp else 8.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
            .semantics { contentDescription = label }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = color
        )
    }
}
