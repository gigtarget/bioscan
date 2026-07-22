package com.example.bioscan.core.recognition

import com.example.bioscan.core.common.CryptoUtils

data class CandidateMatch(
    val employeeId: String,
    val similarityScore: Float,
    val secondBestScore: Float,
    val scoreMargin: Float,
    val decision: IdentityDecision
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

class IdentityMatcher(private val embeddingGenerator: IEmbeddingGenerator) {

    data class EmployeeTemplateInMemory(
        val employeeId: String,
        val floatArrayEmbedding: FloatArray
    )

    private val cachedTemplates = mutableListOf<EmployeeTemplateInMemory>()

    fun updateTemplateIndex(templates: List<Pair<String, String>>) { // Pair<EmployeeId, Base64EncryptedEmbedding>
        synchronized(cachedTemplates) {
            cachedTemplates.clear()
            for (t in templates) {
                val floats = CryptoUtils.decryptEmbedding(t.second)
                if (floats != null) {
                    cachedTemplates.add(EmployeeTemplateInMemory(t.first, floats))
                }
            }
        }
    }

    fun findMatch(
        queryEmbedding: FloatArray,
        threshold: Float = 0.60f,
        minMargin: Float = 0.04f
    ): CandidateMatch {
        val listCopy: List<EmployeeTemplateInMemory>
        synchronized(cachedTemplates) {
            listCopy = ArrayList(cachedTemplates)
        }

        if (listCopy.isEmpty()) {
            return CandidateMatch(
                employeeId = "",
                similarityScore = 0f,
                secondBestScore = 0f,
                scoreMargin = 0f,
                decision = IdentityDecision.UNKNOWN
            )
        }

        // Group scores per employeeId (take best score if employee has multiple templates)
        val employeeScores = mutableMapOf<String, Float>()

        for (item in listCopy) {
            val sim = embeddingGenerator.calculateCosineSimilarity(queryEmbedding, item.floatArrayEmbedding)
            val currentBest = employeeScores[item.employeeId] ?: -1f
            if (sim > currentBest) {
                employeeScores[item.employeeId] = sim
            }
        }

        val sorted = employeeScores.entries.sortedByDescending { it.value }

        val best = sorted.firstOrNull() ?: return CandidateMatch("", 0f, 0f, 0f, IdentityDecision.UNKNOWN)
        val bestScore = best.value
        val bestEmployeeId = best.key

        val secondBestScore = if (sorted.size > 1) sorted[1].value else 0f
        val margin = bestScore - secondBestScore

        if (bestScore < threshold) {
            return CandidateMatch(
                employeeId = bestEmployeeId,
                similarityScore = bestScore,
                secondBestScore = secondBestScore,
                scoreMargin = margin,
                decision = IdentityDecision.UNKNOWN
            )
        }

        if (margin < minMargin) {
            return CandidateMatch(
                employeeId = bestEmployeeId,
                similarityScore = bestScore,
                secondBestScore = secondBestScore,
                scoreMargin = margin,
                decision = IdentityDecision.AMBIGUOUS
            )
        }

        return CandidateMatch(
            employeeId = bestEmployeeId,
            similarityScore = bestScore,
            secondBestScore = secondBestScore,
            scoreMargin = margin,
            decision = IdentityDecision.MATCH
        )
    }
}
