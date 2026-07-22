package com.example.bioscan.core.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "BioScanCamera"

class CameraManager(context: Context) {

    private val appContext = context.applicationContext
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null

    @Volatile
    private var isReleased = false

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        onFrameAvailable: (Bitmap, Int) -> Unit
    ) {
        if (isReleased) return

        val cameraProviderFuture = ProcessCameraProvider.getInstance(appContext)
        cameraProviderFuture.addListener({
            if (isReleased) return@addListener

            runCatching {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = surfaceProvider
                }

                val analyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                imageAnalysis = analyzer
                analyzer.setAnalyzer(executor) { imageProxy ->
                    try {
                        if (!isReleased) {
                            val bitmap = imageProxyToOrientedBitmap(imageProxy)
                            if (bitmap != null && !isReleased) {
                                onFrameAvailable(bitmap, 0)
                            } else {
                                bitmap?.recycle()
                            }
                        }
                    } catch (error: Throwable) {
                        Log.e(TAG, "Camera frame processing failed", error)
                    } finally {
                        imageProxy.close()
                    }
                }

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analyzer
                )
            }.onFailure { error ->
                Log.e(TAG, "Unable to start front camera", error)
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    private fun imageProxyToOrientedBitmap(imageProxy: ImageProxy): Bitmap? {
        return runCatching {
            val plane = imageProxy.planes.first()
            val buffer = plane.buffer.apply { rewind() }
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * imageProxy.width
            val paddedWidth = imageProxy.width + rowPadding / pixelStride

            val paddedBitmap = Bitmap.createBitmap(
                paddedWidth,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )
            paddedBitmap.copyPixelsFromBuffer(buffer)

            val croppedBitmap = Bitmap.createBitmap(
                paddedBitmap,
                0,
                0,
                imageProxy.width,
                imageProxy.height
            )
            if (croppedBitmap !== paddedBitmap) paddedBitmap.recycle()

            val transform = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                postScale(-1f, 1f)
            }

            val orientedBitmap = Bitmap.createBitmap(
                croppedBitmap,
                0,
                0,
                croppedBitmap.width,
                croppedBitmap.height,
                transform,
                true
            )
            if (orientedBitmap !== croppedBitmap) croppedBitmap.recycle()
            orientedBitmap
        }.onFailure { error ->
            Log.e(TAG, "Unable to convert camera frame", error)
        }.getOrNull()
    }

    fun stopCamera() {
        runCatching {
            imageAnalysis?.clearAnalyzer()
            cameraProvider?.unbindAll()
        }.onFailure { error ->
            Log.w(TAG, "Unable to stop camera cleanly", error)
        }
    }

    fun release() {
        if (isReleased) return
        isReleased = true
        stopCamera()
        imageAnalysis = null
        cameraProvider = null
        executor.shutdownNow()
    }
}
