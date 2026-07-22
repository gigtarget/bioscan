package com.example.bioscan.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["status"])
    ]
)
data class SyncQueueEntity(
    @PrimaryKey
    val queueId: String,
    val entityType: String, // ATTENDANCE_EVENT, EMPLOYEE, TEMPLATE
    val entityId: String,
    val action: String, // CREATE, UPDATE, DELETE
    val payloadJson: String,
    val status: String = "PENDING", // PENDING, SYNCED, FAILED
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
