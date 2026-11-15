package io.github.seungminshin97.japeace.data.vehicle

import io.github.seungminshin97.japeace.domain.rest.*
import io.github.seungminshin97.japeace.DemoControls
import io.github.seungminshin97.japeace.domain.policy.ParkingDecision
import io.github.seungminshin97.japeace.domain.policy.ParkingPolicy

class FakeSettingsReader(private val gateway: FakeVehicleGateway) : DemoSettings, DemoControls {
    private val parkingPolicy = ParkingPolicy()
    private val profile = CaptureProfile("demo-seat-v1", CaptureSource.DEMO, "fake-seat-v1")
    private val values = linkedMapOf("foreAft" to 40, "backrest" to 20, "height" to 50, "tilt" to 10)

    override fun setParked(parked: Boolean) = gateway.setParked(parked)

    override fun changeSeatPosition(): Int? = synchronized(values) {
        if (!isParked()) return@synchronized null
        val position = if (values["foreAft"] == 40) 65 else 40
        values["foreAft"] = position
        position
    }

    override suspend fun captureDriverSeat(): CapturedSettings {
        check(isParked()) { "P와 정지 상태에서만 저장할 수 있습니다." }
        return readSettings()
    }

    override suspend fun readSettings(): CapturedSettings = synchronized(values) {
        val at = System.currentTimeMillis()
        CapturedSettings(profile, values.map { (key, raw) ->
            SettingObservation(key, 1, raw, 0, 100, at, replayVerified = true)
        })
    }

    override suspend fun compareAndSet(operation: SettingOperation): DemoWriteResult = synchronized(values) {
        if (!isParked() || operation.areaId != 1 || operation.targetRaw !in 0..100 ||
            values[operation.key] != operation.beforeRaw) {
            return@synchronized DemoWriteResult.REJECTED
        }
        values[operation.key] = operation.targetRaw
        DemoWriteResult.ACCEPTED
    }

    private fun isParked() = parkingPolicy.evaluate(gateway.state.value) == ParkingDecision.Allowed
}
