package com.steadypath.app.presentation.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

/**
 * Botón de emergencia: visible durante toda la sesión de ejercicio activa (contando
 * repeticiones o en el descanso entre series), no solo en algún estado puntual — una caída o
 * un mareo son el escenario de riesgo real en el público adulto mayor de la app, y pueden
 * pasar en cualquier momento de la sesión.
 *
 * Deliberadamente más chico que [GradientButton] y de esquinas suavemente redondeadas en vez
 * de circular, siguiendo el mismo criterio del sistema de componentes de la app: las formas
 * muy redondeadas o circulares resultan menos claras respecto de su función. El tamaño y el
 * color distinguen esto del flujo normal del ejercicio sin competir visualmente con la acción
 * principal (Comenzar/Finalizar).
 *
 * No pide confirmación por diálogo: un toque inicia una cuenta regresiva corta que llama sola
 * si no se cancela. Es el mismo criterio que usan los sistemas de SOS reales (el del Apple
 * Watch, por ejemplo) y no el de un diálogo sí/no — acá la asimetría del error corre al revés
 * que en una acción destructiva: tardar de más por una confirmación cuesta más caro que una
 * llamada de más, sobre todo para alguien mareado o recién caído que tiene que resolver la
 * interacción bajo estrés. Si el permiso de llamada no está o se niega, cae a `ACTION_DIAL`:
 * el teclado queda con el número ya cargado, así el gesto no termina en un callejón sin salida.
 */
@Composable
fun EmergencyCallButton(
    modifier: Modifier = Modifier,
    emergencyNumber: String = EMERGENCY_NUMBER_ARGENTINA
) {
    val context = LocalContext.current
    // null = reposo; con valor = cuenta regresiva en curso.
    var secondsLeft by remember { mutableStateOf<Int?>(null) }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> placeCall(context, emergencyNumber, granted) }

    val counting = secondsLeft
    if (counting == null) {
        Button(
            onClick = { secondsLeft = COUNTDOWN_SECONDS },
            modifier = modifier
                .fillMaxWidth()
                .height(48.dp)
                .semantics { contentDescription = "Emergencia: llamar al $emergencyNumber" },
            shape = RoundedCornerShape(14.dp),
            // Rojo sólido (no el errorContainer del badge de "corregir postura"): esto es una
            // escalada real, no un aviso de ejecución — no deben verse como la misma jerarquía.
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Sos,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Emergencia",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    } else {
        LaunchedCountdown(
            secondsLeft = counting,
            onTick = { secondsLeft = it },
            onFinished = {
                secondsLeft = null
                val hasPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CALL_PHONE
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    placeCall(context, emergencyNumber, hasPermission = true)
                } else {
                    callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                }
            }
        )

        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.error)
                .padding(horizontal = 16.dp)
                .semantics {
                    contentDescription = "Llamando al $emergencyNumber en $counting segundos"
                    // Anuncia cada cambio por lector de pantalla sin que la persona tenga que
                    // volver a enfocar el elemento — importante bajo estrés.
                    liveRegion = LiveRegionMode.Assertive
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Llamando en ${counting}s…",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onError
            )
            TextButton(onClick = { secondsLeft = null }) {
                Text(
                    text = "Cancelar",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onError
                )
            }
        }
    }
}

@Composable
private fun LaunchedCountdown(secondsLeft: Int, onTick: (Int) -> Unit, onFinished: () -> Unit) {
    LaunchedEffect(secondsLeft) {
        if (secondsLeft > 0) {
            delay(1000)
            onTick(secondsLeft - 1)
        } else {
            onFinished()
        }
    }
}

private fun placeCall(context: Context, number: String, hasPermission: Boolean) {
    val action = if (hasPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
    val intent = Intent(action, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/** Cuenta regresiva antes de llamar: alcanza para cancelar un toque accidental sin demorar
 * una emergencia real. */
private const val COUNTDOWN_SECONDS = 3

/** Número de emergencia unificado en Argentina (policía, bomberos y ambulancia). */
private const val EMERGENCY_NUMBER_ARGENTINA = "911"
