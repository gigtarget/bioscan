package com.example.bioscan.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.bioscan.core.database.entity.FaceTemplateEntity

@Dao
interface FaceTemplateDao {
    @Query("SELECT * FROM employee_face_templates WHERE employeeId = :employeeId")
    suspend fun getTemplatesForEmployee(employeeId: String): List<FaceTemplateEntity>

    @Query("SELECT * FROM employee_face_templates")
    suspend fun getAllTemplates(): List<FaceTemplateEntity>

    @Query(
        """
        SELECT templates.*
        FROM employee_face_templates AS templates
        INNER JOIN employees AS employees
            ON employees.employeeId = templates.employeeId
        WHERE employees.isActive = 1
        """
    )
    suspend fun getActiveEmployeeTemplates(): List<FaceTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: FaceTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplates(templates: List<FaceTemplateEntity>)

    @Query("DELETE FROM employee_face_templates WHERE employeeId = :employeeId")
    suspend fun deleteTemplatesForEmployee(employeeId: String)
}
