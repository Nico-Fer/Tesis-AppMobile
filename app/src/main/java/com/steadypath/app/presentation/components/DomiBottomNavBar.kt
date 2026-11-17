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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

enum class BottomNavTab { Home, Routines, Progress }

@Composable
fun DomiBottomNavBar(
    selectedTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit,
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
                isSelected = selectedTab == BottomNavTab.Home,
                onClick = { onTabSelected(BottomNavTab.Home) }
            )
            NavBarItem(
                icon = Icons.Filled.FitnessCenter,
                label = "Mi Rutina",
                isSelected = selectedTab == BottomNavTab.Routines,
                onClick = { onTabSelected(BottomNavTab.Routines) }
            )
            NavBarItem(
                icon = Icons.Filled.Insights,
                label = "Progreso",
                isSelected = selectedTab == BottomNavTab.Progress,
                onClick = { onTabSelected(BottomNavTab.Progress) }
            )
        }
    }
}

/**
 * Ícono y rótulo forman un solo blanco táctil. El rótulo va en `titleMedium` (18 sp), el
 * piso del sistema de diseño para texto de lectura: a 11 sp en mayúsculas, una participante
 * de la prueba con usuarios no podía leerlo. Cada ítem toma un tercio del ancho para que,
 * con la letra del sistema agrandada, el rótulo pase a dos líneas en vez de desbordar.
 */
@Composable
private fun RowScope.NavBarItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = if (isSelected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isSelected) Modifier.background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                ) else Modifier
            )
            .clickable(role = Role.Tab, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(vertical = 6.dp)
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
