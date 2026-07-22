package com.example.bioscan.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.bioscan.core.database.entity.AdminEntity

@Dao
interface AdminDao {
    @Query("SELECT * FROM administrators WHERE username = :username LIMIT 1")
    suspend fun getAdminByUsername(username: String): AdminEntity?

    @Query("SELECT COUNT(*) FROM administrators")
    suspend fun getAdminCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdmin(admin: AdminEntity)
}
