package io.github.seungminshin97.japeace.integration.vehicle

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.car.hardware.property.EvChargeState
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.HandlerThread
import android.os.Build
import android.util.Log
import io.github.seungminshin97.japeace.domain.vehicle.Gear
import io.github.seungminshin97.japeace.domain.vehicle.ChargingStatus
import io.github.seungminshin97.japeace.domain.vehicle.VehicleGateway
import io.github.seungminshin97.japeace.domain.vehicle.VehicleIssue
import io.github.seungminshin97.japeace.domain.vehicle.VehicleReadings
import io.github.seungminshin97.japeace.domain.vehicle.VehicleState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.github.seungminshin97.japeace.domain.vehicle.BatteryReading
import io.github.seungminshin97.japeace.domain.vehicle.batteryReading

/** AAOS read-only adapter. All property IPC runs off the UI thread. No vehicle writes. */
class PleosVehicleGateway(private val context: Context) : VehicleGateway {
    private val mutableState = MutableStateFlow(VehicleState())
    override val state = mutableState.asStateFlow()
    private val lock = Any()
    private val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private var generation = 0L
    private val thread = HandlerThread("VehicleReadings").apply { start() }
    private val handler = Handler(thread.looper)
    // Confined to the worker thread.
    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private var callback: CarPropertyManager.CarPropertyEventCallback? = null
    private var refresh: Runnable? = null
    private var connection = 0L

