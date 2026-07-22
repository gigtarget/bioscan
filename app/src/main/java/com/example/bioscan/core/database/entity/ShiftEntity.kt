package com.example.bioscan.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shifts")
data class ShiftEntity(
    @PrimaryKey
    val shiftId: String,
    val name: String, // e.g. Morning Shift, Night Shift
    val startTime: String, // HH:mm
    val endTime: String, // HH:mm
    val isOvernight: Boolean = false,
    val gracePeriodMinutes: Int = 15
)
