package com.example.bioscan.domain.rules

import com.example.bioscan.core.common.TerminalDirectionMode
import com.example.bioscan.core.common.TimeUtils
import com.example.bioscan.core.database.dao.AttendanceDao
import com.example.bioscan.core.database.entity.AttendanceEventEntity
import com.example.bioscan.core.database.entity.AttendanceSessionEntity
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID

sealed class AttendanceRuleResult {
    data class Success(
        val event: AttendanceEventEntity,
        val session: AttendanceSessionEntity,
        val employeeId: String,
        val eventType: String,
        val message: String,
        val updatedExistingClockOut: Boolean = false
    ) : AttendanceRuleResult()

    data class CooldownActive(val remainingSeconds: Int) : AttendanceRuleResult()
    data class Error(val reason: String) : AttendanceRuleResult()
}

/**
 * Produces a single daily attendance summary per employee:
 *
 * - First accepted scan of the local day becomes CLOCK_IN.
 * - Second accepted scan becomes CLOCK_OUT.
 * - Every later accepted scan replaces the CLOCK_OUT timestamp and evidence.
 * - The database is compacted transactionally to at most two events and one session per day.
 */
class AttendanceRulesEngine(private val attendanceDao: AttendanceDao) {

    @Suppress("UNUSED_PARAMETER")
    suspend fun processAttendanceDecision(
        employeeId: String,
        recognitionScore: Float,
        secondBestScore: Float,
        scoreMargin: Float,
        qualityScore: Float,
        modelVersion: String,
        terminalMode: TerminalDirectionMode = TerminalDirectionMode.SMART_AUTO,
        cooldownSeconds: Int = 15,
        auditThumbnailPath: String? = null,
        deviceId: String = "LOCAL_KIOSK_01",
        timeZoneId: String = TimeZone.getDefault().id
    ): AttendanceRuleResult {
        val now = System.currentTimeMillis()
        val latestEvent = attendanceDao.getLatestEventForEmployee(employeeId)
        if (latestEvent != null) {
            val elapsedSeconds = ((now - latestEvent.timestamp) / 1000L).coerceAtLeast(0L)
            if (elapsedSeconds < cooldownSeconds) {
                return AttendanceRuleResult.CooldownActive(
                    remainingSeconds = (cooldownSeconds - elapsedSeconds).toInt()
                )
            }
        }

        val (dayStart, nextDayStart) = localDayBounds(now, timeZoneId)
        val existingEvents = attendanceDao.getDailyEventsForEmployee(
            employeeId = employeeId,
            startTimeMs = dayStart,
            endTimeMs = nextDayStart
        )
        val existingSession = attendanceDao.getDailySessionForEmployee(
            employeeId = employeeId,
            startTimeMs = dayStart,
            endTimeMs = nextDayStart
        )

        val clockInEvent = existingEvents.firstOrNull()?.copy(
            eventType = "CLOCK_IN",
            syncState = if (existingEvents.first().eventType == "CLOCK_IN") {
                existingEvents.first().syncState
            } else {
                "PENDING"
            }
        ) ?: newEvent(
            employeeId = employeeId,
            eventType = "CLOCK_IN",
            timestamp = now,
            deviceId = deviceId,
            recognitionScore = recognitionScore,
            secondBestScore = secondBestScore,
            scoreMargin = scoreMargin,
            qualityScore = qualityScore,
            modelVersion = modelVersion,
            auditThumbnailPath = auditThumbnailPath
        )

        val acceptedEvents: List<AttendanceEventEntity>
        val session: AttendanceSessionEntity
        val eventToConfirm: AttendanceEventEntity
        val message: String
        val didUpdateClockOut: Boolean

        if (existingEvents.isEmpty()) {
            acceptedEvents = listOf(clockInEvent)
            session = AttendanceSessionEntity(
                sessionId = existingSession?.sessionId ?: UUID.randomUUID().toString(),
                employeeId = employeeId,
                clockInTime = clockInEvent.timestamp,
                clockOutTime = null,
                status = "OPEN",
                totalDurationMinutes = 0L,
                updatedAt = now
            )
            eventToConfirm = clockInEvent
            message = "Day started at ${formatTime(now, timeZoneId)}"
            didUpdateClockOut = false
        } else {
            val previousClockOut = existingEvents.drop(1).maxByOrNull { it.timestamp }
            val clockOutEvent = (previousClockOut ?: newEvent(
                employeeId = employeeId,
                eventType = "CLOCK_OUT",
                timestamp = now,
                deviceId = deviceId,
                recognitionScore = recognitionScore,
                secondBestScore = secondBestScore,
                scoreMargin = scoreMargin,
                qualityScore = qualityScore,
                modelVersion = modelVersion,
                auditThumbnailPath = auditThumbnailPath
            )).copy(
                employeeId = employeeId,
                eventType = "CLOCK_OUT",
                timestamp = now,
                deviceId = deviceId,
                recognitionScore = recognitionScore,
                secondBestScore = secondBestScore,
                scoreMargin = scoreMargin,
                livenessResult = "NOT_REQUIRED",
                qualityScore = qualityScore,
                modelVersion = modelVersion,
                syncState = "PENDING",
                syncAttempts = 0,
                lastSyncError = null,
                auditThumbnailPath = auditThumbnailPath,
                isCorrected = false,
                correctionReason = null,
                correctedByAdminId = null
            )

            acceptedEvents = listOf(clockInEvent, clockOutEvent)
            val durationMinutes = TimeUtils.calculateDurationMinutes(clockInEvent.timestamp, now)
            session = AttendanceSessionEntity(
                sessionId = existingSession?.sessionId ?: UUID.randomUUID().toString(),
                employeeId = employeeId,
                clockInTime = clockInEvent.timestamp,
                clockOutTime = now,
                status = "COMPLETED",
                totalDurationMinutes = durationMinutes,
                updatedAt = now
            )
            eventToConfirm = clockOutEvent
            didUpdateClockOut = existingEvents.size >= 2
            message = if (didUpdateClockOut) {
                "Latest departure updated to ${formatTime(now, timeZoneId)}"
            } else {
                "Departure saved at ${formatTime(now, timeZoneId)}"
            }
        }

        attendanceDao.replaceDailyAttendance(
            employeeId = employeeId,
            startTimeMs = dayStart,
            endTimeMs = nextDayStart,
            events = acceptedEvents,
            session = session,
            updatedAt = now
        )

        return AttendanceRuleResult.Success(
            event = eventToConfirm,
            session = session,
            employeeId = employeeId,
            eventType = eventToConfirm.eventType,
            message = message,
            updatedExistingClockOut = didUpdateClockOut
        )
    }

