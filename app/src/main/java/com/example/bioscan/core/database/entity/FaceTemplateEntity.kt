package com.example.bioscan.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "employee_face_templates",
    indices = [
        Index(value = ["employeeId"]),
        Index(value = ["isCentroid"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["employeeId"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class FaceTemplateEntity(
    @PrimaryKey
    val templateId: String,
    val employeeId: String,
    val encryptedEmbedding: String, // Base64 AES-256 encrypted FloatArray
    val modelVersion: String,
    val qualityScore: Float,
    val poseYaw: Float,
    val posePitch: Float,
    val isCentroid: Boolean = false,
    val enrollmentDeviceId: String = "LOCAL_KIOSK_01",
    val createdAt: Long = System.currentTimeMillis()
)
