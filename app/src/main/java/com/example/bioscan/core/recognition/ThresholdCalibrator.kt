package com.example.bioscan.core.recognition

data class CalibrationResult(
    val recommendedThreshold: Float,
    val recommendedMargin: Float,
    val estimatedFar: Float,
    val estimatedFrr: Float,
    val samePersonPairCount: Int,
    val diffPersonPairCount: Int,
    val mostConfusedPairs: List<Pair<String, String>>
)

class ThresholdCalibrator(private val embeddingGenerator: IEmbeddingGenerator) {

    fun calibrateThresholds(
        employeeEmbeddings: List<Pair<String, FloatArray>> // Pair<EmployeeId, FloatArray>
    ): CalibrationResult {
        if (employeeEmbeddings.isEmpty()) {
            return CalibrationResult(0.72f, 0.08f, 0.001f, 0.01f, 0, 0, emptyList())
        }

        val samePersonSimilarities = mutableListOf<Float>()
        val diffPersonSimilarities = mutableListOf<Float>()
        val confusedPairs = mutableListOf<Pair<String, String>>()

        val size = employeeEmbeddings.size
        for (i in 0 until size) {
            for (j in i + 1 until size) {
                val p1 = employeeEmbeddings[i]
                val p2 = employeeEmbeddings[j]
                val sim = embeddingGenerator.calculateCosineSimilarity(p1.second, p2.second)

                if (p1.first == p2.first) {
                    samePersonSimilarities.add(sim)
                } else {
                    diffPersonSimilarities.add(sim)
                    if (sim > 0.65f) {
                        confusedPairs.add(Pair(p1.first, p2.first))
                    }
                }
            }
        }

        val sortedDiff = diffPersonSimilarities.sortedDescending()
        val maxDiffSim = if (sortedDiff.isNotEmpty()) sortedDiff.first() else 0.5f

        val recommendedThreshold = (maxDiffSim + 0.12f).coerceIn(0.68f, 0.88f)
        val recommendedMargin = 0.08f

        val farCount = diffPersonSimilarities.count { it >= recommendedThreshold }
        val frrCount = samePersonSimilarities.count { it < recommendedThreshold }

        val far = if (diffPersonSimilarities.isNotEmpty()) farCount.toFloat() / diffPersonSimilarities.size else 0.001f
        val frr = if (samePersonSimilarities.isNotEmpty()) frrCount.toFloat() / samePersonSimilarities.size else 0.01f

        return CalibrationResult(
            recommendedThreshold = recommendedThreshold,
            recommendedMargin = recommendedMargin,
            estimatedFar = far,
            estimatedFrr = frr,
            samePersonPairCount = samePersonSimilarities.size,
            diffPersonPairCount = diffPersonSimilarities.size,
            mostConfusedPairs = confusedPairs.take(5)
        )
    }
}
