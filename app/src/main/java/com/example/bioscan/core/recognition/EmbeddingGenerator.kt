package com.example.bioscan.core.recognition

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

interface IEmbeddingGenerator {
    fun generateEmbedding(alignedFaceBitmap: Bitmap): FloatArray
    fun calculateCosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float
    fun calculateEuclideanDistance(embedding1: FloatArray, embedding2: FloatArray): Float
    val modelVersion: String
    val embeddingDimension: Int
}

/**
 * Real on-device FaceNet inference backed by LiteRT/TensorFlow Lite.
 *
 * Input: 160 x 160 RGB float image in [-1, 1]
 * Output: 128-dimensional L2-normalized face embedding
 */
class EmbeddingGenerator(context: Context) : IEmbeddingGenerator, AutoCloseable {

    override val modelVersion: String = MODEL_VERSION
    override val embeddingDimension: Int = EMBEDDING_DIMENSION

    private val interpreterLock = Any()
    private val interpreter: Interpreter

    init {
        val options = Interpreter.Options().apply {
            setNumThreads(4)
            setUseXNNPACK(true)
        }
        interpreter = Interpreter(loadModelFile(context.applicationContext), options)

        val inputShape = interpreter.getInputTensor(0).shape()
        val outputShape = interpreter.getOutputTensor(0).shape()
        require(inputShape.contentEquals(intArrayOf(1, INPUT_SIZE, INPUT_SIZE, 3))) {
            "Unexpected FaceNet input shape: ${inputShape.contentToString()}"
        }
        require(outputShape.lastOrNull() == EMBEDDING_DIMENSION) {
            "Unexpected FaceNet output shape: ${outputShape.contentToString()}"
        }
    }

    override fun generateEmbedding(alignedFaceBitmap: Bitmap): FloatArray {
        require(!alignedFaceBitmap.isRecycled) { "Cannot embed a recycled bitmap" }

        val resized = if (
            alignedFaceBitmap.width == INPUT_SIZE &&
            alignedFaceBitmap.height == INPUT_SIZE
        ) {
            alignedFaceBitmap
        } else {
            Bitmap.createScaledBitmap(alignedFaceBitmap, INPUT_SIZE, INPUT_SIZE, true)
        }

        return try {
            val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
            resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

            val inputBuffer = ByteBuffer
                .allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * FLOAT_BYTES)
                .order(ByteOrder.nativeOrder())

            for (pixel in pixels) {
                inputBuffer.putFloat(((pixel shr 16 and 0xFF) - 127.5f) / 127.5f)
                inputBuffer.putFloat(((pixel shr 8 and 0xFF) - 127.5f) / 127.5f)
                inputBuffer.putFloat(((pixel and 0xFF) - 127.5f) / 127.5f)
            }
            inputBuffer.rewind()

            val output = Array(1) { FloatArray(EMBEDDING_DIMENSION) }
            synchronized(interpreterLock) {
                interpreter.run(inputBuffer, output)
            }

            val embedding = l2Normalize(output[0])
            require(embedding.all { it.isFinite() }) { "Face model returned invalid values" }
            embedding
        } finally {
            if (resized !== alignedFaceBitmap && !resized.isRecycled) {
                resized.recycle()
            }
        }
    }

    fun averageEmbeddings(embeddings: List<FloatArray>): FloatArray {
        require(embeddings.isNotEmpty()) { "At least one embedding is required" }
        require(embeddings.all { it.size == EMBEDDING_DIMENSION }) {
            "All embeddings must have dimension $EMBEDDING_DIMENSION"
        }

        val average = FloatArray(EMBEDDING_DIMENSION)
        embeddings.forEach { embedding ->
            embedding.indices.forEach { index ->
                average[index] += embedding[index]
            }
        }
        average.indices.forEach { index ->
            average[index] /= embeddings.size.toFloat()
        }
        return l2Normalize(average)
    }

    override fun calculateCosineSimilarity(
        embedding1: FloatArray,
        embedding2: FloatArray
    ): Float {
        if (embedding1.size != embedding2.size || embedding1.isEmpty()) return -1f

        var dot = 0f
        var norm1 = 0f
        var norm2 = 0f
        for (index in embedding1.indices) {
            val first = embedding1[index]
            val second = embedding2[index]
            dot += first * second
            norm1 += first * first
            norm2 += second * second
        }

        if (norm1 <= 0f || norm2 <= 0f) return -1f
        return (dot / (sqrt(norm1.toDouble()) * sqrt(norm2.toDouble())).toFloat())
            .coerceIn(-1f, 1f)
    }

    override fun calculateEuclideanDistance(
        embedding1: FloatArray,
        embedding2: FloatArray
    ): Float {
        if (embedding1.size != embedding2.size || embedding1.isEmpty()) {
            return Float.MAX_VALUE
        }

        var sumSquared = 0f
        for (index in embedding1.indices) {
            val difference = embedding1[index] - embedding2[index]
            sumSquared += difference * difference
        }
        return sqrt(sumSquared.toDouble()).toFloat()
    }

    override fun close() {
        synchronized(interpreterLock) {
            interpreter.close()
        }
    }

    private fun l2Normalize(vector: FloatArray): FloatArray {
        var squaredNorm = 0f
        vector.forEach { value -> squaredNorm += value * value }
        val norm = sqrt(squaredNorm.toDouble()).toFloat()
        require(norm > 1e-8f) { "Face embedding has zero length" }
        return FloatArray(vector.size) { index -> vector[index] / norm }
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(MODEL_ASSET_NAME)
        FileInputStream(assetFileDescriptor.fileDescriptor).channel.use { channel ->
            return channel.map(
                FileChannel.MapMode.READ_ONLY,
                assetFileDescriptor.startOffset,
                assetFileDescriptor.declaredLength
            )
        }
    }

    companion object {
        const val MODEL_VERSION = "FaceNet-128-LiteRT-v1"
        const val MODEL_ASSET_NAME = "facenet.tflite"
        const val INPUT_SIZE = 160
        const val EMBEDDING_DIMENSION = 128
        private const val FLOAT_BYTES = 4
    }
}
