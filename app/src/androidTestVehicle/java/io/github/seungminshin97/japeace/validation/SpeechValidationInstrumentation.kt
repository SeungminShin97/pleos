package io.github.seungminshin97.japeace.validation

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.os.Build
import android.util.Log
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import io.github.seungminshin97.japeace.presentation.RestVoiceCommand
import io.github.seungminshin97.japeace.presentation.parseRestVoice
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

class SpeechValidationInstrumentation : Instrumentation() {
    private var audio = ""
    private var invocation = ""
    private var microphone = false

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        audio = arguments?.getString("pcm").orEmpty()
        invocation = arguments?.getString("invocation").orEmpty()
        microphone = arguments?.getString("input") == "microphone"
        start()
    }

    override fun onStart() {
        val report = Bundle()
        val engine = SpeechToText(targetContext.applicationContext, Mode.ON_DEVICE)
        val initialization = ArrayBlockingQueue<String>(1)
        val result = ArrayBlockingQueue<Pair<Boolean, String>>(1)
        val listener = object : ResultListener {
            override fun onUpdated(stt: String, completed: Boolean) {
                if (completed) result.offer(true to stt)
            }
            override fun onError(errorType: ErrorType) {
                result.offer(false to errorType.name)
            }
            override fun onStartedRecognition() = Unit
            override fun onEndedRecognition() = Unit
            override fun onReady() = Unit
            override fun onUpdatedEpdData(on: Long, off: Long) = Unit
            override fun onUpdatedRms(rms: Double) = Unit
        }
        var passed = false
        try {
            if (microphone) check(Build.FINGERPRINT.startsWith("Pleos/crp_avd/") &&
                Build.DEVICE == "crp_avd_x86_64")
            val pcm = if (microphone) null else {
                require(audio.matches(Regex("[a-zA-Z0-9_-]+\\.pcm")))
                val fixture = targetContext.filesDir.resolve(audio)
                check(fixture.length() in 2L..320_000L && fixture.length() % 2 == 0L)
                fixture.readBytes()
            }
            runOnMainSync {
                engine.initialize(object : OnInitializeListener {
                    override fun onSuccess() { initialization.offer("ready") }
                    override fun onFailure(errorType: ErrorType) { initialization.offer(errorType.name) }
                })
            }
            val initialized = initialization.poll(5, TimeUnit.SECONDS)
            report.putString("initialization", initialized ?: "timeout")
            check(initialized == "ready")
            runOnMainSync {
                engine.setLocale(Locale.KOREA)
                engine.addListener(listener)
                if (pcm == null) {
                    engine.request()
                    Log.i("RestSpeechValidation", "Microphone recognition requested")
                } else {
                    engine.sendAudio(pcm)
                    engine.completeAudioSend()
                }
            }
            val recognized = result.poll(30, TimeUnit.SECONDS)
            report.putString("result", recognized?.second ?: "timeout")
            if (recognized?.first == true) {
                val words = recognized.second
                val parsed = parseRestVoice(words, invocation)
                report.putString("command", parsed?.toString() ?: "unrecognized")
                passed = parsed == RestVoiceCommand.CancelAlarm &&
                    (invocation.isBlank() || parseRestVoice(words) == null)
            }
        } catch (error: Exception) {
            report.putString("error", error.message)
        } finally {
            runOnMainSync {
                if (microphone) runCatching { engine.stop() }
                runCatching { engine.removeListener(listener) }
                runCatching { engine.release() }
            }
            finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED, report)
        }
    }
}
