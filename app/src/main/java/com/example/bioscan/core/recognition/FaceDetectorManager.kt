package com.example.bioscan.core.recognition

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class DetectedFaceResult(
    val face: Face,
    val boundingBox: Rect,
    val leftEyePos: android.graphics.PointF?,
    val rightEyePos: android.graphics.PointF?,
    val nosePos: android.graphics.PointF?,
    val mouthPos: android.graphics.PointF?,
    val headEulerAngleYaw: Float,
    val headEulerAnglePitch: Float,
    val headEulerAngleRoll: Float,
    val leftEyeOpenProbability: Float?,
    val rightEyeOpenProbability: Float?,
    val smilingProbability: Float?,
    val trackingId: Int?
)

class FaceDetectorManager {

    private val detector: FaceDetector

    init {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .enableTracking()
            .build()
        detector = FaceDetection.getClient(options)
    }

    suspend fun detectFaces(bitmap: Bitmap, rotationDegrees: Int = 0): List<DetectedFaceResult> =
        suspendCancellableCoroutine { continuation ->
            val inputImage = InputImage.fromBitmap(bitmap, rotationDegrees)
            detector.process(inputImage)
                .addOnSuccessListener { faces ->
                    val results = faces.map { face ->
                        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
                        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
                        val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position
                        val mouth = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position

                        DetectedFaceResult(
                            face = face,
                            boundingBox = face.boundingBox,
                            leftEyePos = leftEye,
                            rightEyePos = rightEye,
                            nosePos = nose,
                            mouthPos = mouth,
                            headEulerAngleYaw = face.headEulerAngleY,
                            headEulerAnglePitch = face.headEulerAngleX,
                            headEulerAngleRoll = face.headEulerAngleZ,
                            leftEyeOpenProbability = face.leftEyeOpenProbability,
                            rightEyeOpenProbability = face.rightEyeOpenProbability,
                            smilingProbability = face.smilingProbability,
                            trackingId = face.trackingId
                        )
                    }
                    if (continuation.isActive) {
                        continuation.resume(results)
                    }
                }
                .addOnFailureListener { exception ->
                    if (continuation.isActive) {
                        continuation.resume(emptyList())
                    }
                }
        }

    fun close() {
        try {
            detector.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
