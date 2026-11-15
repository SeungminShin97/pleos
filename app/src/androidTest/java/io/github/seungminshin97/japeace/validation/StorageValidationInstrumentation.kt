package io.github.seungminshin97.japeace.validation

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import androidx.room.Room
import io.github.seungminshin97.japeace.data.rest.*
import io.github.seungminshin97.japeace.domain.rest.*
import io.github.seungminshin97.japeace.domain.vehicle.Gear
import io.github.seungminshin97.japeace.domain.vehicle.VehicleGateway
import io.github.seungminshin97.japeace.domain.vehicle.VehicleState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.util.UUID

/** Uses unique test-owned databases; never clears the user's rest.db. No vehicle APIs. */
class StorageValidationInstrumentation : Instrumentation() {
    private val report = StringBuilder()
    private var passed = 0
    private var failed = 0
    private var mode = "suite"
    private var processDatabase: RestDatabase? = null

    override fun onCreate(arguments: Bundle?) {
        mode = arguments?.getString("storageMode", "suite") ?: "suite"
        super.onCreate(arguments)
        start()
    }

    override fun onStart() = runBlocking {
        if (mode != "suite") {
            case("process restart phase: $mode") { processPhase(mode) }
            finishReport()
            return@runBlocking
        }
        case("apply and restore are durable, serialized and idempotent") { fixture { _, _, repo ->
            val settings = ExecutionFixture()
            val coordinator = DemoRestCoordinator(repo, settings, settings)
            val preset = repo.savePreset("execution", settings.captureDriverSeat())
            settings.values["foreAft"] = 45
            settings.values["height"] = 55
            settings.beforeWrite = { operation ->
                val journal = repo.getSession(operation.sessionId)
                check(journal.operations.single { it.id == operation.id }.state == OperationState.DISPATCHED)
                check(journal.before.settings.map { it.rawValue } == listOf(45, 55))
            }
            val results = coroutineScope {
                List(2) { async { coordinator.apply("same-apply", preset.id) } }.awaitAll()
            }
            val applied = results.first()
            check(results.all { it == applied } && applied.state == SessionState.APPLIED)
            check(settings.writes == listOf("foreAft", "height"))
            check(repo.observeActiveSession().first()?.id == applied.id)
            check(runCatching { coordinator.apply("second", preset.id) }.isFailure)
            val restored = coordinator.restore(applied.id)
            check(restored.state == SessionState.COMPLETED)
            check(restored.operations.size == 4)
            check(settings.writes == listOf("foreAft", "height", "height", "foreAft"))
            check(settings.values == mapOf("foreAft" to 45, "height" to 55))
            check(coordinator.restore(applied.id) == restored)
            check(repo.observeActiveSession().first() == null)
        } }

        case("driving blocks apply and interrupts remaining operations") { fixture { _, _, repo ->
            val settings = ExecutionFixture()
            val coordinator = DemoRestCoordinator(repo, settings, settings)
            val preset = repo.savePreset("parking", settings.captureDriverSeat())
            settings.values.replaceAll { _, _ -> 45 }
            settings.setParked(false)
            check(runCatching { coordinator.apply("driving", preset.id) }.isFailure)
            check(repo.findRequest("driving") == null)
            settings.setParked(true)
            settings.afterWrite = { settings.setParked(false) }
            val session = coordinator.apply("parking-lost", preset.id)
            check(session.state == SessionState.RECOVERY_REQUIRED)
            check(session.operations.map { it.state } == listOf(OperationState.CONFIRMED, OperationState.PLANNED))
            check(settings.writes == listOf("foreAft"))
            check(runCatching { coordinator.restore(session.id) }.isFailure)
            settings.setParked(true)
            settings.afterWrite = {}
            check(coordinator.restore(session.id).state == SessionState.COMPLETED)
            check(settings.values.values.all { it == 45 })
        } }

        case("manual conflict blocks restore across coordinator restart") { fixture { _, _, repo ->
            val settings = ExecutionFixture()
            val preset = repo.savePreset("manual", settings.captureDriverSeat())
            settings.values["height"] = 55
            val applied = DemoRestCoordinator(repo, settings, settings).apply("manual", preset.id)
            settings.values["height"] = 75
            val restarted = DemoRestCoordinator(repo, settings, settings)
            check(restarted.restore(applied.id).state == SessionState.RECOVERY_REQUIRED)
            check(settings.values["height"] == 75 && settings.writes == listOf("height"))
            restarted.abandon(applied.id)
            check(repo.observeActiveSession().first() == null)
        } }

        case("accepted write without matching readback stays unknown") { fixture { _, _, repo ->
            val settings = ExecutionFixture()
            val coordinator = DemoRestCoordinator(repo, settings, settings)
            val preset = repo.savePreset("readback", settings.captureDriverSeat())
            settings.values["foreAft"] = 45
            settings.afterWrite = { settings.values["foreAft"] = 80 }
            val result = coordinator.apply("unknown", preset.id)
            check(result.operations.first().state == OperationState.UNKNOWN)
            check(coordinator.restore(result.id).state == SessionState.RECOVERY_REQUIRED)
            check(settings.writes == listOf("foreAft") && settings.values["foreAft"] == 80)
        } }

        case("cancellation records uncertainty without replay") { fixture { _, _, repo ->
            val settings = ExecutionFixture()
            val coordinator = DemoRestCoordinator(repo, settings, settings)
            val preset = repo.savePreset("cancelled", settings.captureDriverSeat())
            settings.values["foreAft"] = 45
            settings.beforeWrite = { throw CancellationException("interrupted") }
            check(runCatching { coordinator.apply("cancelled", preset.id) }.exceptionOrNull() is CancellationException)
            val result = checkNotNull(repo.findRequest("cancelled"))
            check(result.state == SessionState.RECOVERY_REQUIRED)
            check(result.operations.first().state == OperationState.UNKNOWN)
            settings.beforeWrite = {}
            check(coordinator.apply("cancelled", preset.id) == result)
            check(settings.writes.isEmpty())
        } }

        case("preset survives database close and reopen") { fixture { name, database, repo ->
            val saved = repo.savePreset("나의 휴식", capture(10))
            database.close()
            val reopened = open(name)
            try {
                val result = RoomRestRepository(reopened).observePresets().first().single()
                check(result == saved)
                check(result.capture.settings.all { !it.replayVerified && it.unit == "RAW_UNKNOWN" })
                check(!result.capture.profile.identityVerified)
            } finally { reopened.close() }
        } }

        case("duplicate request preserves original snapshot and blocks another session") { fixture { _, _, repo ->
            val preset = repo.savePreset("idempotency", capture(10))
            val first = repo.prepareSession("same-request", preset.id, capture(40))
            val second = repo.prepareSession("same-request", preset.id, capture(60))
            check(first == second && second.before.settings.single { it.key == "foreAft" }.rawValue == 40)
            check(runCatching { repo.prepareSession("another-request", preset.id, capture(70)) }.isFailure)
            check(repo.observeActiveSession().first()?.id == first.id)
        } }

        case("operation insert failure rolls back session and snapshot") { fixture { _, database, repo ->
            val preset = repo.savePreset("rollback", capture(10))
            database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_operation BEFORE INSERT ON operations BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
            check(runCatching { repo.prepareSession("rollback-request", preset.id, capture(40)) }.isFailure)
            check(count(database, "sessions") == 0 && count(database, "before_snapshots") == 0 && count(database, "operations") == 0)
            check(repo.observePresets().first().single().id == preset.id)
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_operation")
            check(repo.prepareSession("rollback-request", preset.id, capture(40)).operations.size == 2)
        } }

        case("two database instances permit exactly one active session") { fixture { name, database, repo ->
            val preset = repo.savePreset("concurrency", capture(10))
            val other = open(name)
            try {
                val results = coroutineScope {
                    listOf(async(Dispatchers.IO) { runCatching { repo.prepareSession("client-a", preset.id, capture(40)) } },
                        async(Dispatchers.IO) { runCatching { RoomRestRepository(other).prepareSession("client-b", preset.id, capture(60)) } }).awaitAll()
                }
                check(results.count { it.isSuccess } == 1)
                check(count(database, "sessions") == 1)
                val active = checkNotNull(repo.observeActiveSession().first())
                check(active.before.settings.map { it.rawValue } in listOf(listOf(41, 40), listOf(61, 60)))
            } finally { other.close() }
        } }

        case("interrupted dispatched operation becomes unknown and retains originals") { fixture { name, database, repo ->
            val preset = repo.savePreset("recovery", capture(10))
            val original = repo.prepareSession("restart-request", preset.id, capture(40))
            repo.transitionOperation(original.id, original.operations[0].id, OperationState.PLANNED, OperationState.DISPATCHED)
            database.close()
            val reopened = open(name)
            try {
                val recoveredRepo = RoomRestRepository(reopened)
                recoveredRepo.recoverInterruptedSession()
                val recovered = checkNotNull(recoveredRepo.observeActiveSession().first())
                check(recovered.state == SessionState.RECOVERY_REQUIRED)
                check(recovered.before == original.before)
                check(recovered.operations[0].state == OperationState.UNKNOWN && recovered.operations[0].error == "PROCESS_RESTART")
                check(recovered.operations[1].state == OperationState.PLANNED)
                recoveredRepo.recoverInterruptedSession()
                check(recoveredRepo.observeActiveSession().first() == recovered)
            } finally { reopened.close() }
        } }

        case("stale and unconfirmed callbacks cannot finish operations") { fixture { _, database, repo ->
            val preset = repo.savePreset("transitions", capture(10))
            val session = repo.prepareSession("transitions", preset.id, capture(40))
            val operation = session.operations.first()
            repo.transitionOperation(session.id, operation.id, OperationState.PLANNED, OperationState.DISPATCHED)
            check(runCatching { repo.transitionOperation(session.id, operation.id, OperationState.PLANNED, OperationState.DISPATCHED) }.isFailure)
            check(runCatching { repo.transitionOperation("wrong-session", operation.id, OperationState.DISPATCHED, OperationState.CONFIRMED, operation.targetRaw) }.isFailure)
            check(runCatching { repo.transitionOperation(session.id, operation.id, OperationState.DISPATCHED, OperationState.CONFIRMED, operation.targetRaw + 1) }.isFailure)
            check(repo.observeActiveSession().first()?.operations?.first()?.state == OperationState.DISPATCHED)
            check(runCatching { repo.completeSession(session.id) }.isFailure)
            for (item in session.operations) {
                if (item.id != operation.id) repo.transitionOperation(session.id, item.id, OperationState.PLANNED, OperationState.DISPATCHED)
                repo.transitionOperation(session.id, item.id, OperationState.DISPATCHED, OperationState.CONFIRMED, item.targetRaw)
            }
            repo.completeSession(session.id)
            check(repo.observeActiveSession().first() == null)
            check(database.records().session(session.id)?.session?.state == SessionState.COMPLETED.name)
            check(count(database, "before_snapshots") == 2)
        } }

        case("invalid profiles units ranges and duplicate observations leave no partial plan") { fixture { _, database, repo ->
            val preset = repo.savePreset("validation", capture(10))
            val before = capture(40)
            val invalid = listOf(before.copy(profile = before.profile.copy(source = CaptureSource.VEHICLE)),
                before.copy(settings = before.settings.map { it.copy(unit = "DEGREES") }),
                before.copy(settings = before.settings.map { it.copy(minRaw = 30) }),
                before.copy(settings = before.settings.take(1)))
            for ((index, value) in invalid.withIndex()) check(runCatching { repo.prepareSession("bad-$index", preset.id, value) }.isFailure)
            check(runCatching { repo.savePreset("duplicates", before.copy(settings = before.settings + before.settings.first())) }.isFailure)
            check(runCatching { repo.savePreset(" ", before) }.isFailure)
            check(count(database, "presets") == 1 && count(database, "sessions") == 0)
        } }

        case("explicit abandonment releases slot without deleting recovery history") { fixture { _, database, repo ->
            val preset = repo.savePreset("abandon", capture(10))
            val session = repo.prepareSession("abandon", preset.id, capture(40))
            val operation = session.operations.first()
            repo.transitionOperation(session.id, operation.id, OperationState.PLANNED, OperationState.DISPATCHED)
            repo.transitionOperation(session.id, operation.id, OperationState.DISPATCHED, OperationState.UNKNOWN, error = "TEST_TIMEOUT")
            repo.abandonSession(session.id)
            check(repo.observeActiveSession().first() == null)
            check(database.records().session(session.id)?.session?.state == SessionState.ABANDONED.name)
            check(count(database, "before_snapshots") == 2)
            check(repo.prepareSession("next", preset.id, capture(50)).id != session.id)
        } }

        finishReport()
    }

