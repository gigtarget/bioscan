package com.example.bioscan.core.recognition

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.sqrt


data class QualityCheckResult(
    val isQualified: Boolean,
    val overallScore: Float,
    val blurScore: Float,
    val brightnessScore: Float,
    val poseYaw: Float,
    val posePitch: Float,
    val rejectionReason: String?
)

class FaceQualityAnalyzer {

    fun evaluateQuality(bitmap: Bitmap, faceResult: DetectedFaceResult): QualityCheckResult {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return rejected("Camera frame unavailable")
        }

        val yaw = abs(faceResult.headEulerAngleYaw)
        val pitch = abs(faceResult.headEulerAnglePitch)
        val roll = abs(faceResult.headEulerAngleRoll)

        if (yaw > MAX_YAW) return rejected("Turn slightly toward the camera", yaw, pitch)
        if (pitch > MAX_PITCH) return rejected("Keep your head level", yaw, pitch)
        if (roll > MAX_ROLL) return rejected("Keep your head upright", yaw, pitch)

        val box = faceResult.boundingBox
        val minimumFrameSide = minOf(bitmap.width, bitmap.height).toFloat()
        val faceSide = minOf(box.width(), box.height()).toFloat()
        if (faceSide / minimumFrameSide < MIN_FACE_RATIO) {
            return rejected("Move closer to the camera", yaw, pitch)
        }

        val centerDx = abs(box.exactCenterX() - bitmap.width / 2f) / bitmap.width.toFloat()
        val centerDy = abs(box.exactCenterY() - bitmap.height / 2f) / bitmap.height.toFloat()
        if (centerDx > MAX_CENTER_X_OFFSET || centerDy > MAX_CENTER_Y_OFFSET) {
            return rejected("Center your face inside the frame", yaw, pitch)
        }

        val left = box.left.coerceIn(0, bitmap.width - 1)
        val top = box.top.coerceIn(0, bitmap.height - 1)
        val right = box.right.coerceIn(left + 1, bitmap.width)
        val bottom = box.bottom.coerceIn(top + 1, bitmap.height)
        if (right - left < MIN_FACE_PIXELS || bottom - top < MIN_FACE_PIXELS) {
            return rejected("Move closer to the camera", yaw, pitch)
        }

        val faceCrop = try {
            Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        } catch (_: Throwable) {
            return rejected("Keep your full face inside the frame", yaw, pitch)
        }

        return try {
            val statistics = calculateLuminanceStatistics(faceCrop)
            if (statistics.mean < MIN_BRIGHTNESS) {
                return rejected("Improve lighting on your face", yaw, pitch, statistics.mean)
            }
            if (statistics.mean > MAX_BRIGHTNESS) {
                return rejected("Avoid harsh light or glare", yaw, pitch, statistics.mean)
            }
            if (statistics.standardDeviation < MIN_CONTRAST) {
                return rejected("Use more even, clearer lighting", yaw, pitch, statistics.mean)
            }

            val sharpness = estimateSharpness(faceCrop)
            if (sharpness < MIN_SHARPNESS) {
                return rejected("Hold still until the image is sharp", yaw, pitch, statistics.mean, sharpness)
            }

            val poseScore = (1f - (yaw / MAX_YAW * 0.45f +
                pitch / MAX_PITCH * 0.35f +
                roll / MAX_ROLL * 0.20f)).coerceIn(0f, 1f)
            val lightScore = (1f - abs(statistics.mean - IDEAL_BRIGHTNESS) / IDEAL_BRIGHTNESS)
                .coerceIn(0f, 1f)
            val sharpnessScore = (sharpness / TARGET_SHARPNESS).coerceIn(0f, 1f)
            val contrastScore = (statistics.standardDeviation / TARGET_CONTRAST).coerceIn(0f, 1f)
            val overall = (
                poseScore * 0.30f +
                    lightScore * 0.20f +
                    sharpnessScore * 0.30f +
                    contrastScore * 0.20f
                ).coerceIn(0f, 1f)

            QualityCheckResult(
                isQualified = overall >= MIN_OVERALL_SCORE,
                overallScore = overall,
                blurScore = sharpness,
                brightnessScore = statistics.mean,
                poseYaw = yaw,
                posePitch = pitch,
                rejectionReason = if (overall >= MIN_OVERALL_SCORE) null else "Hold still and face the camera clearly"
            )
        } finally {
            if (!faceCrop.isRecycled) faceCrop.recycle()
        }
    }

    private fun rejected(
        reason: String,
        yaw: Float = 0f,
        pitch: Float = 0f,
        brightness: Float = 0f,
        sharpness: Float = 0f
    ) = QualityCheckResult(
        isQualified = false,
        overallScore = 0f,
        blurScore = sharpness,
        brightnessScore = brightness,
        poseYaw = yaw,
        posePitch = pitch,
        rejectionReason = reason
    )

    private data class LuminanceStatistics(
        val mean: Float,
        val standardDeviation: Float
    )

    private fun calculateLuminanceStatistics(bitmap: Bitmap): LuminanceStatistics {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val step = (pixels.size / 1_200).coerceAtLeast(1)
        var sum = 0.0
        var sumSquared = 0.0
        var count = 0
        for (index in pixels.indices step step) {
            val color = pixels[index]
            val luminance = 0.299 * Color.red(color) +
                0.587 * Color.green(color) +
                0.114 * Color.blue(color)
            sum += luminance
            sumSquared += luminance * luminance
            count++
        }

        if (count == 0) return LuminanceStatistics(0f, 0f)
        val mean = sum / count
        val variance = (sumSquared / count - mean * mean).coerceAtLeast(0.0)
        return LuminanceStatistics(mean.toFloat(), sqrt(variance).toFloat())
    }

    private fun estimateSharpness(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 12 || height < 12) return 0f

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var sum = 0.0
        var count = 0
        val step = 2
        for (y in step until height - step step step) {
            for (x in step until width - step step step) {
                val center = luminance(pixels[y * width + x])
                val laplacian = 4.0 * center -
                    luminance(pixels[y * width + x - step]) -
                    luminance(pixels[y * width + x + step]) -
                    luminance(pixels[(y - step) * width + x]) -
                    luminance(pixels[(y + step) * width + x])
                sum += abs(laplacian)
                count++
            }
        }
        return if (count == 0) 0f else (sum / count).toFloat()
    }

    private fun luminance(color: Int): Double =
        0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)

    private companion object {
        const val MAX_YAW = 32f
        const val MAX_PITCH = 24f
        const val MAX_ROLL = 20f
        const val MIN_FACE_RATIO = 0.22f
        const val MAX_CENTER_X_OFFSET = 0.28f
        const val MAX_CENTER_Y_OFFSET = 0.30f
        const val MIN_FACE_PIXELS = 90
        const val MIN_BRIGHTNESS = 45f
        const val MAX_BRIGHTNESS = 220f
        const val IDEAL_BRIGHTNESS = 135f
        const val MIN_CONTRAST = 22f
        const val TARGET_CONTRAST = 55f
        const val MIN_SHARPNESS = 7f
        const val TARGET_SHARPNESS = 22f
        const val MIN_OVERALL_SCORE = 0.58f
    }
}
