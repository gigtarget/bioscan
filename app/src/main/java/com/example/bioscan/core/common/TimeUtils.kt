package com.example.bioscan.core.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TimeUtils {
    fun getCurrentUtcTimeMs(): Long = System.currentTimeMillis()

    fun formatLocalTime(timestampMs: Long, pattern: String = "yyyy-MM-dd HH:mm:ss", timeZoneId: String? = null): String {
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        if (!timeZoneId.isNull_Blank()) {
            sdf.timeZone = TimeZone.getTimeZone(timeZoneId)
        } else {
            sdf.timeZone = TimeZone.getDefault()
        }
        return sdf.format(Date(timestampMs))
    }

    fun formatHourMinute(timestampMs: Long): String {
        return formatLocalTime(timestampMs, "hh:mm a")
    }

    fun formatDateOnly(timestampMs: Long): String {
        return formatLocalTime(timestampMs, "EEE, MMM dd, yyyy")
    }

    fun calculateDurationMinutes(startMs: Long, endMs: Long): Long {
        if (endMs <= startMs) return 0L
        return (endMs - startMs) / (1000 * 60)
    }

    fun formatDurationHoursMinutes(minutes: Long): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
    }

    private fun String?.isNull_Blank(): Boolean = this.isNullOrBlank()
}
