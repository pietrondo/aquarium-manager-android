package com.pietrondo.aquariummanager

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first

class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as AquariumApplication).aquariumRepository
        val settings = repository.observeBackupScheduleSettings().first()
        if (!settings.autoBackupEnabled) return Result.success()

        val result = repository.runAutomaticBackupNow()
        return if (result.lastBackupStatus?.startsWith("Automatic backup failed:") == true) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "automatic-library-backup"
    }
}
