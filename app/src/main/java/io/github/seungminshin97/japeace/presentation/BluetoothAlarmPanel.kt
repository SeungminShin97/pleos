package io.github.seungminshin97.japeace.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

@SuppressLint("MissingPermission")
@Composable
internal fun BluetoothAlarmPanel(
    automotive: Boolean,
    parked: Boolean,
    minutes: Int?,
    clockTime: String,
    scheduleVehicle: ((Instant) -> Boolean)? = null,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    var peers by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var selectedAddress by rememberSaveable { mutableStateOf<String?>(null) }
    var requestedEpochSecond by rememberSaveable { mutableStateOf<Long?>(null) }
    var outgoing by remember { mutableStateOf<Instant?>(null) }
    var confirmTarget by remember { mutableStateOf<Instant?>(null) }
    var running by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var peersLoaded by remember { mutableStateOf(false) }
    var canReadPeers by remember { mutableStateOf(false) }
    var bluetoothEnabled by remember { mutableStateOf(false) }

    fun loadPeers() {
        try {
            bluetoothEnabled = adapter?.isEnabled == true
            peers = if (bluetoothEnabled) adapter?.bondedDevices.orEmpty().sortedBy { it.name ?: it.address }
            else emptyList()
            if (selectedAddress != null && peers.none { it.address == selectedAddress }) {
                if (bluetoothEnabled) selectedAddress = null
                running = false
                confirmTarget = null
                requestedEpochSecond = null
            }
            peersLoaded = true
            canReadPeers = true
        } catch (_: SecurityException) {
            peers = emptyList()
            running = false
            confirmTarget = null
            requestedEpochSecond = null
            peersLoaded = false
            canReadPeers = false
            message = "근처 기기 권한이 필요합니다."
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) loadPeers() else message = "근처 기기 권한이 필요합니다."
    }
    LifecycleResumeEffect(adapter) {
        canReadPeers = Build.VERSION.SDK_INT < 31 ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (canReadPeers) loadPeers()
            }
        }
        // Bluetooth runs under a separate UID. Broadcasts only trigger a fresh adapter query.
        val registered = adapter != null && runCatching {
            ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            }, ContextCompat.RECEIVER_EXPORTED)
        }.isSuccess
        if (canReadPeers) loadPeers()
        else {
            peers = emptyList()
            peersLoaded = false
            requestedEpochSecond = null
        }
        onPauseOrDispose { if (registered) context.unregisterReceiver(receiver) }
    }
    val peer = peers.firstOrNull { it.address == selectedAddress }
    LaunchedEffect(running, peer, outgoing, automotive, parked.takeIf { automotive }) {
        if (!running || peer == null || adapter == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                if (automotive) {
                    val target = outgoing
                    if (parked && target != null && alarmTime(target) != null) {
                        withTimeout(15_000) { sendBluetoothAlarm(peer, target) }
                        message = "휴대폰이 요청을 받았습니다. 휴대폰에서 알람을 확인해 주세요."
                    } else {
                        message = "P 정지 상태와 알람 시각을 확인해 주세요."
                    }
                } else {
                    requestedEpochSecond = withTimeout(60_000) { receiveBluetoothAlarm(adapter, peer) }.epochSecond
                    message = "차량에서 알람 요청을 받았습니다."
                }
            } catch (_: TimeoutCancellationException) {
                message = "연결 시간이 지났습니다. 상대 기기 화면을 확인해 주세요."
            } catch (_: IOException) {
                message = "블루투스 요청을 완료하지 못했습니다."
            } catch (_: SecurityException) {
                message = "근처 기기 권한이 필요합니다."
            } finally {
                running = false
            }
        }
    }

    Text(when {
        scheduleVehicle != null -> "차량 및 휴대폰 알람"
        automotive -> "휴대폰 알람 보내기"
        else -> "차량 알람 요청 받기"
    })
    when {
        adapter == null -> Text("이 기기는 블루투스를 지원하지 않습니다.")
        peersLoaded && !bluetoothEnabled -> Text("블루투스 꺼짐")
        peersLoaded && peers.isEmpty() -> Text("연결된 기기 없음")
    }
    Button(enabled = !running && adapter != null, onClick = {
        if (Build.VERSION.SDK_INT >= 31 && !canReadPeers) {
            permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else loadPeers()
    }) { Text(if (canReadPeers) "기기 목록 새로고침" else "근처 기기 권한 허용") }
    if (peersLoaded && adapter != null && peers.isEmpty()) {
        TextButton(onClick = {
            try {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                message = "블루투스 설정을 열 수 없습니다."
            } catch (_: SecurityException) {
                message = "블루투스 설정을 열 수 없습니다."
            }
        }) { Text("블루투스 설정") }
    }
    peers.forEach { device ->
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.selectable(selected = peer == device, enabled = !running, role = Role.RadioButton) {
                if (selectedAddress != device.address) {
                    selectedAddress = device.address
                    requestedEpochSecond = null
                }
            },
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            RadioButton(peer == device, onClick = null, enabled = !running)
            Text(runCatching { device.name }.getOrNull() ?: device.address)
        }
    }
    if (running) {
        TextButton(onClick = { running = false }) { Text("블루투스 연결 취소") }
    } else if (peer != null) {
        Button(enabled = !automotive || (parked && minutes != null && minutes in 1..120), onClick = {
            message = null
            requestedEpochSecond = null
            if (automotive) confirmTarget = minutes?.let { alarmTarget(it) }
            else running = true
        }) { Text(when {
            scheduleVehicle != null -> "양쪽 알람 설정"
            automotive -> "알람 시간 보내기"
            else -> "차량 요청 기다리기"
        }) }
        if (automotive) {
            TextButton(enabled = parked, onClick = {
                val target = restAlarmClockTarget(clockTime)
                if (target == null || alarmTime(target) == null) {
                    message = "휴대폰 시계에 보낼 수 있는 지정 시각을 입력해 주세요."
                } else {
                    message = null
                    confirmTarget = target
                }
            }) { Text(if (scheduleVehicle != null) "양쪽 지정 시각 설정" else "지정 시각 보내기") }
        }
    }
    val proposed = confirmTarget
    if (proposed != null && peer != null) {
        val time = proposed.atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
        val deviceName = runCatching { peer.name }.getOrNull() ?: peer.address
        AlertDialog(
            onDismissRequest = { confirmTarget = null },
            title = { Text(if (scheduleVehicle != null) "차량 및 휴대폰 알람" else "휴대폰 알람 보내기") },
            text = { Text(if (scheduleVehicle != null) {
                "차량에 ${time} 알람을 예약하고 ${deviceName}에 같은 시각을 보냅니다."
            } else "${deviceName}에 ${time} 알람을 요청합니다.") },
            confirmButton = {
                TextButton(enabled = parked, onClick = {
                    confirmTarget = null
                    if (alarmTime(proposed) == null) {
                        message = "알람 시각이 지났습니다. 시간을 다시 설정해 주세요."
                    } else if (scheduleVehicle?.invoke(proposed) != false) {
                        outgoing = proposed
                        running = true
                    }
                }) { Text("보내기") }
            },
            dismissButton = { TextButton(onClick = { confirmTarget = null }) { Text("취소") } },
        )
    }
    val target = requestedEpochSecond?.let(Instant::ofEpochSecond)
    val time = target?.let { alarmTime(it) }
    LaunchedEffect(target) {
        if (target != null) {
            delay(Duration.between(Instant.now(), target).toMillis().coerceAtLeast(0))
            requestedEpochSecond = null
            message = "알람 시각이 지났습니다. 차량에서 다시 보내 주세요."
        }
    }
    if (peer != null && target != null && time != null) {
        Text("요청 알람 시각 ${time.format(DateTimeFormatter.ofPattern("HH:mm"))}")
        Button(onClick = {
            message = when (requestPhoneAlarm(context, target)) {
                PhoneClockResult.OPENED -> {
                    requestedEpochSecond = null
                    "시계 앱에서 알람 등록 여부를 확인해 주세요."
                }
                PhoneClockResult.INVALID_TIME -> "알람 시각이 지났습니다. 차량에서 다시 보내 주세요."
                PhoneClockResult.UNAVAILABLE -> "알람 요청을 지원하는 시계 앱이 없습니다."
                PhoneClockResult.DENIED -> "시계 앱이 알람 요청을 허용하지 않습니다."
            }
        }) { Text("휴대폰 시계 앱에 알람 설정") }
    }
    message?.let { Text(it) }
}