    private class ExecutionFixture : DemoSettings, VehicleGateway {
        override val state = MutableStateFlow(VehicleState(true, true, Gear.PARK, 0f))
        val values = linkedMapOf("foreAft" to 10, "height" to 20)
        val writes = mutableListOf<String>()
        var afterWrite: () -> Unit = {}
        var beforeWrite: suspend (SettingOperation) -> Unit = {}

        fun setParked(parked: Boolean) {
            state.value = VehicleState(true, true, if (parked) Gear.PARK else Gear.DRIVE, if (parked) 0f else 5f)
        }

        override suspend fun captureDriverSeat() = CapturedSettings(
            CaptureProfile("execution", CaptureSource.DEMO, "v1"),
            values.map { (key, value) -> SettingObservation(key, 1, value, 0, 100, 1, replayVerified = true) },
        )

        override suspend fun compareAndSet(operation: SettingOperation): DemoWriteResult {
            beforeWrite(operation)
            if (state.value.gear != Gear.PARK || values[operation.key] != operation.beforeRaw) {
                return DemoWriteResult.REJECTED
            }
            values[operation.key] = operation.targetRaw
            writes += operation.key
            afterWrite()
            return DemoWriteResult.ACCEPTED
        }
    }

    private fun finishReport() {
        report.appendLine("Storage validation: passed=$passed, failed=$failed")
        finish(if (failed == 0) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply { putString(REPORT_KEY_STREAMRESULT, "\n$report") })
    }

