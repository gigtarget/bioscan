package com.example.bioscan.presentation.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bioscan.core.common.KioskSettings
import com.example.bioscan.core.common.KioskSettingsRepository
import com.example.bioscan.core.database.BioScanDatabase
import com.example.bioscan.core.recognition.EmbeddingGenerator
import com.example.bioscan.core.recognition.FaceAligner
import com.example.bioscan.core.recognition.FaceDetectorManager
import com.example.bioscan.core.recognition.FaceQualityAnalyzer
import com.example.bioscan.core.recognition.FrameAnalysisResult
import com.example.bioscan.core.recognition.IdentityDecision
import com.example.bioscan.core.recognition.IdentityMatcher
import com.example.bioscan.core.recognition.LivenessEngine
import com.example.bioscan.core.recognition.MultiFrameConsensus
import com.example.bioscan.core.recognition.RecognitionCoordinator
import com.example.bioscan.data.repository.EmployeeRepository
import com.example.bioscan.domain.rules.AttendanceRuleResult
import com.example.bioscan.domain.rules.AttendanceRulesEngine
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "BioScanKiosk"

data class ConfirmationCardState(
    val isVisible: Boolean = false,
    val employeeName: String = "",
    val department: String = "",
    val eventType: String = "",
    val formattedTime: String = "",
    val photoPath: String? = null
)

class KioskMainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = BioScanDatabase.getInstance(application)
    private val employeeRepo = EmployeeRepository(db)
    val settingsRepo = KioskSettingsRepository(application)

    val detectorManager = FaceDetectorManager()
    val qualityAnalyzer = FaceQualityAnalyzer()
    val aligner = FaceAligner()
    val embeddingGenerator = EmbeddingGenerator()
    val identityMatcher = IdentityMatcher(embeddingGenerator)
    val livenessEngine = LivenessEngine()
    val multiFrameConsensus = MultiFrameConsensus()

    val coordinator = RecognitionCoordinator(
        detectorManager = detectorManager,
        qualityAnalyzer = qualityAnalyzer,
        aligner = aligner,
        embeddingGenerator = embeddingGenerator,
        identityMatcher = identityMatcher,
        livenessEngine = livenessEngine,
        multiFrameConsensus = multiFrameConsensus
    )

    private val rulesEngine = AttendanceRulesEngine(db.attendanceDao())

    private val _analysisResult = MutableStateFlow<FrameAnalysisResult?>(null)
    val analysisResult: StateFlow<FrameAnalysisResult?> = _analysisResult.asStateFlow()

    private val _confirmationState = MutableStateFlow(ConfirmationCardState())
    val confirmationState: StateFlow<ConfirmationCardState> = _confirmationState.asStateFlow()

    private val _kioskSettings = MutableStateFlow(KioskSettings())
    val kioskSettings: StateFlow<KioskSettings> = _kioskSettings.asStateFlow()

    private var toneGenerator: ToneGenerator? = null
    private val isFrameProcessing = AtomicBoolean(false)
    private val isAttendanceProcessing = AtomicBoolean(false)

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to initialise notification tone", error)
        }

        viewModelScope.launch {
            settingsRepo.settingsFlow.collect { settings ->
                _kioskSettings.value = settings
            }
        }

        refreshTemplateIndex()
    }

    fun refreshTemplateIndex() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                employeeRepo.getAllFaceTemplates()
                    .map { template -> template.employeeId to template.encryptedEmbedding }
            }.onSuccess { templatePairs ->
                identityMatcher.updateTemplateIndex(templatePairs)
            }.onFailure { error ->
                Log.e(TAG, "Unable to refresh face-template index", error)
            }
        }
    }

    fun processFrame(frameBitmap: Bitmap, rotationDegrees: Int) {
        if (_confirmationState.value.isVisible || isAttendanceProcessing.get()) {
            recycleSafely(frameBitmap)
            return
        }

        if (!isFrameProcessing.compareAndSet(false, true)) {
            recycleSafely(frameBitmap)
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val settings = _kioskSettings.value
                val result = coordinator.analyzeFrame(
                    frameBitmap = frameBitmap,
                    rotationDegrees = rotationDegrees,
                    livenessMode = settings.livenessMode,
                    currentThreshold = settings.recognitionThreshold,
                    currentMargin = settings.recognitionMargin
                )
                _analysisResult.value = result

                val match = result.candidateMatch
                if (
                    match != null &&
                    match.decision == IdentityDecision.MATCH &&
                    match.employeeId.isNotBlank()
                ) {
                    triggerAttendanceRecord(
                        employeeId = match.employeeId,
                        score = match.similarityScore,
                        secondBestScore = match.secondBestScore,
                        margin = match.scoreMargin,
                        qualityScore = result.quality?.overallScore ?: 0.9f
                    )
                }
            } catch (error: Throwable) {
                Log.e(TAG, "Frame analysis failed", error)
                _analysisResult.value = null
                livenessEngine.reset()
            } finally {
                recycleSafely(frameBitmap)
                isFrameProcessing.set(false)
            }
        }
    }

    private fun triggerAttendanceRecord(
        employeeId: String,
        score: Float,
        secondBestScore: Float,
        margin: Float,
        qualityScore: Float
    ) {
        if (!isAttendanceProcessing.compareAndSet(false, true)) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val employee = employeeRepo.getEmployeeById(employeeId)
                val settings = _kioskSettings.value
                val ruleResult = rulesEngine.processAttendanceDecision(
                    employeeId = employeeId,
                    recognitionScore = score,
                    secondBestScore = secondBestScore,
                    scoreMargin = margin,
                    qualityScore = qualityScore,
                    modelVersion = embeddingGenerator.modelVersion,
                    terminalMode = settings.terminalMode,
                    cooldownSeconds = settings.cooldownSeconds
                )

                if (ruleResult is AttendanceRuleResult.Success) {
                    if (settings.soundAlertsEnabled) playSuccessBeep()

                    _confirmationState.value = ConfirmationCardState(
                        isVisible = true,
                        employeeName = employee?.fullName ?: "Employee #$employeeId",
                        department = employee?.department ?: "General",
                        eventType = ruleResult.eventType,
                        formattedTime = ruleResult.message,
                        photoPath = employee?.photoPath
                    )

                    delay(3_200L)
                    _confirmationState.value = ConfirmationCardState()
                    livenessEngine.reset()
                }
            } catch (error: Throwable) {
                Log.e(TAG, "Attendance recording failed", error)
            } finally {
                isAttendanceProcessing.set(false)
            }
        }
    }

    private fun playSuccessBeep() {
        runCatching {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        }.onFailure { error ->
            Log.w(TAG, "Unable to play confirmation tone", error)
        }
    }

    private fun recycleSafely(bitmap: Bitmap) {
        runCatching {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    override fun onCleared() {
        detectorManager.close()
        toneGenerator?.release()
        super.onCleared()
    }
}
