package com.example.bioscan.core.recognition

import android.graphics.Bitmap
import android.graphics.Color

data class QualityCheckResult(
    val isQualified: Boolean,
    val overallScore: Float, // 0.0 to 1.0
    val blurScore: Float,
    val brightnessScore: Float,
    val poseYaw: Float,
    val posePitch: Float,
    val rejectionReason: String?
)

class FaceQualityAnalyzer {

    fun evaluateQuality(bitmap: Bitmap, faceResult: DetectedFaceResult): QualityCheckResult {
        val yaw = Math.abs(faceResult.headEulerAngleYaw)
        val pitch = Math.abs(faceResult.headEulerAnglePitch)

        if (yaw > 35.0f) {
            return QualityCheckResult(false, 0.2f, 0f, 0f, yaw, pitch, "Look straight at the camera")
        }
        if (pitch > 30.0f) {
            return QualityCheckResult(false, 0.2f, 0f, 0f, yaw, pitch, "Keep head level")
        }

        val box = faceResult.boundingBox
        if (box.width() < 50 || box.height() < 50) {
            return QualityCheckResult(false, 0.3f, 0f, 0f, yaw, pitch, "Move closer to camera")
        }

        val left = box.left.coerceIn(0, (bitmap.width - 1).coerceAtLeast(0))
        val top = box.top.coerceIn(0, (bitmap.height - 1).coerceAtLeast(0))
        val right = box.right.coerceIn(left + 1, bitmap.width)
        val bottom = box.bottom.coerceIn(top + 1, bitmap.height)
        val cropW = right - left
        val cropH = bottom - top

        if (cropW < 40 || cropH < 40) {
            return QualityCheckResult(false, 0.3f, 0f, 0f, yaw, pitch, "Center face in frame")
        }

        // Crop face region to analyze illumination and contrast
        val faceCrop = try {
            Bitmap.createBitmap(bitmap, left, top, cropW, cropH)
        } catch (e: Exception) {
            return QualityCheckResult(false, 0.1f, 0f, 0f, yaw, pitch, "Face out of bounds")
        }

        val brightness = calculateAverageBrightness(faceCrop)
        if (brightness < 20) {
            return QualityCheckResult(false, 0.4f, 0f, brightness, yaw, pitch, "Improve lighting (Too Dark)")
        }
        if (brightness > 245) {
            return QualityCheckResult(false, 0.4f, 0f, brightness, yaw, pitch, "Avoid harsh glare (Too Bright)")
        }

        val blurScore = estimateSharpness(faceCrop)
        if (blurScore < 3.0f) {
            return QualityCheckResult(false, 0.4f, blurScore, brightness, yaw, pitch, "Hold still (Image Blurred)")
        }

        val poseScore = 1.0f - ((yaw / 30.0f) * 0.5f + (pitch / 25.0f) * 0.5f)
        val overall = (poseScore * 0.4f + (blurScore.coerceAtMost(100f) / 100f) * 0.3f + (brightness / 255f) * 0.3f).coerceIn(0f, 1f)

        return QualityCheckResult(true, overall, blurScore, brightness, yaw, pitch, null)
    }

    private fun calculateAverageBrightness(bitmap: Bitmap): Float {
        var totalLuminance = 0L
        val width = bitmap.width
        val height = bitmap.height
        val step = (width * height / 400).coerceAtLeast(1)
        var sampleCount = 0

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var i = 0
        while (i < pixels.size) {
            val color = pixels[i]
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            totalLuminance += (0.299 * r + 0.587 * g + 0.114 * b).toLong()
            sampleCount++
            i += step
        }

        return if (sampleCount > 0) totalLuminance.toFloat() / sampleCount else 128f
    }

    private fun estimateSharpness(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 10 || height < 10) return 0f

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var sumDiff = 0L
        var count = 0
        val step = 2

        for (y in 0 until height - step step step) {
            for (x in 0 until width - step step step) {
                val p1 = Color.red(pixels[y * width + x])
                val p2 = Color.red(pixels[y * width + (x + step)])
                val p3 = Color.red(pixels[(y + step) * width + x])
                val diff = Math.abs(p1 - p2) + Math.abs(p1 - p3)
                sumDiff += diff
                count++
            }
        }

        return if (count > 0) (sumDiff.toFloat() / count) else 0f
    }
}
