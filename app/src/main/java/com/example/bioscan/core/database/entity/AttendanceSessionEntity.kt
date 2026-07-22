package com.example.bioscan.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attendance_sessions",
    indices = [
        Index(value = ["employeeId"]),
        Index(value = ["status"]),
        Index(value = ["clockInTime"])
    ]
)
data class AttendanceSessionEntity(
    @PrimaryKey
    val sessionId: String,
    val employeeId: String,
    val clockInTime: Long,
    val clockOutTime: Long? = null,
    val status: String = "OPEN", // OPEN, COMPLETED, AUTO_CLOSED
    val totalDurationMinutes: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)
