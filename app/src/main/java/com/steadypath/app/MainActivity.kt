package com.steadypath.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.rememberNavController
import com.steadypath.app.presentation.navigation.NavGraph
import com.steadypath.app.presentation.ui.theme.DomiTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Inmersivo "sticky": barra de estado y de navegación del sistema ocultas de
        // entrada en toda la app (no solo detrás del contenido, como deja enableEdgeToEdge
        // por sí solo). Un swipe desde el borde las vuelve a mostrar un momento sin salir
        // de la pantalla, así nunca quedan atrapados sin forma de verlas si las necesitan.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            DomiTheme {
                val navController = rememberNavController()
                NavGraph(navController = navController)
            }
        }
    }

    // El diálogo de permiso de cámara (y cualquier otro que le saque el foco a la
    // ventana) hace que el sistema vuelva a mostrar las barras al volver; sin esto,
    // el modo inmersivo solo duraba hasta el primer pedido de permiso.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            WindowInsetsControllerCompat(window, window.decorView)
                .hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
