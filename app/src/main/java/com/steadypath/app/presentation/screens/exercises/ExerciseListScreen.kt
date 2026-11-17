package com.steadypath.app.presentation.screens.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.steadypath.app.domain.model.Exercise
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.components.BottomNavTab
import com.steadypath.app.presentation.components.CatalogExerciseCard
import com.steadypath.app.presentation.components.DomiBottomNavBar
import com.steadypath.app.presentation.components.DomiTopBar

@Composable
fun ExerciseListScreen(
    onExerciseClick: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ExerciseListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DomiTopBar(
            title = "Catálogo",
            showBackArrow = true,
            onBackClick = onNavigateBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Todos los ejercicios",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Mirá todos los ejercicios.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            when (val state = uiState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is UiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is UiState.Success<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val exercises = state.data as List<Exercise>
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(exercises, key = { it.idEjercicio }) { exercise ->
                            CatalogExerciseCard(
                                exercise = exercise,
                                onClick = { onExerciseClick(exercise.idEjercicio) }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }

        val onTabSelected = remember(onNavigateBack) {
            { tab: BottomNavTab ->
                when (tab) {
                    BottomNavTab.Home -> onNavigateBack()
                    else -> {}
                }
            }
        }
        DomiBottomNavBar(
            selectedTab = BottomNavTab.Routines,
            onTabSelected = onTabSelected
        )
    }
}

