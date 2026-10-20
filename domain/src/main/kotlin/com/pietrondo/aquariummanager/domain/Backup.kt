package com.pietrondo.aquariummanager.domain

import java.io.InputStream
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

const val BACKUP_FORMAT_ID = "aquarium-manager-library"
const val CURRENT_BACKUP_FORMAT_VERSION = 1
val SUPPORTED_BACKUP_FORMAT_VERSIONS: Set<Int> = setOf(0, 1)

const val MAX_MANIFEST_BYTES = 64 * 1024
const val MAX_LIBRARY_BYTES = 50 * 1024 * 1024
const val MAX_EXPANSION_RATIO = 100L
const val EXPANSION_RATIO_MIN_COMPRESSED_BYTES = 1024L
const val MAX_TOTAL_ROWS = 250_000
const val MAX_TEXT_FIELD_LENGTH = 4_096
const val DEFAULT_AUTO_BACKUP_RETENTION_COUNT = 7

const val UNENCRYPTED_BACKUP_DISCLOSURE =
    "Backups are unencrypted ZIP archives containing manifest.json and library.json. Anyone with access to the file can read your aquarium notes and histories."

const val BIOLOGICAL_AND_DOSING_DISCLAIMER =
    "Species catalog entries, water parameter trends, and dosing schedules are informational records only. This app does not provide veterinary or biological advice and does not guarantee species suitability, water safety, or outcomes."

const val OFFLINE_PRIVACY_SUMMARY =
    "100% offline: no accounts, no analytics, no internet permission, and no cloud servers. Data stays in local Room storage and user-chosen SAF backup files."

const val APACHE_LICENSE_AND_NOTICES_TEXT = """
Aquarium Manager is licensed under the Apache License, Version 2.0.

Apache License, Version 2.0 - Summary & Key Terms:
- Section 7 (Disclaimer of Warranty): Unless required by applicable law or agreed to in writing, Licensor provides the Work on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied, including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
- Section 8 (Limitation of Liability): In no event and under no legal theory, whether in tort (including negligence), contract, or otherwise, unless required by applicable law (such as deliberate and grossly negligent acts) or agreed to in writing, shall any Contributor be liable to You for damages, including any direct, indirect, special, incidental, or consequential damages of any character arising as a result of this License or out of the use or inability to use the Work.
- Section 9 (Accepting Warranty or Additional Liability): While redistributing the Work or Derivative Works thereof, You may choose to offer, and charge a fee for, acceptance of support, warranty, indemnity, or other liability obligations and/or rights consistent with this License, only on Your own behalf and on Your sole responsibility.

Full license text: https://www.apache.org/licenses/LICENSE-2.0

Third-Party Open-Source Notices:
- AndroidX Room, Activity & Lifecycle (The Android Open Source Project) - Apache License 2.0
- Jetpack Compose & Material 3 (The Android Open Source Project) - Apache License 2.0
- Kotlin Standard Library & Kotlinx Coroutines (JetBrains s.r.o.) - Apache License 2.0

Development Tooling Disclosure:
- PixelLab is an optional development-time artwork tool only and is never bundled into the Android application or used at runtime.
"""

private val SHA256_HEX_REGEX = Regex("^[0-9a-f]{64}$")

data class BackupTableCounts(
    val aquariums: Int = 0,
    val aquariumProfiles: Int = 0,
    val aquariumProfileLinks: Int = 0,
    val measurements: Int = 0,
    val maintenanceEvents: Int = 0,
    val products: Int = 0,
    val dosingPlans: Int = 0,
    val dosingEvents: Int = 0,
    val species: Int = 0,
    val stockEntries: Int = 0,
) {
    val totalRows: Long
        get() = listOf(aquariums, aquariumProfiles, aquariumProfileLinks, measurements,
            maintenanceEvents, products, dosingPlans, dosingEvents, species, stockEntries)
            .sumOf { it.toLong() }
}

data class BackupManifest(
    val format: String = BACKUP_FORMAT_ID,
    val formatVersion: Int = CURRENT_BACKUP_FORMAT_VERSION,
    val appVersion: String,
    val createdAt: Instant,
    val counts: BackupTableCounts,
    val librarySha256: String,
)

data class AquariumSnapshotRecord(
    val id: UUID,
    val name: String,
    val netLitres: BigDecimal? = null,
    val grossLitres: BigDecimal? = null,
    val notes: String? = null,
    val createdAt: Instant,
) {
    init {
        validateNonBlank(name, "Aquarium name")
        requireBoundedLength(name, "Aquarium name")
        requireBoundedLength(notes, "Aquarium notes")
        validateAquariumVolumes(netLitres, grossLitres)
    }
}

