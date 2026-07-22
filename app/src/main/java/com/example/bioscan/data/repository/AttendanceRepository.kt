package com.example.bioscan.data.repository

import com.example.bioscan.core.database.BioScanDatabase
import com.example.bioscan.core.database.entity.AttendanceEventEntity
import com.example.bioscan.core.database.entity.AuditLogEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class AttendanceRepository(private val db: BioScanDatabase) {

    val allEvents: Flow<List<AttendanceEventEntity>> = db.attendanceDao().getAllAttendanceEvents()
    val clockedInCount: Flow<Int> = db.attendanceDao().getCurrentlyClockedInCount()

    fun getEventsInTimeRange(startMs: Long, endMs: Long): Flow<List<AttendanceEventEntity>> {
        return db.attendanceDao().getEventsInTimeRange(startMs, endMs)
    }

    suspend fun correctAttendanceEvent(eventId: String, newType: String, reason: String, adminId: String) {
        db.attendanceDao().correctAttendanceEvent(eventId, newType, reason, adminId)
        db.systemDao().insertAuditLog(
            AuditLogEntity(
                logId = UUID.randomUUID().toString(),
                action = "ATTENDANCE_CORRECTED",
                details = "Event $eventId corrected to $newType with reason: $reason",
                performedBy = adminId
            )
        )
    }
}
