package io.github.seungminshin97.japeace.domain.rest

import io.github.seungminshin97.japeace.domain.policy.ParkingDecision
import io.github.seungminshin97.japeace.domain.policy.ParkingPolicy
import io.github.seungminshin97.japeace.domain.vehicle.VehicleGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class DemoWriteResult { ACCEPTED, REJECTED }

interface DemoSettings : SettingsReader {
    suspend fun readSettings(): CapturedSettings = captureDriverSeat()

    /** Reject without changing anything if the value or parking condition changed. */
    suspend fun compareAndSet(operation: SettingOperation): DemoWriteResult
}

class DemoRestCoordinator(
    private val store: RestExecutionStore,
    private val vehicle: VehicleGateway,
    private val settings: DemoSettings,
) {
    private val mutex = Mutex()
    private val parkingPolicy = ParkingPolicy()
    private var recovered = false

    suspend fun recover() = mutex.withLock { recoverOnce() }

    private suspend fun recoverOnce() {
        if (recovered) return
        store.recoverInterruptedSession()
        recovered = true
    }

    suspend fun apply(requestId: String, presetId: String): StoredSession = mutex.withLock {
        recoverOnce()
        store.findRequest(requestId)?.let {
            require(it.presetId == presetId)
            return@withLock it
        }
        val before = settings.captureDriverSeat()
        check(before.profile.source == CaptureSource.DEMO)
        check(isParked())
        val session = store.prepareSession(requestId, presetId, before)
        execute(session, session.operations) { store.markApplied(session.id) }
    }

    /** Call after the user confirms restoration of this session. */
    suspend fun restore(sessionId: String): StoredSession = mutex.withLock {
        recoverOnce()
        val current = store.getSession(sessionId)
        check(current.before.profile.source == CaptureSource.DEMO)
        if (current.state == SessionState.COMPLETED) return@withLock current
        check(isParked())
        val session = store.prepareRestore(sessionId)
        val restore = session.operations.drop(session.before.settings.size)
        if (restore.any { it.state in setOf(OperationState.UNKNOWN, OperationState.DISPATCHED, OperationState.FAILED) }) {
            return@withLock session
        }
        execute(session, restore.filter { it.state == OperationState.PLANNED }) {
            val latest = store.getSession(sessionId)
            if (latest.operations.any { it.state == OperationState.UNKNOWN }) {
                store.requireRecovery(sessionId)
            } else {
                store.completeSession(sessionId)
            }
        }
    }

    /** Call after the user chooses to resolve the remaining settings manually. */
    suspend fun abandon(sessionId: String) = mutex.withLock {
        recoverOnce()
        val session = store.getSession(sessionId)
        check(session.before.profile.source == CaptureSource.DEMO)
        if (session.state != SessionState.ABANDONED) store.abandonSession(sessionId)
    }

    private suspend fun execute(
        session: StoredSession,
        operations: List<SettingOperation>,
        finish: suspend () -> Unit,
    ): StoredSession {
        try {
            for (operation in operations) {
                if (!isParked() || read(session, operation) != operation.beforeRaw || !isParked()) {
                    store.requireRecovery(session.id)
                    return store.getSession(session.id)
                }
                store.transitionOperation(session.id, operation.id, OperationState.PLANNED, OperationState.DISPATCHED)
                val accepted = isParked() && (operation.beforeRaw == operation.targetRaw ||
                    settings.compareAndSet(operation) == DemoWriteResult.ACCEPTED)
                val verified = accepted && read(session, operation) == operation.targetRaw
                val next = when {
                    verified -> OperationState.CONFIRMED
                    accepted -> OperationState.UNKNOWN
                    else -> OperationState.FAILED
                }
                store.transitionOperation(session.id, operation.id, OperationState.DISPATCHED, next,
                    confirmedRaw = operation.targetRaw.takeIf { verified },
                    error = when (next) {
                        OperationState.UNKNOWN -> "READBACK_MISMATCH"
                        OperationState.FAILED -> "WRITE_REJECTED"
                        else -> null
                    })
                if (!verified) {
                    store.requireRecovery(session.id)
                    return store.getSession(session.id)
                }
            }
            finish()
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { store.requireRecovery(session.id) }
            throw cancelled
        } catch (failure: Exception) {
            try {
                store.requireRecovery(session.id)
            } catch (journalFailure: Exception) {
                failure.addSuppressed(journalFailure)
            }
            throw failure
        }
        return store.getSession(session.id)
    }

    private suspend fun read(session: StoredSession, operation: SettingOperation): Int? {
        val captured = settings.readSettings()
        if (captured.profile != session.before.profile) return null
        val original = session.before.settings.single { it.identity == (operation.key to operation.areaId) }
        return captured.settings.singleOrNull {
            it.identity == original.identity && it.unit == original.unit &&
                it.replayVerified && operation.targetRaw in it.minRaw..it.maxRaw
        }?.rawValue
    }

    private fun isParked() = parkingPolicy.evaluate(vehicle.state.value) == ParkingDecision.Allowed
}
