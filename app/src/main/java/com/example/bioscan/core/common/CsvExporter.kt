package com.example.bioscan.core.common

import android.content.Context
import java.io.File
import java.io.FileWriter

object CsvExporter {
    data class AttendanceCsvRecord(
        val eventId: String,
        val employeeId: String,
        val employeeName: String,
        val department: String,
        val eventType: String,
        val formattedTimestamp: String,
        val recognitionScore: Float,
        val livenessResult: String,
        val syncState: String,
        val deviceId: String
    )

    fun exportAttendanceToCsv(context: Context, records: List<AttendanceCsvRecord>): File {
        val exportDir = File(context.cacheDir, "csv_exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val csvFile = File(exportDir, "attendance_export_${System.currentTimeMillis()}.csv")
        val writer = FileWriter(csvFile)

        // Write Header
        writer.append("Event ID,Employee ID,Employee Name,Department,Event Type,Timestamp,Score,Liveness,Sync State,Device ID\n")

        // Write Rows
        for (r in records) {
            val sanitizedName = escapeCsv(r.employeeName)
            val sanitizedDept = escapeCsv(r.department)
            writer.append("${r.eventId},${r.employeeId},\"$sanitizedName\",\"$sanitizedDept\",${r.eventType},\"${r.formattedTimestamp}\",${"%.4f".format(r.recognitionScore)},${r.livenessResult},${r.syncState},${r.deviceId}\n")
        }

        writer.flush()
        writer.close()
        return csvFile
    }

    private fun escapeCsv(value: String): String {
        return value.replace("\"", "\"\"")
    }
}
