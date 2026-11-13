package io.github.seungminshin97.japeace.data.rest

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "presets")
data class PresetRow(
    @PrimaryKey val id: String,
    val name: String,
    val revision: Int,
    val schemaVersion: Int,
    val createdAt: Long,
    val profileKey: String,
    val source: String,
    val mappingVersion: String,
    val identityVerified: Boolean,
)

@Entity(tableName = "preset_settings", primaryKeys = ["presetId", "settingKey", "areaId"],
    foreignKeys = [ForeignKey(entity = PresetRow::class, parentColumns = ["id"], childColumns = ["presetId"], onDelete = ForeignKey.CASCADE)])
data class PresetSettingRow(
    val presetId: String, val settingKey: String, val areaId: Int, val rawValue: Int,
    val minRaw: Int, val maxRaw: Int, val observedAt: Long, val unit: String, val replayVerified: Boolean,
)

@Entity(tableName = "sessions", indices = [Index(value = ["requestId"], unique = true),
    Index(value = ["activeSlot"], unique = true), Index("presetId")],
    foreignKeys = [ForeignKey(entity = PresetRow::class, parentColumns = ["id"], childColumns = ["presetId"], onDelete = ForeignKey.RESTRICT)])
data class SessionRow(
    @PrimaryKey val id: String, val requestId: String, val presetId: String, val presetRevision: Int,
    val schemaVersion: Int, val state: String, val startedAt: Long, val finishedAt: Long?,
    val activeSlot: Int?, val profileKey: String, val source: String,
    val mappingVersion: String, val identityVerified: Boolean,
)

@Entity(tableName = "before_snapshots", primaryKeys = ["sessionId", "settingKey", "areaId"],
    foreignKeys = [ForeignKey(entity = SessionRow::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)])
data class BeforeRow(
    val sessionId: String, val settingKey: String, val areaId: Int, val rawValue: Int,
    val minRaw: Int, val maxRaw: Int, val observedAt: Long, val unit: String, val replayVerified: Boolean,
)

@Entity(tableName = "operations", indices = [Index(value = ["sessionId", "sequence"], unique = true)],
    foreignKeys = [ForeignKey(entity = SessionRow::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)])
data class OperationRow(
    @PrimaryKey val id: String, val sessionId: String, val sequence: Int, val settingKey: String,
    val areaId: Int, val beforeRaw: Int, val targetRaw: Int, val state: String,
    val confirmedRaw: Int?, val error: String?,
)

data class PresetRecord(
    @Embedded val preset: PresetRow,
    @Relation(parentColumn = "id", entityColumn = "presetId") val settings: List<PresetSettingRow>,
)

data class SessionRecord(
    @Embedded val session: SessionRow,
    @Relation(parentColumn = "id", entityColumn = "sessionId") val before: List<BeforeRow>,
    @Relation(parentColumn = "id", entityColumn = "sessionId") val operations: List<OperationRow>,
)

@Dao
interface RestDao {
    @Transaction @Query("SELECT * FROM presets ORDER BY createdAt DESC, id")
    fun observePresets(): Flow<List<PresetRecord>>
    @Transaction @Query("SELECT * FROM sessions WHERE activeSlot = 1 LIMIT 1")
    fun observeActive(): Flow<SessionRecord?>
    @Transaction @Query("SELECT * FROM presets WHERE id = :id")
    suspend fun preset(id: String): PresetRecord?
    @Transaction @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun session(id: String): SessionRecord?
    @Transaction @Query("SELECT * FROM sessions WHERE requestId = :requestId")
    suspend fun request(requestId: String): SessionRecord?
    @Transaction @Query("SELECT * FROM sessions WHERE activeSlot = 1 LIMIT 1")
    suspend fun active(): SessionRecord?
    @Insert suspend fun insertPreset(row: PresetRow)
    @Insert suspend fun insertPresetSettings(rows: List<PresetSettingRow>)
    @Insert suspend fun insertSession(row: SessionRow)
    @Insert suspend fun insertBefore(rows: List<BeforeRow>)
    @Insert suspend fun insertOperations(rows: List<OperationRow>)
    @Query("UPDATE operations SET state = :next, confirmedRaw = :confirmedRaw, error = :error WHERE id = :id AND sessionId = :sessionId AND state = :expected AND EXISTS (SELECT 1 FROM sessions WHERE id = :sessionId AND activeSlot = 1)")
    suspend fun transition(sessionId: String, id: String, expected: String, next: String, confirmedRaw: Int?, error: String?): Int
    @Query("UPDATE sessions SET state = :state, activeSlot = NULL, finishedAt = :at WHERE id = :id AND activeSlot = 1")
    suspend fun finish(id: String, state: String, at: Long): Int
    @Query("UPDATE operations SET state = 'UNKNOWN', error = 'PROCESS_RESTART' WHERE sessionId = :id AND state = 'DISPATCHED'")
    suspend fun markInterruptedOperations(id: String)
    @Query("UPDATE sessions SET state = 'RECOVERY_REQUIRED' WHERE id = :id AND activeSlot = 1")
    suspend fun markRecovery(id: String)
}

@Database(entities = [PresetRow::class, PresetSettingRow::class, SessionRow::class, BeforeRow::class, OperationRow::class],
    version = 1, exportSchema = true)
abstract class RestDatabase : RoomDatabase() {
    abstract fun records(): RestDao

    companion object {
        @Volatile private var instance: RestDatabase? = null
        fun get(context: Context): RestDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, RestDatabase::class.java, "rest.db")
                .build().also { instance = it }
        }
    }
}
