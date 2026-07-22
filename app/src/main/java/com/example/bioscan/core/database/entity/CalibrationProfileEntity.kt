package com.example.bioscan.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calibration_profiles")
data class CalibrationProfileEntity(
    @PrimaryKey
    val profileId: String,
    val modelVersion: String,
    val recommendedThreshold: Float,
    val recommendedMargin: Float,
    val estimatedFar: Float,
    val estimatedFrr: Float,
    val samePersonPairCount: Int,
    val diffPersonPairCount: Int,
    val calibratedAt: Long = System.currentTimeMillis()
)
