package io.github.seungminshin97.japeace.presentation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import io.github.seungminshin97.japeace.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class RestMusicState { STOPPED, PLAYING, ERROR }

@MainThread
internal class RestMusicPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(AudioManager::class.java)
    private val mutableState = MutableStateFlow(RestMusicState.STOPPED)
    val state = mutableState.asStateFlow()
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null

    fun play() {
        if (player != null) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change ->
                if (change < 0) stop()
            }, Handler(Looper.getMainLooper()))
            .build()
        if (audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            mutableState.value = RestMusicState.ERROR
            return
        }
        focus = request
        try {
            val music = checkNotNull(MediaPlayer.create(
                appContext, R.raw.rest_music, attributes, AudioManager.AUDIO_SESSION_ID_GENERATE,
            ))
            player = music
            music.setOnErrorListener { failed, _, _ ->
                if (player === failed) {
                    stop()
                    mutableState.value = RestMusicState.ERROR
                }
                true
            }
            music.isLooping = true
            music.start()
            mutableState.value = RestMusicState.PLAYING
        } catch (_: RuntimeException) {
            stop()
            mutableState.value = RestMusicState.ERROR
        }
    }

    fun stop() {
        player?.release()
        player = null
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
        mutableState.value = RestMusicState.STOPPED
    }

    fun openMusicApp(context: Context): Boolean {
        val intent = if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            Intent("android.car.intent.action.MEDIA_TEMPLATE")
        } else {
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
        }
        return try {
            context.startActivity(intent)
            stop()
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