    override fun start() {
        val token = synchronized(lock) {
            generation += 1
            mutableState.value = VehicleState(issue = VehicleIssue.CONNECTING)
            generation
        }
        handler.post {
            disconnect()
            if (!current(token)) return@post
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                publish(token, VehicleState(issue = VehicleIssue.UNSUPPORTED))
                return@post
            }
            if (context.checkSelfPermission(Car.PERMISSION_SPEED) != PackageManager.PERMISSION_GRANTED) {
                publish(token, VehicleState(issue = VehicleIssue.PERMISSION_REQUIRED))
                return@post
            }
            try {
                car = Car.createCar(context, handler, 0L) { readyCar, ready ->
                    handler.post lifecycle@{
                        if (!current(token)) return@lifecycle
                        clearSubscription()
                        if (ready) subscribe(readyCar, token)
                        else {
                            publish(token, VehicleState(issue = VehicleIssue.DISCONNECTED))
                            handler.postDelayed({
                                if (current(token) && state.value.issue == VehicleIssue.DISCONNECTED) start()
                            }, 1_500L)
                        }
                    }
                }
                if (car == null) publish(token, VehicleState(issue = VehicleIssue.DISCONNECTED))
            } catch (_: SecurityException) {
                publish(token, VehicleState(issue = VehicleIssue.PERMISSION_REQUIRED))
            } catch (_: RuntimeException) {
                publish(token, VehicleState(issue = VehicleIssue.READ_FAILED))
            }
        }
    }

    override fun stop() {
        synchronized(lock) {
            generation += 1
            mutableState.value = VehicleState(issue = VehicleIssue.DISCONNECTED)
        }
        handler.post { disconnect() }
    }

    override fun close() {
        stop()
        handler.post { thread.quitSafely() }
    }

    @Suppress("DEPRECATION") // API 34 image does not support the newer subscribePropertyEvents API.
    private fun subscribe(readyCar: Car, token: Long) {
        try {
            val properties = readyCar.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                ?: throw IllegalStateException("CarPropertyManager unavailable")
            manager = properties
            val session = connection
            val readings = VehicleReadings()
            var battery: BatteryReading? = null
            var chargePortConnected: Boolean? = null
            var chargingStatus: ChargingStatus? = null
            fun snapshot() = readings.snapshot().copy(
                batteryPercent = battery?.percent,
                batteryUsesNominalCapacity = battery?.usesNominalCapacity == true,
                chargePortConnected = chargePortConnected,
                chargingStatus = chargingStatus,
            )
            fun active() = current(token) && connection == session && manager === properties
            fun accept(value: CarPropertyValue<*>) {
                if (value.areaId != 0) return
                val available = value.status == CarPropertyValue.STATUS_AVAILABLE
                when (value.propertyId) {
                    VehiclePropertyIds.GEAR_SELECTION -> {
                        if (!available) readings.invalidateGear()
                        else readings.gear((value.value as? Int)?.toGear(), value.timestamp)
                    }
                    VehiclePropertyIds.PERF_VEHICLE_SPEED -> {
                        if (!available) readings.invalidateSpeed()
                        else readings.speed(value.value as? Float, value.timestamp)
                    }
                }
            }
            val events = object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(value: CarPropertyValue<*>) {
                    if (!active()) return
                    accept(value)
                    publish(token, snapshot())
                }

                override fun onErrorEvent(propertyId: Int, areaId: Int) {
                    if (!active() || areaId != 0) return
                    when (propertyId) {
                        VehiclePropertyIds.GEAR_SELECTION -> readings.invalidateGear()
                        VehiclePropertyIds.PERF_VEHICLE_SPEED -> readings.invalidateSpeed()
                    }
                    publish(token, snapshot())
                }
            }
            callback = events
            val gearSubscribed = properties.registerCallback(events, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            val speedSubscribed = properties.registerCallback(events, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
            if (!gearSubscribed || !speedSubscribed) {
                clearSubscription()
                publish(token, VehicleState(connected = true, issue = VehicleIssue.UNSUPPORTED))
                return
            }
            publish(token, VehicleState(connected = true, issue = VehicleIssue.DATA_UNAVAILABLE))
            val poll = object : Runnable {
                private var energyRefreshAt = 0L

                override fun run() {
                    if (!active()) return
                    try {
                        // ON_CHANGE silence does not prove freshness; re-query both statuses.
                        accept(properties.getProperty<Int>(VehiclePropertyIds.GEAR_SELECTION, 0))
                        accept(properties.getProperty<Float>(VehiclePropertyIds.PERF_VEHICLE_SPEED, 0))
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now >= energyRefreshAt) {
                            energyRefreshAt = now + 10_000
                            val level = properties.readOptional<Float>(VehiclePropertyIds.EV_BATTERY_LEVEL)
                            val currentCapacity = properties.readOptional<Float>(VehiclePropertyIds.EV_CURRENT_BATTERY_CAPACITY)
                            battery = batteryReading(level, currentCapacity) {
                                properties.readOptional<Float>(VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY)
                            }
                            chargePortConnected = properties.readOptional<Boolean>(VehiclePropertyIds.EV_CHARGE_PORT_CONNECTED)
                            chargingStatus = when (properties.readOptional<Int>(VehiclePropertyIds.EV_CHARGE_STATE)) {
                                EvChargeState.STATE_CHARGING -> ChargingStatus.CHARGING
                                EvChargeState.STATE_FULLY_CHARGED -> ChargingStatus.TARGET_REACHED
                                EvChargeState.STATE_NOT_CHARGING -> ChargingStatus.NOT_CHARGING
                                EvChargeState.STATE_ERROR -> ChargingStatus.ERROR
                                else -> null
                            }
                        }
                        publish(token, snapshot())
                    } catch (_: SecurityException) {
                        clearSubscription()
                        publish(token, VehicleState(connected = true, issue = VehicleIssue.PERMISSION_REQUIRED))
                        return
                    } catch (_: RuntimeException) {
                        readings.invalidateGear()
                        readings.invalidateSpeed()
                        publish(token, VehicleState(connected = true, issue = VehicleIssue.READ_FAILED))
                    }
                    if (active()) handler.postDelayed(this, 1_000L)
                }
            }
            refresh = poll
            handler.post(poll)
        } catch (_: SecurityException) {
            clearSubscription()
            publish(token, VehicleState(connected = true, issue = VehicleIssue.PERMISSION_REQUIRED))
        } catch (_: RuntimeException) {
            clearSubscription()
            publish(token, VehicleState(issue = VehicleIssue.READ_FAILED))
        }
    }

    private fun current(token: Long) = synchronized(lock) { generation == token }

    private inline fun <reified T> CarPropertyManager.readOptional(id: Int): T? = runCatching {
        getProperty<T>(id, 0)?.takeIf { it.status == CarPropertyValue.STATUS_AVAILABLE }?.value
    }.getOrNull()

    private fun publish(token: Long, value: VehicleState) = synchronized(lock) {
        if (generation == token) {
            if (debug && mutableState.value != value) {
                Log.d("VehicleReadings", "connected=${value.connected}, valid=${value.readingsValid}, gear=${value.gear}, speedMps=${value.speedMetersPerSecond}, issue=${value.issue}")
            }
            mutableState.value = value
        }
    }

    private fun clearSubscription() {
        connection += 1
        refresh?.let { handler.removeCallbacks(it) }
        refresh = null
        callback?.let { events -> runCatching { manager?.unregisterCallback(events) } }
        callback = null
        manager = null
    }

    private fun disconnect() {
        clearSubscription()
        runCatching { car?.disconnect() }
        car = null
    }

    private fun Int.toGear(): Gear? = when (this) {
        VehicleGear.GEAR_PARK -> Gear.PARK
        VehicleGear.GEAR_REVERSE -> Gear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
        VehicleGear.GEAR_DRIVE -> Gear.DRIVE
        else -> null
    }
}
