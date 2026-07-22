package com.example.bioscan.domain.rules

import com.example.bioscan.core.common.TerminalDirectionMode
import com.example.bioscan.core.common.TimeUtils
import com.example.bioscan.core.database.dao.AttendanceDao
import com.example.bioscan.core.database.entity.AttendanceEventEntity
import com.example.bioscan.core.database.entity.AttendanceSessionEntity
import java.util.UUID

sealed class AttendanceRuleResult {
    data class Success(
        val event: AttendanceEventEntity,
        val session: AttendanceSessionEntity,
        val employeeId: String,
        val eventType: String,
        val message: String
    ) : AttendanceRuleResult()

    data class CooldownActive(val remainingSeconds: Int) : AttendanceRuleResult()
    data class Error(val reason: String) : AttendanceRuleResult()
}

class AttendanceRulesEngine(private val attendanceDao: AttendanceDao) {

    suspend fun processAttendanceDecision(
        employeeId: String,
        recognitionScore: Float,
        secondBestScore: Float,
        scoreMargin: Float,
        qualityScore: Float,
        modelVersion: String,
        terminalMode: TerminalDirectionMode = TerminalDirectionMode.SMART_AUTO,
        cooldownSeconds: Int = 15,
        auditThumbnailPath: String? = null
    ): AttendanceRuleResult {
        val now = System.currentTimeMillis()

        // 1. Check cooldown
        val latestEvent = attendanceDao.getLatestEventForEmployee(employeeId)
        if (latestEvent != null) {
            val elapsedSec = (now - latestEvent.timestamp) / 1000
            if (elapsedSec < cooldownSeconds) {
                return AttendanceRuleResult.CooldownActive(remainingSeconds = (cooldownSeconds - elapsedSec).toInt())
            }
        }

        // 2. Lookup open session
        val openSession = attendanceDao.getOpenSessionForEmployee(employeeId)

        val eventType = when (terminalMode) {
            TerminalDirectionMode.CHECK_IN_ONLY -> "CLOCK_IN"
            TerminalDirectionMode.CHECK_OUT_ONLY -> "CLOCK_OUT"
            TerminalDirectionMode.SMART_AUTO, TerminalDirectionMode.MANUAL_PROMPT -> {
                if (openSession == null) "CLOCK_IN" else "CLOCK_OUT"
            }
        }

        val eventId = UUID.randomUUID().toString()
        val event = AttendanceEventEntity(
            eventId = eventId,
            employeeId = employeeId,
            eventType = eventType,
            timestamp = now,
            deviceId = "LOCAL_KIOSK_01",
            recognitionScore = recognitionScore,
            secondBestScore = secondBestScore,
            scoreMargin = scoreMargin,
            livenessResult = "PASSED",
            qualityScore = qualityScore,
            modelVersion = modelVersion,
            syncState = "PENDING",
            auditThumbnailPath = auditThumbnailPath
        )

        val updatedOrCreateSession = if (eventType == "CLOCK_IN") {
            AttendanceSessionEntity(
                sessionId = UUID.randomUUID().toString(),
                employeeId = employeeId,
                clockInTime = now,
                clockOutTime = null,
                status = "OPEN",
                totalDurationMinutes = 0L,
                updatedAt = now
            )
        } else {
            val clockInTime = openSession?.clockInTime ?: (now - 8 * 3600 * 1000)
            val durationMin = TimeUtils.calculateDurationMinutes(clockInTime, now)
            (openSession ?: AttendanceSessionEntity(
                sessionId = UUID.randomUUID().toString(),
                employeeId = employeeId,
                clockInTime = clockInTime,
                clockOutTime = now,
                status = "COMPLETED",
                totalDurationMinutes = durationMin,
                updatedAt = now
            )).copy(
                clockOutTime = now,
                status = "COMPLETED",
                totalDurationMinutes = durationMin,
                updatedAt = now
            )
        }

        attendanceDao.recordAttendanceAndSession(event, updatedOrCreateSession)

        val msg = if (eventType == "CLOCK_IN") "Welcome! Clocked In at ${TimeUtils.formatHourMinute(now)}" else "Goodbye! Clocked Out at ${TimeUtils.formatHourMinute(now)}"

        return AttendanceRuleResult.Success(
            event = event,
            session = updatedOrCreateSession,
            employeeId = employeeId,
            eventType = eventType,
            message = msg
        )
    }
}