data class AquariumProfileSnapshotRecord(
    val id: UUID,
    val name: String,
) {
    init {
        validateNonBlank(name, "Aquarium profile name")
        requireBoundedLength(name, "Aquarium profile name")
    }
}

data class AquariumProfileLinkSnapshotRecord(
    val aquariumId: UUID,
    val profileId: UUID,
)

data class MeasurementSnapshotRecord(
    val id: UUID,
    val aquariumId: UUID,
    val recordedAt: Instant,
    val ph: BigDecimal? = null,
    val khDkh: BigDecimal? = null,
    val ghDgh: BigDecimal? = null,
    val no2MgL: BigDecimal? = null,
    val no3MgL: BigDecimal? = null,
    val tdsPpm: BigDecimal? = null,
    val temperatureC: BigDecimal? = null,
) {
    init {
        validateMeasurementReadings(ph, khDkh, ghDgh, no2MgL, no3MgL, tdsPpm, temperatureC)
    }
}

data class MaintenanceEventSnapshotRecord(
    val id: UUID,
    val aquariumId: UUID,
    val type: String,
    val occurredAt: Instant,
    val changedLitres: BigDecimal? = null,
    val notes: String? = null,
) {
    init {
        validateMaintenanceEvent(type, changedLitres)
        requireBoundedLength(type, "Maintenance event type")
        requireBoundedLength(notes, "Maintenance event notes")
    }
}

data class ProductSnapshotRecord(
    val id: UUID,
    val name: String,
) {
    init {
        validateNonBlank(name, "Product name")
        requireBoundedLength(name, "Product name")
    }
}

data class DosingPlanSnapshotRecord(
    val id: UUID,
    val aquariumId: UUID,
    val productId: UUID,
    val dose: BigDecimal,
    val unit: String,
    val frequency: DosingFrequency,
    val weekdays: Set<DayOfWeek>,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val timeZone: ZoneId,
) {
    init {
        validateDosingPlan(dose, unit, frequency, weekdays, startDate, endDate)
        requireBoundedLength(unit, "Dose unit")
        requireBoundedLength(timeZone.id, "Time zone")
    }
}

data class DosingEventSnapshotRecord(
    val id: UUID,
    val planId: UUID? = null,
    val aquariumId: UUID,
    val occurredAt: Instant,
    val productNameSnapshot: String,
    val dose: BigDecimal,
    val unit: String,
) {
    init {
        validateDosingEvent(productNameSnapshot, dose, unit)
        requireBoundedLength(productNameSnapshot, "Product name snapshot")
        requireBoundedLength(unit, "Dose unit")
    }
}

data class SpeciesSnapshotRecord(
    val id: UUID,
    val kind: SpeciesKind,
    val scientificName: String? = null,
    val commonName: String,
) {
    init {
        validateSpecies(commonName, scientificName)
        requireBoundedLength(commonName, "Species common name")
        requireBoundedLength(scientificName, "Species scientific name")
    }
}

data class StockEntrySnapshotRecord(
    val id: UUID,
    val aquariumId: UUID,
    val speciesId: UUID,
    val quantity: BigDecimal? = null,
    val introducedAt: Instant,
    val exitedAt: Instant? = null,
    val notes: String? = null,
) {
    init {
        validateStockEntry(quantity, introducedAt, exitedAt)
        requireBoundedLength(notes, "Stock entry notes")
    }
}

data class LibrarySnapshot(
    val aquariums: List<AquariumSnapshotRecord> = emptyList(),
    val aquariumProfiles: List<AquariumProfileSnapshotRecord> = emptyList(),
    val aquariumProfileLinks: List<AquariumProfileLinkSnapshotRecord> = emptyList(),
    val measurements: List<MeasurementSnapshotRecord> = emptyList(),
    val maintenanceEvents: List<MaintenanceEventSnapshotRecord> = emptyList(),
    val products: List<ProductSnapshotRecord> = emptyList(),
    val dosingPlans: List<DosingPlanSnapshotRecord> = emptyList(),
    val dosingEvents: List<DosingEventSnapshotRecord> = emptyList(),
    val species: List<SpeciesSnapshotRecord> = emptyList(),
    val stockEntries: List<StockEntrySnapshotRecord> = emptyList(),
) {
    fun counts(): BackupTableCounts = BackupTableCounts(
        aquariums = aquariums.size,
        aquariumProfiles = aquariumProfiles.size,
        aquariumProfileLinks = aquariumProfileLinks.size,
        measurements = measurements.size,
        maintenanceEvents = maintenanceEvents.size,
        products = products.size,
        dosingPlans = dosingPlans.size,
        dosingEvents = dosingEvents.size,
        species = species.size,
        stockEntries = stockEntries.size,
    )
}

