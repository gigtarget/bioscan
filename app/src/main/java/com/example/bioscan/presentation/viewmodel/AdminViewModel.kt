package com.example.bioscan.presentation.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bioscan.core.backup.BackupManager
import com.example.bioscan.core.common.CryptoUtils
import com.example.bioscan.core.common.CsvExporter
import com.example.bioscan.core.common.KioskSettingsRepository
import com.example.bioscan.core.common.TimeUtils
import com.example.bioscan.core.database.BioScanDatabase
import com.example.bioscan.core.database.entity.EmployeeEntity
import com.example.bioscan.core.database.entity.FaceTemplateEntity
import com.example.bioscan.core.recognition.CalibrationResult
import com.example.bioscan.core.recognition.DetectedFaceResult
import com.example.bioscan.core.recognition.EmbeddingGenerator
import com.example.bioscan.core.recognition.EncryptedTemplateRecord
import com.example.bioscan.core.recognition.FaceAligner
import com.example.bioscan.core.recognition.FaceDetectorManager
import com.example.bioscan.core.recognition.FaceQualityAnalyzer
import com.example.bioscan.core.recognition.IdentityDecision
import com.example.bioscan.core.recognition.IdentityMatcher
import com.example.bioscan.core.recognition.ThresholdCalibrator
import com.example.bioscan.core.security.AdminSecurityManager
import com.example.bioscan.data.repository.AttendanceRepository
import com.example.bioscan.data.repository.EmployeeRepository
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "BioScanEnrollment"

enum class EnrollmentPose {
    FRONT_NEUTRAL,
    TURN_LEFT,
    TURN_RIGHT,
    SMILE,
    FRONT_FINAL
}

data class EnrollmentStepState(
    val stepIndex: Int = 0,
    val title: String = "Front view",
    val prompt: String = "Look straight at the camera and hold still",
    val collectedSamples: Int = 0,
    val targetSamples: Int = 12,
    val samplesInCurrentStep: Int = 0,
    val targetSamplesInCurrentStep: Int = 3,
    val isComplete: Boolean = false,
    val duplicateWarningEmployeeName: String? = null,
    val qualityMessage: String = "Position one face inside the frame",
    val isSaving: Boolean = false
)

private data class EnrollmentStepDefinition(
    val pose: EnrollmentPose,
    val title: String,
    val prompt: String,
    val sampleTarget: Int
)

private data class EnrollmentSample(
    val embedding: FloatArray,
    val qualityScore: Float,
    val poseYaw: Float,
    val posePitch: Float
)

class AdminViewModel(application: Application) : AndroidViewModel(application) {

    private val db = BioScanDatabase.getInstance(application)
    val employeeRepo = EmployeeRepository(db)
    val attendanceRepo = AttendanceRepository(db)
    val securityManager = AdminSecurityManager(db.adminDao())
    val settingsRepo = KioskSettingsRepository(application)

    private val backupManager = BackupManager(application)
    private val embeddingGenerator = EmbeddingGenerator(application)
    private val aligner = FaceAligner()
    private val qualityAnalyzer = FaceQualityAnalyzer()
    private val calibrator = ThresholdCalibrator(embeddingGenerator)
    val detectorManager = FaceDetectorManager()

    private val enrollmentSteps = listOf(
        EnrollmentStepDefinition(
            EnrollmentPose.FRONT_NEUTRAL,
            "Front view",
            "Look straight at the camera with a neutral expression",
            3
        ),
        EnrollmentStepDefinition(
            EnrollmentPose.TURN_LEFT,
            "Side angle 1",
            "Slowly turn your face slightly to either side",
            2
        ),
        EnrollmentStepDefinition(
            EnrollmentPose.TURN_RIGHT,
            "Side angle 2",
            "Turn slightly to the opposite side",
            2
        ),
        EnrollmentStepDefinition(
            EnrollmentPose.SMILE,
            "Expression",
            "Look forward and give a natural smile",
            2
        ),
        EnrollmentStepDefinition(
            EnrollmentPose.FRONT_FINAL,
            "Final front view",
            "Look straight again and hold still",
            3
        )
    )

    private val totalEnrollmentSamples = enrollmentSteps.sumOf { it.sampleTarget }
    private val enrollmentSamples = mutableListOf<EnrollmentSample>()
    private val enrollmentFrameProcessing = AtomicBoolean(false)
    private var lastAcceptedSampleAt = 0L
    private var firstSideDirection = 0

