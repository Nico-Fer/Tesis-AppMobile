package com.steadypath.app.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.steadypath.app.R

/**
 * Logotipo "DOMI.IA" — la marca en top bars y headers.
 *
 * El navy del wordmark es ilegible sobre el surface oscuro, así que hay una variante
 * recoloreada en `drawable-night-nodpi` que Android resuelve solo según el tema.
 */
@Composable
fun DomiWordmark(
    modifier: Modifier = Modifier,
    height: Dp = 28.dp,
    contentDescription: String? = "DOMI.IA"
) {
    Image(
        painter = painterResource(R.drawable.domi_wordmark),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier.height(height)
    )
}

/**
 * Logo completo (isotipo + wordmark) sobre tarjeta blanca redondeada, como en los mocks.
 *
 * El blanco es deliberado y va en ambos temas: el degradé teal→azul del isotipo está
 * pensado sobre fondo claro y la tarjeta lo aísla del surface, sin recurrir a un borde.
 */
@Composable
fun DomiLogoLockup(
    modifier: Modifier = Modifier,
    size: Dp = 112.dp,
    contentDescription: String? = "DOMI.IA"
) {
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Image(
            painter = painterResource(R.drawable.domi_logo),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        )
    }
}
