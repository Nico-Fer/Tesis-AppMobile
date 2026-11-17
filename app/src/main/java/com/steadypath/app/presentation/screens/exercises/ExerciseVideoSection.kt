package com.steadypath.app.presentation.screens.exercises

import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.steadypath.app.R
import com.steadypath.app.domain.model.ExerciseVideo
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.ui.theme.MediaSurface
import com.steadypath.app.presentation.ui.theme.OnMediaSurface

/**
 * Área de video del detalle del ejercicio. Los videos se reproducen en
 * secuencia y arrancan solos; un tap alterna pausa/reproducción.
 */
@Composable
fun ExerciseVideoSection(
    videosState: UiState<List<ExerciseVideo>>,
    exerciseName: String
) {
    when (videosState) {
        is UiState.Loading -> VideoPlaceholder(exerciseName) {
            CircularProgressIndicator(color = OnMediaSurface)
        }
        is UiState.Success<*> -> {
            @Suppress("UNCHECKED_CAST")
            val videos = videosState.data as List<ExerciseVideo>
            if (videos.isEmpty()) {
                VideoPlaceholder(exerciseName) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.VideocamOff,
                            contentDescription = null,
                            tint = OnMediaSurface,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Este ejercicio no tiene video",
                            style = MaterialTheme.typography.titleMedium,
                            color = OnMediaSurface
                        )
                    }
                }
            } else {
                VideoPlayer(videos, exerciseName)
            }
        }
        else -> VideoPlaceholder(exerciseName) {}
    }
}

@Composable
private fun VideoPlaceholder(
    exerciseName: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(MediaSurface)
            .semantics { contentDescription = "Video del ejercicio $exerciseName" },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun VideoPlayer(
    videos: List<ExerciseVideo>,
    exerciseName: String
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }

    // Las URLs firmadas van directo a S3 por el stack HTTP de ExoPlayer (sin AuthInterceptor).
    val player = remember(videos) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItems(videos.map { MediaItem.fromUri(it.url) })
            // Loop infinito de la playlist: al terminar el último vuelve al primero.
            repeatMode = Player.REPEAT_MODE_ALL
            // Es una demostración visual del movimiento, no tiene audio relevante —
            // y arrancar con sonido en una pantalla que no lo pide sorprende.
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(MediaSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                player.playWhenReady = !player.playWhenReady
            }
            .semantics { contentDescription = "Reproductor de video del ejercicio $exerciseName" }
    ) {
        AndroidView(
            factory = { ctx ->
                // Se infla desde XML para poder pedir surface_type="texture_view";
                // ver el comentario en res/layout/view_exercise_player.xml.
                val view = LayoutInflater.from(ctx)
                    .inflate(R.layout.view_exercise_player, null) as PlayerView
                view.player = player
                view
            },
            // Soltar el player antes de que la vista muera evita que quede pintado
            // el último frame durante la animación de salida.
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize()
        )

        if (!isPlaying) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Reanudar video",
                tint = OnMediaSurface,
                modifier = Modifier
                    .size(64.dp)
                    .align(Alignment.Center)
            )
        }
    }
}
