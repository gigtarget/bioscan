package com.example.bioscan.presentation.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bioscan.core.backup.BackupManager
import com.example.bioscan.core.common.CsvExporter
import com.example.bioscan.core.common.CryptoUtils
import com.example.bioscan.core.common.KioskSettingsRepository
import com.example.bioscan.core.common.TimeUtils
import com.example.bioscan.core.database.BioScanDatabase
import com.example.bioscan.core.database.entity.EmployeeEntity
import com.example.bioscan.core.database.entity.FaceTemplateEntity
import com.example.bioscan.core.recognition.CalibrationResult
import com.example.bioscan.core.recognition.DetectedFaceResult
import com.example.bioscan.core.recognition.EmbeddingGenerator
import com.example.bioscan.core.recognition.FaceAligner
import com.example.bioscan.core.recognition.FaceQualityAnalyzer
import com.example.bioscan.core.recognition.IdentityMatcher
import com.example.bioscan.core.recognition.ThresholdCalibrator
import com.example.bioscan.core.security.AdminSecurityManager
import com.example.bioscan.data.repository.AttendanceRepository
import com.example.bioscan.data.repository.EmployeeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class EnrollmentStepState(
    val stepIndex: Int = 0,
    val title: String = "Straight Face",
    val prompt: String = "Look straight into the camera",
    val collectedSamples: Int = 0,
    val targetSamples: Int = 5,
    val isComplete: Boolean = false,
    val duplicateWarningEmployeeName: String? = null
)

class AdminViewModel(application: Application) : AndroidViewModel(application) {

    private val db = BioScanDatabase.getInstance(application)
    val employeeRepo = EmployeeRepository(db)
    val attendanceRepo = AttendanceRepository(db)
    val securityManager = AdminSecurityManager(db.adminDao())
    val settingsRepo = KioskSettingsRepository(application)

    private val backupManager = BackupManager(application)
    private val embeddingGenerator = EmbeddingGenerator()
    private val aligner = FaceAligner()
    private val qualityAnalyzer = FaceQualityAnalyzer()
    private val calibrator = ThresholdCalibrator(embeddingGenerator)
    val detectorManager = com.example.bioscan.core.recognition.FaceDetectorManager()

    private val _enrollmentState = MutableStateFlow(EnrollmentStepState())
    val enrollmentState: StateFlow<EnrollmentStepState> = _enrollmentState.asStateFlow()

    private val tempCollectedEmbeddings = mutableListOf<FloatArray>()
    private val tempCollectedQualities = mutableListOf<Float>()

    private val _calibrationResult = MutableStateFlow<CalibrationResult?>(null)
    val calibrationResult: StateFlow<CalibrationResult?> = _calibrationResult.asStateFlow()

    private val _backupStatusMessage = MutableStateFlow<String?>(null)
    val backupStatusMessage: StateFlow<String?> = _backupStatusMessage.asStateFlow()

    init {
        viewModelScope.launch {
            securityManager.ensureDefaultAdminExists()
        }
    }

    fun processEnrollmentFrame(bitmap: Bitmap, rotationDegrees: Int) {
        if (_enrollmentState.value.isComplete) return
        viewModelScope.launch(Dispatchers.Default) {
            val faces = detectorManager.detectFaces(bitmap, rotationDegrees)
            if (faces.isNotEmpty()) {
                val primaryFace = faces.first()
                collectEnrollmentSample(bitmap, primaryFace)
            }
        }
    }

    fun collectEnrollmentSample(bitmap: Bitmap, faceResult: DetectedFaceResult): Boolean {
        val quality = qualityAnalyzer.evaluateQuality(bitmap, faceResult)
        if (!quality.isQualified) return false

        val aligned = aligner.alignAndCropFace(bitmap, faceResult) ?: return false
        val embedding = embeddingGenerator.generateEmbedding(aligned)

        tempCollectedEmbeddings.add(embedding)
        tempCollectedQualities.add(quality.overallScore)

        val newCount = tempCollectedEmbeddings.size
        _enrollmentState.value = _enrollmentState.value.copy(
            collectedSamples = newCount
        )

        if (newCount >= 5) {
            _enrollmentState.value = _enrollmentState.value.copy(isComplete = true)
        }
        return true
    }

