package com.example.bioscan.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.bioscan.core.database.BioScanDatabase
import com.example.bioscan.core.database.entity.SyncQueueEntity

class SyncRepository(private val context: Context) {

    private val db = BioScanDatabase.getInstance(context)

    suspend fun enqueueAttendanceSync(eventId: String, payloadJson: String) {
        val item = SyncQueueEntity(
            queueId = java.util.UUID.randomUUID().toString(),
            entityType = "ATTENDANCE_EVENT",
            entityId = eventId,
            action = "CREATE",
            payloadJson = payloadJson,
            status = "PENDING"
        )
        db.systemDao().enqueueSyncItem(item)
        scheduleSyncWork()
    }

    fun scheduleSyncWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWork = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(syncWork)
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val db = BioScanDatabase.getInstance(applicationContext)
        val pendingItems = db.systemDao().getPendingSyncQueue()

        for (item in pendingItems) {
            try {
                // Perform idempotent mock/server upload logic here
                db.systemDao().deleteSyncQueueItem(item.queueId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return Result.success()
    }
}
