package io.github.seungminshin97.japeace.domain.rest

import kotlinx.coroutines.flow.Flow

enum class CaptureSource { DEMO, PLEOS_EMULATOR, VEHICLE }
enum class OperationState { PLANNED, DISPATCHED, CONFIRMED, FAILED, UNKNOWN }
enum class SessionState { PLANNED, RECOVERY_REQUIRED, COMPLETED, ABANDONED }

/** A build/profile label is not a verified vehicle identity. No VIN is collected. */
data class CaptureProfile(
    val key: String,
    val source: CaptureSource,
    val mappingVersion: String,
    val identityVerified: Boolean = false,
)

data class SettingObservation(
    val key: String,
    val areaId: Int,
    val rawValue: Int,
    val minRaw: Int,
    val maxRaw: Int,
    val observedAtEpochMillis: Long,
    val unit: String = "RAW_UNKNOWN",
    val replayVerified: Boolean = false,
) {
    val identity: Pair<String, Int> get() = key to areaId
    fun validate() {
        require(key.isNotBlank() && areaId > 0 && unit.isNotBlank())
        require(minRaw <= maxRaw && rawValue in minRaw..maxRaw)
        require(observedAtEpochMillis > 0)
    }
}

data class CapturedSettings(val profile: CaptureProfile, val settings: List<SettingObservation>) {
    fun validate() {
        require(profile.key.isNotBlank() && profile.mappingVersion.isNotBlank())
        require(settings.isNotEmpty())
        settings.forEach { it.validate() }
        require(settings.map { it.identity }.distinct().size == settings.size) { "Duplicate setting area" }
    }
}

data class RestPreset(
    val id: String,
    val name: String,
    val revision: Int,
    val schemaVersion: Int,
    val createdAtEpochMillis: Long,
    val capture: CapturedSettings,
)

data class SettingOperation(
    val id: String,
    val sessionId: String,
    val sequence: Int,
    val key: String,
    val areaId: Int,
    val beforeRaw: Int,
    val targetRaw: Int,
    val state: OperationState,
    val confirmedRaw: Int? = null,
    val error: String? = null,
)

data class StoredSession(
    val id: String,
    val requestId: String,
    val presetId: String,
    val presetRevision: Int,
    val state: SessionState,
    val before: CapturedSettings,
    val operations: List<SettingOperation>,
)

fun interface SettingsReader { suspend fun captureDriverSeat(): CapturedSettings }

/** Durable records only. These methods never send a vehicle command. */
interface RestRepository {
    fun observePresets(): Flow<List<RestPreset>>
    fun observeActiveSession(): Flow<StoredSession?>
    suspend fun savePreset(name: String, capture: CapturedSettings): RestPreset
    suspend fun prepareSession(requestId: String, presetId: String, before: CapturedSettings): StoredSession
    suspend fun transitionOperation(sessionId: String, operationId: String, expected: OperationState,
        next: OperationState, confirmedRaw: Int? = null, error: String? = null)
    suspend fun completeSession(sessionId: String)
    suspend fun abandonSession(sessionId: String)
    suspend fun recoverInterruptedSession()
}
