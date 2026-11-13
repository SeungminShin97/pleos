package io.github.seungminshin97.japeace.integration.vehicle

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Build
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.area.SeatValue
import io.github.seungminshin97.japeace.domain.rest.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Captures read-supported driver settings. No movement or memory-selection calls. */
class PleosSettingsReader(private val context: Context) : SettingsReader {
    override suspend fun captureDriverSeat(): CapturedSettings = withContext(Dispatchers.IO) {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { "이 Android 버전에서는 차량 설정 조회를 지원하지 않습니다." }
        val car = checkNotNull(Car.createCar(context)) { "차량 서비스에 연결하지 못했습니다." }
        var vehicle: Vehicle? = null
        try {
            val properties = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            requireParked(properties)
            val sdk = Vehicle(context).also { vehicle = it; it.initialize() }
            val seat = sdk.getSeat()
            val area = SeatArea(SeatValue.ROW_1_LEFT)
            val axes = listOf(
                Axis("foreAft", seat::checkSeatForeAftPositionCapabilities, seat::getCurrentSeatForeAftPosition),
                Axis("backrest", seat::checkSeatBackrestAnglePositionCapabilities, seat::getCurrentSeatBackrestAnglePosition),
                Axis("height", seat::checkSeatHeightPositionCapabilities, seat::getCurrentSeatHeightPosition),
                Axis("tilt", seat::checkSeatTiltPositionCapabilities, seat::getCurrentSeatTiltPosition),
                Axis("depth", seat::checkSeatDepthPositionCapabilities, seat::getCurrentSeatDepthPosition),
                Axis("beltHeight", seat::checkSeatBeltHeightPositionCapabilities, seat::getCurrentSeatBeltHeightPosition),
                Axis("headrestAngle", seat::checkSeatHeadrestAnglePositionCapabilities, seat::getCurrentSeatHeadrestAnglePosition),
                Axis("headrestForeAft", seat::checkSeatHeadrestForeAftPositionCapabilities, seat::getCurrentSeatHeadrestForeAftPosition),
                Axis("headrestHeight", seat::checkSeatHeadrestHeightPositionCapabilities, seat::getCurrentSeatHeadrestHeightAftPosition),
                Axis("lumbarForeAft", seat::checkSeatLumbarForeAftPositionCapabilities, seat::getCurrentSeatLumbarForeAftPosition),
                Axis("lumbarSide", seat::checkSeatLumbarSideSupportPositionCapabilities, seat::getCurrentSeatLumbarSideSupportPosition),
            )
            val values = mutableListOf<SettingObservation>()
            for (axis in axes) {
                val (config, areas) = query<Pair<CapabilityConfig, Map<SeatArea, AreaBounds<Int>>>> { done, failed ->
                    axis.capabilities({ capability, supported -> done(capability to supported) }, failed)
                }
                val bounds = areas[area] ?: continue
                if (config.accessType == AccessType.WRITE) continue
                val raw = checkNotNull(query<Int?> { done, failed -> axis.read(area, done, failed) }) { "정상적인 좌석 위치를 확인하지 못했습니다." }
                values += SettingObservation(axis.name, SeatValue.ROW_1_LEFT.id, raw, bounds.minValue, bounds.maxValue,
                    System.currentTimeMillis())
            }
            requireParked(properties)
            val source = if (Build.FINGERPRINT.startsWith("Pleos/crp_avd/")) CaptureSource.PLEOS_EMULATOR else CaptureSource.VEHICLE
            CapturedSettings(CaptureProfile("unverified:${Build.FINGERPRINT}", source, "pleos-seat-2.3.2-raw-v1"), values)
                .also { it.validate() }
        } finally {
            vehicle?.release()
            car.disconnect()
        }
    }

    private data class Axis(
        val name: String,
        val capabilities: ((CapabilityConfig, Map<SeatArea, AreaBounds<Int>>) -> Unit, (Exception) -> Unit) -> Unit,
        val read: (SeatArea, (Int?) -> Unit, (Exception) -> Unit) -> Unit,
    )

    @Suppress("DEPRECATION")
    private fun requireParked(properties: CarPropertyManager) {
        val gear = properties.getProperty<Int>(VehiclePropertyIds.GEAR_SELECTION, 0)
        val speed = properties.getProperty<Float>(VehiclePropertyIds.PERF_VEHICLE_SPEED, 0)
        check(gear.status == CarPropertyValue.STATUS_AVAILABLE && speed.status == CarPropertyValue.STATUS_AVAILABLE
            && gear.value == VehicleGear.GEAR_PARK && speed.value.isFinite() && speed.value == 0f) { "P와 정지 상태에서만 저장할 수 있습니다." }
    }

    private suspend fun <T> query(request: ((T) -> Unit, (Exception) -> Unit) -> Unit): T = withTimeout(8_000L) {
        suspendCancellableCoroutine { continuation ->
            val finished = AtomicBoolean(false)
            continuation.invokeOnCancellation { finished.set(true) }
            request({ value -> if (finished.compareAndSet(false, true)) continuation.resume(value) },
                { error -> if (finished.compareAndSet(false, true)) continuation.resumeWithException(error) })
        }
    }
}
