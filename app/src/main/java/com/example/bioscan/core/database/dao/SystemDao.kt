package com.example.bioscan.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.bioscan.core.database.entity.AuditLogEntity
import com.example.bioscan.core.database.entity.CalibrationProfileEntity
import com.example.bioscan.core.database.entity.RecognitionModelEntity
import com.example.bioscan.core.database.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SystemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity)

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 200")
    fun getAuditLogs(): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: RecognitionModelEntity)

    @Query("SELECT * FROM recognition_models WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveModel(): RecognitionModelEntity?

    @Query("SELECT * FROM recognition_models")
    suspend fun getAllModels(): List<RecognitionModelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalibrationProfile(profile: CalibrationProfileEntity)

    @Query("SELECT * FROM calibration_profiles ORDER BY calibratedAt DESC LIMIT 1")
    suspend fun getLatestCalibration(): CalibrationProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueSyncItem(item: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingSyncQueue(): List<SyncQueueEntity>

    @Query("DELETE FROM sync_queue WHERE queueId = :queueId")
    suspend fun deleteSyncQueueItem(queueId: String)
}
