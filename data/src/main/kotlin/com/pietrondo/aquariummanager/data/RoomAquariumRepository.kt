package com.pietrondo.aquariummanager.data

import android.util.AtomicFile
import com.pietrondo.aquariummanager.domain.AquariumDetail
import com.pietrondo.aquariummanager.domain.AquariumDraft
import com.pietrondo.aquariummanager.domain.AquariumProfile
import com.pietrondo.aquariummanager.domain.AquariumRepository
import com.pietrondo.aquariummanager.domain.AquariumSummary
import com.pietrondo.aquariummanager.domain.BackupDocumentGateway
import com.pietrondo.aquariummanager.domain.BackupDocumentDeleteResult
import com.pietrondo.aquariummanager.domain.BackupExportResult
import com.pietrondo.aquariummanager.domain.BackupManifest
import com.pietrondo.aquariummanager.domain.BackupRestorePreview
import com.pietrondo.aquariummanager.domain.BackupScheduleSettings
import com.pietrondo.aquariummanager.domain.CatalogBundle
import com.pietrondo.aquariummanager.domain.DoseUnitCatalogItem
import com.pietrondo.aquariummanager.domain.DosingEventDraft
import com.pietrondo.aquariummanager.domain.DosingEventRecord
import com.pietrondo.aquariummanager.domain.DosingFrequency
import com.pietrondo.aquariummanager.domain.DosingPlanDraft
import com.pietrondo.aquariummanager.domain.DosingPlanRecord
import com.pietrondo.aquariummanager.domain.MaintenanceDraft
import com.pietrondo.aquariummanager.domain.MaintenanceRecord
import com.pietrondo.aquariummanager.domain.MaintenanceTypeCatalogItem
import com.pietrondo.aquariummanager.domain.MeasurementDraft
import com.pietrondo.aquariummanager.domain.MeasurementRecord
import com.pietrondo.aquariummanager.domain.ProductRecord
import com.pietrondo.aquariummanager.domain.SpeciesCatalogItem
import com.pietrondo.aquariummanager.domain.SpeciesDraft
import com.pietrondo.aquariummanager.domain.SpeciesKind
import com.pietrondo.aquariummanager.domain.StockEntryDraft
import com.pietrondo.aquariummanager.domain.StockEntryRecord
import com.pietrondo.aquariummanager.domain.filterByKind
import com.pietrondo.aquariummanager.domain.validateNonBlank
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RoomAquariumRepository(
    private val dao: AquariumDao,
    private val clock: () -> Instant = Instant::now,
    private val documentGateway: BackupDocumentGateway? = null,
    private val backupWorkDir: File? = null,
    private val appVersion: String = "0.1.0",
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AquariumRepository {
    var safetyBackupFailureHook: (() -> Unit)? = null
    var safetyBackupPromotionFailureHook: (() -> Unit)? = null
    var midImportFailureHook: (() -> Unit)? = null
    var scheduleSettingsPersistenceFailureHook: (() -> Unit)? = null

    private val operationMutex = Mutex()
    private val defaultCatalog: CatalogBundle by lazy { CatalogJsonParser.loadDefaultCatalog() }
    private val maintenanceCatalogFlow = MutableStateFlow(defaultCatalog.maintenanceTypes)
    private val doseUnitCatalogFlow = MutableStateFlow(defaultCatalog.doseUnits)
    private val scheduleSettingsFlow = MutableStateFlow(BackupScheduleSettings())
    private val scheduleSettingsLoadMutex = Mutex()
    private var scheduleSettingsLoaded = false

    override fun observeAquariums(): Flow<List<AquariumSummary>> =
        dao.observeAquariums().map { rows -> rows.map { it.toSummary() } }

    override fun observeAquariumDetails(): Flow<List<AquariumDetail>> =
        combine(
            dao.observeAquariums(),
            dao.observeProfiles(),
            dao.observeAllProfileLinks(),
        ) { aquariums, profiles, links ->
            val profileById = profiles.associate { it.id to it.toDomain() }
            val linksByAquarium = links.groupBy(
                keySelector = { it.aquariumId },
                valueTransform = { it.profileId },
            )
            aquariums.map { aquarium ->
                val assignedProfiles = linksByAquarium[aquarium.id]
                    .orEmpty()
                    .mapNotNull { profileById[it] }
                    .sortedWith(compareBy<AquariumProfile> { it.name }.thenBy { it.id })
                aquarium.toDetail(assignedProfiles)
            }
        }

    override fun observeProfiles(): Flow<List<AquariumProfile>> =
        dao.observeProfiles().map { rows -> rows.map { it.toDomain() } }

    override fun observeMeasurements(aquariumId: UUID): Flow<List<MeasurementRecord>> =
        dao.observeMeasurementsForAquarium(aquariumId).map { rows -> rows.map { it.toDomain() } }

    override fun observeMaintenanceEvents(aquariumId: UUID): Flow<List<MaintenanceRecord>> =
        dao.observeMaintenanceEventsForAquarium(aquariumId).map { rows -> rows.map { it.toDomain() } }

    override fun observeMaintenanceTypeCatalog(): Flow<List<MaintenanceTypeCatalogItem>> =
        maintenanceCatalogFlow.asStateFlow()

    override fun observeSpeciesCatalog(kind: SpeciesKind?): Flow<List<SpeciesCatalogItem>> =
        dao.observeAllSpecies().map { rows ->
            rows.map { it.toDomain() }.filterByKind(kind)
        }

    override fun observeStockEntries(aquariumId: UUID): Flow<List<StockEntryRecord>> =
        combine(
            dao.observeStockEntriesForAquarium(aquariumId),
            dao.observeAllSpecies(),
        ) { entries, speciesRows ->
            val speciesById = speciesRows.associate { it.id to it.toDomain() }
            entries.mapNotNull { entry ->
                val species = speciesById[entry.speciesId] ?: return@mapNotNull null
                entry.toDomain(species)
            }
        }

    override fun observeProducts(): Flow<List<ProductRecord>> =
        dao.observeAllProducts().map { rows -> rows.map { it.toDomain() } }

    override fun observeDoseUnitCatalog(): Flow<List<DoseUnitCatalogItem>> =
        doseUnitCatalogFlow.asStateFlow()

    override fun observeDosingPlans(aquariumId: UUID): Flow<List<DosingPlanRecord>> =
        combine(
            dao.observeDosingPlansForAquarium(aquariumId),
            dao.observeAllProducts(),
        ) { plans, products ->
            val productById = products.associate { it.id to it.toDomain() }
            plans.mapNotNull { plan ->
                val product = productById[plan.productId] ?: return@mapNotNull null
                plan.toDomain(product)
            }
        }

    override fun observeDosingEvents(aquariumId: UUID): Flow<List<DosingEventRecord>> =
        dao.observeDosingEventsForAquarium(aquariumId).map { rows -> rows.map { it.toDomain() } }

    override fun observeBackupScheduleSettings(): Flow<BackupScheduleSettings> = flow {
        ensureScheduleSettingsLoaded()
        emitAll(scheduleSettingsFlow.asStateFlow())
    }

    override suspend fun ensureCatalogSeeded() {
        seedCatalog(defaultCatalog)
    }

    suspend fun seedCatalog(bundle: CatalogBundle) = operationMutex.withLock {
        val profileEntities = bundle.profileLabels.map {
            AquariumProfileEntity(id = it.id, name = it.name.trim())
        }
        val speciesEntities = bundle.species.map {
            SpeciesEntity(
                id = it.id,
                kind = it.kind,
                scientificName = it.scientificName?.trim()?.takeIf { s -> s.isNotEmpty() },
                commonName = it.commonName.trim(),
            )
        }
        dao.seedCatalogEntities(profileEntities, speciesEntities)
        maintenanceCatalogFlow.value = bundle.maintenanceTypes
        doseUnitCatalogFlow.value = bundle.doseUnits
    }

    override suspend fun createAquarium(draft: AquariumDraft): UUID = operationMutex.withLock {
        val id = UUID.randomUUID()
        val normalizedNotes = draft.notes?.trim()?.takeIf { it.isNotEmpty() }
        val entity = AquariumEntity(
            id = id,
            name = draft.name.trim(),
            netLitres = draft.netLitres,
            grossLitres = draft.grossLitres,
            notes = normalizedNotes,
            createdAt = clock(),
        )
        dao.insertAquariumWithProfiles(entity, draft.profileIds)
        id
    }

    override suspend fun updateAquarium(id: UUID, draft: AquariumDraft) = operationMutex.withLock {
        val trimmedName = draft.name.trim()
        validateNonBlank(trimmedName, "Aquarium name")
        val normalizedNotes = draft.notes?.trim()?.takeIf { it.isNotEmpty() }
        dao.updateAquariumWithProfiles(
            id = id,
            name = trimmedName,
            netLitres = draft.netLitres,
            grossLitres = draft.grossLitres,
            notes = normalizedNotes,
            profileIds = draft.profileIds,
        )
    }

    override suspend fun deleteAquarium(id: UUID) = operationMutex.withLock {
        dao.deleteAquariumWithHistories(id)
    }

    override suspend fun createProfile(name: String): AquariumProfile = operationMutex.withLock {
        val trimmed = name.trim()
        validateNonBlank(trimmed, "Aquarium profile name")
        val created = dao.createUniqueProfile(UUID.randomUUID(), trimmed)
        created.toDomain()
    }

    override suspend fun recordMeasurement(aquariumId: UUID, draft: MeasurementDraft): UUID =
        operationMutex.withLock {
            val id = UUID.randomUUID()
            val entity = MeasurementEntity(
                id = id,
                aquariumId = aquariumId,
                recordedAt = draft.recordedAt ?: clock(),
                ph = draft.ph,
                khDkh = draft.khDkh,
                ghDgh = draft.ghDgh,
                no2MgL = draft.no2MgL,
                no3MgL = draft.no3MgL,
                tdsPpm = draft.tdsPpm,
                temperatureC = draft.temperatureC,
            )
            dao.insertMeasurement(entity)
            id
        }

    override suspend fun deleteMeasurement(id: UUID) = operationMutex.withLock {
        dao.deleteMeasurementById(id)
    }

    override suspend fun recordMaintenanceEvent(aquariumId: UUID, draft: MaintenanceDraft): UUID =
        operationMutex.withLock {
            val trimmedType = draft.type.trim()
            requireKnownMaintenanceType(trimmedType)
            val id = UUID.randomUUID()
            val entity = MaintenanceEventEntity(
                id = id,
                aquariumId = aquariumId,
                type = trimmedType,
                occurredAt = draft.occurredAt ?: clock(),
                changedLitres = draft.changedLitres,
                notes = draft.notes?.trim()?.takeIf { it.isNotEmpty() },
            )
            dao.insertMaintenanceEvent(entity)
            id
        }

    override suspend fun updateMaintenanceEvent(
        id: UUID,
        aquariumId: UUID,
        draft: MaintenanceDraft,
    ) = operationMutex.withLock {
        val trimmedType = draft.type.trim()
        requireKnownMaintenanceType(trimmedType)
        val existing = dao.getMaintenanceEventById(id)
            ?: throw IllegalArgumentException("Maintenance event not found: $id")
        val updated = existing.copy(
            aquariumId = aquariumId,
            type = trimmedType,
            occurredAt = draft.occurredAt ?: existing.occurredAt,
            changedLitres = draft.changedLitres,
            notes = draft.notes?.trim()?.takeIf { it.isNotEmpty() },
        )
        dao.updateMaintenanceEvent(updated)
    }

    override suspend fun deleteMaintenanceEvent(id: UUID) = operationMutex.withLock {
        dao.deleteMaintenanceEventById(id)
    }

    override suspend fun createSpecies(draft: SpeciesDraft): SpeciesCatalogItem =
        operationMutex.withLock {
            val trimmedCommon = draft.commonName.trim()
            val trimmedScientific = draft.scientificName?.trim()?.takeIf { it.isNotEmpty() }
            val created = dao.createUniqueSpecies(
                id = UUID.randomUUID(),
                kind = draft.kind,
                commonName = trimmedCommon,
                scientificName = trimmedScientific,
            )
            created.toDomain()
        }

    override suspend fun updateSpecies(id: UUID, draft: SpeciesDraft): SpeciesCatalogItem =
        operationMutex.withLock {
            val trimmedCommon = draft.commonName.trim()
            val trimmedScientific = draft.scientificName?.trim()?.takeIf { it.isNotEmpty() }
            val updated = dao.updateUniqueSpecies(
                id = id,
                kind = draft.kind,
                commonName = trimmedCommon,
                scientificName = trimmedScientific,
            )
            updated.toDomain()
        }

    override suspend fun deleteSpecies(id: UUID) = operationMutex.withLock {
        dao.deleteUnreferencedSpecies(id)
    }

    override suspend fun createStockEntry(aquariumId: UUID, draft: StockEntryDraft): UUID =
        operationMutex.withLock {
            require(dao.getSpeciesById(draft.speciesId) != null) {
                "Species not found: ${draft.speciesId}"
            }
            val id = UUID.randomUUID()
            val introducedAt = draft.introducedAt ?: clock()
            val entity = StockEntryEntity(
                id = id,
                aquariumId = aquariumId,
                speciesId = draft.speciesId,
                quantity = draft.quantity,
                introducedAt = introducedAt,
                exitedAt = draft.exitedAt,
                notes = draft.notes?.trim()?.takeIf { it.isNotEmpty() },
            )
            dao.insertStockEntry(entity)
            id
        }

    override suspend fun updateStockEntry(id: UUID, aquariumId: UUID, draft: StockEntryDraft) =
        operationMutex.withLock {
            require(dao.getSpeciesById(draft.speciesId) != null) {
                "Species not found: ${draft.speciesId}"
            }
            val existing = dao.getStockEntryById(id)
                ?: throw IllegalArgumentException("Stock entry not found: $id")
            val updated = existing.copy(
                aquariumId = aquariumId,
                speciesId = draft.speciesId,
                quantity = draft.quantity,
                introducedAt = draft.introducedAt ?: existing.introducedAt,
                exitedAt = draft.exitedAt,
                notes = draft.notes?.trim()?.takeIf { it.isNotEmpty() },
            )
            dao.updateStockEntry(updated)
        }

    override suspend fun markStockExited(id: UUID, exitedAt: Instant?) = operationMutex.withLock {
        val existing = dao.getStockEntryById(id)
            ?: throw IllegalArgumentException("Stock entry not found: $id")
        val resolvedExit = exitedAt ?: clock().let { now ->
            if (now.isBefore(existing.introducedAt)) existing.introducedAt else now
        }
        val updated = existing.copy(exitedAt = resolvedExit)
        dao.updateStockEntry(updated)
    }

    override suspend fun deleteStockEntry(id: UUID) = operationMutex.withLock {
        dao.deleteStockEntryById(id)
    }

    override suspend fun createProduct(name: String): ProductRecord = operationMutex.withLock {
        val trimmed = name.trim()
        validateNonBlank(trimmed, "Product name")
        val created = dao.createUniqueProduct(UUID.randomUUID(), trimmed)
        created.toDomain()
    }

    override suspend fun updateProduct(id: UUID, name: String): ProductRecord =
        operationMutex.withLock {
            val trimmed = name.trim()
            validateNonBlank(trimmed, "Product name")
            val updated = dao.updateUniqueProduct(id, trimmed)
            updated.toDomain()
        }

    override suspend fun deleteProduct(id: UUID) = operationMutex.withLock {
        dao.deleteUnreferencedProduct(id)
    }

    override suspend fun createDosingPlan(aquariumId: UUID, draft: DosingPlanDraft): UUID =
        operationMutex.withLock {
            val trimmedUnit = draft.unit.trim()
            requireKnownDoseUnit(trimmedUnit)
            require(dao.getProductById(draft.productId) != null) {
                "Product not found: ${draft.productId}"
            }
            val id = UUID.randomUUID()
            val entity = DosingPlanEntity(
                id = id,
                aquariumId = aquariumId,
                productId = draft.productId,
                dose = draft.dose,
                unit = trimmedUnit,
                frequency = draft.frequency,
                weekdays = if (draft.frequency == DosingFrequency.DAILY) {
                    emptySet()
                } else {
                    draft.weekdays
                },
                startDate = draft.startDate,
                endDate = draft.endDate,
                timeZone = draft.timeZone,
            )
            dao.insertDosingPlan(entity)
            id
        }

    override suspend fun updateDosingPlan(id: UUID, aquariumId: UUID, draft: DosingPlanDraft) =
        operationMutex.withLock {
            val trimmedUnit = draft.unit.trim()
            requireKnownDoseUnit(trimmedUnit)
            require(dao.getProductById(draft.productId) != null) {
                "Product not found: ${draft.productId}"
            }
            val existing = dao.getDosingPlanById(id)
                ?: throw IllegalArgumentException("Dosing plan not found: $id")
            val updated = existing.copy(
                aquariumId = aquariumId,
                productId = draft.productId,
                dose = draft.dose,
                unit = trimmedUnit,
                frequency = draft.frequency,
                weekdays = if (draft.frequency == DosingFrequency.DAILY) {
                    emptySet()
                } else {
                    draft.weekdays
                },
                startDate = draft.startDate,
                endDate = draft.endDate,
                timeZone = draft.timeZone,
            )
            dao.updateDosingPlan(updated)
        }

    override suspend fun deleteDosingPlan(id: UUID) = operationMutex.withLock {
        dao.deleteDosingPlanById(id)
    }

    override suspend fun recordDosingFromPlan(
        aquariumId: UUID,
        planId: UUID,
        occurredAt: Instant?,
    ): UUID = operationMutex.withLock {
        val eventId = UUID.randomUUID()
        dao.recordPlanDosingSnapshot(
            eventId = eventId,
            aquariumId = aquariumId,
            planId = planId,
            occurredAt = occurredAt ?: clock(),
        )
        eventId
    }

    override suspend fun recordAdHocDosingEvent(
        aquariumId: UUID,
        draft: DosingEventDraft,
    ): UUID = operationMutex.withLock {
        val trimmedProduct = draft.productNameSnapshot.trim()
        validateNonBlank(trimmedProduct, "Product name snapshot")
        val trimmedUnit = draft.unit.trim()
        requireKnownDoseUnit(trimmedUnit)
        val eventId = UUID.randomUUID()
        val entity = DosingEventEntity(
            id = eventId,
            planId = null,
            aquariumId = aquariumId,
            occurredAt = draft.occurredAt ?: clock(),
            productNameSnapshot = trimmedProduct,
            dose = draft.dose,
            unit = trimmedUnit,
        )
        dao.insertDosingEvent(entity)
        eventId
    }

    override suspend fun deleteDosingEvent(id: UUID) = operationMutex.withLock {
        dao.deleteDosingEventById(id)
    }

    override suspend fun exportBackupBytes(): ByteArray = operationMutex.withLock {
        buildVerifiedCurrentSnapshotZip().first
    }

    override suspend fun exportBackupToUri(destinationUri: String): BackupExportResult =
        operationMutex.withLock {
            val gateway = requireDocumentGateway()
            val (zipBytes, manifest) = buildVerifiedCurrentSnapshotZip()
            writeAndReadBackVerifyDestination(gateway, destinationUri, zipBytes, manifest, deleteOnFailure = false)
            BackupExportResult(destinationUri = destinationUri, manifest = manifest)
        }

    override suspend fun inspectBackupUri(sourceUri: String): BackupRestorePreview {
        val gateway = requireDocumentGateway()
        val verified = gateway.openDocumentInputStream(sourceUri).use { stream ->
            withContext(defaultDispatcher) {
                LibraryBackupCodec.inspectAndParseZip(stream, activeCatalogBundle())
            }
        }
        return verified.toPreview(sourceUri = sourceUri)
    }

    override suspend fun inspectBackupBytes(
        zipBytes: ByteArray,
        sourceUri: String?,
    ): BackupRestorePreview {
        val verified = withContext(defaultDispatcher) {
            LibraryBackupCodec.inspectAndParseZip(
                inputStream = ByteArrayInputStream(zipBytes),
                catalog = activeCatalogBundle(),
            )
        }
        return verified.toPreview(sourceUri = sourceUri)
    }

    override suspend fun restoreBackupFromUri(
        sourceUri: String,
        confirmReplace: Boolean,
    ): BackupRestorePreview {
        require(confirmReplace) {
            "Explicit replace confirmation is required before restoring a library backup"
        }
        val gateway = requireDocumentGateway()
        val verified = gateway.openDocumentInputStream(sourceUri).use { stream ->
            withContext(defaultDispatcher) {
                LibraryBackupCodec.inspectAndParseZip(stream, activeCatalogBundle())
            }
        }
        return executeVerifiedReplace(verified, sourceUri = sourceUri)
    }

    override suspend fun restoreBackupFromBytes(
        zipBytes: ByteArray,
        confirmReplace: Boolean,
    ): BackupRestorePreview {
        require(confirmReplace) {
            "Explicit replace confirmation is required before restoring a library backup"
        }
        val verified = withContext(defaultDispatcher) {
            LibraryBackupCodec.inspectAndParseZip(
                inputStream = ByteArrayInputStream(zipBytes),
                catalog = activeCatalogBundle(),
            )
        }
        return executeVerifiedReplace(verified, sourceUri = null)
    }

    override suspend fun updateAutoBackupSettings(
        enabled: Boolean,
        treeUri: String?,
        retentionCount: Int,
    ): BackupScheduleSettings = operationMutex.withLock {
        ensureScheduleSettingsLoaded()
        val current = scheduleSettingsFlow.value
        val normalizedTree = treeUri?.trim()?.takeIf { it.isNotEmpty() }
        val resetGenerations = if (normalizedTree != current.treeUri) {
            emptyList()
        } else {
            current.verifiedAutoGenerations
        }
        val statusMessage = when {
            !enabled -> "Automatic daily backup paused"
            normalizedTree == null -> "Select a backup folder to enable automatic daily backups"
            else -> current.lastBackupStatus ?: "Automatic daily backup enabled (keeps last $retentionCount verified backups)"
        }
        val updated = current.copy(
            autoBackupEnabled = enabled,
            treeUri = normalizedTree,
            retentionCount = retentionCount.coerceAtLeast(1),
            lastBackupStatus = statusMessage,
            verifiedAutoGenerations = resetGenerations,
            pendingCleanupUris = if (normalizedTree != current.treeUri) emptyList() else current.pendingCleanupUris,
            pendingGenerationUris = if (normalizedTree != current.treeUri) emptyList() else current.pendingGenerationUris,
            pendingGenerationFileNames = if (normalizedTree != current.treeUri) emptyList() else current.pendingGenerationFileNames,
        )
        persistScheduleSettings(updated)
        updated
    }

    override suspend fun runAutomaticBackupNow(): BackupScheduleSettings = operationMutex.withLock {
        ensureScheduleSettingsLoaded()
        var current = scheduleSettingsFlow.value
        val treeUri = current.treeUri
        if (treeUri.isNullOrBlank()) {
            val updated = current.copy(
                lastBackupStatus = "Folder permission required — please select a backup folder",
            )
            persistScheduleSettings(updated)
            return@withLock updated
        }
        val gateway = documentGateway
        if (gateway == null || !gateway.hasPersistedTreePermission(treeUri)) {
            val updated = current.copy(
                lastBackupStatus = "Folder permission required — please reselect backup folder",
            )
            persistScheduleSettings(updated)
            return@withLock updated
        }

        if (current.pendingGenerationFileNames.isNotEmpty()) {
            val remainingNames = current.pendingGenerationFileNames.toMutableList()
            val pendingUris = current.pendingGenerationUris.toMutableList()
            val verified = current.verifiedAutoGenerations.toMutableList()
            for (name in current.pendingGenerationFileNames) {
                var lookupFailed = false
                val uri = try {
                    gateway.findAutoBackupDocument(treeUri, name)
                } catch (_: Exception) {
                    lookupFailed = true
                    null
                }
                if (lookupFailed) continue
                if (uri == null) {
                    remainingNames.remove(name)
                    continue
                }
                val archive = try {
                    gateway.openDocumentInputStream(uri).use { input ->
                        withContext(defaultDispatcher) {
                            LibraryBackupCodec.inspectAndParseZip(input, activeCatalogBundle())
                        }
                    }
                } catch (_: Exception) {
                    null
                }
                if (archive != null) {
                    if (uri !in verified) verified.add(uri)
                    pendingUris.remove(uri)
                    remainingNames.remove(name)
                } else {
                    when (gateway.deleteDocumentResult(uri)) {
                        BackupDocumentDeleteResult.DELETED, BackupDocumentDeleteResult.NOT_FOUND -> {
                            pendingUris.remove(uri)
                            remainingNames.remove(name)
                        }
                        BackupDocumentDeleteResult.FAILED -> Unit
                    }
                }
            }
            if (remainingNames.size != current.pendingGenerationFileNames.size ||
                pendingUris.size != current.pendingGenerationUris.size ||
                verified.size != current.verifiedAutoGenerations.size
            ) {
                current = current.copy(
                    pendingGenerationFileNames = remainingNames,
                    pendingGenerationUris = pendingUris,
                    verifiedAutoGenerations = verified,
                )
                persistScheduleSettings(current)
            }
            if (remainingNames.isNotEmpty()) {
                val updated = current.copy(lastBackupStatus = "Automatic backup skipped: pending generation recovery " +
                    "(${remainingNames.size} document(s) could not be verified or removed)")
                persistScheduleSettings(updated)
                return@withLock updated
            }
        }

        if (current.pendingGenerationUris.isNotEmpty()) {
            val pending = current.pendingGenerationUris.toMutableList()
            val verified = current.verifiedAutoGenerations.toMutableList()
            for (uri in current.pendingGenerationUris) {
                val archive = try {
                    gateway.openDocumentInputStream(uri).use { input ->
                        withContext(defaultDispatcher) {
                            LibraryBackupCodec.inspectAndParseZip(input, activeCatalogBundle())
                        }
                    }
                } catch (_: Exception) {
                    null
                }
                if (archive != null) {
                    if (uri !in verified) verified.add(uri)
                    pending.remove(uri)
                } else when (gateway.deleteDocumentResult(uri)) {
                    BackupDocumentDeleteResult.DELETED, BackupDocumentDeleteResult.NOT_FOUND -> pending.remove(uri)
                    BackupDocumentDeleteResult.FAILED -> Unit
                }
            }
            if (pending.isNotEmpty()) {
                val updated = current.copy(
                    pendingGenerationUris = pending,
                    verifiedAutoGenerations = verified,
                    lastBackupStatus = "Automatic backup skipped: pending generation recovery " +
                        "(${pending.size} document(s) could not be verified or removed)",
                )
                persistScheduleSettings(updated)
                return@withLock updated
            }
            current = current.copy(pendingGenerationUris = emptyList(), verifiedAutoGenerations = verified)
            persistScheduleSettings(current)
        }

        if (current.pendingCleanupUris.isNotEmpty()) {
            val remaining = current.pendingCleanupUris.toMutableList()
            for (uri in current.pendingCleanupUris) {
                if (gateway.deleteDocumentResult(uri) != BackupDocumentDeleteResult.FAILED) remaining.remove(uri)
            }
            if (remaining.isNotEmpty()) {
                val updated = current.copy(
                    pendingCleanupUris = remaining,
                    lastBackupStatus = "Automatic backup skipped: partial-file cleanup pending " +
                        "(${remaining.size} document(s) could not be removed)",
                )
                persistScheduleSettings(updated)
                return@withLock updated
            }
            current = current.copy(pendingCleanupUris = emptyList())
            persistScheduleSettings(current)
        }

        val retention = current.retentionCount.coerceAtLeast(1)
        val priorOverflow = (current.verifiedAutoGenerations.size - retention).coerceAtLeast(0)
        if (priorOverflow > 0) {
            val remaining = current.verifiedAutoGenerations.toMutableList()
            var cleanupFailures = 0
            for (oldUri in current.verifiedAutoGenerations.take(priorOverflow)) {
                if (gateway.deleteDocumentResult(oldUri) != BackupDocumentDeleteResult.FAILED) remaining.remove(oldUri)
                else cleanupFailures++
            }
            if (cleanupFailures > 0) {
                val updated = current.copy(
                    lastBackupStatus = "Automatic backup skipped: retention cleanup pending " +
                        "($cleanupFailures of $priorOverflow old files could not be removed)",
                )
                persistScheduleSettings(updated)
                return@withLock updated
            }
            current = current.copy(verifiedAutoGenerations = remaining)
            persistScheduleSettings(current)
        }

        val now = clock()
        val shortToken = UUID.randomUUID().toString().substring(0, 8)
        val fileName = "aquarium-auto-backup-${now.toEpochMilli()}-$shortToken.zip"
        var createdDocUri: String? = null
        var createdDocVerified = false

        return@withLock try {
            val (zipBytes, manifest) = buildVerifiedCurrentSnapshotZip(createdAt = now)
            val markedName = current.copy(
                pendingGenerationFileNames = (current.pendingGenerationFileNames + fileName).distinct(),
            )
            persistScheduleSettings(markedName)
            current = markedName
            val newDocUri = gateway.createAutoBackupDocument(treeUri, fileName)
            createdDocUri = newDocUri
            val markedPending = current.copy(
                pendingGenerationUris = (current.pendingGenerationUris + newDocUri).distinct(),
            )
            try {
                persistScheduleSettings(markedPending)
            } catch (failure: Exception) {
                // No durable recovery marker exists. Best-effort remove the new provider document.
                gateway.deleteDocumentBestEffort(newDocUri)
                throw failure
            }
            current = markedPending
            writeAndReadBackVerifyDestination(gateway, newDocUri, zipBytes, manifest, deleteOnFailure = false)
            createdDocVerified = true

            val allVerified = current.verifiedAutoGenerations + newDocUri
            val excessCount = (allVerified.size - retention).coerceAtLeast(0)
            val toPrune = allVerified.take(excessCount)
            val retained = allVerified.drop(excessCount).toMutableList()
            var cleanupFailures = 0

            for (oldUri in toPrune) {
                if (gateway.deleteDocumentResult(oldUri) == BackupDocumentDeleteResult.FAILED) {
                    retained.add(0, oldUri)
                    cleanupFailures++
                }
            }

            val updated = current.copy(
                lastBackupAt = now,
                lastBackupStatus = if (cleanupFailures == 0) {
                    "Verified automatic backup saved (${manifest.counts.totalRows} records)"
                } else {
                    "Verified automatic backup saved (${manifest.counts.totalRows} records); " +
                        "retention cleanup pending ($cleanupFailures old file(s), " +
                        "${retained.size}/$retention generations tracked)"
                },
                verifiedAutoGenerations = retained,
                pendingGenerationUris = current.pendingGenerationUris - newDocUri,
                pendingGenerationFileNames = current.pendingGenerationFileNames - fileName,
            )
            persistScheduleSettings(updated)
            updated
        } catch (cancelled: CancellationException) {
            val uri = createdDocUri
            if (uri != null && !createdDocVerified && !gateway.deleteDocumentBestEffort(uri)) {
                persistScheduleSettings(
                    current.copy(pendingCleanupUris = (current.pendingCleanupUris + uri).distinct()),
                )
            }
            throw cancelled
        } catch (e: ScheduleSettingsPersistenceException) {
            throw e
        } catch (e: Exception) {
            val uri = createdDocUri
            val cleanupPending = uri != null && !createdDocVerified && !gateway.deleteDocumentBestEffort(uri)
            val updated = current.copy(
                pendingCleanupUris = if (cleanupPending) (current.pendingCleanupUris + uri!!).distinct()
                else current.pendingCleanupUris,
                pendingGenerationUris = if (uri != null && !createdDocVerified) {
                    current.pendingGenerationUris - uri
                } else current.pendingGenerationUris,
                pendingGenerationFileNames = if (uri != null && !createdDocVerified && !cleanupPending) {
                    current.pendingGenerationFileNames - fileName
                } else current.pendingGenerationFileNames,
                lastBackupStatus = if (cleanupPending) {
                    "Automatic backup failed: ${e.message ?: "storage error"}; partial-file cleanup pending"
                } else {
                    "Automatic backup failed: ${e.message ?: "storage error"}"
                },
            )
            persistScheduleSettings(updated)
            updated
        }
    }

    private suspend fun executeVerifiedReplace(
        verified: VerifiedBackupArchive,
        sourceUri: String?,
    ): BackupRestorePreview = operationMutex.withLock {
        // 1. Make and verify a safety snapshot of the current library before any live table mutation
        createAndVerifySafetySnapshot()

        // 2. Replace all 10 tables inside one Room @Transaction (rolls back on any failure)
        dao.replaceEntireLibrary(
            snapshot = verified.snapshot,
            afterClearHook = midImportFailureHook,
        )

        verified.toPreview(sourceUri = sourceUri)
    }

    private suspend fun createAndVerifySafetySnapshot() {
        safetyBackupFailureHook?.invoke()
        val (safetyBytes, safetyManifest) = buildVerifiedCurrentSnapshotZip()
        val workDir = backupWorkDir ?: return
        val safetyDir = File(workDir, "safety-backups")
        val tempSafetyFile = File(safetyDir, "safety-backup-${UUID.randomUUID()}.zip.tmp")
        val latestSafetyFile = File(safetyDir, "safety-backup-latest.zip")
        withContext(ioDispatcher) {
            if (!safetyDir.exists() && !safetyDir.mkdirs()) {
                throw IOException("Could not create safety-backups directory")
            }
            tempSafetyFile.writeBytes(safetyBytes)
        }
        try {
            val readbackVerified = withContext(defaultDispatcher) {
                tempSafetyFile.inputStream().use { input ->
                    LibraryBackupCodec.inspectAndParseZip(input, activeCatalogBundle())
                }
            }
            if (readbackVerified.manifest.librarySha256 != safetyManifest.librarySha256) {
                throw IOException("Safety backup readback SHA-256 verification failed")
            }
            promoteVerifiedSafetyBackup(tempSafetyFile, latestSafetyFile)
        } finally {
            withContext(ioDispatcher) { tempSafetyFile.delete() }
        }
    }

    private suspend fun buildVerifiedCurrentSnapshotZip(
        createdAt: Instant = clock(),
    ): Pair<ByteArray, BackupManifest> {
        val snapshot = withContext(ioDispatcher) { dao.readFullLibrarySnapshot() }
        val stageDir = backupWorkDir?.let { File(it, "staging") }
        return withContext(defaultDispatcher) {
            LibraryBackupCodec.buildVerifiedBackupZipBytes(
                snapshot = snapshot,
                createdAt = createdAt,
                appVersion = appVersion,
                catalog = activeCatalogBundle(),
                tempDir = stageDir,
            )
        }
    }

    private suspend fun writeAndReadBackVerifyDestination(
        gateway: BackupDocumentGateway,
        destinationUri: String,
        zipBytes: ByteArray,
        expectedManifest: BackupManifest,
        deleteOnFailure: Boolean,
    ) {
        try {
            gateway.writeDocument(destinationUri, zipBytes)
            val readback = gateway.openDocumentInputStream(destinationUri).use { stream ->
                withContext(defaultDispatcher) {
                    LibraryBackupCodec.inspectAndParseZip(stream, activeCatalogBundle())
                }
            }
            if (readback.manifest.librarySha256 != expectedManifest.librarySha256 ||
                readback.manifest.counts != expectedManifest.counts
            ) {
                throw IOException("Destination readback verification mismatch for $destinationUri")
            }
        } catch (cancelled: CancellationException) {
            if (deleteOnFailure) gateway.deleteDocumentBestEffort(destinationUri)
            throw cancelled
        } catch (e: Exception) {
            if (deleteOnFailure) gateway.deleteDocumentBestEffort(destinationUri)
            if (e is IOException) throw e
            throw IOException("Backup destination verification failed: ${e.message}", e)
        }
    }

    private suspend fun promoteVerifiedSafetyBackup(tempFile: File, latestFile: File) =
        withContext(ioDispatcher) {
            val atomicFile = android.util.AtomicFile(latestFile)
            val output = atomicFile.startWrite()
            try {
                tempFile.inputStream().use { input -> input.copyTo(output) }
                safetyBackupPromotionFailureHook?.invoke()
                atomicFile.finishWrite(output)
            } catch (failure: Exception) {
                atomicFile.failWrite(output)
                throw IOException("Could not promote verified safety backup", failure)
            }
        }

    private fun activeCatalogBundle(): CatalogBundle =
        defaultCatalog.copy(
            maintenanceTypes = maintenanceCatalogFlow.value,
            doseUnits = doseUnitCatalogFlow.value,
        )

    private fun requireDocumentGateway(): BackupDocumentGateway =
        documentGateway ?: throw IllegalStateException("BackupDocumentGateway is not configured")

    private fun loadInitialScheduleSettings(): BackupScheduleSettings {
        val dir = backupWorkDir ?: return BackupScheduleSettings()
        val file = File(dir, "backup_schedule_settings.json")
        if (!file.exists() && !File("${file.path}.bak").exists() && !File("${file.path}.new").exists()) {
            return BackupScheduleSettings()
        }
        return try {
            AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { reader ->
                LibraryBackupCodec.parseScheduleSettings(reader.readText())
            }
        } catch (_: Exception) {
            BackupScheduleSettings()
        }
    }

    private suspend fun ensureScheduleSettingsLoaded() = scheduleSettingsLoadMutex.withLock {
        if (!scheduleSettingsLoaded) {
            scheduleSettingsFlow.value = withContext(ioDispatcher) { loadInitialScheduleSettings() }
            scheduleSettingsLoaded = true
        }
    }

    private suspend fun persistScheduleSettings(settings: BackupScheduleSettings) {
        withContext(ioDispatcher) {
            val dir = backupWorkDir
            if (dir != null) {
                if (!dir.exists() && !dir.mkdirs()) {
                    throw ScheduleSettingsPersistenceException("Could not create backup settings directory")
                }
                val atomicFile = AtomicFile(File(dir, "backup_schedule_settings.json"))
                val output = try {
                    atomicFile.startWrite()
                } catch (failure: Exception) {
                    throw ScheduleSettingsPersistenceException("Could not start backup settings write", failure)
                }
                try {
                    output.write(LibraryBackupCodec.serializeScheduleSettings(settings).toByteArray(Charsets.UTF_8))
                    scheduleSettingsPersistenceFailureHook?.invoke()
                    atomicFile.finishWrite(output)
                } catch (failure: Exception) {
                    atomicFile.failWrite(output)
                    throw ScheduleSettingsPersistenceException("Could not persist automatic backup settings", failure)
                }
            }
        }
        scheduleSettingsFlow.value = settings
    }

    private class ScheduleSettingsPersistenceException(message: String, cause: Throwable? = null) :
        IOException(message, cause)

    private fun requireKnownMaintenanceType(code: String) {
        validateNonBlank(code, "Maintenance type code")
        val knownCodes = maintenanceCatalogFlow.value
        require(knownCodes.any { it.code == code }) {
            "Unknown maintenance type code: $code"
        }
    }

    private fun requireKnownDoseUnit(code: String) {
        validateNonBlank(code, "Dose unit code")
        val knownUnits = doseUnitCatalogFlow.value
        require(knownUnits.any { it.code == code }) {
            "Unknown dose unit code: $code"
        }
    }
}
