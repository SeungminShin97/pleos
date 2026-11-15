package io.github.seungminshin97.japeace.presentation

import android.Manifest
import android.provider.Settings
import android.net.Uri
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.Date
import java.time.Instant
import java.time.Duration

@Composable
fun RestToolsPanel(
    viewModel: HomeViewModel,
    parked: Boolean,
    selectedId: String?,
    sessionId: String?,
    requestedRestMinutes: Int? = null,
    onRestRequestHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val voiceSettings = remember(context) {
        context.getSharedPreferences("rest-voice", android.content.Context.MODE_PRIVATE)
    }
    var invocation by remember { mutableStateOf(voiceSettings.getString("invocation", "").orEmpty()) }
    val automotive = context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
    val alarm = remember(context) { VehicleRestAlarm(context) }
    var scheduledTime by remember(alarm) { mutableStateOf(alarm.scheduledTimeMillis()) }
    val pending = scheduledTime != null
    var soundStatus by remember { mutableStateOf(alarm.soundStatus()) }
    var preview by remember { mutableStateOf<android.media.Ringtone?>(null) }
    val alarmPreferences = remember(context) {
        context.getSharedPreferences("rest-alarm-preferences", android.content.Context.MODE_PRIVATE)
    }
    var phoneDestination by remember(alarmPreferences) { mutableStateOf(alarmPreferences.getBoolean("phone", false)) }
    var vehicleDestination by remember(alarmPreferences) { mutableStateOf(alarmPreferences.getBoolean("vehicle", true)) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    var minutes by rememberSaveable { mutableStateOf("20") }
    var alarmTime by rememberSaveable { mutableStateOf("") }
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreRequest by rememberSaveable { mutableStateOf<String?>(null) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var koreanReady by remember { mutableStateOf(false) }
    val locale = Locale.forLanguageTag("ko-KR")
    DisposableEffect(context) {
        var active = true
        val engine = TextToSpeech(context.applicationContext) { status ->
            if (active && status == TextToSpeech.SUCCESS) {
                koreanReady = tts?.setLanguage(locale)?.let { it >= TextToSpeech.LANG_AVAILABLE } == true
            }
        }
        tts = engine
        onDispose {
            active = false
            engine.shutdown()
        }
    }

    fun report(message: String, speak: Boolean = false) {
        feedback = message
        if (speak && koreanReady) tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "rest-feedback")
    }

    fun scheduleVehicleAlarm(delayMillis: Long, success: String, speak: Boolean = false): Boolean {
        soundStatus = alarm.soundStatus()
        if (automotive && !vehicleDestination) {
            report("차량에서 울리기를 켜 주세요.", speak)
            return false
        }
        when (alarm.requestScheduleAfter(delayMillis, viewModel.vehicleState.value)) {
            VehicleAlarmResult.INVALID_TIME -> report("알람 시각을 다시 설정해 주세요.", speak)
            VehicleAlarmResult.PARK_REQUIRED -> report("P 정지 상태에서 설정해 주세요.", speak)
            VehicleAlarmResult.EXACT_ALARM_PERMISSION_REQUIRED -> {
                report("알람 권한을 허용한 뒤 다시 시작해 주세요.", speak)
                try {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:${context.packageName}")))
                } catch (_: ActivityNotFoundException) {
                    report("이 기기에서 알람 권한 설정을 열 수 없습니다.", speak)
                }
            }
            VehicleAlarmResult.NOTIFICATION_PERMISSION_REQUIRED -> {
                report("알림을 허용한 뒤 다시 시작해 주세요.", speak)
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            VehicleAlarmResult.NOTIFICATIONS_DISABLED -> {
                report("알림을 허용한 뒤 다시 시작해 주세요.", speak)
                try {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                } catch (_: ActivityNotFoundException) {
                    report("이 기기에서 알림 설정을 열 수 없습니다.", speak)
                }
            }
            VehicleAlarmResult.SCHEDULED -> {
                scheduledTime = alarm.scheduledTimeMillis()
                report(success, speak)
                return true
            }
            VehicleAlarmResult.FAILED -> {
                scheduledTime = alarm.scheduledTimeMillis()
                report("알람을 예약할 수 없습니다. 알람 권한을 확인해 주세요.", speak)
            }
        }
        return false
    }

    fun schedule(value: Int?, speak: Boolean = false) {
        if (value == null || value !in 1..120) {
            report("1~120분을 입력해 주세요.", speak)
            return
        }
        scheduleVehicleAlarm(value * 60_000L, "${value}분 휴식 알람을 예약했습니다.", speak)
    }

    val bluetoothAlarmSelected = automotive && phoneDestination
    LaunchedEffect(requestedRestMinutes, bluetoothAlarmSelected) {
        val duration = requestedRestMinutes ?: return@LaunchedEffect
        if (bluetoothAlarmSelected) {
            minutes = duration.toString()
            report("알람 시간을 입력했습니다. 화면에서 확인해 주세요.")
            onRestRequestHandled()
        }
    }
    requestedRestMinutes?.takeUnless { bluetoothAlarmSelected }?.let { duration ->
        AlertDialog(
            onDismissRequest = onRestRequestHandled,
            title = { Text("차량 휴식 알람") },
            text = { Text(if (parked) "${duration}분 후 차량에서 알람을 울릴까요?" else "P 정지 상태에서 설정할 수 있습니다.") },
            confirmButton = {
                TextButton(enabled = parked && (!automotive || vehicleDestination), onClick = {
                    minutes = duration.toString()
                    onRestRequestHandled()
                    schedule(duration)
                }) { Text("설정") }
            },
            dismissButton = { TextButton(onClick = onRestRequestHandled) { Text("취소") } },
        )
    }

    LifecycleResumeEffect(alarm) {
        scheduledTime = alarm.scheduledTimeMillis()
        soundStatus = alarm.soundStatus()
        onPauseOrDispose {
            preview?.stop()
            preview = null
        }
    }
    LaunchedEffect(preview) {
        val sound = preview ?: return@LaunchedEffect
        try {
            delay(3000)
            preview = null
        } finally {
            sound.stop()
        }
    }
    LaunchedEffect(alarm, pending) {
        while (scheduledTime != null) {
            delay(1000)
            if (alarm.scheduledTimeMillis() == null) scheduledTime = null
        }
    }

    var followUp by remember { mutableStateOf(false) }
    var awaitingCommand by remember { mutableStateOf(false) }
    val startVoice = rememberRestSpeech(locale.toLanguageTag(), { report(it) }) { words ->
        if (!awaitingCommand && isRestInvocation(words, invocation)) {
            followUp = true
        } else {
            awaitingCommand = false
            when (val command = parseRestVoice(words, invocation)) {
                RestVoiceCommand.Apply -> when {
                    !viewModel.canExecute -> report("이 환경에서는 휴식 설정 적용을 사용할 수 없습니다.", true)
                    !parked -> report("P와 정지 상태에서만 적용할 수 있습니다.", true)
                    !storage.ready || storage.saving || storage.executing || sessionId != null ->
                        report("현재 휴식 설정을 적용할 수 없습니다.", true)
                    selectedId == null -> report("적용할 휴식 설정을 먼저 선택해 주세요.", true)
                    else -> viewModel.applyPreset(selectedId)
                }
                RestVoiceCommand.Restore -> {
                    if (!viewModel.canExecute || !parked || sessionId == null) {
                        report("복귀 가능한 주차 세션이 없습니다.", true)
                    } else {
                        restoreRequest = sessionId
                        report("화면에서 복귀를 확인해 주세요.", true)
                    }
                }
                RestVoiceCommand.CancelAlarm -> {
                    alarm.cancel()
                    scheduledTime = null
                    report("타이머를 취소했습니다.", true)
                }
                is RestVoiceCommand.Alarm -> if (bluetoothAlarmSelected) {
                    minutes = command.minutes.toString()
                    report("알람 시간을 입력했습니다. 화면에서 확인해 주세요.", true)
                } else schedule(command.minutes, true)
                null -> report("명령을 인식하지 못했습니다.", true)
            }
        }
    }
    LaunchedEffect(followUp) {
        if (followUp) {
            followUp = false
            awaitingCommand = true
            startVoice()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text("음성 및 휴식 타이머")
        Button(onClick = {
            awaitingCommand = false
            startVoice()
        }) { Text("음성 명령") }
        OutlinedTextField(
            value = invocation,
            onValueChange = { value ->
                if (value.length <= 20) {
                    invocation = value
                    voiceSettings.edit().putString("invocation", value.trim()).apply()
                }
            },
            label = { Text("앱 내 음성 호출어") },
            singleLine = true,
        )
        Text("예시: 휴식 설정 적용, 복귀, 알람 20분, 알람 취소")
        OutlinedTextField(minutes, { if (it.length <= 3 && it.all(Char::isDigit)) minutes = it },
            label = { Text("타이머 시간(분)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        if (automotive) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(vehicleDestination, onCheckedChange = { enabled ->
                    vehicleDestination = enabled
                    alarmPreferences.edit().putBoolean("vehicle", enabled).apply()
                    if (!enabled) {
                        preview?.stop()
                        preview = null
                    }
                }, modifier = androidx.compose.ui.Modifier.semantics { contentDescription = "차량에서 울리기" })
                Text("차량에서 울리기")
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(phoneDestination, onCheckedChange = { enabled ->
                    phoneDestination = enabled
                    alarmPreferences.edit().putBoolean("phone", enabled).apply()
                },
                    modifier = androidx.compose.ui.Modifier.semantics { contentDescription = "휴대폰에서 울리기" })
                Text("휴대폰에서 울리기")
            }
        }
        if (!automotive || vehicleDestination || phoneDestination) {
            OutlinedTextField(alarmTime, { alarmTime = it.take(5) },
                label = { Text("지정 시각 (HH:mm)") }, singleLine = true)
        }
        if (!automotive || phoneDestination) {
            BluetoothAlarmPanel(
                automotive, parked, minutes.toIntOrNull(), alarmTime,
                scheduleVehicle = if (automotive && vehicleDestination) { target ->
                    scheduleVehicleAlarm(
                        Duration.between(Instant.now(), target).toMillis().coerceAtLeast(1),
                        "차량 알람을 예약했습니다.",
                    )
                } else null,
            )
        }
        if (!automotive || vehicleDestination) {
            HorizontalDivider()
            soundStatus?.let { Text(it) }
            TextButton(onClick = {
                val playing = preview
                if (playing != null) {
                    playing.stop()
                    preview = null
                } else {
                    soundStatus = alarm.soundStatus()
                    val sound = runCatching { alarm.previewRingtone() }.getOrNull()
                    if (sound != null && runCatching { sound.play(); sound.isPlaying }.getOrDefault(false)) {
                        preview = sound
                        feedback = null
                    } else {
                        sound?.stop()
                        report(soundStatus ?: "알람 소리를 재생할 수 없습니다.")
                    }
                }
            }) { Text(if (preview == null) "알람 소리 미리 듣기" else "미리 듣기 중지") }
            TextButton(onClick = {
                try {
                    alarm.openSoundSettings()
                } catch (_: ActivityNotFoundException) {
                    report("이 기기에서 휴식 알람 소리 설정을 열 수 없습니다.")
                }
            }) { Text("알람 소리 설정") }
            if (!automotive || !phoneDestination) {
                Button(onClick = { schedule(minutes.toIntOrNull()) }, enabled = parked) {
                    Text(if (pending) "알람 다시 설정" else "알람 시작")
                }
                OutlinedButton(enabled = parked, onClick = {
                    val delayMillis = restAlarmDelayMillis(alarmTime)
                    if (delayMillis == null) report("시각을 24시간 형식으로 입력해 주세요.")
                    else scheduleVehicleAlarm(delayMillis, "${alarmTime} 차량 알람을 예약했습니다.")
                }) { Text("지정 시각에 알람") }
            }
            Text("기기 전원이 켜져 있고 알림이 허용되어 있어야 합니다.")
        }
        scheduledTime?.let { time ->
            val formattedTime = android.text.format.DateFormat.getTimeFormat(context).format(Date(time))
            Text("예약된 알람 $formattedTime")
            TextButton(onClick = {
                alarm.cancel()
                scheduledTime = null
                report("이 기기의 알람을 취소했습니다.")
            }) { Text("이 기기의 알람 취소") }
        }
        feedback?.let { Text(it) }
        if (!automotive) {
            val timerMinutes = minutes.toIntOrNull()
            val clockTimer = timerMinutes?.let(::phoneTimerIntent)
            if (timerMinutes != null && clockTimer?.resolveActivity(context.packageManager) != null) {
                TextButton(onClick = {
                    report(when (requestPhoneTimer(context, timerMinutes)) {
                        PhoneClockResult.OPENED -> "시계 앱에서 타이머 시작 여부를 확인해 주세요. 취소도 시계 앱에서 할 수 있습니다."
                        PhoneClockResult.INVALID_TIME -> "1~120분을 입력해 주세요."
                        PhoneClockResult.UNAVAILABLE -> "이 기기의 시계 앱을 열 수 없습니다."
                        PhoneClockResult.DENIED -> "시계 앱에 타이머 요청을 전달할 수 없습니다."
                    })
                }) { Text("이 휴대폰의 시계 앱에서 열기") }
            }
        }
    }
    val requested = restoreRequest
    if (requested != null && requested == sessionId) {
        AlertDialog(
            onDismissRequest = { restoreRequest = null },
            title = { Text("이전 설정으로 복귀할까요?") },
            confirmButton = { TextButton(enabled = parked && storage.ready && !storage.saving && !storage.executing,
                onClick = { restoreRequest = null; viewModel.restoreSession(requested) }) { Text("복귀 확인") } },
            dismissButton = { TextButton(onClick = { restoreRequest = null }) { Text("취소") } },
        )
    }
}
