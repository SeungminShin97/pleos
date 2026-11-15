package io.github.seungminshin97.japeace.integration.vehicle

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/** Runs one foreground recognition session and releases the SDK when it returns or is cancelled. */
internal suspend fun recognizeRestSpeech(context: Context, report: (String) -> Unit): String? =
    withContext(Dispatchers.Main.immediate) {
        val engine = SpeechToText(context.applicationContext, Mode.ON_DEVICE)
        val initialization = Channel<Boolean>(1)
        val results = Channel<Result<String>>(1)
        val speechDetected = Channel<Unit>(Channel.CONFLATED)
        val listener = object : ResultListener {
            override fun onUpdated(stt: String, completed: Boolean) {
                if (completed) results.trySend(Result.success(stt))
                else if (stt.isNotBlank()) speechDetected.trySend(Unit)
            }
            override fun onError(errorType: ErrorType) {
                results.trySend(Result.failure(IllegalStateException(errorType.name)))
            }
            override fun onStartedRecognition() = Unit
            override fun onEndedRecognition() = Unit
            override fun onReady() = Unit
            override fun onUpdatedEpdData(on: Long, off: Long) = Unit
            override fun onUpdatedRms(rms: Double) = Unit
        }
        var requested = false
        var finalWords: String? = null
        try {
            engine.initialize(object : OnInitializeListener {
                override fun onSuccess() { initialization.trySend(true) }
                override fun onFailure(errorType: ErrorType) { initialization.trySend(false) }
            })
            if (withTimeoutOrNull(4_000) { initialization.receive() } != true) {
                report("Pleos 음성 인식을 연결할 수 없습니다.")
                return@withContext null
            }
            engine.setLocale(Locale.KOREA)
            engine.addListener(listener)
            engine.request()
            requested = true
            report("음성 명령을 말해 주세요.")
            val result = withTimeoutOrNull(30_000) {
                var processing = false
                val first = withTimeoutOrNull(15_000) {
                    select {
                        results.onReceive { it }
                        speechDetected.onReceive {
                            processing = true
                            null
                        }
                    }
                }
                if (processing) {
                    report("음성 명령을 처리 중입니다.")
                    results.receive()
                } else first
            }
            val words = result?.getOrNull()
            when {
                result == null -> report("음성 인식 시간이 초과됐습니다.")
                result.isFailure -> report("음성 인식에 실패했습니다. (${result.exceptionOrNull()?.message})")
                words.isNullOrBlank() -> report("명령을 인식하지 못했습니다.")
                else -> finalWords = words
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: RuntimeException) {
            report("Pleos 음성 인식을 시작할 수 없습니다.")
        } finally {
            if (requested) runCatching { engine.stop() }
            runCatching { engine.removeListener(listener) }
            runCatching { engine.release() }
            initialization.close()
            results.close()
            speechDetected.close()
        }
        finalWords
    }
