package com.example.bioscan.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.bioscan.core.database.dao.AdminDao
import com.example.bioscan.core.database.dao.AttendanceDao
import com.example.bioscan.core.database.dao.EmployeeDao
import com.example.bioscan.core.database.dao.FaceTemplateDao
import com.example.bioscan.core.database.dao.SystemDao
import com.example.bioscan.core.database.entity.AdminEntity
import com.example.bioscan.core.database.entity.AttendanceEventEntity
import com.example.bioscan.core.database.entity.AttendanceSessionEntity
import com.example.bioscan.core.database.entity.AuditLogEntity
import com.example.bioscan.core.database.entity.CalibrationProfileEntity
import com.example.bioscan.core.database.entity.EmployeeEntity
import com.example.bioscan.core.database.entity.FaceTemplateEntity
import com.example.bioscan.core.database.entity.RecognitionModelEntity
import com.example.bioscan.core.database.entity.ShiftEntity
import com.example.bioscan.core.database.entity.SyncQueueEntity

@Database(
    entities = [
        EmployeeEntity::class,
        FaceTemplateEntity::class,
        AttendanceEventEntity::class,
        AttendanceSessionEntity::class,
        ShiftEntity::class,
        AdminEntity::class,
        RecognitionModelEntity::class,
        CalibrationProfileEntity::class,
        SyncQueueEntity::class,
        AuditLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class BioScanDatabase : RoomDatabase() {
    abstract fun employeeDao(): EmployeeDao
    abstract fun faceTemplateDao(): FaceTemplateDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun adminDao(): AdminDao
    abstract fun systemDao(): SystemDao

    companion object {
        @Volatile
        private var INSTANCE: BioScanDatabase? = null

        fun getInstance(context: Context): BioScanDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BioScanDatabase::class.java,
                    "bioscan_kiosk.db"
                )
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