    private val _enrollmentState = MutableStateFlow(initialEnrollmentState())
    val enrollmentState: StateFlow<EnrollmentStepState> = _enrollmentState.asStateFlow()

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
        if (_enrollmentState.value.isComplete || _enrollmentState.value.isSaving) {
            recycleSafely(bitmap)
            return
        }
        if (!enrollmentFrameProcessing.compareAndSet(false, true)) {
            recycleSafely(bitmap)
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val faces = detectorManager.detectFaces(bitmap, rotationDegrees)
                when {
                    faces.isEmpty() -> updateEnrollmentMessage("No face detected")
                    faces.size > 1 -> updateEnrollmentMessage("Only one person can be enrolled at a time")
                    else -> collectEnrollmentSample(bitmap, faces.first())
                }
            } catch (error: Throwable) {
                Log.e(TAG, "Enrollment frame failed", error)
                updateEnrollmentMessage("Unable to analyse this frame. Hold still and try again")
            } finally {
                recycleSafely(bitmap)
                enrollmentFrameProcessing.set(false)
            }
        }
    }

    private fun collectEnrollmentSample(bitmap: Bitmap, faceResult: DetectedFaceResult) {
        val state = _enrollmentState.value
        val step = enrollmentSteps.getOrNull(state.stepIndex) ?: return

        val now = System.currentTimeMillis()
        if (now - lastAcceptedSampleAt < MIN_SAMPLE_INTERVAL_MS) {
            updateEnrollmentMessage(step.prompt)
            return
        }

        val quality = qualityAnalyzer.evaluateQuality(bitmap, faceResult)
        if (!quality.isQualified) {
            updateEnrollmentMessage(quality.rejectionReason ?: "Hold still and improve the image")
            return
        }

        val poseIssue = poseRejectionReason(step.pose, faceResult)
        if (poseIssue != null) {
            updateEnrollmentMessage(poseIssue)
            return
        }

        val aligned = aligner.alignAndCropFace(bitmap, faceResult)
        if (aligned == null) {
            updateEnrollmentMessage("Keep your full face inside the frame")
            return
        }

        try {
            val embedding = embeddingGenerator.generateEmbedding(aligned)
            enrollmentSamples.add(
                EnrollmentSample(
                    embedding = embedding,
                    qualityScore = quality.overallScore,
                    poseYaw = faceResult.headEulerAngleYaw,
                    posePitch = faceResult.headEulerAnglePitch
                )
            )
            lastAcceptedSampleAt = now
            advanceEnrollmentStep()
        } catch (error: Throwable) {
            Log.e(TAG, "Face embedding failed", error)
            updateEnrollmentMessage("Face model could not process this image")
        } finally {
            recycleSafely(aligned)
        }
    }

    private fun advanceEnrollmentStep() {
        val previous = _enrollmentState.value
        val currentDefinition = enrollmentSteps[previous.stepIndex]
        val nextSamplesInStep = previous.samplesInCurrentStep + 1
        val total = enrollmentSamples.size

        if (nextSamplesInStep >= currentDefinition.sampleTarget) {
            val nextStepIndex = previous.stepIndex + 1
            if (nextStepIndex >= enrollmentSteps.size) {
                _enrollmentState.value = previous.copy(
                    collectedSamples = total,
                    samplesInCurrentStep = currentDefinition.sampleTarget,
                    isComplete = true,
                    qualityMessage = "Face profile captured. Checking for duplicates…"
                )
                checkDuplicateEnrollment { }
                return
            }

            val nextStep = enrollmentSteps[nextStepIndex]
            _enrollmentState.value = previous.copy(
                stepIndex = nextStepIndex,
                title = nextStep.title,
                prompt = nextStep.prompt,
                collectedSamples = total,
                samplesInCurrentStep = 0,
                targetSamplesInCurrentStep = nextStep.sampleTarget,
                qualityMessage = nextStep.prompt
            )
        } else {
            _enrollmentState.value = previous.copy(
                collectedSamples = total,
                samplesInCurrentStep = nextSamplesInStep,
                qualityMessage = "Captured ${nextSamplesInStep}/${currentDefinition.sampleTarget}. ${currentDefinition.prompt}"
            )
        }
    }

    private fun poseRejectionReason(
        pose: EnrollmentPose,
        face: DetectedFaceResult
    ): String? {
        val yaw = face.headEulerAngleYaw
        val pitch = abs(face.headEulerAnglePitch)
        if (pitch > 18f) return "Keep your chin level"

        return when (pose) {
            EnrollmentPose.FRONT_NEUTRAL,
            EnrollmentPose.FRONT_FINAL -> if (abs(yaw) > 9f) {
                "Look straight at the camera"
            } else {
                null
            }

            EnrollmentPose.TURN_LEFT -> {
                val direction = if (yaw >= 0f) 1 else -1
                when {
                    abs(yaw) !in 11f..28f -> "Turn slightly to either side—not too far"
                    firstSideDirection != 0 && direction != firstSideDirection -> "Keep the same side angle for this step"
                    else -> {
                        if (firstSideDirection == 0) firstSideDirection = direction
                        null
                    }
                }
            }

            EnrollmentPose.TURN_RIGHT -> {
                val direction = if (yaw >= 0f) 1 else -1
                when {
                    abs(yaw) !in 11f..28f -> "Turn slightly to the opposite side—not too far"
                    firstSideDirection == 0 -> "Complete the first side angle before this step"
                    direction == firstSideDirection -> "Turn to the opposite side"
                    else -> null
                }
            }

            EnrollmentPose.SMILE -> when {
                abs(yaw) > 10f -> "Look straight while smiling"
                (face.smilingProbability ?: 0f) < 0.55f -> "Give a natural smile"
                else -> null
            }
        }
    }

    fun checkDuplicateEnrollment(onChecked: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val duplicate = findDuplicateEmployee()
            _enrollmentState.value = _enrollmentState.value.copy(
                duplicateWarningEmployeeName = duplicate?.fullName,
                qualityMessage = if (duplicate == null) {
                    "Face profile is complete and ready to save"
                } else {
                    "This face already belongs to ${duplicate.fullName}"
                }
            )
            withContext(Dispatchers.Main) {
                onChecked(duplicate?.fullName)
            }
        }
    }

    private suspend fun findDuplicateEmployee(): EmployeeEntity? {
        if (enrollmentSamples.size < totalEnrollmentSamples) return null

        val centroid = embeddingGenerator.averageEmbeddings(
            enrollmentSamples.map { it.embedding }
        )
        val matcher = IdentityMatcher(embeddingGenerator)
        matcher.updateTemplateIndex(
            employeeRepo.getActiveFaceTemplates()
                .filter { it.modelVersion == embeddingGenerator.modelVersion }
                .map { template ->
                    EncryptedTemplateRecord(
                        employeeId = template.employeeId,
                        encryptedEmbedding = template.encryptedEmbedding,
                        modelVersion = template.modelVersion,
                        qualityScore = template.qualityScore,
                        isCentroid = template.isCentroid
                    )
                }
        )

        val match = matcher.findMatch(
            queryEmbedding = centroid,
            threshold = IdentityMatcher.DUPLICATE_THRESHOLD,
            minMargin = IdentityMatcher.DUPLICATE_MIN_MARGIN
        )
        return if (match.decision == IdentityDecision.MATCH && match.employeeId.isNotBlank()) {
            employeeRepo.getEmployeeById(match.employeeId)
        } else {
            null
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
        if (fullName.isBlank()) {
            updateEnrollmentMessage("Enter the employee's full name")
            return
        }
        if (!_enrollmentState.value.isComplete || enrollmentSamples.size < totalEnrollmentSamples) {
            updateEnrollmentMessage("Complete every face-capture step before saving")
            return
        }
        if (_enrollmentState.value.isSaving) return

        _enrollmentState.value = _enrollmentState.value.copy(
            isSaving = true,
            qualityMessage = "Saving encrypted face profile…"
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val normalizedName = fullName.trim()
                val employeeWithSameName = employeeRepo.getEmployeeByName(normalizedName)
                val duplicateEmployee = findDuplicateEmployee()

                if (
                    duplicateEmployee != null &&
                    duplicateEmployee.employeeId != employeeWithSameName?.employeeId
                ) {
                    _enrollmentState.value = _enrollmentState.value.copy(
                        isSaving = false,
                        duplicateWarningEmployeeName = duplicateEmployee.fullName,
                        qualityMessage = "Save blocked: this face is already enrolled as ${duplicateEmployee.fullName}"
                    )
                    return@launch
                }

                val employeeId = employeeWithSameName?.employeeId
                    ?: "EMP_${UUID.randomUUID().toString().replace("-", "").take(12).uppercase()}"
                val now = System.currentTimeMillis()
                val employee = EmployeeEntity(
                    employeeId = employeeId,
                    fullName = normalizedName,
                    department = department.ifBlank { "General" }.trim(),
                    designation = designation.ifBlank { "Staff" }.trim(),
                    payType = payType,
                    hourlyRate = hourlyRate,
                    phoneNumber = phoneNumber?.trim()?.takeIf { it.isNotEmpty() },
                    notes = notes,
                    isActive = true,
                    enrollmentDate = employeeWithSameName?.enrollmentDate ?: now,
                    photoPath = employeeWithSameName?.photoPath,
                    updatedAt = now
                )

                val centroid = embeddingGenerator.averageEmbeddings(
                    enrollmentSamples.map { it.embedding }
                )
                val templates = buildList {
                    add(
                        FaceTemplateEntity(
                            templateId = UUID.randomUUID().toString(),
                            employeeId = employeeId,
                            encryptedEmbedding = CryptoUtils.encryptEmbedding(centroid),
                            modelVersion = embeddingGenerator.modelVersion,
                            qualityScore = enrollmentSamples.map { it.qualityScore }.average().toFloat(),
                            poseYaw = 0f,
                            posePitch = 0f,
                            isCentroid = true
                        )
                    )
                    enrollmentSamples.forEach { sample ->
                        add(
                            FaceTemplateEntity(
                                templateId = UUID.randomUUID().toString(),
                                employeeId = employeeId,
                                encryptedEmbedding = CryptoUtils.encryptEmbedding(sample.embedding),
                                modelVersion = embeddingGenerator.modelVersion,
                                qualityScore = sample.qualityScore,
                                poseYaw = sample.poseYaw,
                                posePitch = sample.posePitch,
                                isCentroid = false
                            )
                        )
                    }
                }

                employeeRepo.saveEmployeeWithTemplates(
                    employee = employee,
                    templates = templates,
                    replaceExistingTemplates = true
                )

                resetEnrollment()
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (error: Throwable) {
                Log.e(TAG, "Unable to save employee enrollment", error)
                _enrollmentState.value = _enrollmentState.value.copy(
                    isSaving = false,
                    qualityMessage = "Could not save the face profile. Please retry"
                )
            }
        }
    }

    fun resetEnrollment() {
        enrollmentSamples.clear()
        lastAcceptedSampleAt = 0L
        firstSideDirection = 0
        enrollmentFrameProcessing.set(false)
        _enrollmentState.value = initialEnrollmentState()
    }

    private fun initialEnrollmentState(): EnrollmentStepState {
        val first = enrollmentSteps.first()
        return EnrollmentStepState(
            stepIndex = 0,
            title = first.title,
            prompt = first.prompt,
            collectedSamples = 0,
            targetSamples = totalEnrollmentSamples,
            samplesInCurrentStep = 0,
            targetSamplesInCurrentStep = first.sampleTarget,
            isComplete = false,
            duplicateWarningEmployeeName = null,
            qualityMessage = first.prompt,
            isSaving = false
        )
    }

    private fun updateEnrollmentMessage(message: String) {
        _enrollmentState.value = _enrollmentState.value.copy(qualityMessage = message)
    }

    fun runCalibration() {
        viewModelScope.launch(Dispatchers.IO) {
            val embeddingsList = employeeRepo.getActiveFaceTemplates()
                .filter { it.modelVersion == embeddingGenerator.modelVersion }
                .mapNotNull { template ->
                    CryptoUtils.decryptEmbedding(template.encryptedEmbedding)?.let { embedding ->
                        template.employeeId to embedding
                    }
                }
            _calibrationResult.value = calibrator.calibrateThresholds(embeddingsList)
        }
    }

    fun exportAttendanceCsv(context: Context, onFileReady: (File) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val eventsList = db.attendanceDao().getPendingSyncEvents()
            val records = eventsList.map { event ->
                val employee = employeeRepo.getEmployeeById(event.employeeId)
                CsvExporter.AttendanceCsvRecord(
                    eventId = event.eventId,
                    employeeId = event.employeeId,
                    employeeName = employee?.fullName ?: "Unknown",
                    department = employee?.department ?: "N/A",
                    eventType = event.eventType,
                    formattedTimestamp = TimeUtils.formatLocalTime(event.timestamp),
                    recognitionScore = event.recognitionScore,
                    livenessResult = event.livenessResult,
                    syncState = event.syncState,
                    deviceId = event.deviceId
                )
            }
            val file = CsvExporter.exportAttendanceToCsv(context, records)
            withContext(Dispatchers.Main) { onFileReady(file) }
        }
    }

    fun createBackup() {
        viewModelScope.launch(Dispatchers.IO) {
            val backupDir = getApplication<Application>()
                .getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val file = File(backupDir, "bioscan_backup_${System.currentTimeMillis()}.zip")
            val success = backupManager.createBackupZip(file)
            _backupStatusMessage.value = if (success) {
                "Backup created at ${file.name}"
            } else {
                "Backup creation failed."
            }
        }
    }

    private fun recycleSafely(bitmap: Bitmap) {
        runCatching {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    override fun onCleared() {
        detectorManager.close()
        embeddingGenerator.close()
        super.onCleared()
    }

    private companion object {
        const val MIN_SAMPLE_INTERVAL_MS = 650L
    }
}
