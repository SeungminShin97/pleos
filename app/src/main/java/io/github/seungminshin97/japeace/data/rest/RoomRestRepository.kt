package io.github.seungminshin97.japeace.data.rest

import androidx.room.withTransaction
import io.github.seungminshin97.japeace.domain.rest.*
import kotlinx.coroutines.flow.map
import java.util.UUID

class RoomRestRepository(private val database: RestDatabase) : RestRepository {
    private val dao = database.records()
    override fun observePresets() = dao.observePresets().map { rows -> rows.map { it.toDomain() } }
    override fun observeActiveSession() = dao.observeActive().map { it?.toDomain() }

    override suspend fun savePreset(name: String, capture: CapturedSettings): RestPreset {
        require(name.trim().length in 1..40) { "프리셋 이름은 1~40자로 입력해 주세요." }
        capture.validate()
        val row = PresetRow(UUID.randomUUID().toString(), name.trim(), 1, 1, System.currentTimeMillis(),
            capture.profile.key, capture.profile.source.name, capture.profile.mappingVersion, capture.profile.identityVerified)
        return database.withTransaction {
            dao.insertPreset(row)
            dao.insertPresetSettings(capture.settings.map { value -> PresetSettingRow(row.id, value.key, value.areaId,
                value.rawValue, value.minRaw, value.maxRaw, value.observedAtEpochMillis, value.unit, value.replayVerified) })
            checkNotNull(dao.preset(row.id)).toDomain()
        }
    }

    override suspend fun prepareSession(requestId: String, presetId: String, before: CapturedSettings): StoredSession {
        require(requestId.isNotBlank())
        before.validate()
        return database.withTransaction {
            dao.request(requestId)?.let { existing ->
                require(existing.session.presetId == presetId) { "Request ID belongs to another preset" }
                return@withTransaction existing.toDomain()
            }
            check(dao.active() == null) { "An unresolved session already exists" }
            val preset = checkNotNull(dao.preset(presetId)).toDomain()
            require(preset.schemaVersion == 1 && preset.capture.profile == before.profile) { "Capture profile mismatch" }
            val current = before.settings.associateBy { it.identity }
            val originals = preset.capture.settings.map { target ->
                val original = requireNotNull(current[target.identity]) { "Missing original setting" }
                require(target.unit == original.unit && target.rawValue in original.minRaw..original.maxRaw) { "Unit or supported range changed" }
                original
            }
            val id = UUID.randomUUID().toString()
            val row = SessionRow(id, requestId, preset.id, preset.revision, 1, SessionState.PLANNED.name,
                System.currentTimeMillis(), null, 1, before.profile.key, before.profile.source.name,
                before.profile.mappingVersion, before.profile.identityVerified)
            dao.insertSession(row)
            dao.insertBefore(originals.map { value -> BeforeRow(id, value.key, value.areaId, value.rawValue,
                value.minRaw, value.maxRaw, value.observedAtEpochMillis, value.unit, value.replayVerified) })
            dao.insertOperations(preset.capture.settings.mapIndexed { index, target ->
                OperationRow(UUID.randomUUID().toString(), id, index, target.key, target.areaId,
                    originals[index].rawValue, target.rawValue, OperationState.PLANNED.name, null, null)
            })
            checkNotNull(dao.session(id)).toDomain()
        }
    }

    override suspend fun transitionOperation(sessionId: String, operationId: String, expected: OperationState,
        next: OperationState, confirmedRaw: Int?, error: String?) {
        require((expected == OperationState.PLANNED && next == OperationState.DISPATCHED) ||
            (expected == OperationState.DISPATCHED && next in setOf(OperationState.CONFIRMED, OperationState.FAILED, OperationState.UNKNOWN)))
        database.withTransaction {
            val operation = checkNotNull(dao.session(sessionId)).operations.single { it.id == operationId }
            if (next == OperationState.CONFIRMED) require(confirmedRaw == operation.targetRaw) { "Unconfirmed target value" }
            else require(confirmedRaw == null)
            check(dao.transition(sessionId, operationId, expected.name, next.name, confirmedRaw, error) == 1) { "Stale operation transition" }
        }
    }

    override suspend fun completeSession(sessionId: String) = database.withTransaction {
        val record = checkNotNull(dao.session(sessionId))
        check(record.operations.isNotEmpty() && record.operations.all { it.state == OperationState.CONFIRMED.name })
        check(dao.finish(sessionId, SessionState.COMPLETED.name, System.currentTimeMillis()) == 1)
    }

    override suspend fun abandonSession(sessionId: String) = database.withTransaction {
        // A future coordinator must obtain explicit user confirmation before calling this method.
        check(dao.finish(sessionId, SessionState.ABANDONED.name, System.currentTimeMillis()) == 1)
    }

    override suspend fun recoverInterruptedSession() = database.withTransaction {
        dao.active()?.let { record ->
            dao.markInterruptedOperations(record.session.id)
            dao.markRecovery(record.session.id)
        }
        Unit
    }

    private fun PresetRecord.toDomain() = RestPreset(preset.id, preset.name, preset.revision, preset.schemaVersion,
        preset.createdAt, CapturedSettings(CaptureProfile(preset.profileKey, CaptureSource.valueOf(preset.source),
            preset.mappingVersion, preset.identityVerified), settings.sortedWith(compareBy({ it.settingKey }, { it.areaId })).map {
            SettingObservation(it.settingKey, it.areaId, it.rawValue, it.minRaw, it.maxRaw, it.observedAt, it.unit, it.replayVerified)
        }))

    private fun SessionRecord.toDomain() = StoredSession(session.id, session.requestId, session.presetId, session.presetRevision,
        SessionState.valueOf(session.state), CapturedSettings(CaptureProfile(session.profileKey, CaptureSource.valueOf(session.source),
            session.mappingVersion, session.identityVerified), before.sortedWith(compareBy({ it.settingKey }, { it.areaId })).map {
            SettingObservation(it.settingKey, it.areaId, it.rawValue, it.minRaw, it.maxRaw, it.observedAt, it.unit, it.replayVerified)
        }), operations.sortedBy { it.sequence }.map {
            SettingOperation(it.id, it.sessionId, it.sequence, it.settingKey, it.areaId, it.beforeRaw, it.targetRaw,
                OperationState.valueOf(it.state), it.confirmedRaw, it.error)
        })
}
