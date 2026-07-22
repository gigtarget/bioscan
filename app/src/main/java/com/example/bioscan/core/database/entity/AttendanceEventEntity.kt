package com.example.bioscan.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attendance_events",
    indices = [
        Index(value = ["employeeId"]),
        Index(value = ["timestamp"]),
        Index(value = ["employeeId", "timestamp"]),
        Index(value = ["syncState"])
    ]
)
data class AttendanceEventEntity(
    @PrimaryKey
    val eventId: String,
    val employeeId: String,
    val eventType: String, // CLOCK_IN, CLOCK_OUT, BREAK_START, BREAK_END, MANUAL_CORRECTION
    val timestamp: Long,
    val deviceId: String = "LOCAL_KIOSK_01",
    val recognitionScore: Float,
    val secondBestScore: Float,
    val scoreMargin: Float,
    val livenessResult: String, // PASSED, CHALLENGE_VERIFIED, BYPASSED
    val qualityScore: Float,
    val modelVersion: String,
    val syncState: String = "PENDING", // PENDING, SYNCED, FAILED
    val syncAttempts: Int = 0,
    val lastSyncError: String? = null,
    val auditThumbnailPath: String? = null,
    val isCorrected: Boolean = false,
    val correctionReason: String? = null,
    val correctedByAdminId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
