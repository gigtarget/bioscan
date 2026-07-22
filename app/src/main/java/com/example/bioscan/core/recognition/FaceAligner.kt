package com.example.bioscan.core.recognition

import android.graphics.Bitmap
import android.graphics.Matrix
import kotlin.math.max
import kotlin.math.roundToInt

class FaceAligner {

    fun alignAndCropFace(sourceBitmap: Bitmap, faceResult: DetectedFaceResult): Bitmap? {
        if (sourceBitmap.isRecycled) return null

        val box = faceResult.boundingBox
        if (box.width() <= 0 || box.height() <= 0) return null

        val paddedSide = (max(box.width(), box.height()) * FACE_PADDING_FACTOR).roundToInt()
            .coerceAtLeast(1)
        val centerX = box.exactCenterX().roundToInt()
        val centerY = (box.exactCenterY() - box.height() * VERTICAL_OFFSET).roundToInt()

        var left = centerX - paddedSide / 2
        var top = centerY - paddedSide / 2
        var right = left + paddedSide
        var bottom = top + paddedSide

        if (left < 0) {
            right -= left
            left = 0
        }
        if (top < 0) {
            bottom -= top
            top = 0
        }
        if (right > sourceBitmap.width) {
            left -= right - sourceBitmap.width
            right = sourceBitmap.width
        }
        if (bottom > sourceBitmap.height) {
            top -= bottom - sourceBitmap.height
            bottom = sourceBitmap.height
        }

        left = left.coerceAtLeast(0)
        top = top.coerceAtLeast(0)
        right = right.coerceAtMost(sourceBitmap.width)
        bottom = bottom.coerceAtMost(sourceBitmap.height)

        val cropWidth = right - left
        val cropHeight = bottom - top
        if (cropWidth < MIN_CROP_SIZE || cropHeight < MIN_CROP_SIZE) return null

        var crop: Bitmap? = null
        var rotated: Bitmap? = null
        var square: Bitmap? = null

        return try {
            crop = Bitmap.createBitmap(sourceBitmap, left, top, cropWidth, cropHeight)

            val matrix = Matrix().apply {
                postRotate(-faceResult.headEulerAngleRoll)
            }
            rotated = Bitmap.createBitmap(
                crop,
                0,
                0,
                crop.width,
                crop.height,
                matrix,
                true
            )

            val squareSide = minOf(rotated.width, rotated.height)
            val squareLeft = (rotated.width - squareSide) / 2
            val squareTop = (rotated.height - squareSide) / 2
            square = Bitmap.createBitmap(rotated, squareLeft, squareTop, squareSide, squareSide)

            Bitmap.createScaledBitmap(
                square,
                EmbeddingGenerator.INPUT_SIZE,
                EmbeddingGenerator.INPUT_SIZE,
                true
            )
        } catch (_: Throwable) {
            null
        } finally {
            square?.let { bitmap ->
                if (bitmap !== rotated && !bitmap.isRecycled) bitmap.recycle()
            }
            rotated?.let { bitmap ->
                if (bitmap !== crop && !bitmap.isRecycled) bitmap.recycle()
            }
            crop?.let { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }

    private companion object {
        const val FACE_PADDING_FACTOR = 1.45f
        const val VERTICAL_OFFSET = 0.04f
        const val MIN_CROP_SIZE = 80
    }
}
