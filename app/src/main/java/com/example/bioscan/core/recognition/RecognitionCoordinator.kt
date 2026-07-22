package com.example.bioscan.core.recognition

import android.graphics.Bitmap
import com.example.bioscan.core.common.LivenessMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


data class FrameAnalysisResult(
    val faceDetected: Boolean,
    val faceCount: Int,
    val detectedFace: DetectedFaceResult?,
    val quality: QualityCheckResult?,
    val liveness: LivenessState?,
    val candidateMatch: CandidateMatch?,
    val alignedFaceBitmap: Bitmap?,
    val inferenceTimeMs: Long
)

class RecognitionCoordinator(
    val detectorManager: FaceDetectorManager,
    val qualityAnalyzer: FaceQualityAnalyzer,
    val aligner: FaceAligner,
    val embeddingGenerator: IEmbeddingGenerator,
    val identityMatcher: IdentityMatcher,
    val livenessEngine: LivenessEngine,
    val multiFrameConsensus: MultiFrameConsensus
) {

    suspend fun analyzeFrame(
        frameBitmap: Bitmap,
        rotationDegrees: Int = 0,
        livenessMode: LivenessMode = LivenessMode.STANDARD,
        currentThreshold: Float = IdentityMatcher.DEFAULT_THRESHOLD,
        currentMargin: Float = IdentityMatcher.DEFAULT_MIN_MARGIN
    ): FrameAnalysisResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        val faces = detectorManager.detectFaces(frameBitmap, rotationDegrees)
        if (faces.isEmpty()) {
            multiFrameConsensus.clearAll()
            return@withContext result(
                startTime = startTime,
                faceDetected = false,
                faceCount = 0
            )
        }

        if (faces.size > 1) {
            multiFrameConsensus.clearAll()
            val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
            return@withContext result(
                startTime = startTime,
                faceDetected = true,
                faceCount = faces.size,
                detectedFace = primaryFace,
                candidateMatch = CandidateMatch("", 0f, 0f, 0f, IdentityDecision.MULTIPLE_FACES)
            )
        }

        val face = faces.first()
        val quality = qualityAnalyzer.evaluateQuality(frameBitmap, face)
        if (!quality.isQualified) {
            multiFrameConsensus.clearTrack(face.trackingId)
            return@withContext result(
                startTime = startTime,
                faceDetected = true,
                faceCount = 1,
                detectedFace = face,
                quality = quality,
                candidateMatch = CandidateMatch("", 0f, 0f, 0f, IdentityDecision.LOW_QUALITY)
            )
        }

        val livenessState = livenessEngine.evaluateLiveness(face, livenessMode)
        if (!livenessState.isCompleted && livenessMode != LivenessMode.OFF) {
            multiFrameConsensus.clearTrack(face.trackingId)
            return@withContext result(
                startTime = startTime,
                faceDetected = true,
                faceCount = 1,
                detectedFace = face,
                quality = quality,
                liveness = livenessState,
                candidateMatch = CandidateMatch("", 0f, 0f, 0f, IdentityDecision.LIVENESS_FAILED)
            )
        }

        val alignedBitmap = aligner.alignAndCropFace(frameBitmap, face)
            ?: return@withContext result(
                startTime = startTime,
                faceDetected = true,
                faceCount = 1,
                detectedFace = face,
                quality = quality,
                liveness = livenessState,
                candidateMatch = CandidateMatch("", 0f, 0f, 0f, IdentityDecision.LOW_QUALITY)
            )

        try {
            val embedding = embeddingGenerator.generateEmbedding(alignedBitmap)
            val initialMatch = identityMatcher.findMatch(
                queryEmbedding = embedding,
                threshold = currentThreshold.coerceAtLeast(IdentityMatcher.DEFAULT_THRESHOLD),
                minMargin = currentMargin.coerceAtLeast(IdentityMatcher.DEFAULT_MIN_MARGIN)
            )
            val finalMatch = multiFrameConsensus.addSampleAndCheckConsensus(
                face.trackingId,
                initialMatch
            )

            result(
                startTime = startTime,
                faceDetected = true,
                faceCount = 1,
                detectedFace = face,
                quality = quality,
                liveness = livenessState,
                candidateMatch = finalMatch
            )
        } finally {
            if (!alignedBitmap.isRecycled) alignedBitmap.recycle()
        }
    }

    private fun result(
        startTime: Long,
        faceDetected: Boolean,
        faceCount: Int,
        detectedFace: DetectedFaceResult? = null,
        quality: QualityCheckResult? = null,
        liveness: LivenessState? = null,
        candidateMatch: CandidateMatch? = null
    ) = FrameAnalysisResult(
        faceDetected = faceDetected,
        faceCount = faceCount,
        detectedFace = detectedFace,
        quality = quality,
        liveness = liveness,
        candidateMatch = candidateMatch,
        alignedFaceBitmap = null,
        inferenceTimeMs = System.currentTimeMillis() - startTime
    )
}
