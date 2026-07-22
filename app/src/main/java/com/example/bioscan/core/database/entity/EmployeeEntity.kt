package com.example.bioscan.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "employees",
    indices = [
        Index(value = ["isActive"]),
        Index(value = ["department"])
    ]
)
data class EmployeeEntity(
    @PrimaryKey
    val employeeId: String,
    val fullName: String,
    val department: String,
    val designation: String,
    val phoneNumber: String? = null,
    val payType: String = "HOURLY", // HOURLY or MONTHLY
    val hourlyRate: Double? = null,
    val overtimeRate: Double? = null,
    val shiftId: String? = null,
    val isActive: Boolean = true,
    val enrollmentDate: Long = System.currentTimeMillis(),
    val photoPath: String? = null,
    val notes: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
