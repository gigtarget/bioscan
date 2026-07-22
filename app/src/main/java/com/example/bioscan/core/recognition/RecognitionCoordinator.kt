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
        currentThreshold: Float = 0.72f,
        currentMargin: Float = 0.08f
    ): FrameAnalysisResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        val faces = detectorManager.detectFaces(frameBitmap, rotationDegrees)
        if (faces.isEmpty()) {
            return@withContext FrameAnalysisResult(
                faceDetected = false,
                faceCount = 0,
                detectedFace = null,
                quality = null,
                liveness = null,
                candidateMatch = null,
                alignedFaceBitmap = null,
                inferenceTimeMs = System.currentTimeMillis() - startTime
            )
        }

        if (faces.size > 1) {
            val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
            return@withContext FrameAnalysisResult(
                faceDetected = true,
                faceCount = faces.size,
                detectedFace = primaryFace,
                quality = null,
                liveness = null,
                candidateMatch = CandidateMatch("", 0f, 0f, 0f, IdentityDecision.MULTIPLE_FACES),
                alignedFaceBitmap = null,
                inferenceTimeMs = System.currentTimeMillis() - startTime
            )
        }

        val face = faces.first()
        val quality = qualityAnalyzer.evaluateQuality(frameBitmap, face)

        if (!quality.isQualified) {
            return@withContext FrameAnalysisResult(
                faceDetected = true,
                faceCount = 1,
                detectedFace = face,
                quality = quality,
                liveness = null,
                candidateMatch = CandidateMatch("", 0f, 0f, 0f, IdentityDecision.LOW_QUALITY),
                alignedFaceBitmap = null,
                inferenceTimeMs = System.currentTimeMillis() - startTime
            )
        }

        // Active liveness evaluation
        val livenessState = livenessEngine.evaluateLiveness(face, livenessMode)
        if (!livenessState.isCompleted && livenessMode != LivenessMode.OFF) {
            return@withContext FrameAnalysisResult(
                faceDetected = true,
                faceCount = 1,
                detectedFace = face,
                quality = quality,
                liveness = livenessState,
                candidateMatch = CandidateMatch("", 0f, 0f, 0f, IdentityDecision.LIVENESS_FAILED),
                alignedFaceBitmap = null,
                inferenceTimeMs = System.currentTimeMillis() - startTime
            )
        }

        val alignedBitmap = aligner.alignAndCropFace(frameBitmap, face)
        if (alignedBitmap == null) {
            return@withContext FrameAnalysisResult(
                faceDetected = true,
                faceCount = 1,
                detectedFace = face,
                quality = quality,
                liveness = livenessState,
                candidateMatch = CandidateMatch("", 0f, 0f, 0f, IdentityDecision.LOW_QUALITY),
                alignedFaceBitmap = null,
                inferenceTimeMs = System.currentTimeMillis() - startTime
            )
        }

        val embedding = embeddingGenerator.generateEmbedding(alignedBitmap)
        val initialMatch = identityMatcher.findMatch(embedding, currentThreshold, currentMargin)
        val finalMatch = multiFrameConsensus.addSampleAndCheckConsensus(face.trackingId, initialMatch)

        val totalTime = System.currentTimeMillis() - startTime

        FrameAnalysisResult(
            faceDetected = true,
            faceCount = 1,
            detectedFace = face,
            quality = quality,
            liveness = livenessState,
            candidateMatch = finalMatch,
            alignedFaceBitmap = alignedBitmap,
            inferenceTimeMs = totalTime
        )
    }
}
