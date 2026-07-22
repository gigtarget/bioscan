package com.example.bioscan.core.recognition

import com.example.bioscan.core.common.CryptoUtils


data class CandidateMatch(
    val employeeId: String,
    val similarityScore: Float,
    val secondBestScore: Float,
    val scoreMargin: Float,
    val decision: IdentityDecision,
    val supportingTemplateCount: Int = 0
)

enum class IdentityDecision {
    MATCH,
    UNKNOWN,
    AMBIGUOUS,
    LOW_QUALITY,
    LIVENESS_FAILED,
    MULTIPLE_FACES,
    COOLDOWN,
    ENGINE_ERROR
}

data class EncryptedTemplateRecord(
    val employeeId: String,
    val encryptedEmbedding: String,
    val modelVersion: String,
    val qualityScore: Float,
    val isCentroid: Boolean
)

class IdentityMatcher(private val embeddingGenerator: IEmbeddingGenerator) {

    private data class EmployeeTemplateInMemory(
        val employeeId: String,
        val embedding: FloatArray,
        val qualityScore: Float,
        val isCentroid: Boolean
    )

    private data class EmployeeScore(
        val employeeId: String,
        val score: Float,
        val supportingTemplates: Int
    )

    private val cachedTemplates = mutableListOf<EmployeeTemplateInMemory>()

    fun updateTemplateIndex(templates: List<EncryptedTemplateRecord>) {
        val decodedTemplates = templates.mapNotNull { template ->
            if (template.modelVersion != embeddingGenerator.modelVersion) return@mapNotNull null

            val embedding = CryptoUtils.decryptEmbedding(template.encryptedEmbedding)
                ?: return@mapNotNull null
            if (embedding.size != embeddingGenerator.embeddingDimension || embedding.any { !it.isFinite() }) {
                return@mapNotNull null
            }

            EmployeeTemplateInMemory(
                employeeId = template.employeeId,
                embedding = embedding,
                qualityScore = template.qualityScore.coerceIn(0.25f, 1f),
                isCentroid = template.isCentroid
            )
        }

        synchronized(cachedTemplates) {
            cachedTemplates.clear()
            cachedTemplates.addAll(decodedTemplates)
        }
    }

    fun findMatch(
        queryEmbedding: FloatArray,
        threshold: Float = DEFAULT_THRESHOLD,
        minMargin: Float = DEFAULT_MIN_MARGIN
    ): CandidateMatch {
        if (
            queryEmbedding.size != embeddingGenerator.embeddingDimension ||
            queryEmbedding.any { !it.isFinite() }
        ) {
            return unknown()
        }

        val templates = synchronized(cachedTemplates) { cachedTemplates.toList() }
        if (templates.isEmpty()) return unknown()

        val employeeScores = templates
            .groupBy { it.employeeId }
            .mapNotNull { (employeeId, employeeTemplates) ->
                scoreEmployee(employeeId, employeeTemplates, queryEmbedding, threshold)
            }
            .sortedByDescending { it.score }

        val best = employeeScores.firstOrNull() ?: return unknown()
        val secondBestScore = employeeScores.getOrNull(1)?.score ?: -1f
        val margin = best.score - secondBestScore

        if (best.supportingTemplates < MIN_SUPPORTING_TEMPLATES || best.score < threshold) {
            return CandidateMatch(
                employeeId = "",
                similarityScore = best.score,
                secondBestScore = secondBestScore,
                scoreMargin = margin,
                decision = IdentityDecision.UNKNOWN,
                supportingTemplateCount = best.supportingTemplates
            )
        }

        if (margin < minMargin) {
            return CandidateMatch(
                employeeId = "",
                similarityScore = best.score,
                secondBestScore = secondBestScore,
                scoreMargin = margin,
                decision = IdentityDecision.AMBIGUOUS,
                supportingTemplateCount = best.supportingTemplates
            )
        }

        return CandidateMatch(
            employeeId = best.employeeId,
            similarityScore = best.score,
            secondBestScore = secondBestScore,
            scoreMargin = margin,
            decision = IdentityDecision.MATCH,
            supportingTemplateCount = best.supportingTemplates
        )
    }

    private fun scoreEmployee(
        employeeId: String,
        templates: List<EmployeeTemplateInMemory>,
        queryEmbedding: FloatArray,
        threshold: Float
    ): EmployeeScore? {
        if (templates.size < MIN_SUPPORTING_TEMPLATES) return null

        val scored = templates.map { template ->
            val similarity = embeddingGenerator.calculateCosineSimilarity(
                queryEmbedding,
                template.embedding
            )
            Triple(similarity, template.qualityScore, template.isCentroid)
        }

        val sampleScores = scored
            .filterNot { it.third }
            .sortedByDescending { it.first }
        val centroidScore = scored
            .filter { it.third }
            .maxOfOrNull { it.first }

        val supportingThreshold = (threshold - SUPPORT_TOLERANCE).coerceAtLeast(0.60f)
        val supportingTemplates = scored.count { it.first >= supportingThreshold }

        val topSamples = sampleScores.take(TOP_SAMPLE_COUNT)
        if (topSamples.isEmpty()) return null

        val qualityWeightSum = topSamples.sumOf { it.second.toDouble() }.toFloat()
            .coerceAtLeast(0.001f)
        val weightedSampleScore = topSamples.sumOf { (score, quality, _) ->
            (score * quality).toDouble()
        }.toFloat() / qualityWeightSum

        val combinedScore = if (centroidScore != null) {
            centroidScore * CENTROID_WEIGHT + weightedSampleScore * SAMPLE_WEIGHT
        } else {
            weightedSampleScore
        }

        return EmployeeScore(
            employeeId = employeeId,
            score = combinedScore.coerceIn(-1f, 1f),
            supportingTemplates = supportingTemplates
        )
    }

    private fun unknown() = CandidateMatch(
        employeeId = "",
        similarityScore = -1f,
        secondBestScore = -1f,
        scoreMargin = 0f,
        decision = IdentityDecision.UNKNOWN,
        supportingTemplateCount = 0
    )

    companion object {
        const val DEFAULT_THRESHOLD = 0.80f
        const val DEFAULT_MIN_MARGIN = 0.12f
        const val DUPLICATE_THRESHOLD = 0.86f
        const val DUPLICATE_MIN_MARGIN = 0.08f

        private const val MIN_SUPPORTING_TEMPLATES = 3
        private const val TOP_SAMPLE_COUNT = 4
        private const val SUPPORT_TOLERANCE = 0.08f
        private const val CENTROID_WEIGHT = 0.60f
        private const val SAMPLE_WEIGHT = 0.40f
    }
}
