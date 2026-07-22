package com.example.bioscan.data.repository

import com.example.bioscan.core.database.BioScanDatabase
import com.example.bioscan.core.database.entity.AuditLogEntity
import com.example.bioscan.core.database.entity.EmployeeEntity
import com.example.bioscan.core.database.entity.FaceTemplateEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class EmployeeRepository(private val db: BioScanDatabase) {

    val allEmployees: Flow<List<EmployeeEntity>> = db.employeeDao().getAllEmployees()
    val activeEmployees: Flow<List<EmployeeEntity>> = db.employeeDao().getActiveEmployees()
    val activeEmployeeCount: Flow<Int> = db.employeeDao().getActiveEmployeeCount()

    suspend fun getEmployeeById(id: String): EmployeeEntity? = db.employeeDao().getEmployeeById(id)

    suspend fun insertOrUpdateEmployee(employee: EmployeeEntity) {
        db.employeeDao().insertEmployee(employee)
    }

    suspend fun setEmployeeActive(id: String, isActive: Boolean) {
        db.employeeDao().setEmployeeActiveStatus(id, isActive)
        db.systemDao().insertAuditLog(
            AuditLogEntity(
                logId = UUID.randomUUID().toString(),
                action = "EMPLOYEE_STATUS_CHANGED",
                details = "Employee $id set to isActive=$isActive",
                performedBy = "ADMIN"
            )
        )
    }

    suspend fun saveFaceTemplates(templates: List<FaceTemplateEntity>) {
        db.faceTemplateDao().insertTemplates(templates)
    }

    suspend fun getTemplatesForEmployee(employeeId: String): List<FaceTemplateEntity> {
        return db.faceTemplateDao().getTemplatesForEmployee(employeeId)
    }

    suspend fun getAllFaceTemplates(): List<FaceTemplateEntity> {
        return db.faceTemplateDao().getAllTemplates()
    }

    suspend fun deleteEmployee(employeeId: String) {
        db.employeeDao().deleteEmployeeById(employeeId)
        db.faceTemplateDao().deleteTemplatesForEmployee(employeeId)
        db.systemDao().insertAuditLog(
            AuditLogEntity(
                logId = UUID.randomUUID().toString(),
                action = "EMPLOYEE_DELETED",
                details = "Employee $employeeId and templates removed",
                performedBy = "ADMIN"
            )
        )
    }
}