data class BackupRestorePreview(
    val formatVersion: Int,
    val appVersion: String,
    val createdAt: Instant,
    val counts: BackupTableCounts,
    val totalRows: Int = counts.totalRows.toInt(),
    val librarySha256: String,
    val sourceUri: String? = null,
)

data class BackupExportResult(
    val destinationUri: String,
    val manifest: BackupManifest,
)

data class BackupScheduleSettings(
    val autoBackupEnabled: Boolean = false,
    val treeUri: String? = null,
    val retentionCount: Int = DEFAULT_AUTO_BACKUP_RETENTION_COUNT,
    val lastBackupAt: Instant? = null,
    val lastBackupStatus: String? = null,
    val verifiedAutoGenerations: List<String> = emptyList(),
    val pendingCleanupUris: List<String> = emptyList(),
    val pendingGenerationUris: List<String> = emptyList(),
    val pendingGenerationFileNames: List<String> = emptyList(),
) {
    init {
        require(retentionCount >= 1) { "Retention count must be at least 1" }
    }
}

interface BackupDocumentGateway {
    suspend fun writeDocument(destinationUri: String, bytes: ByteArray)
    suspend fun openDocumentInputStream(sourceUri: String): InputStream
    suspend fun deleteDocumentBestEffort(documentUri: String): Boolean
    suspend fun hasPersistedTreePermission(treeUri: String): Boolean
    suspend fun createAutoBackupDocument(treeUri: String, fileName: String): String
    suspend fun findAutoBackupDocument(treeUri: String, fileName: String): String?
    suspend fun deleteDocumentResult(documentUri: String): BackupDocumentDeleteResult
}

enum class BackupDocumentDeleteResult { DELETED, NOT_FOUND, FAILED }

fun validateBackupManifest(manifest: BackupManifest) {
    require(manifest.format == BACKUP_FORMAT_ID) {
        "Unsupported backup format: ${manifest.format}"
    }
    require(manifest.formatVersion in SUPPORTED_BACKUP_FORMAT_VERSIONS) {
        "Unsupported backup formatVersion: ${manifest.formatVersion}"
    }
    validateNonBlank(manifest.appVersion, "Backup appVersion")
    requireBoundedLength(manifest.appVersion, "Backup appVersion")
    val counts = manifest.counts
    require(
        counts.aquariums >= 0 &&
            counts.aquariumProfiles >= 0 &&
            counts.aquariumProfileLinks >= 0 &&
            counts.measurements >= 0 &&
            counts.maintenanceEvents >= 0 &&
            counts.products >= 0 &&
            counts.dosingPlans >= 0 &&
            counts.dosingEvents >= 0 &&
            counts.species >= 0 &&
            counts.stockEntries >= 0
    ) {
        "Backup table counts cannot be negative"
    }
    require(counts.totalRows <= MAX_TOTAL_ROWS.toLong()) {
        "Backup total rows (${counts.totalRows}) exceeds maximum limit ($MAX_TOTAL_ROWS)"
    }
    require(SHA256_HEX_REGEX.matches(manifest.librarySha256)) {
        "Invalid librarySha256 in manifest: must be 64 lowercase hex characters"
    }
}

