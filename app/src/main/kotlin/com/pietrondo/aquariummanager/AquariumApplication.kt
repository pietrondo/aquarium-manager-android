package com.pietrondo.aquariummanager

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.room.Room
import com.pietrondo.aquariummanager.data.AquariumDatabase
import com.pietrondo.aquariummanager.data.RoomAquariumRepository
import com.pietrondo.aquariummanager.data.SafBackupDocumentGateway
import com.pietrondo.aquariummanager.domain.AquariumRepository
import java.io.File
import java.util.concurrent.TimeUnit

class AquariumApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            AutoBackupWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS).build(),
        )
    }

    private val database: AquariumDatabase by lazy {
        Room.databaseBuilder(applicationContext, AquariumDatabase::class.java, "aquariums.db")
            .addMigrations(*AquariumDatabase.ALL_MIGRATIONS)
            .build()
    }
    val aquariumRepository: AquariumRepository by lazy {
        RoomAquariumRepository(
            dao = database.aquariumDao(),
            documentGateway = SafBackupDocumentGateway(applicationContext),
            backupWorkDir = File(applicationContext.filesDir, "backups"),
        )
    }
}
