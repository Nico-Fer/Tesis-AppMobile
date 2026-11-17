package com.steadypath.app.presentation.screens.realtime

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Refuerzo no visual de las correcciones (TTS + vibración): el público objetivo son
 * adultos mayores que no miran la pantalla mientras ejercitan.
 */
class FeedbackEffects(context: Context) {

    private var ttsReady = false
    private val tts = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            setSpanishLocale()
        }
    }

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var lastSpokenAtMs = 0L
    private var lastEncouragementAtMs = 0L

    private fun setSpanishLocale() {
        val esAr = Locale("es", "AR")
        val result = tts.setLanguage(esAr)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale("es"))
        }
    }

    /**
     * Lee la corrección en voz alta, con rate-limit: correcciones consecutivas muy
     * seguidas se pisan entre sí y aturden en vez de ayudar.
     */
    fun speakCorrection(message: String) {
        if (!ttsReady || message.isBlank()) return
        val now = System.currentTimeMillis()
        if (now - lastSpokenAtMs < MIN_SPEECH_GAP_MS) return
        lastSpokenAtMs = now
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "correction")
    }

    /** Pulso corto que marca "esta repetición necesita corrección". */
    fun vibrateCorrection() {
        vibrator?.vibrate(
            VibrationEffect.createOneShot(VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }

    /**
     * Refuerzo positivo hablado, con cooldown propio más largo: anima sin aturdir a
     * cadencia de una rep cada ~2 s, y nunca pisa una corrección reciente.
     */
    fun speakEncouragement(message: String) {
        if (!ttsReady || message.isBlank()) return
        val now = System.currentTimeMillis()
        if (now - lastEncouragementAtMs < MIN_ENCOURAGEMENT_GAP_MS) return
        if (now - lastSpokenAtMs < MIN_SPEECH_GAP_MS) return
        lastEncouragementAtMs = now
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "encouragement")
    }

    /** Conteo hablado de la racha de reps correctas: corto, una por rep. */
    fun speakRepCount(count: Int) {
        if (!ttsReady || count <= 0) return
        val now = System.currentTimeMillis()
        // No pisar una corrección recién hablada; el número suelto puede esperar a la próxima.
        if (now - lastSpokenAtMs < MIN_REP_COUNT_GAP_MS) return
        tts.speak(count.toString(), TextToSpeech.QUEUE_FLUSH, null, "rep-count")
    }

    /** Anuncio importante (fin de serie): siempre habla y pisa lo que esté sonando. */
    fun speakAnnouncement(message: String) {
        if (!ttsReady || message.isBlank()) return
        lastSpokenAtMs = System.currentTimeMillis()
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "announcement")
    }

    fun release() {
        tts.stop()
        tts.shutdown()
    }

    companion object {
        /** TODO: calibrar con datos (¿aturde o ayuda a esta cadencia?). */
        private const val MIN_SPEECH_GAP_MS = 4_000L
        private const val MIN_ENCOURAGEMENT_GAP_MS = 8_000L
        private const val MIN_REP_COUNT_GAP_MS = 1_500L
        private const val VIBRATION_MS = 250L
    }
}

/** Instancia atada a la composición: se libera el TTS al salir de la pantalla. */
@Composable
fun rememberFeedbackEffects(): FeedbackEffects {
    val context = LocalContext.current
    val effects = remember { FeedbackEffects(context) }
    DisposableEffect(Unit) {
        onDispose { effects.release() }
    }
    return effects
}
