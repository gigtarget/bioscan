package com.example.bioscan

import com.example.bioscan.core.recognition.EmbeddingGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EmbeddingGeneratorTest {

    private lateinit var embeddingGenerator: EmbeddingGenerator

    @Before
    fun setUp() {
        embeddingGenerator = EmbeddingGenerator()
    }

    @Test
    fun testCosineSimilarityIdenticalVectors() {
        val vec = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)
        val sim = embeddingGenerator.calculateCosineSimilarity(vec, vec)
        assertEquals(1.0f, sim, 0.001f)
    }

    @Test
    fun testCosineSimilarityOrthogonalVectors() {
        val vec1 = floatArrayOf(1.0f, 0.0f)
        val vec2 = floatArrayOf(0.0f, 1.0f)
        val sim = embeddingGenerator.calculateCosineSimilarity(vec1, vec2)
        assertEquals(0.0f, sim, 0.001f)
    }

    @Test
    fun testEuclideanDistance() {
        val vec1 = floatArrayOf(0.0f, 0.0f)
        val vec2 = floatArrayOf(3.0f, 4.0f)
        val dist = embeddingGenerator.calculateEuclideanDistance(vec1, vec2)
        assertEquals(5.0f, dist, 0.001f)
    }
}
