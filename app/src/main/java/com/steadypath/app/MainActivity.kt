package com.steadypath.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import com.steadypath.app.presentation.components.AvisoDeConexion
import com.steadypath.app.presentation.components.AvisoDeConexionViewModel
import com.steadypath.app.presentation.components.avisoPara
import com.steadypath.app.presentation.navigation.AuthGraph
import com.steadypath.app.presentation.navigation.ExerciseCountdown
import com.steadypath.app.presentation.navigation.Login
import com.steadypath.app.presentation.navigation.PrepareSpace
import com.steadypath.app.presentation.navigation.RealtimeExercise
import com.steadypath.app.presentation.navigation.RecordOwnReference
import com.steadypath.app.presentation.navigation.RecordPatientReference
import com.steadypath.app.presentation.navigation.ReferenceCapture
import com.steadypath.app.presentation.navigation.Splash

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
                val avisoViewModel: AvisoDeConexionViewModel = hiltViewModel()
                val estado by avisoViewModel.estado.collectAsStateWithLifecycle()
                val destino = navController.currentBackStackEntryAsState().value?.destination

                // En las pantallas de cámara no se muestra: que la franja aparezca o se vaya
                // en medio de un ejercicio mueve el visor justo cuando la persona está
                // mirando su postura. Lo que haga igual queda en la cola y se avisa al salir.
                val aviso = if (destino == null || destino.esDeCamaraODeArranque()) {
                    null
                } else {
                    avisoPara(estado, conDatosGuardados = !destino.esDeIngreso())
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    AvisoDeConexion(
                        aviso = aviso,
                        onIniciarSesion = {
                            // Vacía toda la pila: desde Login no se debe poder volver
                            // atrás a pantallas de una sesión que ya no sirve.
                            navController.navigate(Login) {
                                popUpTo(navController.graph.id) { inclusive = true }
                            }
                        },
                        onCerrarAvisoDeRechazo = avisoViewModel::avisoDeRechazoVisto
                    )
                    // Con la franja visible el margen superior (recorte de la cámara frontal)
                    // ya lo ocupa ella: las pantallas no deben volver a dejarlo.
                    val sinMargenSuperior = if (aviso != null) {
                        Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    } else {
                        Modifier
                    }
                    Box(modifier = Modifier.weight(1f).then(sinMargenSuperior)) {
                        NavGraph(navController = navController)
                    }
                }
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

private val RUTAS_DE_CAMARA_O_ARRANQUE = listOf(
    Splash::class,
    PrepareSpace::class,
    ExerciseCountdown::class,
    RealtimeExercise::class,
    RecordOwnReference::class,
    RecordPatientReference::class,
    ReferenceCapture::class
)

private fun NavDestination.esDeCamaraODeArranque(): Boolean =
    RUTAS_DE_CAMARA_O_ARRANQUE.any { hasRoute(it) }

private fun NavDestination.esDeIngreso(): Boolean = hierarchy.any { it.hasRoute(AuthGraph::class) }
