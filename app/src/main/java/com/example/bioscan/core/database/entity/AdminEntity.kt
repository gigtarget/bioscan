package com.example.bioscan.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "administrators")
data class AdminEntity(
    @PrimaryKey
    val adminId: String,
    val username: String,
    val passwordHash: String,
    val salt: String,
    val fullName: String,
    val role: String = "SUPER_ADMIN",
    val createdAt: Long = System.currentTimeMillis()
)
