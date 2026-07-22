package com.example.bioscan.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recognition_models")
data class RecognitionModelEntity(
    @PrimaryKey
    val modelId: String,
    val name: String,
    val version: String,
    val checksum: String,
    val inputWidth: Int,
    val inputHeight: Int,
    val normalization: String, // e.g. [-1, 1] or [0, 1]
    val embeddingDimension: Int,
    val defaultThreshold: Float,
    val defaultMargin: Float,
    val isActive: Boolean = true,
    val installedAt: Long = System.currentTimeMillis()
)
