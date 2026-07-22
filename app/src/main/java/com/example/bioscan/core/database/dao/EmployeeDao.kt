package com.example.bioscan.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.bioscan.core.database.entity.EmployeeEntity
import com.example.bioscan.core.database.entity.FaceTemplateEntity
import kotlinx.coroutines.flow.Flow

data class EmployeeWithTemplates(
    val employee: EmployeeEntity,
    val templates: List<FaceTemplateEntity>
)

@Dao
interface EmployeeDao {
    @Query("SELECT * FROM employees ORDER BY fullName ASC")
    fun getAllEmployees(): Flow<List<EmployeeEntity>>

    @Query("SELECT * FROM employees WHERE isActive = 1 ORDER BY fullName ASC")
    fun getActiveEmployees(): Flow<List<EmployeeEntity>>

    @Query("SELECT * FROM employees WHERE employeeId = :employeeId")
    suspend fun getEmployeeById(employeeId: String): EmployeeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmployee(employee: EmployeeEntity)

    @Update
    suspend fun updateEmployee(employee: EmployeeEntity)

    @Query("UPDATE employees SET isActive = :isActive, updatedAt = :timestamp WHERE employeeId = :employeeId")
    suspend fun setEmployeeActiveStatus(employeeId: String, isActive: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM employees WHERE employeeId = :employeeId")
    suspend fun deleteEmployeeById(employeeId: String)

    @Query("SELECT COUNT(*) FROM employees WHERE isActive = 1")
    fun getActiveEmployeeCount(): Flow<Int>
}
