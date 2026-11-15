package io.github.seungminshin97.japeace.presentation

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val alarmServiceId = UUID.fromString("cfa9bd89-ab5c-46e2-a2dc-a880a45523ec")
private const val protocolVersion = 1
private const val clockAlarmWindowSeconds = 24 * 60 * 60L

internal fun alarmTarget(minutes: Int, now: Instant = Instant.now()): Instant? {
    if (minutes !in 1..120) return null
    val deadline = now.plusSeconds(minutes * 60L)
    val minute = deadline.truncatedTo(ChronoUnit.MINUTES)
    // The phone clock supports whole minutes. Preserve at least the requested duration.
    return if (deadline == minute) minute else minute.plusSeconds(60)
}

internal fun alarmTime(
    target: Instant,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): ZonedDateTime? {
    if (!target.isAfter(now) || !target.isBefore(now.plusSeconds(clockAlarmWindowSeconds))) return null
    val localTarget = target.atZone(zone)
    val clockTime = localTarget.toLocalTime().truncatedTo(ChronoUnit.MINUTES)
    val localNow = now.atZone(zone)
    val currentMinute = localNow.toLocalTime().truncatedTo(ChronoUnit.MINUTES)
    if (clockTime == currentMinute) return null

    // ACTION_SET_ALARM receives only hour and minute for a one-time alarm.
    val date = if (clockTime.isAfter(currentMinute)) localNow.toLocalDate() else localNow.toLocalDate().plusDays(1)
    val nextLocalTime = date.atTime(clockTime)
    val offset = zone.rules.getValidOffsets(nextLocalTime).singleOrNull() ?: return null
    return localTarget.takeIf { nextLocalTime.toInstant(offset) == target }
}

internal fun receivedAlarmTarget(epochSecond: Long, now: Instant = Instant.now()): Instant? =
    epochSecond.takeIf { it in now.epochSecond..now.plusSeconds(clockAlarmWindowSeconds).epochSecond }
        ?.let(Instant::ofEpochSecond)
        ?.takeIf { alarmTime(it, now) != null }

// The panel checks BLUETOOTH_CONNECT before opening secure, bonded-device sockets.
@SuppressLint("MissingPermission")
internal suspend fun sendBluetoothAlarm(peer: BluetoothDevice, target: Instant) =
    bluetoothIo { track ->
        peer.createRfcommSocketToServiceRecord(alarmServiceId).use { socket ->
            track(socket)
            socket.connect()
            DataOutputStream(socket.outputStream).apply {
                writeByte(protocolVersion)
                writeLong(target.epochSecond)
                flush()
            }
            if (DataInputStream(socket.inputStream).readUnsignedByte() != protocolVersion) {
                throw IOException("Alarm request rejected")
            }
        }
    }

@SuppressLint("MissingPermission")
internal suspend fun receiveBluetoothAlarm(adapter: BluetoothAdapter, peer: BluetoothDevice): Instant =
    bluetoothIo { track ->
        adapter.listenUsingRfcommWithServiceRecord("Japeace Alarm", alarmServiceId).use { listener ->
            track(listener)
            listener.accept().use { socket ->
                track(socket)
                if (socket.remoteDevice != peer) throw IOException("Unexpected Bluetooth device")
                val input = DataInputStream(socket.inputStream)
                if (input.readUnsignedByte() != protocolVersion) throw IOException("Unknown alarm request")
                val target = receivedAlarmTarget(input.readLong())
                    ?: throw IOException("Alarm time is out of range")
                DataOutputStream(socket.outputStream).apply {
                    writeByte(protocolVersion)
                    flush()
                }
                target
            }
        }
    }

private suspend fun <T> bluetoothIo(block: ((Closeable) -> Unit) -> T): T =
    suspendCancellableCoroutine { continuation ->
        val sockets = mutableListOf<Closeable>()
        continuation.invokeOnCancellation {
            synchronized(sockets) { sockets.asReversed().forEach { runCatching { it.close() } } }
        }
        Dispatchers.IO.dispatch(EmptyCoroutineContext) {
            if (!continuation.isActive) return@dispatch
            try {
                val result = block { socket ->
                    synchronized(sockets) {
                        if (continuation.isActive) sockets.add(socket) else socket.close()
                    }
                }
                continuation.resume(result)
            } catch (error: Exception) {
                continuation.resumeWithException(error)
            }
        }
    }
