package io.github.seungminshin97.japeace.integration.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.MoveControl
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import io.github.seungminshin97.japeace.presentation.PassengerSeat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

internal fun supportsPleosRestControl(context: Context): Boolean =
    context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
        Build.DEVICE == "crp_avd_x86_64" && Build.FINGERPRINT.startsWith("Pleos/crp_avd/")

internal suspend fun adjustPassengerSeat(
    context: Context,
    target: PassengerSeat,
    forward: Boolean,
    stateAvailable: () -> Boolean,
): String {
    if (!supportsPleosRestControl(context)) return "이 환경에서는 동승석을 조정할 수 없습니다."
    val area = SeatArea(when (target) {
        PassengerSeat.FRONT -> SeatValue.ROW_1_RIGHT
        PassengerSeat.REAR_LEFT -> SeatValue.ROW_2_LEFT
        PassengerSeat.REAR_RIGHT -> SeatValue.ROW_2_RIGHT
    })
    val vehicle = Vehicle(context.applicationContext)
    try {
        vehicle.initialize()
        val seat = vehicle.getSeat()
        val supported = withTimeoutOrNull(3000) {
            suspendCancellableCoroutine { continuation ->
                seat.checkMoveSeatForeAftCapabilities(
                    { _, areas -> continuation.resume(area in areas) },
                    { continuation.resume(false) },
                )
            }
        }
        if (supported != true) return "${target.label}의 전후 조정 지원을 확인하지 못했습니다."
        val direction = if (forward) MoveControl.FORWARD else MoveControl.BACKWARD
        suspend fun readPosition(): Int? = withTimeoutOrNull(2000) {
            suspendCancellableCoroutine { continuation ->
                seat.getCurrentSeatForeAftPosition(area,
                    { position -> continuation.resume(position) },
                    { continuation.resume(null) })
            }
        }

        suspend fun send(command: MoveControl): Boolean = withTimeoutOrNull(2000) {
            suspendCancellableCoroutine { continuation ->
                seat.moveSeatForeAft(area, command,
                    { _, reported -> continuation.resume(reported == command) },
                    { error ->
                        Log.w("PassengerSeat", "Seat $target command $command failed", error)
                        continuation.resume(false)
                    })
            }
        } == true

        val before = readPosition() ?: return "${target.label}의 현재 위치를 확인할 수 없습니다."
        if (!stateAvailable()) return "차량 상태를 확인할 수 없어 조정을 중지했습니다."
        var stopped: Boolean
        val accepted = try {
            val acknowledged = send(direction)
            if (acknowledged) delay(300)
            acknowledged
        } finally {
            stopped = withContext(NonCancellable) { runCatching { send(MoveControl.STOP) }.getOrDefault(false) }
        }
        val after = if (stopped && accepted) readPosition() else null
        return when {
            !accepted && !stopped -> "${target.label} 이동과 정지 응답을 확인하지 못했습니다."
            !stopped -> "${target.label} 정지 응답을 확인하지 못했습니다."
            !accepted -> "${target.label} 이동 요청이 거절됐거나 응답이 없습니다."
            after == null -> "${target.label} 이동과 정지 응답을 받았습니다. 위치 조회 실패."
            before == after -> "${target.label} 위치 변화 없음 ($before → $after)"
            else -> "${target.label} 위치 변경 ($before → $after)"
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        return "${target.label} 조정 요청을 처리하지 못했습니다."
    } finally {
        runCatching { vehicle.release() }
    }
}

internal suspend fun readRestClimatePower(context: Context, target: Boolean?, parked: () -> Boolean): String {
    if (target != null && !supportsPleosRestControl(context)) return "이 환경에서는 공조를 제어할 수 없습니다."
    val vehicle = Vehicle(context.applicationContext)
    return try {
        vehicle.initialize()
        val climate = vehicle.getHVAC()
        val areas = withTimeoutOrNull(3_000) {
            suspendCancellableCoroutine<List<SeatArea>?> { continuation ->
                climate.checkHvacPowerCapabilities(
                    { continuation.resume(it) },
                    { continuation.resume(null) },
                )
            }
        }.orEmpty()
        if (target != null) {
            if (areas.isEmpty()) return "공조 제어 구역을 확인할 수 없습니다."
            for (area in areas) {
                if (!parked()) return "공조 제어를 중지했습니다. 차량을 주차한 뒤 상태를 확인하세요."
                val accepted = withTimeoutOrNull(2_000) {
                    suspendCancellableCoroutine<Boolean> { continuation ->
                        climate.setHvacPower(area, target,
                            { _, result -> continuation.resume(result == target) },
                            { continuation.resume(false) },
                        )
                    }
                }
                if (accepted != true) return "공조 제어 응답을 확인할 수 없습니다."
            }
        }
        suspend fun readStates() = withTimeoutOrNull(4_000) {
            areas.map { area ->
                suspendCancellableCoroutine<Boolean?> { continuation ->
                    climate.getHvacPowerState(area, { continuation.resume(it) }, { continuation.resume(null) })
                }
            }
        }.orEmpty()
        val states = if (target == null) {
            readStates()
        } else {
            withTimeoutOrNull(6_000) {
                var observed = readStates()
                while (observed.isEmpty() || observed.any { it != target }) {
                    delay(350)
                    observed = readStates()
                }
                observed
            } ?: return "요청한 공조 상태를 확인하지 못했습니다."
        }
        when {
            states.isEmpty() || null in states -> "공조 상태를 확인할 수 없습니다."
            states.all { it == true } -> "공조 켜짐"
            states.all { it == false } -> "공조 꺼짐"
            else -> "일부 구역 공조 켜짐"
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: RuntimeException) {
        "공조 상태를 확인할 수 없습니다."
    } finally {
        runCatching { vehicle.release() }
    }
}
