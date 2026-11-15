package io.github.seungminshin97.japeace.presentation

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun RestMusicPanel(visible: Boolean) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val player = remember(context) { RestMusicPlayer(context) }
    val state by player.state.collectAsStateWithLifecycle()
    var unavailable by remember { mutableStateOf(false) }
    LaunchedEffect(player) {
        restAlarmDeliveries.collect { player.stop() }
    }
    DisposableEffect(lifecycle, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player.stop()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.stop()
        }
    }
    if (!visible) return

    val playing = state == RestMusicState.PLAYING
    OutlinedButton(onClick = { if (playing) player.stop() else player.play() }) {
        Text(if (playing) "휴식 음악 중지" else "휴식 음악 재생")
    }
    if (state == RestMusicState.ERROR) Text("휴식 음악을 재생할 수 없습니다.")
    OutlinedButton(onClick = { unavailable = !player.openMusicApp(context) }) {
        Text("음악 앱 열기")
    }
    if (unavailable) Text("이 기기에서 음악 앱을 열 수 없습니다.")
}
