package com.example.bioscan.data.repository

import androidx.room.withTransaction
import com.example.bioscan.core.database.BioScanDatabase
import com.example.bioscan.core.database.entity.AuditLogEntity
import com.example.bioscan.core.database.entity.EmployeeEntity
import com.example.bioscan.core.database.entity.FaceTemplateEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class EmployeeRepository(private val db: BioScanDatabase) {

    val allEmployees: Flow<List<EmployeeEntity>> = db.employeeDao().getAllEmployees()
    val activeEmployees: Flow<List<EmployeeEntity>> = db.employeeDao().getActiveEmployees()
    val activeEmployeeCount: Flow<Int> = db.employeeDao().getActiveEmployeeCount()

    suspend fun getEmployeeById(id: String): EmployeeEntity? =
        db.employeeDao().getEmployeeById(id)

    suspend fun getEmployeeByName(fullName: String): EmployeeEntity? =
        db.employeeDao().getEmployeeByNormalizedName(fullName)

    suspend fun insertOrUpdateEmployee(employee: EmployeeEntity) {
        db.employeeDao().insertEmployee(employee)
    }

    suspend fun saveEmployeeWithTemplates(
        employee: EmployeeEntity,
        templates: List<FaceTemplateEntity>,
        replaceExistingTemplates: Boolean = true
    ) {
        require(templates.isNotEmpty()) { "Face templates are required" }
        require(templates.all { it.employeeId == employee.employeeId }) {
            "Every face template must belong to the employee being saved"
        }

        db.withTransaction {
            db.employeeDao().insertEmployee(employee)
            if (replaceExistingTemplates) {
                db.faceTemplateDao().deleteTemplatesForEmployee(employee.employeeId)
            }
            db.faceTemplateDao().insertTemplates(templates)
            db.systemDao().insertAuditLog(
                AuditLogEntity(
                    logId = UUID.randomUUID().toString(),
                    action = if (replaceExistingTemplates) {
                        "EMPLOYEE_FACE_ENROLLED"
                    } else {
                        "EMPLOYEE_FACE_TEMPLATE_ADDED"
                    },
                    details = "Saved ${templates.size} current-model face templates for ${employee.employeeId}",
                    performedBy = "ADMIN"
                )
            )
        }
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

    suspend fun getTemplatesForEmployee(employeeId: String): List<FaceTemplateEntity> =
        db.faceTemplateDao().getTemplatesForEmployee(employeeId)

    suspend fun getAllFaceTemplates(): List<FaceTemplateEntity> =
        db.faceTemplateDao().getAllTemplates()

    suspend fun getActiveFaceTemplates(): List<FaceTemplateEntity> =
        db.faceTemplateDao().getActiveEmployeeTemplates()

    suspend fun deleteEmployee(employeeId: String) {
        db.withTransaction {
            db.faceTemplateDao().deleteTemplatesForEmployee(employeeId)
            db.employeeDao().deleteEmployeeById(employeeId)
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
}