    private fun newEvent(
        employeeId: String,
        eventType: String,
        timestamp: Long,
        deviceId: String,
        recognitionScore: Float,
        secondBestScore: Float,
        scoreMargin: Float,
        qualityScore: Float,
        modelVersion: String,
        auditThumbnailPath: String?
    ) = AttendanceEventEntity(
        eventId = UUID.randomUUID().toString(),
        employeeId = employeeId,
        eventType = eventType,
        timestamp = timestamp,
        deviceId = deviceId,
        recognitionScore = recognitionScore,
        secondBestScore = secondBestScore,
        scoreMargin = scoreMargin,
        livenessResult = "NOT_REQUIRED",
        qualityScore = qualityScore,
        modelVersion = modelVersion,
        syncState = "PENDING",
        auditThumbnailPath = auditThumbnailPath
    )

    private fun localDayBounds(timestampMs: Long, timeZoneId: String): Pair<Long, Long> {
        val timeZone = TimeZone.getTimeZone(timeZoneId)
        val start = Calendar.getInstance(timeZone).apply {
            timeInMillis = timestampMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        return start.timeInMillis to end.timeInMillis
    }

    private fun formatTime(timestampMs: Long, timeZoneId: String): String =
        TimeUtils.formatLocalTime(timestampMs, "hh:mm a", timeZoneId)
}
