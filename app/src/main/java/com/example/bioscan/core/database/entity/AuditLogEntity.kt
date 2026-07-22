package com.example.bioscan.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audit_logs",
    indices = [
        Index(value = ["timestamp"])
    ]
)
data class AuditLogEntity(
    @PrimaryKey
    val logId: String,
    val action: String, // e.g., EMPLOYEE_ADDED, ATTENDANCE_CORRECTED, KIOSK_EXITED
    val details: String,
    val performedBy: String,
    val timestamp: Long = System.currentTimeMillis()
)
