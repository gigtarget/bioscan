package com.example.bioscan.core.recognition

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint

class FaceAligner {

    companion object {
        const val TARGET_SIZE = 112
    }

    fun alignAndCropFace(sourceBitmap: Bitmap, faceResult: DetectedFaceResult): Bitmap? {
        val leftEye = faceResult.leftEyePos
        val rightEye = faceResult.rightEyePos

        val box = faceResult.boundingBox
        if (box.width() <= 0 || box.height() <= 0) return null

        val matrix = Matrix()

        if (leftEye != null && rightEye != null) {
            val deltaX = rightEye.x - leftEye.x
            val deltaY = rightEye.y - leftEye.y
            val angle = Math.toDegrees(Math.atan2(deltaY.toDouble(), deltaX.toDouble())).toFloat()

            val eyeCenterX = (leftEye.x + rightEye.x) / 2f
            val eyeCenterY = (leftEye.y + rightEye.y) / 2f

            matrix.postRotate(angle, eyeCenterX, eyeCenterY)
        }

        val paddingX = (box.width() * 0.15f).toInt()
        val paddingY = (box.height() * 0.15f).toInt()

        val left = (box.left - paddingX).coerceAtLeast(0)
        val top = (box.top - paddingY).coerceAtLeast(0)
        val right = (box.right + paddingX).coerceAtMost(sourceBitmap.width)
        val bottom = (box.bottom + paddingY).coerceAtMost(sourceBitmap.height)

        val cropWidth = right - left
        val cropHeight = bottom - top

        if (cropWidth <= 0 || cropHeight <= 0) return null

        return try {
            val cropped = Bitmap.createBitmap(sourceBitmap, left, top, cropWidth, cropHeight, matrix, true)
            Bitmap.createScaledBitmap(cropped, TARGET_SIZE, TARGET_SIZE, true)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
