package com.steadypath.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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

/** Mismo criterio que [DomiBottomNavBar]: rótulo de 18 sp y un tercio del ancho por ítem. */
@Composable
private fun RowScope.NavBarItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = if (isSelected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant
    val background = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(role = Role.Tab, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) { selected = isSelected }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            textAlign = TextAlign.Center
        )
    }
}
