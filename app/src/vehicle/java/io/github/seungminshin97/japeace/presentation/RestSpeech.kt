package io.github.seungminshin97.japeace.presentation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.seungminshin97.japeace.integration.vehicle.recognizeRestSpeech
import kotlinx.coroutines.launch

@Composable
internal fun rememberRestSpeech(language: String, onStatus: (String) -> Unit, onWords: (String) -> Unit): () -> Unit {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val report by rememberUpdatedState(onStatus)
    val recognized by rememberUpdatedState(onWords)
    var session by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    LifecycleResumeEffect(Unit) {
        onPauseOrDispose { session?.cancel() }
    }
    return start@{
        if (session?.isActive == true) return@start
        session = scope.launch {
            val finalWords = recognizeRestSpeech(context) { report(it) }
            session = null
            finalWords?.let { recognized(it) }
        }
    }
}
