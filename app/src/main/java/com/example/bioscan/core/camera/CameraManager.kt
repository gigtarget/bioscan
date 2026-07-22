package com.example.bioscan.core.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors

class CameraManager(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    @Volatile
    private var isStopped = false

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        onFrameAvailable: (Bitmap, Int) -> Unit
    ) {
        isStopped = false
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    try {
                        if (!isStopped) {
                            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                            val bitmap = imageProxyToBitmap(imageProxy)
                            if (bitmap != null && !isStopped) {
                                onFrameAvailable(bitmap, rotationDegrees)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        imageProxy.close()
                    }
                }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                cameraProvider?.unbindAll()
                if (!isStopped) {
                    cameraProvider?.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val bitmap = Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )
            val buffer = imageProxy.planes[0].buffer
            buffer.rewind()
            bitmap.copyPixelsFromBuffer(buffer)

            // Mirror horizontally for front camera if needed
            val matrix = Matrix().apply {
                postScale(-1f, 1f, imageProxy.width / 2f, imageProxy.height / 2f)
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun stopCamera() {
        isStopped = true
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
