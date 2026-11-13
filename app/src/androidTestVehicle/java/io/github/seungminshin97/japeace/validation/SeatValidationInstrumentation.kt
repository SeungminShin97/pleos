package io.github.seungminshin97.japeace.validation

import android.app.Activity
import android.app.Instrumentation
import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.Seat
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.MoveControl
import ai.pleos.playground.vehicle.listener.SeatForeAftPositionListener
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private typealias Failure = (Exception) -> Unit
private typealias PositionCaps = ((CapabilityConfig, Map<SeatArea, AreaBounds<Int>>) -> Unit, Failure) -> Unit
private typealias MoveCaps = ((CapabilityConfig, List<SeatArea>) -> Unit, Failure) -> Unit

/** Explicit emulator-only SDK probe. Not a production seat controller or preset implementation. */
class SeatValidationInstrumentation : Instrumentation() {
    private var arguments = Bundle()
    private val report = StringBuilder()
    private lateinit var vehicle: Vehicle
    private lateinit var properties: CarPropertyManager

    override fun onCreate(arguments: Bundle?) {
        this.arguments = arguments ?: Bundle()
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        var car: Car? = null
        var success = false
        try {
            check(Build.FINGERPRINT.startsWith("Pleos/crp_avd/") && Build.DEVICE == "crp_avd_x86_64") {
                "Probe is restricted to the known Pleos x86_64 emulator"
            }
            check(targetContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
            emit("environment", "fingerprint" to Build.FINGERPRINT, "operation" to arguments.getString("operation", "inventory"))
            car = Car.createCar(targetContext) ?: error("AAOS service unavailable")
            properties = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            vehicle = Vehicle(targetContext)
            vehicle.initialize()
            val axes = axes(vehicle.getSeat())
            when (val operation = arguments.getString("operation", "inventory")) {
                "inventory" -> inventory(axes, vehicle.getSeat())
                "motion" -> motion(axes.first { it.name == arguments.getString("axis", "foreAft") })
                else -> error("Unknown operation: $operation")
            }
            success = true
        } catch (error: Exception) {
            emit("failure", "error" to describe(error))
        } finally {
            // SDK callbacks have completed or individually timed out before releasing the connection.
            if (::vehicle.isInitialized) runCatching { vehicle.release() }
            runCatching { car?.disconnect() }
            emit("finished", "success" to success)
            finish(if (success) Activity.RESULT_OK else Activity.RESULT_CANCELED,
                Bundle().apply { putString(REPORT_KEY_STREAMRESULT, "\n$report") })
        }
    }

    private data class Axis(
        val name: String,
        val positionCaps: PositionCaps,
        val moveCaps: MoveCaps,
        val read: (SeatArea, (Int?) -> Unit, Failure) -> Unit,
        val move: (SeatArea, MoveControl, (SeatArea, MoveControl?) -> Unit, Failure) -> Unit,
    )

    private fun axes(seat: Seat) = listOf(
        Axis("foreAft", seat::checkSeatForeAftPositionCapabilities, seat::checkMoveSeatForeAftCapabilities, seat::getCurrentSeatForeAftPosition, seat::moveSeatForeAft),
        Axis("backrest", seat::checkSeatBackrestAnglePositionCapabilities, seat::checkMoveSeatBackrestAngleCapabilities, seat::getCurrentSeatBackrestAnglePosition, seat::moveSeatBackrestAngle),
        Axis("height", seat::checkSeatHeightPositionCapabilities, seat::checkMoveSeatHeightCapabilities, seat::getCurrentSeatHeightPosition, seat::moveSeatHeight),
        Axis("tilt", seat::checkSeatTiltPositionCapabilities, seat::checkMoveSeatTiltCapabilities, seat::getCurrentSeatTiltPosition, seat::moveSeatTilt),
        Axis("depth", seat::checkSeatDepthPositionCapabilities, seat::checkMoveSeatDepthCapabilities, seat::getCurrentSeatDepthPosition, seat::moveSeatDepth),
        Axis("beltHeight", seat::checkSeatBeltHeightPositionCapabilities, seat::checkMoveSeatBeltHeightCapabilities, seat::getCurrentSeatBeltHeightPosition, seat::moveSeatBeltHeight),
        Axis("headrestAngle", seat::checkSeatHeadrestAnglePositionCapabilities, seat::checkMoveSeatHeadrestAngleCapabilities, seat::getCurrentSeatHeadrestAnglePosition, seat::moveSeatHeadrestAngle),
        Axis("headrestForeAft", seat::checkSeatHeadrestForeAftPositionCapabilities, seat::checkMoveSeatHeadrestForeAftCapabilities, seat::getCurrentSeatHeadrestForeAftPosition, seat::moveSeatHeadrestForeAft),
        Axis("headrestHeight", seat::checkSeatHeadrestHeightPositionCapabilities, seat::checkMoveSeatHeadrestHeightCapabilities, seat::getCurrentSeatHeadrestHeightAftPosition, seat::moveSeatHeadrestHeight),
        Axis("lumbarForeAft", seat::checkSeatLumbarForeAftPositionCapabilities, seat::checkMoveSeatLumbarForeAftCapabilities, seat::getCurrentSeatLumbarForeAftPosition, seat::moveSeatLumbarForeAft),
        Axis("lumbarSide", seat::checkSeatLumbarSideSupportPositionCapabilities, seat::checkMoveSeatLumbarSideSupportCapabilities, seat::getCurrentSeatLumbarSideSupportPosition, seat::moveSeatLumbarSideSupport),
    )

    private fun inventory(axes: List<Axis>, seat: Seat) {
        for (axis in axes) {
            runCatching { positionCapabilities(axis) }.onSuccess { (config, areas) ->
                for ((area, bounds) in areas) {
                    emit("positionCapability", "axis" to axis.name, "area" to areaName(area), "access" to config.accessType,
                        "minRaw" to bounds.minValue, "maxRaw" to bounds.maxValue)
                    runCatching { read(axis, area) }.onSuccess { emit("position", "axis" to axis.name, "area" to areaName(area), "raw" to it) }
                        .onFailure { emit("readFailure", "axis" to axis.name, "area" to areaName(area), "error" to describe(it)) }
                }
                if (areas.isEmpty()) emit("emptyPositionCapability", "axis" to axis.name)
            }.onFailure { emit("capabilityFailure", "axis" to axis.name, "kind" to "position", "error" to describe(it)) }
            runCatching { moveCapabilities(axis) }.onSuccess { (config, areas) ->
                emit("moveCapability", "axis" to axis.name, "access" to config.accessType, "areas" to areas.map(::areaName))
            }.onFailure { emit("capabilityFailure", "axis" to axis.name, "kind" to "move", "error" to describe(it)) }
        }
        runCatching {
            await<Pair<CapabilityConfig, Map<SeatArea, AreaBounds<Int>>>> { done, failed ->
                seat.checkSeatMemoryCapabilities({ config, areas -> done(config to areas) }, failed)
            }
        }.onSuccess { (config, areas) ->
            areas.forEach { (area, bounds) -> emit("memoryCapability", "area" to areaName(area), "access" to config.accessType,
                "minSlot" to bounds.minValue, "maxSlot" to bounds.maxValue) }
        }.onFailure { emit("capabilityFailure", "kind" to "memory", "error" to describe(it)) }
    }

    private fun motion(axis: Axis) {
        val seat = vehicle.getSeat()
        val area = SeatArea(SeatValue.valueOf(arguments.getString("area", "ROW_1_LEFT")))
        val listener = object : SeatForeAftPositionListener {
            override fun onSeatForeAftPositionUpdated(value: Int?) {
                emit("positionEvent", "axis" to axis.name, "area" to areaName(area), "raw" to value)
            }
            override fun onFailed(exception: Exception) { emit("listenerFailure", "error" to describe(exception)) }
        }
        if (axis.name == "foreAft") seat.registerSeatForeAftPosition(area, listener)
        try { observeMotion(axis) }
        finally {
            if (axis.name == "foreAft") seat.unregisterSeatForeAftPosition(area, listener)
        }
    }

    private fun observeMotion(axis: Axis) {
        val area = SeatArea(SeatValue.valueOf(arguments.getString("area", "ROW_1_LEFT")))
        val (_, positionAreas) = positionCapabilities(axis)
        check(positionAreas.containsKey(area)) { "Requested area is not readable" }
        check(moveCapabilities(axis).second.contains(area)) { "Requested area is not movable" }
        requireParked()
        val original = read(axis, area)
        emit("savedPosition", "axis" to axis.name, "area" to areaName(area), "raw" to original)
        val direction = MoveControl.valueOf(arguments.getString("direction", "FORWARD"))
        check(direction != MoveControl.STOP)
        val pulseMillis = arguments.getString("pulseMillis", "400").toLong()
        require(pulseMillis in 50..2_000) { "Pulse duration must be 50..2000 ms" }
        pulse(axis, area, direction, pulseMillis)
        val moved = read(axis, area)
        SystemClock.sleep(500)
        val afterStop = read(axis, area)
        emit("motionObservation", "original" to original, "afterMove" to moved, "afterStop" to afterStop,
            "changed" to (moved != original), "stableAfterStop" to (moved == afterStop))
        if (moved == original) {
            emit("replayNotAttempted", "reason" to "No position change observed; do not infer a motion model")
            return
        }
        check(moved == afterStop) { "STOP did not produce stable readings; no further movement" }
        // Bounded emulator experiment only, learning the observed sign instead of assuming a unit/direction.
        val firstSign = (moved - original).compareTo(0)
        var current = moved
        var attempts = 0
        while (current != original && attempts < 12) {
            val sameDirection = (original - current).compareTo(0) == firstSign
            val next = if (sameDirection) direction else opposite(direction)
            pulse(axis, area, next, 60)
            current = read(axis, area)
            attempts += 1
            emit("replayObservation", "attempt" to attempts, "targetRaw" to original, "actualRaw" to current)
        }
        emit("replayResult", "targetRaw" to original, "actualRaw" to current, "exact" to (current == original), "attempts" to attempts)
    }

    private fun pulse(axis: Axis, area: SeatArea, direction: MoveControl, millis: Long) {
        requireParked()
        try {
            command(axis, area, direction)
            SystemClock.sleep(millis)
        } finally {
            // Send the SDK STOP command even when the move callback fails or times out.
            command(axis, area, MoveControl.STOP)
        }
    }

    private fun command(axis: Axis, area: SeatArea, direction: MoveControl) {
        emit("commandSent", "axis" to axis.name, "area" to areaName(area), "direction" to direction)
        await<Unit> { done, failed -> axis.move(area, direction, { callbackArea, callbackDirection ->
            emit("commandCallback", "area" to areaName(callbackArea), "direction" to callbackDirection)
            done(Unit)
        }, failed) }
    }

    private fun requireParked() {
        val gear = properties.getProperty<Int>(VehiclePropertyIds.GEAR_SELECTION, 0)
        val speed = properties.getProperty<Float>(VehiclePropertyIds.PERF_VEHICLE_SPEED, 0)
        check(gear.status == CarPropertyValue.STATUS_AVAILABLE && speed.status == CarPropertyValue.STATUS_AVAILABLE
            && gear.value == VehicleGear.GEAR_PARK && speed.value.isFinite() && speed.value == 0f) {
            "Movement blocked: current AAOS data must be available, P and zero speed"
        }
    }

    private fun positionCapabilities(axis: Axis) = await<Pair<CapabilityConfig, Map<SeatArea, AreaBounds<Int>>>> { done, failed ->
        axis.positionCaps({ config, areas -> done(config to areas) }, failed)
    }

    private fun moveCapabilities(axis: Axis) = await<Pair<CapabilityConfig, List<SeatArea>>> { done, failed ->
        axis.moveCaps({ config, areas -> done(config to areas) }, failed)
    }

    private fun read(axis: Axis, area: SeatArea): Int = checkNotNull(await<Int?> { done, failed -> axis.read(area, done, failed) }) {
        "SDK returned an empty position"
    }

    private fun <T> await(request: ((T) -> Unit, Failure) -> Unit): T {
        val latch = CountDownLatch(1)
        val value = AtomicReference<CallbackResult<T>>()
        request({ result -> if (value.compareAndSet(null, CallbackResult(Result.success(result)))) latch.countDown() },
            { error -> if (value.compareAndSet(null, CallbackResult(Result.failure(error)))) latch.countDown() })
        check(latch.await(8, TimeUnit.SECONDS)) { "SDK callback timeout" }
        return value.get().result.getOrThrow()
    }

    private data class CallbackResult<T>(val result: Result<T>)

    private fun areaName(area: SeatArea) = area.values.joinToString("+") { "${it.name}(${it.id})" }
    private fun opposite(direction: MoveControl) = if (direction == MoveControl.FORWARD) MoveControl.BACKWARD else MoveControl.FORWARD
    private fun describe(error: Throwable) = "${error.javaClass.name}: ${error.message}"

    @Synchronized private fun emit(event: String, vararg fields: Pair<String, Any?>) {
        val record = JSONObject().put("event", event)
        fields.forEach { (key, value) -> record.put(key, value ?: JSONObject.NULL) }
        val line = record.toString()
        report.appendLine(line)
        Log.i("SeatValidation", line)
    }
}
