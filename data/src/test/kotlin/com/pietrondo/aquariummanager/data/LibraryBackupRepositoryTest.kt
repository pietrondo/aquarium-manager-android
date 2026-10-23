package com.pietrondo.aquariummanager.data

import androidx.room.Room
import com.pietrondo.aquariummanager.domain.AquariumDraft
import com.pietrondo.aquariummanager.domain.AquariumSnapshotRecord
import com.pietrondo.aquariummanager.domain.BACKUP_FORMAT_ID
import com.pietrondo.aquariummanager.domain.BackupDocumentGateway
import com.pietrondo.aquariummanager.domain.BackupDocumentDeleteResult
import com.pietrondo.aquariummanager.domain.BackupManifest
import com.pietrondo.aquariummanager.domain.BackupTableCounts
import com.pietrondo.aquariummanager.domain.DosingEventDraft
import com.pietrondo.aquariummanager.domain.DosingFrequency
import com.pietrondo.aquariummanager.domain.DosingPlanDraft
import com.pietrondo.aquariummanager.domain.LibrarySnapshot
import com.pietrondo.aquariummanager.domain.MAX_MANIFEST_BYTES
import com.pietrondo.aquariummanager.domain.MaintenanceDraft
import com.pietrondo.aquariummanager.domain.MeasurementDraft
import com.pietrondo.aquariummanager.domain.SpeciesDraft
import com.pietrondo.aquariummanager.domain.SpeciesKind
import com.pietrondo.aquariummanager.domain.StockEntryDraft
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal
import java.nio.file.Files
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LibraryBackupRepositoryTest {
    private lateinit var database: AquariumDatabase
    private lateinit var tempWorkDir: File
    private lateinit var fakeGateway: FakeBackupDocumentGateway
    private lateinit var repository: RoomAquariumRepository
    private var nowEpochSecond = 1_792_350_000L

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, AquariumDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tempWorkDir = Files.createTempDirectory("aquarium-backup-test").toFile()
        fakeGateway = FakeBackupDocumentGateway()
        repository = RoomAquariumRepository(
            dao = database.aquariumDao(),
            clock = { Instant.ofEpochSecond(nowEpochSecond++) },
            documentGateway = fakeGateway,
            backupWorkDir = tempWorkDir,
            appVersion = "0.1.0",
        )
    }

    @After
    fun tearDown() {
        database.close()
        tempWorkDir.deleteRecursively()
    }

    @Test
    fun goldenFixtureAndLegacyV0MigrationRoundTripAndDeviceUriExclusion() = runBlocking {
        val catalog = CatalogJsonParser.loadDefaultCatalog()
        val aqId = UUID.fromString("30000000-0000-0000-0000-000000000001")
        val profile = catalog.profileLabels.first()
        val species = catalog.species.first()
        val productId = UUID.fromString("40000000-0000-0000-0000-000000000001")
        val planId = UUID.fromString("50000000-0000-0000-0000-000000000001")

        val snapshot = LibrarySnapshot(
            aquariums = listOf(
                AquariumSnapshotRecord(
                    id = aqId,
                    name = "Acquario di Pietro \uD83C\uDF3F\uD83D\uDC1F",
                    netLitres = BigDecimal("42.5"),
                    grossLitres = null,
                    notes = null,
                    createdAt = Instant.parse("2026-10-18T10:00:00Z"),
                )
            ),
            aquariumProfiles = listOf(
                com.pietrondo.aquariummanager.domain.AquariumProfileSnapshotRecord(
                    id = profile.id,
                    name = profile.name,
                )
            ),
            aquariumProfileLinks = listOf(
                com.pietrondo.aquariummanager.domain.AquariumProfileLinkSnapshotRecord(
                    aquariumId = aqId,
                    profileId = profile.id,
                )
            ),
            measurements = listOf(
                com.pietrondo.aquariummanager.domain.MeasurementSnapshotRecord(
                    id = UUID.fromString("60000000-0000-0000-0000-000000000001"),
                    aquariumId = aqId,
                    recordedAt = Instant.parse("2026-10-18T11:00:00Z"),
                    ph = BigDecimal("6.45"),
                    khDkh = null,
                    ghDgh = BigDecimal("5"),
                    no2MgL = BigDecimal("0"),
                    no3MgL = null,
                    tdsPpm = BigDecimal("148"),
                    temperatureC = BigDecimal("22.5"),
                )
            ),
            maintenanceEvents = listOf(
                com.pietrondo.aquariummanager.domain.MaintenanceEventSnapshotRecord(
                    id = UUID.fromString("70000000-0000-0000-0000-000000000001"),
                    aquariumId = aqId,
                    type = "WATER_CHANGE",
                    occurredAt = Instant.parse("2026-10-18T12:00:00Z"),
                    changedLitres = BigDecimal("12.5"),
                    notes = "Acqua osmotica + sali",
                )
            ),
            products = listOf(
                com.pietrondo.aquariummanager.domain.ProductSnapshotRecord(
                    id = productId,
                    name = "SaltyShrimp Bee Mineral GH+",
                )
            ),
            dosingPlans = listOf(
                com.pietrondo.aquariummanager.domain.DosingPlanSnapshotRecord(
                    id = planId,
                    aquariumId = aqId,
                    productId = productId,
                    dose = BigDecimal("1.25"),
                    unit = "g",
                    frequency = DosingFrequency.WEEKLY,
                    weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                    startDate = LocalDate.parse("2026-10-01"),
                    endDate = LocalDate.parse("2026-12-31"),
                    timeZone = ZoneId.of("Europe/Rome"),
                )
            ),
            dosingEvents = listOf(
                com.pietrondo.aquariummanager.domain.DosingEventSnapshotRecord(
                    id = UUID.fromString("80000000-0000-0000-0000-000000000001"),
                    planId = planId,
                    aquariumId = aqId,
                    occurredAt = Instant.parse("2026-10-18T12:05:00Z"),
                    productNameSnapshot = "SaltyShrimp Bee Mineral GH+",
                    dose = BigDecimal("1.25"),
                    unit = "g",
                )
            ),
            species = listOf(
                com.pietrondo.aquariummanager.domain.SpeciesSnapshotRecord(
                    id = species.id,
                    kind = species.kind,
                    scientificName = "Caridina cf. cantonensis",
                    commonName = species.commonName,
                )
            ),
            stockEntries = listOf(
                com.pietrondo.aquariummanager.domain.StockEntrySnapshotRecord(
                    id = UUID.fromString("90000000-0000-0000-0000-000000000001"),
                    aquariumId = aqId,
                    speciesId = species.id,
                    quantity = null,
                    introducedAt = Instant.parse("2026-10-10T09:00:00Z"),
                    exitedAt = null,
                    notes = "Colonia iniziale",
                )
            ),
        )

        val libraryBytes = LibraryBackupCodec.serializeLibraryJson(snapshot)
        val libraryJsonText = libraryBytes.toString(Charsets.UTF_8)
        assertTrue(libraryJsonText.contains("Acquario di Pietro \uD83C\uDF3F\uD83D\uDC1F"))
        assertTrue(libraryJsonText.contains("Caridina cf. cantonensis"))
        assertFalse(libraryJsonText.contains("treeUri"))
        assertFalse(libraryJsonText.contains("verifiedAutoGenerations"))

        val sha256 = LibraryBackupCodec.computeSha256Hex(libraryBytes)
        assertEquals(64, sha256.length)

        val manifest = BackupManifest(
            format = BACKUP_FORMAT_ID,
            formatVersion = 1,
            appVersion = "9.9.9-future-app-build",
            createdAt = Instant.parse("2026-10-19T12:00:00Z"),
            counts = snapshot.counts(),
            librarySha256 = sha256,
        )
        val manifestBytes = LibraryBackupCodec.serializeManifestJson(manifest)
        val parsedManifest = LibraryBackupCodec.parseManifestJson(manifestBytes)
        assertEquals(manifest, parsedManifest)

        val parsedSnapshot = LibraryBackupCodec.parseLibraryJson(libraryBytes, 1, catalog)
        assertEquals(snapshot, parsedSnapshot)

        val hugeDecimalJson = JSONObject(libraryJsonText).apply {
            getJSONArray("aquariums").getJSONObject(0).put("netLitres", "1E+100000000")
        }.toString().toByteArray(Charsets.UTF_8)
        assertThrowsIllegalArgument {
            LibraryBackupCodec.parseLibraryJson(hugeDecimalJson, 1, catalog)
        }

        val overflowWeekdayJson = JSONObject(libraryJsonText).apply {
            getJSONArray("dosingPlans").getJSONObject(0)
                .put("weekdays", org.json.JSONArray().put(4_294_967_297L))
        }.toString().toByteArray(Charsets.UTF_8)
        assertThrowsIllegalArgument {
            LibraryBackupCodec.parseLibraryJson(overflowWeekdayJson, 1, catalog)
        }

        // Legacy formatVersion = 0 migration fixture:
        // Uses legacy key "profileLinks", omits "dosingEvents", and uses "name" on species
        val v0Json = JSONObject(libraryJsonText).apply {
            val links = remove("aquariumProfileLinks")
            put("profileLinks", links)
            remove("dosingEvents")
            val speciesArr = getJSONArray("species")
            val sp0 = speciesArr.getJSONObject(0)
            val common = sp0.remove("commonName")
            sp0.put("name", common)
        }.toString().toByteArray(Charsets.UTF_8)

        val migratedFromV0 = LibraryBackupCodec.parseLibraryJson(v0Json, 0, catalog)
        assertEquals(snapshot.copy(dosingEvents = emptyList()), migratedFromV0)

        // Reject device-local field injected into library.json
        val pollutedJson = JSONObject(libraryJsonText).apply {
            put("treeUri", "content://com.android.externalstorage.documents/tree/primary%3ABackups")
        }.toString().toByteArray(Charsets.UTF_8)
        assertThrowsIllegalArgument {
            LibraryBackupCodec.parseLibraryJson(pollutedJson, 1, catalog)
        }

        // Reject future formatVersion = 2 in manifest JSON
        val futureManifestBytes = JSONObject(manifestBytes.toString(Charsets.UTF_8)).apply {
            put("formatVersion", 2)
        }.toString().toByteArray(Charsets.UTF_8)
        assertThrowsIllegalArgument {
            LibraryBackupCodec.parseManifestJson(futureManifestBytes)
        }
    }

    @Test
    fun exportCoherentVerifiedZipSnapshotAndConcurrentWriteSerializationAndFailureCleanup() = runBlocking {
        populateSampleLibrary()

        val exportUri = "content://fake/manual/backup-1.zip"
        val result = repository.exportBackupToUri(exportUri)
        assertEquals(exportUri, result.destinationUri)
        assertEquals(1, result.manifest.formatVersion)
        assertTrue(fakeGateway.exists(exportUri))

        val preview = repository.inspectBackupUri(exportUri)
        assertEquals(result.manifest.librarySha256, preview.librarySha256)
        assertEquals(result.manifest.counts, preview.counts)

        // Concurrent edit and export serialize cleanly without corrupting snapshot
        val deferredExport = async {
            repository.exportBackupToUri("content://fake/manual/backup-concurrent.zip")
        }
        val deferredWrite = async {
            repository.createAquarium(
                AquariumDraft(
                    name = "Concurrent Second Tank",
                    netLitres = BigDecimal("60"),
                )
            )
        }
        val concurrentExportResult = deferredExport.await()
        deferredWrite.await()
        val concurrentPreview = repository.inspectBackupUri(concurrentExportResult.destinationUri)
        assertEquals(concurrentExportResult.manifest.counts, concurrentPreview.counts)

        // Failed manual export leaves the selected destination in place and leaves prior backups untouched.
        val failingUri = "content://fake/manual/backup-fail.zip"
        fakeGateway.failWriteForUris.add(failingUri)
        try {
            repository.exportBackupToUri(failingUri)
            fail("Expected IOException on failing destination write")
        } catch (_: IOException) {
            // Expected
        }
        assertTrue(fakeGateway.exists(failingUri))
        assertTrue(fakeGateway.exists(exportUri))
    }

    @Test
    fun hostileZipParserRejectsTraversalDuplicatesExtraFilesMissingFilesBombsAndCorruptionBeforeDbMutation() = runBlocking {
        populateSampleLibrary()
        val validZip = repository.exportBackupBytes()
        val aquariumsBefore = repository.observeAquariumDetails().first()
        assertEquals(1, aquariumsBefore.size)

        val catalog = CatalogJsonParser.loadDefaultCatalog()
        val validArchive = LibraryBackupCodec.inspectAndParseZip(ByteArrayInputStream(validZip), catalog)
        val validManifestBytes = LibraryBackupCodec.serializeManifestJson(validArchive.manifest)
        val validLibraryBytes = LibraryBackupCodec.serializeLibraryJson(validArchive.snapshot)

        // 1. Path traversal entry
        val traversalZip = buildCustomZip(
            listOf(
                "../manifest.json" to validManifestBytes,
                "library.json" to validLibraryBytes,
            )
        )
        assertThrowsIllegalArgumentSuspend {
            repository.restoreBackupFromBytes(traversalZip, confirmReplace = true)
        }

        // 2. Subdirectory entry
        val subdirZip = buildCustomZip(
            listOf(
                "manifest.json" to validManifestBytes,
                "nested/library.json" to validLibraryBytes,
            )
        )
        assertThrowsIllegalArgumentSuspend {
            repository.restoreBackupFromBytes(subdirZip, confirmReplace = true)
        }

        // 3. Duplicate entry
        val duplicateZip = buildCustomZip(
            listOf(
                "manifest.json" to validManifestBytes,
                "manifest.json" to validManifestBytes,
                "library.json" to validLibraryBytes,
            )
        )
        assertThrowsIllegalArgumentSuspend {
            repository.restoreBackupFromBytes(duplicateZip, confirmReplace = true)
        }

        // 4. Extra unexpected file
        val extraFileZip = buildCustomZip(
            listOf(
                "manifest.json" to validManifestBytes,
                "library.json" to validLibraryBytes,
                "extra.txt" to "surprise".toByteArray(),
            )
        )
        assertThrowsIllegalArgumentSuspend {
            repository.restoreBackupFromBytes(extraFileZip, confirmReplace = true)
        }

        // 5. Missing library.json
        val missingLibraryZip = buildCustomZip(
            listOf("manifest.json" to validManifestBytes)
        )
        assertThrowsIllegalArgumentSuspend {
            repository.restoreBackupFromBytes(missingLibraryZip, confirmReplace = true)
        }

        // 6. Oversized manifest (> 64 KiB)
        val oversizedManifestZip = buildCustomZip(
            listOf(
                "manifest.json" to ByteArray(MAX_MANIFEST_BYTES + 100) { ' '.code.toByte() },
                "library.json" to validLibraryBytes,
            )
        )
        assertThrowsIllegalArgumentSuspend {
            repository.restoreBackupFromBytes(oversizedManifestZip, confirmReplace = true)
        }

        // 7. Expansion ratio bomb (> 100:1 ratio)
        val highlyCompressibleLibrary = ByteArray(250_000) { ' '.code.toByte() }
        val bombZip = buildCustomZip(
            listOf(
                "manifest.json" to validManifestBytes,
                "library.json" to highlyCompressibleLibrary,
            ),
            compressionLevel = Deflater.BEST_COMPRESSION,
        )
        assertThrowsIllegalArgumentSuspend {
            repository.restoreBackupFromBytes(bombZip, confirmReplace = true)
        }

        // 8. Corrupted library.json (SHA-256 mismatch)
        val corruptedLibraryBytes = validLibraryBytes + "\n".toByteArray(Charsets.UTF_8)
        val badHashZip = buildCustomZip(
            listOf(
                "manifest.json" to validManifestBytes,
                "library.json" to corruptedLibraryBytes,
            )
        )
        assertThrowsIllegalArgumentSuspend {
            repository.restoreBackupFromBytes(badHashZip, confirmReplace = true)
        }

        // 9. Table count mismatch between manifest.json and library.json
        val wrongCountsManifest = validArchive.manifest.copy(
            counts = validArchive.manifest.counts.copy(aquariums = 99)
        )
        val countMismatchZip = buildCustomZip(
            listOf(
                "manifest.json" to LibraryBackupCodec.serializeManifestJson(wrongCountsManifest),
                "library.json" to validLibraryBytes,
            )
        )
        assertThrowsIllegalArgumentSuspend {
            repository.restoreBackupFromBytes(countMismatchZip, confirmReplace = true)
        }

        // Verify live Room database was never mutated by any hostile archive
        val aquariumsAfter = repository.observeAquariumDetails().first()
        assertEquals(aquariumsBefore, aquariumsAfter)
    }

    @Test
    fun unsupportedCompressionMethodAndEncryptedEntriesAreRejectedBeforeDbMutation() = runBlocking {
        populateSampleLibrary()
        val validZip = repository.exportBackupBytes()
        val aquariumsBefore = repository.observeAquariumDetails().first()

        val unsupportedMethodZip = patchAllZipEntryHeaders(validZip, compressionMethod = 99)
        assertThrowsIllegalArgumentSuspend {
            repository.restoreBackupFromBytes(unsupportedMethodZip, confirmReplace = true)
        }

        val encryptedZip = patchAllZipEntryHeaders(validZip, encrypted = true)
        assertThrowsIllegalArgumentSuspend {
            repository.restoreBackupFromBytes(encryptedZip, confirmReplace = true)
        }

        assertEquals(aquariumsBefore, repository.observeAquariumDetails().first())
    }

    @Test
    fun replaceLibrarySemanticRoundTripRollbackOnMidImportFailureAndSafetyBackupEnforcement() = runBlocking {
        populateSampleLibrary()

        // Configure device-local backup settings before export/restore
        val treeUri = "content://fake/tree/auto-backups"
        fakeGateway.persistedTrees.add(treeUri)
        repository.updateAutoBackupSettings(enabled = true, treeUri = treeUri, retentionCount = 7)

        // Export Library A
        val zipA = repository.exportBackupBytes()
        val previewA = repository.inspectBackupBytes(zipA)

        // Mutate live database to Library B
        val tankBId = repository.createAquarium(
            AquariumDraft(
                name = "Temporary Tank B",
                netLitres = BigDecimal("100"),
            )
        )
        assertEquals(2, repository.observeAquariumDetails().first().size)

        // 1. Missing confirmation is rejected without touching Library B
        assertThrowsIllegalArgumentSuspend {
            repository.restoreBackupFromBytes(zipA, confirmReplace = false)
        }
        assertEquals(2, repository.observeAquariumDetails().first().size)

        // 2. Safety backup failure aborts before touching Library B
        repository.safetyBackupFailureHook = {
            throw IOException("Simulated disk full while writing safety backup")
        }
        try {
            repository.restoreBackupFromBytes(zipA, confirmReplace = true)
            fail("Expected IOException when safety backup creation fails")
        } catch (_: IOException) {
            // Expected
        } finally {
            repository.safetyBackupFailureHook = null
        }
        assertEquals(2, repository.observeAquariumDetails().first().size)

        // 3. Injected mid-import failure rolls back Room transaction AND keeps safety backup of Library B
        repository.midImportFailureHook = {
            throw IllegalStateException("Simulated mid-import crash after table clear")
        }
        try {
            repository.restoreBackupFromBytes(zipA, confirmReplace = true)
            fail("Expected IllegalStateException on mid-import failure")
        } catch (_: IllegalStateException) {
            // Expected
        } finally {
            repository.midImportFailureHook = null
        }
        val afterRollback = repository.observeAquariumDetails().first()
        assertEquals(2, afterRollback.size)
        assertTrue(afterRollback.any { it.id == tankBId })

        val safetyBackupFile = File(tempWorkDir, "safety-backups/safety-backup-latest.zip")
        assertTrue("Verified safety backup must remain on disk", safetyBackupFile.isFile)
        val previousSafetyBackup = safetyBackupFile.readBytes()
        repository.safetyBackupPromotionFailureHook = {
            throw IOException("Simulated safety backup promotion failure")
        }
        try {
            repository.restoreBackupFromBytes(zipA, confirmReplace = true)
            fail("Expected safety backup promotion to fail")
        } catch (_: IOException) {
            // The prior verified backup must remain usable if promotion fails.
        } finally {
            repository.safetyBackupPromotionFailureHook = null
        }
        assertTrue(previousSafetyBackup.contentEquals(safetyBackupFile.readBytes()))
        val safetyPreview = repository.inspectBackupBytes(safetyBackupFile.readBytes())
        assertEquals(2, safetyPreview.counts.aquariums)

        // 4. Successful replace restores Library A and preserves device-local schedule settings
        val restoredPreview = repository.restoreBackupFromBytes(zipA, confirmReplace = true)
        assertEquals(previewA.librarySha256, restoredPreview.librarySha256)

        val zipA2 = repository.exportBackupBytes()
        val previewA2 = repository.inspectBackupBytes(zipA2)
        assertEquals(previewA.librarySha256, previewA2.librarySha256)
        assertEquals(previewA.counts, previewA2.counts)

        val scheduleAfterRestore = repository.observeBackupScheduleSettings().first()
        assertTrue(scheduleAfterRestore.autoBackupEnabled)
        assertEquals(treeUri, scheduleAfterRestore.treeUri)
        assertEquals(7, scheduleAfterRestore.retentionCount)
    }

    @Test
    fun safManualReadbackVerificationAndAutoBackupSevenGenerationRetention() = runBlocking {
        populateSampleLibrary()

        val validManualUri = "content://fake/manual/good-backup.zip"
        repository.exportBackupToUri(validManualUri)
        assertTrue(fakeGateway.exists(validManualUri))

        // A manual destination is user-owned, so failed verification must not delete it.
        val corruptedDestUri = "content://fake/manual/corrupted-write.zip"
        fakeGateway.writeDocument(corruptedDestUri, "keep existing backup".toByteArray())
        fakeGateway.corruptOnWriteUris.add(corruptedDestUri)
        try {
            repository.exportBackupToUri(corruptedDestUri)
            fail("Expected readback verification to reject corrupted provider write")
        } catch (_: Exception) {
            // Expected
        }
        assertTrue("A selected manual destination must never be deleted", fakeGateway.exists(corruptedDestUri))
        assertTrue("Previous valid manual backup must remain untouched", fakeGateway.exists(validManualUri))

        // Automatic backup with missing/revoked permission records actionable status without crashing
        val treeUri = "content://fake/tree/daily"
        repository.updateAutoBackupSettings(enabled = true, treeUri = treeUri, retentionCount = 7)
        val revokedStatus = repository.runAutomaticBackupNow()
        assertNotNull(revokedStatus.lastBackupStatus)
        assertTrue(revokedStatus.lastBackupStatus!!.contains("permission", ignoreCase = true))

        // Grant permission and place a manual backup + arbitrary user file in the same tree
        fakeGateway.persistedTrees.add(treeUri)
        val userNotesUri = "$treeUri/user-notes.txt"
        val userManualZipUri = "$treeUri/aquarium-manual-backup.zip"
        fakeGateway.writeDocument(userNotesUri, "My private aquarium notes".toByteArray())
        fakeGateway.writeDocument(userManualZipUri, repository.exportBackupBytes())

        // Run 9 automatic backup generations -> keeps latest 7, prunes oldest 2 app-owned auto backups
        val createdAutoUris = mutableListOf<String>()
        repeat(9) {
            val status = repository.runAutomaticBackupNow()
            assertEquals(minOf(it + 1, 7), status.verifiedAutoGenerations.size)
            createdAutoUris.add(status.verifiedAutoGenerations.last())
        }

        val finalSettings = repository.observeBackupScheduleSettings().first()
        assertEquals(7, finalSettings.verifiedAutoGenerations.size)
        assertFalse("Generation 1 should be pruned", fakeGateway.exists(createdAutoUris[0]))
        assertFalse("Generation 2 should be pruned", fakeGateway.exists(createdAutoUris[1]))
        for (i in 2 until 9) {
            assertTrue("Generation ${i + 1} must be retained", fakeGateway.exists(createdAutoUris[i]))
        }
        assertTrue("Manual backup in tree must never be pruned", fakeGateway.exists(userManualZipUri))
        assertTrue("Arbitrary user file in tree must never be pruned", fakeGateway.exists(userNotesUri))

        fakeGateway.failedDeleteUris.add(createdAutoUris[2])
        val afterDeleteFailure = repository.runAutomaticBackupNow()
        assertTrue("Failed prune must remain tracked for retry", createdAutoUris[2] in afterDeleteFailure.verifiedAutoGenerations)
        assertTrue("Failed prune must be reported", afterDeleteFailure.lastBackupStatus!!.contains("cleanup pending", ignoreCase = true))
        assertTrue("Failed prune must not remove the backup", fakeGateway.exists(createdAutoUris[2]))
        val generationCountAfterDeleteFailure = afterDeleteFailure.verifiedAutoGenerations.size
        val afterRepeatedDeleteFailure = repository.runAutomaticBackupNow()
        assertEquals(generationCountAfterDeleteFailure, afterRepeatedDeleteFailure.verifiedAutoGenerations.size)
        assertTrue(afterRepeatedDeleteFailure.lastBackupStatus!!.contains("cleanup pending", ignoreCase = true))
        fakeGateway.failedDeleteUris.remove(createdAutoUris[2])
        val afterDeleteRetry = repository.runAutomaticBackupNow()
        assertFalse("Previously failed prune is retried", fakeGateway.exists(createdAutoUris[2]))
        assertTrue(afterDeleteRetry.verifiedAutoGenerations.size <= afterDeleteRetry.retentionCount)

        // If 10th automatic backup fails during write/readback, none of the 7 retained generations are pruned
        fakeGateway.failNextAutoCreateWrite = true
        val afterFailedAuto = repository.runAutomaticBackupNow()
        assertEquals(7, afterFailedAuto.verifiedAutoGenerations.size)
        afterDeleteRetry.verifiedAutoGenerations.forEach { uri ->
            assertTrue("Retained generation must survive failed auto run", fakeGateway.exists(uri))
        }
    }

    @Test
    fun failedAutomaticDocumentCleanupIsPersistedAndRetriedAfterRepositoryRestart() = runBlocking {
        populateSampleLibrary()
        val treeUri = "content://fake/tree/cleanup-retry"
        fakeGateway.persistedTrees.add(treeUri)
        repository.updateAutoBackupSettings(enabled = true, treeUri = treeUri, retentionCount = 7)
        fakeGateway.failNextAutoCreateWrite = true
        fakeGateway.failDeleteForNextAuto = true

        val failed = repository.runAutomaticBackupNow()
        val partialUri = fakeGateway.createdAutoUris.last()
        assertTrue(fakeGateway.exists(partialUri))
        assertTrue(partialUri in failed.pendingCleanupUris)

        repository = RoomAquariumRepository(
            dao = database.aquariumDao(),
            clock = { Instant.ofEpochSecond(nowEpochSecond++) },
            documentGateway = fakeGateway,
            backupWorkDir = tempWorkDir,
            appVersion = "0.1.0",
        )
        fakeGateway.failedDeleteUris.remove(partialUri)
        val retried = repository.runAutomaticBackupNow()
        assertFalse(fakeGateway.exists(partialUri))
        assertTrue(retried.pendingCleanupUris.isEmpty())
    }

    @Test
    fun verifiedGenerationWithFailedFinalSettingsWriteIsReconciledAfterRestart() = runBlocking {
        populateSampleLibrary()
        val treeUri = "content://fake/tree/final-write-failure"
        fakeGateway.persistedTrees.add(treeUri)
        repository.updateAutoBackupSettings(enabled = true, treeUri = treeUri, retentionCount = 3)
        var writes = 0
        repository.scheduleSettingsPersistenceFailureHook = {
            writes++
            if (writes == 3) throw IOException("Simulated final settings write failure")
        }

        try {
            repository.runAutomaticBackupNow()
            fail("Expected final settings persistence to fail")
        } catch (_: IOException) {
            // The durable pending marker must remain for restart reconciliation.
        }
        repository.scheduleSettingsPersistenceFailureHook = null
        val uri = fakeGateway.createdAutoUris.last()
        assertTrue(fakeGateway.exists(uri))

        repository = RoomAquariumRepository(
            dao = database.aquariumDao(),
            clock = { Instant.ofEpochSecond(nowEpochSecond++) },
            documentGateway = fakeGateway,
            backupWorkDir = tempWorkDir,
            appVersion = "0.1.0",
        )
        val recovered = repository.runAutomaticBackupNow()
        assertTrue("Verified pending generation should be promoted", uri in recovered.verifiedAutoGenerations)
        assertTrue(recovered.pendingGenerationUris.isEmpty())
        assertTrue(fakeGateway.exists(uri))
    }

    @Test
    fun prunedGenerationIsNotTrackedForeverAfterFinalSettingsWriteFailure() = runBlocking {
        populateSampleLibrary()
        val treeUri = "content://fake/tree/prune-crash"
        fakeGateway.persistedTrees.add(treeUri)
        repository.updateAutoBackupSettings(enabled = true, treeUri = treeUri, retentionCount = 1)
        val original = repository.runAutomaticBackupNow().verifiedAutoGenerations.single()
        var writes = 0
        repository.scheduleSettingsPersistenceFailureHook = {
            if (++writes == 3) throw IOException("final settings failure")
        }
        try {
            repository.runAutomaticBackupNow()
            fail("Expected final settings failure after pruning")
        } catch (_: IOException) {
            // Old URI deletion happened, but durable settings still mention it.
        }
        repository.scheduleSettingsPersistenceFailureHook = null
        assertFalse(fakeGateway.exists(original))
        repository = newRepositoryForRestart()

        val recovered = repository.runAutomaticBackupNow()
        assertTrue(recovered.verifiedAutoGenerations.none { it == original })
        assertEquals(1, recovered.verifiedAutoGenerations.size)
        assertTrue(recovered.verifiedAutoGenerations.all(fakeGateway::exists))
    }

    @Test
    fun failedUriMarkerWriteAndDeleteIsRecoveredByDurableFilenameIntent() = runBlocking {
        populateSampleLibrary()
        val treeUri = "content://fake/tree/filename-recovery"
        fakeGateway.persistedTrees.add(treeUri)
        repository.updateAutoBackupSettings(enabled = true, treeUri = treeUri, retentionCount = 1)
        var writes = 0
        repository.scheduleSettingsPersistenceFailureHook = {
            if (++writes == 2) throw IOException("URI marker write failed")
        }
        fakeGateway.failDeleteForNextAuto = true
        try {
            repository.runAutomaticBackupNow()
            fail("Expected URI marker write failure")
        } catch (_: IOException) {
            // Durable filename intent is retained before the document is created.
        }
        repository.scheduleSettingsPersistenceFailureHook = null
        val orphan = fakeGateway.createdAutoUris.last()
        assertTrue(fakeGateway.exists(orphan))
        repository = newRepositoryForRestart()
        val pending = repository.runAutomaticBackupNow()
        assertTrue("Recovery must report unresolved cleanup", pending.lastBackupStatus!!.contains("pending generation recovery"))
        assertTrue(fakeGateway.exists(orphan))
        fakeGateway.failedDeleteUris.remove(orphan)
        val recovered = repository.runAutomaticBackupNow()
        assertFalse(fakeGateway.exists(orphan))
        assertTrue(recovered.pendingGenerationFileNames.isEmpty())
    }

    @Test
    fun switchingTreeClearsOldTreeRecoveryMarkers() = runBlocking {
        val oldTree = "content://fake/tree/revoked"
        fakeGateway.persistedTrees.add(oldTree)
        repository.updateAutoBackupSettings(enabled = true, treeUri = oldTree, retentionCount = 2)
        var writes = 0
        repository.scheduleSettingsPersistenceFailureHook = {
            if (++writes == 2) throw IOException("stop after intent")
        }
        fakeGateway.failDeleteForNextAuto = true
        try {
            repository.runAutomaticBackupNow()
            fail("Expected URI marker persistence failure")
        } catch (_: IOException) {
            // Old-tree markers remain scoped to the old tree.
        }
        repository.scheduleSettingsPersistenceFailureHook = null
        fakeGateway.persistedTrees.remove(oldTree)
        val newTree = "content://fake/tree/new"
        fakeGateway.persistedTrees.add(newTree)
        val switched = repository.updateAutoBackupSettings(enabled = true, treeUri = newTree, retentionCount = 2)
        assertTrue(switched.pendingGenerationUris.isEmpty())
        assertTrue(switched.pendingGenerationFileNames.isEmpty())
        assertTrue(switched.pendingCleanupUris.isEmpty())
    }

    @Test
    fun backupCodecRunsOnInjectedBackgroundDispatcher() = runBlocking {
        populateSampleLibrary()
        val dispatcher = TrackingDispatcher()
        val backgroundRepository = RoomAquariumRepository(
            dao = database.aquariumDao(),
            documentGateway = fakeGateway,
            backupWorkDir = tempWorkDir,
            defaultDispatcher = dispatcher,
        )
        val bytes = repository.exportBackupBytes()
        backgroundRepository.inspectBackupBytes(bytes)
        assertTrue("Backup codec work must dispatch off Main", dispatcher.wasDispatched)
    }

    @Test
    fun scheduleSettingsPersistAtomicallyAndDoNotPublishUncommittedState() = runBlocking {
        repository.updateAutoBackupSettings(enabled = false, treeUri = null, retentionCount = 7)
        val before = repository.observeBackupScheduleSettings().first()
        val settingsFile = File(tempWorkDir, "backup_schedule_settings.json")
        val originalBytes = settingsFile.readBytes()
        repository.scheduleSettingsPersistenceFailureHook = {
            throw IOException("Simulated partial schedule write")
        }
        try {
            repository.updateAutoBackupSettings(
                enabled = true,
                treeUri = "content://fake/tree/new",
                retentionCount = 3,
            )
            fail("Expected schedule persistence failure")
        } catch (_: IOException) {
            // Expected; caller must not observe nondurable settings.
        } finally {
            repository.scheduleSettingsPersistenceFailureHook = null
        }
        assertEquals(before, repository.observeBackupScheduleSettings().first())
        assertTrue(originalBytes.contentEquals(settingsFile.readBytes()))
    }

    private suspend fun populateSampleLibrary() {
        repository.ensureCatalogSeeded()
        val profiles = repository.observeProfiles().first()
        val speciesList = repository.observeSpeciesCatalog(null).first()
        val aqId = repository.createAquarium(
            AquariumDraft(
                name = "Acquario di Pietro 🌿🐟",
                netLitres = BigDecimal("42.5"),
                grossLitres = BigDecimal("50"),
                notes = "Caridina nano tank",
                profileIds = setOf(profiles.first().id),
            )
        )
        repository.recordMeasurement(
            aquariumId = aqId,
            draft = MeasurementDraft(
                ph = BigDecimal("6.5"),
                khDkh = BigDecimal("1.0"),
                ghDgh = BigDecimal("5.5"),
                temperatureC = BigDecimal("22.4"),
            ),
        )
        repository.recordMaintenanceEvent(
            aquariumId = aqId,
            draft = MaintenanceDraft(
                type = "WATER_CHANGE",
                changedLitres = BigDecimal("10"),
                notes = "Weekly RO change",
            ),
        )
        val customSpecies = repository.createSpecies(
            SpeciesDraft(
                kind = SpeciesKind.FAUNA,
                commonName = "Crystal Red Shrimp",
                scientificName = "Caridina cf. cantonensis",
            )
        )
        repository.createStockEntry(
            aquariumId = aqId,
            draft = StockEntryDraft(
                speciesId = customSpecies.id,
                quantity = BigDecimal("12"),
                notes = "Breeding group",
            ),
        )
        val product = repository.createProduct("SaltyShrimp GH+")
        val planId = repository.createDosingPlan(
            aquariumId = aqId,
            draft = DosingPlanDraft(
                productId = product.id,
                dose = BigDecimal("1.5"),
                unit = "g",
                frequency = DosingFrequency.WEEKLY,
                weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
                startDate = LocalDate.parse("2026-10-01"),
                timeZone = ZoneId.of("Europe/Rome"),
            ),
        )
        repository.recordDosingFromPlan(aquariumId = aqId, planId = planId)
        repository.recordAdHocDosingEvent(
            aquariumId = aqId,
            draft = DosingEventDraft(
                productNameSnapshot = "Bacter AE",
                dose = BigDecimal("0.5"),
                unit = "g",
            ),
        )
        assertTrue(speciesList.isNotEmpty())
    }

    private fun newRepositoryForRestart() = RoomAquariumRepository(
        dao = database.aquariumDao(),
        clock = { Instant.ofEpochSecond(nowEpochSecond++) },
        documentGateway = fakeGateway,
        backupWorkDir = tempWorkDir,
        appVersion = "0.1.0",
    )

    private fun patchAllZipEntryHeaders(
        zipBytes: ByteArray,
        compressionMethod: Int? = null,
        encrypted: Boolean = false,
    ): ByteArray {
        val patched = zipBytes.copyOf()
        var offset = 0
        while (offset + 10 <= patched.size) {
            val signature = readIntLe(patched, offset)
            when (signature) {
                0x04034b50 -> {
                    if (encrypted) patched[offset + 6] = (patched[offset + 6].toInt() or 0x01).toByte()
                    if (compressionMethod != null) writeShortLe(patched, offset + 8, compressionMethod)
                }
                0x02014b50 -> {
                    if (encrypted) patched[offset + 8] = (patched[offset + 8].toInt() or 0x01).toByte()
                    if (compressionMethod != null) writeShortLe(patched, offset + 10, compressionMethod)
                }
            }
            offset++
        }
        return patched
    }

    private fun readIntLe(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun writeShortLe(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun buildCustomZip(
        entries: List<Pair<String, ByteArray>>,
        compressionLevel: Int = Deflater.DEFAULT_COMPRESSION,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val seen = mutableSetOf<String>()
        val replacements = mutableListOf<Pair<ByteArray, ByteArray>>()
        ZipOutputStream(out).use { zip ->
            zip.setLevel(compressionLevel)
            var dupIndex = 1
            for ((name, bytes) in entries) {
                val wireName = if (seen.add(name)) {
                    name
                } else {
                    val prefix = "dup${dupIndex++}_"
                    require(name.length >= prefix.length) { "Entry name too short for duplicate replacement" }
                    val placeholder = prefix + name.substring(prefix.length)
                    replacements.add(
                        placeholder.toByteArray(Charsets.UTF_8) to name.toByteArray(Charsets.UTF_8)
                    )
                    placeholder
                }
                val entry = ZipEntry(wireName)
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        val rawBytes = out.toByteArray()
        for ((fromBytes, toBytes) in replacements) {
            replaceAllSubsequences(rawBytes, fromBytes, toBytes)
        }
        return rawBytes
    }

    private fun replaceAllSubsequences(target: ByteArray, pattern: ByteArray, replacement: ByteArray) {
        require(pattern.size == replacement.size)
        var i = 0
        while (i <= target.size - pattern.size) {
            var match = true
            for (j in pattern.indices) {
                if (target[i + j] != pattern[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                System.arraycopy(replacement, 0, target, i, replacement.size)
                i += pattern.size
            } else {
                i++
            }
        }
    }

    private fun assertThrowsIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected
        }
    }

    private suspend fun assertThrowsIllegalArgumentSuspend(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected
        }
    }
}

private class FakeBackupDocumentGateway : BackupDocumentGateway {
    private val files = linkedMapOf<String, ByteArray>()
    val persistedTrees = mutableSetOf<String>()
    val failWriteForUris = mutableSetOf<String>()
    val failedDeleteUris = mutableSetOf<String>()
    val corruptOnWriteUris = mutableSetOf<String>()
    var failNextAutoCreateWrite: Boolean = false
    var failDeleteForNextAuto: Boolean = false
    val createdAutoUris = mutableListOf<String>()
    private var autoCounter = 1

    fun exists(uri: String): Boolean = uri in files

    override suspend fun writeDocument(destinationUri: String, bytes: ByteArray) {
        if (destinationUri in failWriteForUris) {
            files[destinationUri] = ByteArray(12) { 0x1F }
            throw IOException("Simulated write failure for $destinationUri")
        }
        if (destinationUri in corruptOnWriteUris) {
            files[destinationUri] = bytes.copyOfRange(0, maxOf(1, bytes.size / 2))
            return
        }
        files[destinationUri] = bytes.clone()
    }

    override suspend fun openDocumentInputStream(sourceUri: String): InputStream {
        val bytes = files[sourceUri] ?: throw IOException("Document not found: $sourceUri")
        return ByteArrayInputStream(bytes)
    }

    override suspend fun deleteDocumentBestEffort(documentUri: String): Boolean {
        if (documentUri in failedDeleteUris) return false
        return files.remove(documentUri) != null
    }

    override suspend fun deleteDocumentResult(documentUri: String): BackupDocumentDeleteResult = when {
        documentUri in files && documentUri in failedDeleteUris -> BackupDocumentDeleteResult.FAILED
        documentUri in files -> {
            files.remove(documentUri)
            BackupDocumentDeleteResult.DELETED
        }
        else -> BackupDocumentDeleteResult.NOT_FOUND
    }

    override suspend fun hasPersistedTreePermission(treeUri: String): Boolean {
        return treeUri in persistedTrees
    }

    override suspend fun createAutoBackupDocument(treeUri: String, fileName: String): String {
        val uri = "$treeUri/${autoCounter++}-$fileName"
        createdAutoUris.add(uri)
        if (failDeleteForNextAuto) {
            failDeleteForNextAuto = false
            failedDeleteUris.add(uri)
        }
        if (failNextAutoCreateWrite) {
            failNextAutoCreateWrite = false
            corruptOnWriteUris.add(uri)
        }
        files[uri] = ByteArray(0)
        return uri
    }

    override suspend fun findAutoBackupDocument(treeUri: String, fileName: String): String? =
        files.keys.firstOrNull { it.startsWith("$treeUri/") && it.endsWith(fileName) }
}

private class TrackingDispatcher : CoroutineDispatcher() {
    var wasDispatched = false
        private set

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        wasDispatched = true
        Dispatchers.Default.dispatch(context, block)
    }
}
