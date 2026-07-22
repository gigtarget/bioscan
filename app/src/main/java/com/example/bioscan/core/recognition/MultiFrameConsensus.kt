package com.example.bioscan.core.recognition

import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap


data class FrameCandidateSample(
    val employeeId: String,
    val score: Float,
    val secondBestScore: Float,
    val margin: Float,
    val supportingTemplateCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)

class MultiFrameConsensus {

    private val trackHistory = ConcurrentHashMap<Int, ArrayDeque<FrameCandidateSample>>()

    fun addSampleAndCheckConsensus(
        trackingId: Int?,
        candidateMatch: CandidateMatch
    ): CandidateMatch {
        val trackKey = trackingId ?: FALLBACK_TRACK_KEY
        val history = trackHistory.getOrPut(trackKey) { ArrayDeque() }
        val now = System.currentTimeMillis()

        synchronized(history) {
            while (history.isNotEmpty() && now - history.first().timestamp > MAX_SAMPLE_AGE_MS) {
                history.removeFirst()
            }

            if (candidateMatch.decision != IdentityDecision.MATCH || candidateMatch.employeeId.isBlank()) {
                history.clear()
                return candidateMatch
            }

            if (history.isNotEmpty() && history.last().employeeId != candidateMatch.employeeId) {
                history.clear()
            }

            history.addLast(
                FrameCandidateSample(
                    employeeId = candidateMatch.employeeId,
                    score = candidateMatch.similarityScore,
                    secondBestScore = candidateMatch.secondBestScore,
                    margin = candidateMatch.scoreMargin,
                    supportingTemplateCount = candidateMatch.supportingTemplateCount,
                    timestamp = now
                )
            )
            while (history.size > MAX_HISTORY_SIZE) history.removeFirst()

            if (history.size < REQUIRED_CONSENSUS_FRAMES) {
                return candidateMatch.copy(decision = IdentityDecision.AMBIGUOUS)
            }

            val recent = history.toList().takeLast(REQUIRED_CONSENSUS_FRAMES)
            val sameEmployee = recent.all { it.employeeId == candidateMatch.employeeId }
            val averageScore = recent.map { it.score }.average().toFloat()
            val averageSecond = recent.map { it.secondBestScore }.average().toFloat()
            val averageMargin = recent.map { it.margin }.average().toFloat()
            val minimumSupport = recent.minOf { it.supportingTemplateCount }

            return if (
                sameEmployee &&
                averageScore >= IdentityMatcher.DEFAULT_THRESHOLD &&
                averageMargin >= IdentityMatcher.DEFAULT_MIN_MARGIN
            ) {
                candidateMatch.copy(
                    similarityScore = averageScore,
                    secondBestScore = averageSecond,
                    scoreMargin = averageMargin,
                    supportingTemplateCount = minimumSupport,
                    decision = IdentityDecision.MATCH
                )
            } else {
                candidateMatch.copy(
                    similarityScore = averageScore,
                    secondBestScore = averageSecond,
                    scoreMargin = averageMargin,
                    supportingTemplateCount = minimumSupport,
                    decision = IdentityDecision.AMBIGUOUS
                )
            }
        }
    }

    fun clearTrack(trackingId: Int?) {
        trackHistory.remove(trackingId ?: FALLBACK_TRACK_KEY)
    }

    fun clearAll() {
        trackHistory.clear()
    }

    private companion object {
        const val REQUIRED_CONSENSUS_FRAMES = 4
        const val MAX_HISTORY_SIZE = 6
        const val MAX_SAMPLE_AGE_MS = 2_500L
        const val FALLBACK_TRACK_KEY = Int.MIN_VALUE
    }
}
