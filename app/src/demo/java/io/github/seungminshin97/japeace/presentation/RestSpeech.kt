package io.github.seungminshin97.japeace.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay

@Composable
internal fun rememberRestSpeech(language: String, onStatus: (String) -> Unit, onWords: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val report by rememberUpdatedState(onStatus)
    val recognized by rememberUpdatedState(onWords)
    var active by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun release() {
        val recognizer = active
        active = null
        recognizer?.destroy()
    }

    fun start() {
        if (active != null) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            report("이 기기에서 음성 인식을 사용할 수 없습니다.")
            return
        }
        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            active = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    if (active !== recognizer) return
                    release()
                    val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (words.isNullOrBlank()) report("명령을 인식하지 못했습니다.") else recognized(words)
                }
                override fun onError(error: Int) {
                    if (active !== recognizer) return
                    release()
                    report("음성 인식에 실패했습니다. (${error})")
                }
                override fun onReadyForSpeech(params: Bundle?) = report("음성 명령을 말해 주세요.")
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language))
        } catch (_: RuntimeException) {
            release()
            report("음성 인식을 시작할 수 없습니다.")
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else report("음성 인식에 마이크 권한이 필요합니다.")
    }
    LifecycleResumeEffect(Unit) { onPauseOrDispose { release() } }
    LaunchedEffect(active) {
        if (active != null) {
            delay(15_000)
            release()
            report("음성 인식 시간이 초과됐습니다.")
        }
    }
    return {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
}
