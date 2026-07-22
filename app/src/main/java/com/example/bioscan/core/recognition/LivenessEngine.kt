package com.example.bioscan.core.recognition

import com.example.bioscan.core.common.LivenessMode
import kotlin.math.abs
import kotlin.random.Random

enum class LivenessChallengeType {
    BLINK,
    TURN_LEFT,
    TURN_RIGHT,
    SMILE,
    LOOK_STRAIGHT
}

data class LivenessState(
    val currentChallenge: LivenessChallengeType,
    val isCompleted: Boolean,
    val progress: Float,
    val instructionMessage: String
)

class LivenessEngine {

    private val stateLock = Any()
    private var activeChallengeSequence = emptyList<LivenessChallengeType>()
    private var currentChallengeIndex = 0
    private var isBlinkDetected = false
    private var challengeStartTimeMs = 0L
    private var completedAtMs = 0L
    private var activeTrackingId: Int? = null
    private var consecutiveChallengeFrames = 0

    fun reset() = synchronized(stateLock) {
        activeChallengeSequence = emptyList()
        currentChallengeIndex = 0
        isBlinkDetected = false
        challengeStartTimeMs = 0L
        completedAtMs = 0L
        activeTrackingId = null
        consecutiveChallengeFrames = 0
    }

    fun startNewChallengeSequence(mode: LivenessMode, trackingId: Int? = null) = synchronized(stateLock) {
        startNewChallengeSequenceLocked(mode, trackingId)
    }

    fun evaluateLiveness(faceResult: DetectedFaceResult, mode: LivenessMode): LivenessState =
        synchronized(stateLock) {
            if (mode == LivenessMode.OFF) {
                return@synchronized completedState("Liveness disabled")
            }

            val now = System.currentTimeMillis()
            val trackingId = faceResult.trackingId
            val faceChanged = activeTrackingId != null && trackingId != null && activeTrackingId != trackingId

            if (activeChallengeSequence.isEmpty() || faceChanged) {
                startNewChallengeSequenceLocked(mode, trackingId)
            }

            if (currentChallengeIndex >= activeChallengeSequence.size) {
                if (completedAtMs == 0L) completedAtMs = now
                if (now - completedAtMs <= COMPLETION_HOLD_MS) {
                    return@synchronized completedState("Liveness verified")
                }
                startNewChallengeSequenceLocked(mode, trackingId)
            }

            if (now - challengeStartTimeMs > CHALLENGE_TIMEOUT_MS) {
                startNewChallengeSequenceLocked(mode, trackingId)
            }

            val currentChallenge = activeChallengeSequence.getOrNull(currentChallengeIndex)
                ?: return@synchronized completedState("Liveness verified")

            val challengePassed = when (currentChallenge) {
                LivenessChallengeType.LOOK_STRAIGHT -> {
                    abs(faceResult.headEulerAngleYaw) < 12f &&
                        abs(faceResult.headEulerAnglePitch) < 12f
                }

                LivenessChallengeType.BLINK -> {
                    val leftOpen = faceResult.leftEyeOpenProbability ?: 1f
                    val rightOpen = faceResult.rightEyeOpenProbability ?: 1f
                    if (leftOpen < 0.25f && rightOpen < 0.25f) {
                        isBlinkDetected = true
                    }
                    isBlinkDetected && leftOpen > 0.65f && rightOpen > 0.65f
                }

                LivenessChallengeType.TURN_LEFT -> faceResult.headEulerAngleYaw < -18f
                LivenessChallengeType.TURN_RIGHT -> faceResult.headEulerAngleYaw > 18f
                LivenessChallengeType.SMILE -> (faceResult.smilingProbability ?: 0f) > 0.6f
            }

            if (challengePassed) {
                consecutiveChallengeFrames++
            } else {
                consecutiveChallengeFrames = 0
            }

            val requiredFrames = if (currentChallenge == LivenessChallengeType.BLINK) 1 else 2
            if (consecutiveChallengeFrames >= requiredFrames) {
                advanceToNextChallengeLocked(now)
            }

            val completed = currentChallengeIndex >= activeChallengeSequence.size
            if (completed) {
                return@synchronized completedState("Liveness verified")
            }

            val nextChallenge = activeChallengeSequence[currentChallengeIndex]
            LivenessState(
                currentChallenge = nextChallenge,
                isCompleted = false,
                progress = currentChallengeIndex.toFloat() / activeChallengeSequence.size.toFloat(),
                instructionMessage = instructionFor(nextChallenge)
            )
        }

    private fun startNewChallengeSequenceLocked(mode: LivenessMode, trackingId: Int?) {
        activeChallengeSequence = when (mode) {
            LivenessMode.OFF -> listOf(LivenessChallengeType.LOOK_STRAIGHT)
            LivenessMode.STANDARD -> listOf(
                LivenessChallengeType.LOOK_STRAIGHT,
                if (Random.nextBoolean()) LivenessChallengeType.BLINK else LivenessChallengeType.TURN_LEFT
            )
            LivenessMode.STRICT -> listOf(
                LivenessChallengeType.LOOK_STRAIGHT,
                LivenessChallengeType.BLINK,
                if (Random.nextBoolean()) LivenessChallengeType.TURN_LEFT else LivenessChallengeType.TURN_RIGHT
            )
        }
        currentChallengeIndex = 0
        isBlinkDetected = false
        challengeStartTimeMs = System.currentTimeMillis()
        completedAtMs = 0L
        activeTrackingId = trackingId
        consecutiveChallengeFrames = 0
    }

    private fun advanceToNextChallengeLocked(now: Long) {
        currentChallengeIndex = (currentChallengeIndex + 1).coerceAtMost(activeChallengeSequence.size)
        isBlinkDetected = false
        consecutiveChallengeFrames = 0
        challengeStartTimeMs = now
        if (currentChallengeIndex >= activeChallengeSequence.size) {
            completedAtMs = now
        }
    }

    private fun completedState(message: String) = LivenessState(
        currentChallenge = LivenessChallengeType.LOOK_STRAIGHT,
        isCompleted = true,
        progress = 1f,
        instructionMessage = message
    )

    private fun instructionFor(challenge: LivenessChallengeType): String = when (challenge) {
        LivenessChallengeType.LOOK_STRAIGHT -> "Look straight into the camera"
        LivenessChallengeType.BLINK -> "Blink your eyes"
        LivenessChallengeType.TURN_LEFT -> "Turn your head slightly left"
        LivenessChallengeType.TURN_RIGHT -> "Turn your head slightly right"
        LivenessChallengeType.SMILE -> "Smile"
    }

    private companion object {
        const val CHALLENGE_TIMEOUT_MS = 8_000L
        const val COMPLETION_HOLD_MS = 1_500L
    }
}