    private suspend fun processPhase(mode: String) = withContext(Dispatchers.IO) {
        val name = "rest-validation-process-restart.db"
        when (mode) {
            "stage-recovery" -> {
                targetContext.deleteDatabase(name) // Only the named fixture, never rest.db.
                val database = open(name).also { processDatabase = it }
                val repo = RoomRestRepository(database)
                val preset = repo.savePreset("process restart fixture", capture(10))
                val session = repo.prepareSession("process-restart", preset.id, capture(40))
                repo.transitionOperation(session.id, session.operations.first().id, OperationState.PLANNED, OperationState.DISPATCHED)
                report.appendLine("STAGED: committed DISPATCHED with original foreAft=40; database deliberately left open")
            }
            "verify-recovery" -> {
                val database = open(name)
                try {
                    val repo = RoomRestRepository(database)
                    val before = checkNotNull(repo.observeActiveSession().first())
                    check(before.operations.first().state == OperationState.DISPATCHED)
                    check(before.before.settings.single { it.key == "foreAft" }.rawValue == 40)
                    repo.recoverInterruptedSession()
                    val after = checkNotNull(repo.observeActiveSession().first())
                    check(after.state == SessionState.RECOVERY_REQUIRED && after.before == before.before)
                    check(after.operations.first().state == OperationState.UNKNOWN)
                    report.appendLine("RECOVERED: original foreAft=40, state=RECOVERY_REQUIRED, operation=UNKNOWN")
                } finally { database.close(); targetContext.deleteDatabase(name) }
            }
            else -> error("Unknown storage mode")
        }
    }

    private suspend fun case(name: String, block: suspend () -> Unit) {
        try { block(); passed += 1; report.appendLine("PASS: $name") }
        catch (error: Throwable) { failed += 1; report.appendLine("FAIL: $name: ${error.javaClass.name}: ${error.message}") }
    }

    private suspend fun fixture(block: suspend (String, RestDatabase, RoomRestRepository) -> Unit) = withContext(Dispatchers.IO) {
        val name = "rest-validation-${UUID.randomUUID()}.db"
        val database = open(name)
        try { block(name, database, RoomRestRepository(database)) }
        finally { database.close(); targetContext.deleteDatabase(name) }
    }

    private fun open(name: String) = Room.databaseBuilder(targetContext, RestDatabase::class.java, name).build()
    private fun count(database: RestDatabase, table: String): Int = database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM `$table`").use {
        check(it.moveToFirst()); it.getInt(0)
    }

    private fun capture(raw: Int) = CapturedSettings(CaptureProfile("test-profile", CaptureSource.DEMO, "test-v1"),
        listOf(SettingObservation("foreAft", 1, raw, 0, 100, 12345), SettingObservation("backrest", 1, raw + 1, 0, 100, 12345)))
}
