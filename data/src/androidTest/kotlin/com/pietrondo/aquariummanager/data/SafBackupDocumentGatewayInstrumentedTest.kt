package com.pietrondo.aquariummanager.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pietrondo.aquariummanager.domain.AquariumDraft
import com.pietrondo.aquariummanager.domain.BackupDestinationOccupiedException
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device level coverage of the SAF failure modes: the production gateway talks to a real
 * [android.content.ContentResolver] and a provider that misbehaves on demand.
 */
@RunWith(AndroidJUnit4::class)
class SafBackupDocumentGatewayInstrumentedTest {
    private lateinit var database: AquariumDatabase
    private lateinit var repository: RoomAquariumRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        HostileBackupProvider.reset()
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AquariumDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val workDir = File(context.cacheDir, "instrumented-backup-work").apply { mkdirs() }
        repository = RoomAquariumRepository(
            dao = database.aquariumDao(),
            documentGateway = SafBackupDocumentGateway(context),
            backupWorkDir = workDir,
            appVersion = "0.1.0",
        )
    }

    @After
    fun tearDown() {
        database.close()
        HostileBackupProvider.reset()
    }

    @Test
    fun exportFailsAndKeepsTheLastValidBackupWhenTheProviderMisbehaves() = runBlocking {
        repository.createAquarium(AquariumDraft(name = "Reef"))

        val goodUri = HostileBackupProvider.uriFor("verified-backup")
        createEmptyDocument(goodUri)
        val exported = repository.exportBackupToUri(goodUri.toString())
        assertEquals(1, exported.manifest.counts.aquariums)
        val verifiedBytes = readBytes(goodUri)

        val hostileBehaviors = listOf(
            HostileBackupProvider.Behavior.UNAVAILABLE,
            HostileBackupProvider.Behavior.REVOKED,
            HostileBackupProvider.Behavior.OUT_OF_SPACE,
            HostileBackupProvider.Behavior.TRUNCATED_WRITE,
        )
        for (behavior in hostileBehaviors) {
            val docId = "failed-${behavior.name.lowercase()}"
            val uri = HostileBackupProvider.uriFor(docId)
            createEmptyDocument(uri)
            HostileBackupProvider.behaviors[docId] = behavior

            try {
                repository.exportBackupToUri(uri.toString())
                fail("Expected the export to fail against a $behavior provider")
            } catch (_: Exception) {
                // The provider failure must surface as a failed export, never as a success.
            }

            assertEquals(verifiedBytes.toList(), readBytes(goodUri).toList())
            assertEquals(1, repository.inspectBackupUri(goodUri.toString()).counts.aquariums)
            assertEquals(1, repository.observeAquariums().first().size)
        }
    }

    @Test
    fun exportRefusesToReplaceAnExistingValidBackup() = runBlocking {
        repository.createAquarium(AquariumDraft(name = "Reef"))
        val uri = HostileBackupProvider.uriFor("existing-backup")
        createEmptyDocument(uri)
        repository.exportBackupToUri(uri.toString())
        val verifiedBytes = readBytes(uri)

        try {
            repository.exportBackupToUri(uri.toString())
            fail("Expected an occupied destination to be rejected")
        } catch (occupied: BackupDestinationOccupiedException) {
            assertTrue(occupied.message!!.contains("Choose a new file"))
        }

        assertEquals(verifiedBytes.toList(), readBytes(uri).toList())
        assertEquals(1, repository.inspectBackupUri(uri.toString()).counts.aquariums)
    }

    @Test
    fun restoreFromARevokedDocumentFailsAndLeavesTheLibraryUnchanged() = runBlocking {
        repository.createAquarium(AquariumDraft(name = "Reef"))
        val docId = "restore-source"
        val uri = HostileBackupProvider.uriFor(docId)
        createEmptyDocument(uri)
        repository.exportBackupToUri(uri.toString())
        repository.createAquarium(AquariumDraft(name = "Second tank"))

        HostileBackupProvider.behaviors[docId] = HostileBackupProvider.Behavior.REVOKED
        try {
            repository.restoreBackupFromUri(uri.toString(), confirmReplace = true)
            fail("Expected the restore to fail for a revoked document")
        } catch (_: Exception) {
            // A revoked grant cannot replace the live library.
        }
        assertEquals(2, repository.observeAquariums().first().size)

        HostileBackupProvider.behaviors.remove(docId)
        assertEquals(1, repository.inspectBackupUri(uri.toString()).counts.aquariums)
    }

    /** The SAF picker creates the destination document before the app writes into it. */
    private fun createEmptyDocument(uri: Uri) {
        context.contentResolver.openOutputStream(uri, "wt")!!.close()
    }

    private fun readBytes(uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
}
