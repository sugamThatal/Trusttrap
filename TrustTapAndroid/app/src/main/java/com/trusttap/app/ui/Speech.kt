package com.trusttap.app.ui

import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

class TrustTapSpeaker internal constructor(
    private val engine: () -> TextToSpeech?,
    private val ready: () -> Boolean
) {
    operator fun invoke(text: String) {
        if (ready()) {
            engine()?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "trusttap_result"
            )
        }
    }

    fun stop() {
        engine()?.stop()
    }
}

@Composable
fun rememberTrustTapSpeaker(): TrustTapSpeaker {
    val context = LocalContext.current
    val engineState = remember { mutableStateOf<TextToSpeech?>(null) }
    val readyState = remember { mutableStateOf(false) }

    DisposableEffect(context) {
        var createdEngine: TextToSpeech? = null
        createdEngine = TextToSpeech(context) { status ->
            readyState.value = status == TextToSpeech.SUCCESS
            if (readyState.value) {
                createdEngine?.language = Locale.getDefault()
            }
        }
        val engine = createdEngine
        engineState.value = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            engineState.value = null
            readyState.value = false
        }
    }

    return remember {
        TrustTapSpeaker(
            engine = { engineState.value },
            ready = { readyState.value }
        )
    }
}