    fun checkDuplicateEnrollment(onChecked: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (tempCollectedEmbeddings.isEmpty()) {
                onChecked(null)
                return@launch
            }
            val sampleEmbedding = tempCollectedEmbeddings.first()
            val existingTemplates = employeeRepo.getAllFaceTemplates()
            val matcher = IdentityMatcher(embeddingGenerator)
            matcher.updateTemplateIndex(existingTemplates.map { Pair(it.employeeId, it.encryptedEmbedding) })

            val match = matcher.findMatch(sampleEmbedding, threshold = 0.70f)
            if (match.decision == com.example.bioscan.core.recognition.IdentityDecision.MATCH && match.employeeId.isNotBlank()) {
                val existingEmp = employeeRepo.getEmployeeById(match.employeeId)
                onChecked(existingEmp?.fullName ?: "Employee ID: ${match.employeeId}")
            } else {
                onChecked(null)
            }
        }
    }

    fun saveEnrolledEmployee(
        fullName: String,
        department: String,
        designation: String,
        payType: String,
        hourlyRate: Double?,
        phoneNumber: String?,
        notes: String?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val empId = "EMP_${System.currentTimeMillis().toString().takeLast(6)}"
            val employee = EmployeeEntity(
                employeeId = empId,
                fullName = fullName,
                department = department,
                designation = designation,
                payType = payType,
                hourlyRate = hourlyRate,
                phoneNumber = phoneNumber,
                notes = notes,
                enrollmentDate = System.currentTimeMillis()
            )

            employeeRepo.insertOrUpdateEmployee(employee)

            val templates = tempCollectedEmbeddings.mapIndexed { idx, floatArray ->
                FaceTemplateEntity(
                    templateId = UUID.randomUUID().toString(),
                    employeeId = empId,
                    encryptedEmbedding = CryptoUtils.encryptEmbedding(floatArray),
                    modelVersion = embeddingGenerator.modelVersion,
                    qualityScore = tempCollectedQualities.getOrElse(idx) { 0.9f },
                    poseYaw = 0f,
                    posePitch = 0f,
                    isCentroid = (idx == 0)
                )
            }

            employeeRepo.saveFaceTemplates(templates)
            resetEnrollment()
            viewModelScope.launch(Dispatchers.Main) { onSuccess() }
        }
    }

    fun resetEnrollment() {
        tempCollectedEmbeddings.clear()
        tempCollectedQualities.clear()
        _enrollmentState.value = EnrollmentStepState()
    }

    fun runCalibration() {
        viewModelScope.launch(Dispatchers.IO) {
            val allTemplates = employeeRepo.getAllFaceTemplates()
            val embeddingsList = allTemplates.mapNotNull {
                val floats = CryptoUtils.decryptEmbedding(it.encryptedEmbedding)
                if (floats != null) Pair(it.employeeId, floats) else null
            }
            val res = calibrator.calibrateThresholds(embeddingsList)
            _calibrationResult.value = res
        }
    }

    fun exportAttendanceCsv(context: Context, onFileReady: (File) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val events = db.attendanceDao().getAllAttendanceEvents()
            // Collect first page list for CSV export
            val eventsList = db.attendanceDao().getEventsInTimeRange(0L, System.currentTimeMillis()).let {
                // Collect one-shot list
                db.attendanceDao().getPendingSyncEvents() // fallback query or map
            }
            val records = eventsList.map {
                val emp = employeeRepo.getEmployeeById(it.employeeId)
                CsvExporter.AttendanceCsvRecord(
                    eventId = it.eventId,
                    employeeId = it.employeeId,
                    employeeName = emp?.fullName ?: "Unknown",
                    department = emp?.department ?: "N/A",
                    eventType = it.eventType,
                    formattedTimestamp = TimeUtils.formatLocalTime(it.timestamp),
                    recognitionScore = it.recognitionScore,
                    livenessResult = it.livenessResult,
                    syncState = it.syncState,
                    deviceId = it.deviceId
                )
            }
            val file = CsvExporter.exportAttendanceToCsv(context, records)
            viewModelScope.launch(Dispatchers.Main) { onFileReady(file) }
        }
    }

    fun createBackup() {
        viewModelScope.launch(Dispatchers.IO) {
            val backupDir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val file = File(backupDir, "bioscan_backup_${System.currentTimeMillis()}.zip")
            val success = backupManager.createBackupZip(file)
            _backupStatusMessage.value = if (success) "Backup created at ${file.name}" else "Backup creation failed."
        }
    }

    override fun onCleared() {
        super.onCleared()
        detectorManager.close()
    }
}
