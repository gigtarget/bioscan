package com.example.bioscan.core.recognition

import com.example.bioscan.core.common.LivenessMode
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
    val progress: Float, // 0.0 to 1.0
    val instructionMessage: String
)

class LivenessEngine {

    private var activeChallengeSequence = listOf<LivenessChallengeType>()
    private var currentChallengeIndex = 0
    private var isBlinkDetected = false
    private var challengeStartTimeMs = 0L

    fun startNewChallengeSequence(mode: LivenessMode) {
        challengeStartTimeMs = System.currentTimeMillis()
        isBlinkDetected = false
        currentChallengeIndex = 0

        activeChallengeSequence = when (mode) {
            LivenessMode.OFF -> listOf(LivenessChallengeType.LOOK_STRAIGHT)
            LivenessMode.STANDARD -> {
                val challenges = mutableListOf(LivenessChallengeType.LOOK_STRAIGHT)
                val randomChallenge = if (Random.nextBoolean()) LivenessChallengeType.BLINK else LivenessChallengeType.TURN_LEFT
                challenges.add(randomChallenge)
                challenges
            }
            LivenessMode.STRICT -> {
                listOf(
                    LivenessChallengeType.LOOK_STRAIGHT,
                    LivenessChallengeType.BLINK,
                    if (Random.nextBoolean()) LivenessChallengeType.TURN_LEFT else LivenessChallengeType.TURN_RIGHT
                )
            }
        }
    }

    fun evaluateLiveness(faceResult: DetectedFaceResult, mode: LivenessMode): LivenessState {
        if (mode == LivenessMode.OFF) {
            return LivenessState(LivenessChallengeType.LOOK_STRAIGHT, true, 1.0f, "Liveness Off")
        }

        if (activeChallengeSequence.isEmpty()) {
            startNewChallengeSequence(mode)
        }

        val currentChallenge = activeChallengeSequence[currentChallengeIndex]

        val timeoutMs = 8000L
        if (System.currentTimeMillis() - challengeStartTimeMs > timeoutMs) {
            startNewChallengeSequence(mode) // Reset on timeout
        }

        when (currentChallenge) {
            LivenessChallengeType.LOOK_STRAIGHT -> {
                val yaw = Math.abs(faceResult.headEulerAngleYaw)
                val pitch = Math.abs(faceResult.headEulerAnglePitch)
                if (yaw < 12f && pitch < 12f) {
                    advanceToNextChallenge()
                } else {
                    return LivenessState(currentChallenge, false, 0.3f, "Look straight into camera")
                }
            }
            LivenessChallengeType.BLINK -> {
                val leftOpen = faceResult.leftEyeOpenProbability ?: 1.0f
                val rightOpen = faceResult.rightEyeOpenProbability ?: 1.0f

                // Detect eye closure followed by opening
                if (leftOpen < 0.25f && rightOpen < 0.25f) {
                    isBlinkDetected = true
                }
                if (isBlinkDetected && leftOpen > 0.65f && rightOpen > 0.65f) {
                    advanceToNextChallenge()
                } else {
                    return LivenessState(currentChallenge, false, 0.5f, "Please blink your eyes")
                }
            }
            LivenessChallengeType.TURN_LEFT -> {
                val yaw = faceResult.headEulerAngleYaw
                if (yaw < -18f) {
                    advanceToNextChallenge()
                } else {
                    return LivenessState(currentChallenge, false, 0.5f, "Turn head slightly left")
                }
            }
            LivenessChallengeType.TURN_RIGHT -> {
                val yaw = faceResult.headEulerAngleYaw
                if (yaw > 18f) {
                    advanceToNextChallenge()
                } else {
                    return LivenessState(currentChallenge, false, 0.5f, "Turn head slightly right")
                }
            }
            LivenessChallengeType.SMILE -> {
                val smile = faceResult.smilingProbability ?: 0f
                if (smile > 0.6f) {
                    advanceToNextChallenge()
                } else {
                    return LivenessState(currentChallenge, false, 0.5f, "Please smile")
                }
            }
        }

        val completed = currentChallengeIndex >= activeChallengeSequence.size
        val progress = (currentChallengeIndex.toFloat() / activeChallengeSequence.size.toFloat()).coerceIn(0f, 1f)
        val msg = if (completed) "Liveness verified!" else "Follow prompt..."

        return LivenessState(
            currentChallenge = if (completed) LivenessChallengeType.LOOK_STRAIGHT else activeChallengeSequence[currentChallengeIndex],
            isCompleted = completed,
            progress = progress,
            instructionMessage = msg
        )
    }

    private fun advanceToNextChallenge() {
        currentChallengeIndex++
    }
}
