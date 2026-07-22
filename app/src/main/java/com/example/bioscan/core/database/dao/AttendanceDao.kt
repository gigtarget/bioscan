package com.example.bioscan.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.bioscan.core.database.entity.AttendanceEventEntity
import com.example.bioscan.core.database.entity.AttendanceSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance_events ORDER BY timestamp DESC")
    fun getAllAttendanceEvents(): Flow<List<AttendanceEventEntity>>

    @Query("SELECT * FROM attendance_events WHERE employeeId = :employeeId ORDER BY timestamp DESC")
    fun getAttendanceEventsForEmployee(employeeId: String): Flow<List<AttendanceEventEntity>>

    @Query("SELECT * FROM attendance_events WHERE timestamp >= :startTimeMs AND timestamp <= :endTimeMs ORDER BY timestamp DESC")
    fun getEventsInTimeRange(startTimeMs: Long, endTimeMs: Long): Flow<List<AttendanceEventEntity>>

    @Query("SELECT * FROM attendance_events WHERE employeeId = :employeeId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestEventForEmployee(employeeId: String): AttendanceEventEntity?

    @Query("SELECT * FROM attendance_sessions WHERE employeeId = :employeeId AND status = 'OPEN' LIMIT 1")
    suspend fun getOpenSessionForEmployee(employeeId: String): AttendanceSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: AttendanceEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: AttendanceSessionEntity)

    @Update
    suspend fun updateSession(session: AttendanceSessionEntity)

    @Transaction
    suspend fun recordAttendanceAndSession(
        event: AttendanceEventEntity,
        sessionToUpdateOrCreate: AttendanceSessionEntity
    ) {
        insertEvent(event)
        insertSession(sessionToUpdateOrCreate)
    }

    @Query("UPDATE attendance_events SET isCorrected = 1, eventType = :newEventType, correctionReason = :reason, correctedByAdminId = :adminId WHERE eventId = :eventId")
    suspend fun correctAttendanceEvent(eventId: String, newEventType: String, reason: String, adminId: String)

    @Query("SELECT COUNT(*) FROM attendance_sessions WHERE status = 'OPEN'")
    fun getCurrentlyClockedInCount(): Flow<Int>

    @Query("SELECT * FROM attendance_events WHERE syncState = 'PENDING'")
    suspend fun getPendingSyncEvents(): List<AttendanceEventEntity>

    @Query("UPDATE attendance_events SET syncState = :syncState, syncAttempts = syncAttempts + 1, lastSyncError = :lastError WHERE eventId = :eventId")
    suspend fun updateSyncState(eventId: String, syncState: String, lastError: String? = null)
}
