package com.example.bioscan.core.recognition

import java.util.concurrent.ConcurrentHashMap

data class FrameCandidateSample(
    val employeeId: String,
    val score: Float,
    val timestamp: Long = System.currentTimeMillis()
)

class MultiFrameConsensus {

    // TrackId -> List of recent frame samples
    private val trackHistory = ConcurrentHashMap<Int, MutableList<FrameCandidateSample>>()
    private val requiredConsensusCount = 1
    private val maxSampleAgeMs = 3000L

    fun addSampleAndCheckConsensus(
        trackingId: Int?,
        candidateMatch: CandidateMatch
    ): CandidateMatch {
        if (trackingId == null) return candidateMatch
        if (candidateMatch.decision != IdentityDecision.MATCH) return candidateMatch

        val history = trackHistory.getOrPut(trackingId) { mutableListOf() }
        val now = System.currentTimeMillis()

        synchronized(history) {
            history.removeAll { now - it.timestamp > maxSampleAgeMs }
            history.add(FrameCandidateSample(candidateMatch.employeeId, candidateMatch.similarityScore, now))

            val countForSameId = history.count { it.employeeId == candidateMatch.employeeId }

            return if (countForSameId >= requiredConsensusCount) {
                candidateMatch
            } else {
                candidateMatch.copy(decision = IdentityDecision.AMBIGUOUS)
            }
        }
    }

    fun clearTrack(trackingId: Int) {
        trackHistory.remove(trackingId)
    }

    fun clearAll() {
        trackHistory.clear()
    }
}
