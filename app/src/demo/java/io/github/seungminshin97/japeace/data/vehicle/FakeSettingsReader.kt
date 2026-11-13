package io.github.seungminshin97.japeace.data.vehicle

import io.github.seungminshin97.japeace.domain.rest.*
import io.github.seungminshin97.japeace.domain.policy.ParkingDecision
import io.github.seungminshin97.japeace.domain.policy.ParkingPolicy

class FakeSettingsReader(private val gateway: FakeVehicleGateway) : SettingsReader {
    override suspend fun captureDriverSeat(): CapturedSettings {
        check(ParkingPolicy().evaluate(gateway.state.value) == ParkingDecision.Allowed) { "P와 정지 상태에서만 저장할 수 있습니다." }
        val at = System.currentTimeMillis()
        return CapturedSettings(CaptureProfile("demo-seat-v1", CaptureSource.DEMO, "fake-seat-v1"),
            listOf("foreAft" to 40, "backrest" to 20, "height" to 50, "tilt" to 10).map { (key, raw) ->
                SettingObservation(key, 1, raw, 0, 100, at)
            })
    }
}