fun validateLibrarySnapshot(snapshot: LibrarySnapshot, catalog: CatalogBundle) {
    val totalRows = snapshot.counts().totalRows
    require(totalRows <= MAX_TOTAL_ROWS.toLong()) {
        "Library snapshot total rows ($totalRows) exceeds maximum limit ($MAX_TOTAL_ROWS)"
    }

    val aquariumIds = mutableSetOf<UUID>()
    for (aquarium in snapshot.aquariums) {
        require(aquariumIds.add(aquarium.id)) {
            "Duplicate aquarium ID in backup: ${aquarium.id}"
        }
    }

    val profileIds = mutableSetOf<UUID>()
    val profileNamesLower = mutableSetOf<String>()
    for (profile in snapshot.aquariumProfiles) {
        require(profileIds.add(profile.id)) {
            "Duplicate aquarium profile ID in backup: ${profile.id}"
        }
        val normalizedName = profile.name.trim().lowercase()
        require(profileNamesLower.add(normalizedName)) {
            "Duplicate aquarium profile name in backup: ${profile.name}"
        }
    }

    val linkKeys = mutableSetOf<Pair<UUID, UUID>>()
    for (link in snapshot.aquariumProfileLinks) {
        require(link.aquariumId in aquariumIds) {
            "Orphan aquariumId in aquarium_profile_links: ${link.aquariumId}"
        }
        require(link.profileId in profileIds) {
            "Orphan profileId in aquarium_profile_links: ${link.profileId}"
        }
        require(linkKeys.add(link.aquariumId to link.profileId)) {
            "Duplicate aquarium_profile_links pair: (${link.aquariumId}, ${link.profileId})"
        }
    }

    val measurementIds = mutableSetOf<UUID>()
    for (measurement in snapshot.measurements) {
        require(measurementIds.add(measurement.id)) {
            "Duplicate measurement ID in backup: ${measurement.id}"
        }
        require(measurement.aquariumId in aquariumIds) {
            "Orphan aquariumId in measurements: ${measurement.aquariumId}"
        }
    }

    val maintenanceIds = mutableSetOf<UUID>()
    for (event in snapshot.maintenanceEvents) {
        require(maintenanceIds.add(event.id)) {
            "Duplicate maintenance event ID in backup: ${event.id}"
        }
        require(event.aquariumId in aquariumIds) {
            "Orphan aquariumId in maintenance_events: ${event.aquariumId}"
        }
        catalog.validateMaintenanceTypeCode(event.type)
    }

    val productIds = mutableSetOf<UUID>()
    val productNamesLower = mutableSetOf<String>()
    for (product in snapshot.products) {
        require(productIds.add(product.id)) {
            "Duplicate product ID in backup: ${product.id}"
        }
        val normalizedName = product.name.trim().lowercase()
        require(productNamesLower.add(normalizedName)) {
            "Duplicate product name in backup: ${product.name}"
        }
    }

    val dosingPlanById = mutableMapOf<UUID, DosingPlanSnapshotRecord>()
    for (plan in snapshot.dosingPlans) {
        require(dosingPlanById.put(plan.id, plan) == null) {
            "Duplicate dosing plan ID in backup: ${plan.id}"
        }
        require(plan.aquariumId in aquariumIds) {
            "Orphan aquariumId in dosing_plans: ${plan.aquariumId}"
        }
        require(plan.productId in productIds) {
            "Orphan productId in dosing_plans: ${plan.productId}"
        }
        catalog.validateDoseUnitCode(plan.unit)
    }

    val dosingEventIds = mutableSetOf<UUID>()
    for (event in snapshot.dosingEvents) {
        require(dosingEventIds.add(event.id)) {
            "Duplicate dosing event ID in backup: ${event.id}"
        }
        require(event.aquariumId in aquariumIds) {
            "Orphan aquariumId in dosing_events: ${event.aquariumId}"
        }
        if (event.planId != null) {
            val referencedPlan = dosingPlanById[event.planId]
                ?: throw IllegalArgumentException("Orphan planId in dosing_events: ${event.planId}")
            require(referencedPlan.aquariumId == event.aquariumId) {
                "Dosing event ${event.id} references plan ${event.planId} belonging to another aquarium"
            }
        }
        catalog.validateDoseUnitCode(event.unit)
    }

    val speciesIds = mutableSetOf<UUID>()
    val seenSpeciesDomain = mutableListOf<SpeciesCatalogItem>()
    for (item in snapshot.species) {
        require(speciesIds.add(item.id)) {
            "Duplicate species ID in backup: ${item.id}"
        }
        validateUniqueSpeciesCatalog(
            existing = seenSpeciesDomain,
            kind = item.kind,
            commonName = item.commonName,
            scientificName = item.scientificName,
        )
        seenSpeciesDomain.add(
            SpeciesCatalogItem(
                id = item.id,
                kind = item.kind,
                commonName = item.commonName,
                scientificName = item.scientificName,
            )
        )
    }

    val stockEntryIds = mutableSetOf<UUID>()
    for (entry in snapshot.stockEntries) {
        require(stockEntryIds.add(entry.id)) {
            "Duplicate stock entry ID in backup: ${entry.id}"
        }
        require(entry.aquariumId in aquariumIds) {
            "Orphan aquariumId in stock_entries: ${entry.aquariumId}"
        }
        require(entry.speciesId in speciesIds) {
            "Orphan speciesId in stock_entries: ${entry.speciesId}"
        }
    }
}

private fun requireBoundedLength(value: String?, fieldName: String) {
    validateOptionalTextLength(value, fieldName)
}
