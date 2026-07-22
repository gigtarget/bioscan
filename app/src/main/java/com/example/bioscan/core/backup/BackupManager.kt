package com.example.bioscan.core.backup

import android.content.Context
import com.example.bioscan.core.common.CryptoUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupManifest(
    val appVersion: String = "1.0",
    val dbVersion: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val dbChecksum: String
)

class BackupManager(private val context: Context) {

    fun createBackupZip(outputFile: File): Boolean {
        return try {
            val dbFile = context.getDatabasePath("bioscan_kiosk.db")
            if (!dbFile.exists()) return false

            val dbBytes = dbFile.readBytes()
            val dbChecksum = CryptoUtils.sha256(dbBytes)

            val zipOut = ZipOutputStream(FileOutputStream(outputFile))

            // Add DB entry
            zipOut.putNextEntry(ZipEntry("bioscan_kiosk.db"))
            zipOut.write(dbBytes)
            zipOut.closeEntry()

            // Add Manifest entry
            val manifestJson = """
                {
                  "appVersion": "1.0",
                  "dbVersion": 1,
                  "timestamp": ${System.currentTimeMillis()},
                  "dbChecksum": "$dbChecksum"
                }
            """.trimIndent()
            zipOut.putNextEntry(ZipEntry("manifest.json"))
            zipOut.write(manifestJson.toByteArray())
            zipOut.closeEntry()

            zipOut.flush()
            zipOut.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun restoreFromBackupZip(backupZipFile: File): Boolean {
        return try {
            if (!backupZipFile.exists()) return false

            val zipIn = ZipInputStream(FileInputStream(backupZipFile))
            var entry = zipIn.nextEntry

            var restoredDbBytes: ByteArray? = null

            while (entry != null) {
                if (entry.name == "bioscan_kiosk.db") {
                    restoredDbBytes = zipIn.readBytes()
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
            zipIn.close()

            if (restoredDbBytes != null) {
                val dbFile = context.getDatabasePath("bioscan_kiosk.db")
                FileOutputStream(dbFile).use { it.write(restoredDbBytes) }
                return true
            }
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
