package com.pietrondo.aquariummanager.domain

import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class AquariumSummary(val id: UUID, val name: String)

data class AquariumProfile(val id: UUID, val name: String) {
    init {
        validateNonBlank(name, "Aquarium profile name")
    }
}

enum class SpeciesKind { FAUNA, FLORA }

enum class DosingFrequency { DAILY, WEEKLY }

enum class DoseUnitDimension { VOLUME, MASS, COUNT }

const val CATALOG_FORMAT_ID = "aquarium-manager-catalog"
val SUPPORTED_CATALOG_VERSIONS: Set<Int> = setOf(1, 2)

data class MaintenanceTypeCatalogItem(
    val code: String,
    val label: String,
) {
    init {
        validateNonBlank(code, "Maintenance type code")
        validateNonBlank(label, "Maintenance type label")
    }
}

data class DoseUnitCatalogItem(
    val code: String,
    val label: String,
    val dimension: DoseUnitDimension,
) {
    init {
        validateNonBlank(code, "Dose unit code")
        validateNonBlank(label, "Dose unit label")
    }
}

data class SpeciesCatalogItem(
    val id: UUID,
    val kind: SpeciesKind,
    val commonName: String,
    val scientificName: String? = null,
) {
    init {
        validateSpecies(commonName, scientificName)
    }
}

data class CatalogBundle(
    val format: String = CATALOG_FORMAT_ID,
    val version: Int = 1,
    val profileLabels: List<AquariumProfile>,
    val maintenanceTypes: List<MaintenanceTypeCatalogItem>,
    val doseUnits: List<DoseUnitCatalogItem>,
    val species: List<SpeciesCatalogItem>,
) {
    init {
        require(format == CATALOG_FORMAT_ID) { "Unsupported catalog format: $format" }
        require(version in SUPPORTED_CATALOG_VERSIONS) { "Unsupported catalog version: $version" }

        val profileIds = mutableSetOf<UUID>()
        val profileNamesLower = mutableSetOf<String>()
        for (profile in profileLabels) {
            require(profileIds.add(profile.id)) { "Duplicate profile ID in catalog: ${profile.id}" }
            val normalizedName = profile.name.trim().lowercase()
            require(profileNamesLower.add(normalizedName)) {
                "Duplicate profile name in catalog: ${profile.name}"
            }
        }

        val maintenanceCodes = mutableSetOf<String>()
        for (item in maintenanceTypes) {
            require(maintenanceCodes.add(item.code)) {
                "Duplicate maintenance type code in catalog: ${item.code}"
            }
        }

        val unitCodes = mutableSetOf<String>()
        for (unit in doseUnits) {
            require(unitCodes.add(unit.code)) { "Duplicate dose unit code in catalog: ${unit.code}" }
        }

        val speciesIds = mutableSetOf<UUID>()
        for (item in species) {
            require(speciesIds.add(item.id)) { "Duplicate species ID in catalog: ${item.id}" }
        }
    }

    fun validateMaintenanceTypeCode(code: String) {
        validateNonBlank(code, "Maintenance type code")
        require(maintenanceTypes.any { it.code == code }) {
            "Unknown maintenance type code: $code"
        }
    }

    fun validateDoseUnitCode(code: String) {
        validateNonBlank(unitCode = code)
        require(doseUnits.any { it.code == code }) {
            "Unknown dose unit code: $code"
        }
    }

    fun requireCompatibleDoseDimension(fromUnitCode: String, toUnitCode: String) {
        val fromUnit = doseUnits.firstOrNull { it.code == fromUnitCode }
            ?: throw IllegalArgumentException("Unknown dose unit code: $fromUnitCode")
        val toUnit = doseUnits.firstOrNull { it.code == toUnitCode }
            ?: throw IllegalArgumentException("Unknown dose unit code: $toUnitCode")
        require(fromUnit.dimension == toUnit.dimension) {
            "Incompatible dose unit dimensions: ${fromUnit.code} (${fromUnit.dimension}) vs ${toUnit.code} (${toUnit.dimension})"
        }
    }

    private fun validateNonBlank(unitCode: String) {
        com.pietrondo.aquariummanager.domain.validateNonBlank(unitCode, "Dose unit code")
    }
}

data class AquariumDetail(
    val id: UUID,
    val name: String,
    val netLitres: BigDecimal?,
    val grossLitres: BigDecimal?,
    val notes: String?,
    val createdAt: Instant,
    val profiles: List<AquariumProfile>,
) {
    fun toSummary(): AquariumSummary = AquariumSummary(id = id, name = name)
}

data class AquariumDraft(
    val name: String,
    val netLitres: BigDecimal? = null,
    val grossLitres: BigDecimal? = null,
    val notes: String? = null,
    val profileIds: Set<UUID> = emptySet(),
) {
    init {
        validateNonBlank(name, "Aquarium name")
        validateTextLength(name, "Aquarium name")
        validateOptionalTextLength(notes, "Aquarium notes")
        validateAquariumVolumes(netLitres, grossLitres)
    }
}

enum class WaterParameter(
    val code: String,
    val label: String,
    val unit: String,
) {
    PH(code = "PH", label = "pH", unit = ""),
    KH_DKH(code = "KH_DKH", label = "KH", unit = "dKH"),
    GH_DGH(code = "GH_DGH", label = "GH", unit = "dGH"),
    NO2_MGL(code = "NO2_MGL", label = "NO2", unit = "mg/L"),
    NO3_MGL(code = "NO3_MGL", label = "NO3", unit = "mg/L"),
    TDS_PPM(code = "TDS_PPM", label = "TDS", unit = "ppm"),
    TEMPERATURE_C(code = "TEMPERATURE_C", label = "Temperature", unit = "°C"),
    ;

    val displayTitle: String
        get() = if (unit.isEmpty()) label else "$label ($unit)"
}

data class MeasurementRecord(
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

    fun valueFor(parameter: WaterParameter): BigDecimal? = when (parameter) {
        WaterParameter.PH -> ph
        WaterParameter.KH_DKH -> khDkh
        WaterParameter.GH_DGH -> ghDgh
        WaterParameter.NO2_MGL -> no2MgL
        WaterParameter.NO3_MGL -> no3MgL
        WaterParameter.TDS_PPM -> tdsPpm
        WaterParameter.TEMPERATURE_C -> temperatureC
    }
}

data class MeasurementDraft(
    val recordedAt: Instant? = null,
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

enum class ParameterTrendDirection {
    RISING,
    FALLING,
    STABLE,
    SINGLE_POINT,
    NO_DATA,
}

data class ParameterTrendPoint(
    val measurementId: UUID,
    val recordedAt: Instant,
    val value: BigDecimal?,
)

data class ParameterTrendSummary(
    val parameter: WaterParameter,
    val points: List<ParameterTrendPoint>,
    val measuredCount: Int,
    val missingGapCount: Int,
    val latestValue: BigDecimal?,
    val latestRecordedAt: Instant?,
    val previousValue: BigDecimal?,
    val deltaFromPrevious: BigDecimal?,
    val minValue: BigDecimal?,
    val maxValue: BigDecimal?,
    val direction: ParameterTrendDirection,
    val accessibilityCue: String,
)

data class MaintenanceRecord(
    val id: UUID,
    val aquariumId: UUID,
    val type: String,
    val occurredAt: Instant,
    val changedLitres: BigDecimal? = null,
    val notes: String? = null,
) {
    init {
        validateMaintenanceEvent(type, changedLitres)
        validateOptionalTextLength(notes, "Maintenance event notes")
    }
}

data class MaintenanceDraft(
    val type: String,
    val occurredAt: Instant? = null,
    val changedLitres: BigDecimal? = null,
    val notes: String? = null,
) {
    init {
        validateMaintenanceEvent(type, changedLitres)
        validateOptionalTextLength(notes, "Maintenance event notes")
    }
}

fun calculateParameterTrend(
    parameter: WaterParameter,
    measurements: List<MeasurementRecord>,
): ParameterTrendSummary {
    val newestFirst = measurements.sortedWith(
        compareByDescending<MeasurementRecord> { it.recordedAt }.thenBy { it.id }
    )
    val chronological = newestFirst.asReversed()
    val points = chronological.map { record ->
        ParameterTrendPoint(
            measurementId = record.id,
            recordedAt = record.recordedAt,
            value = record.valueFor(parameter),
        )
    }
    val measuredPoints = points.filter { it.value != null }
    val measuredCount = measuredPoints.size
    val missingGapCount = points.size - measuredCount
    val latestPoint = measuredPoints.lastOrNull()
    val previousPoint = if (measuredCount >= 2) measuredPoints[measuredCount - 2] else null
    val latestValue = latestPoint?.value
    val latestRecordedAt = latestPoint?.recordedAt
    val previousValue = previousPoint?.value
    val deltaFromPrevious = if (latestValue != null && previousValue != null) {
        latestValue.subtract(previousValue)
    } else {
        null
    }
    val measuredValues = measuredPoints.mapNotNull { it.value }
    val minValue = measuredValues.minWithOrNull(BigDecimal::compareTo)
    val maxValue = measuredValues.maxWithOrNull(BigDecimal::compareTo)
    val unitSuffix = if (parameter.unit.isEmpty()) "" else " ${parameter.unit}"

    val (direction, cue) = when {
        measuredCount == 0 -> ParameterTrendDirection.NO_DATA to "— No readings"
        measuredCount == 1 -> ParameterTrendDirection.SINGLE_POINT to "• Single reading"
        deltaFromPrevious!!.signum() > 0 -> {
            val deltaText = deltaFromPrevious.stripTrailingZeros().toPlainString()
            ParameterTrendDirection.RISING to "↑ Rising (+$deltaText$unitSuffix)"
        }
        deltaFromPrevious.signum() < 0 -> {
            val deltaText = deltaFromPrevious.stripTrailingZeros().toPlainString()
            ParameterTrendDirection.FALLING to "↓ Falling ($deltaText$unitSuffix)"
        }
        else -> ParameterTrendDirection.STABLE to "→ Stable (0$unitSuffix)"
    }

    return ParameterTrendSummary(
        parameter = parameter,
        points = points,
        measuredCount = measuredCount,
        missingGapCount = missingGapCount,
        latestValue = latestValue,
        latestRecordedAt = latestRecordedAt,
        previousValue = previousValue,
        deltaFromPrevious = deltaFromPrevious,
        minValue = minValue,
        maxValue = maxValue,
        direction = direction,
        accessibilityCue = cue,
    )
}

fun calculateParameterTrends(
    measurements: List<MeasurementRecord>,
): List<ParameterTrendSummary> =
    WaterParameter.entries.map { parameter ->
        calculateParameterTrend(parameter, measurements)
    }

fun evaluateMaintenanceVolumeWarning(
    changedLitres: BigDecimal?,
    netLitres: BigDecimal?,
    grossLitres: BigDecimal? = null,
): String? {
    if (changedLitres == null) return null
    val changedText = changedLitres.stripTrailingZeros().toPlainString()
    if (netLitres != null && changedLitres > netLitres) {
        val netText = netLitres.stripTrailingZeros().toPlainString()
        return "Note: changed volume ($changedText L) exceeds aquarium net volume ($netText L)."
    }
    if (netLitres == null && grossLitres != null && changedLitres > grossLitres) {
        val grossText = grossLitres.stripTrailingZeros().toPlainString()
        return "Note: changed volume ($changedText L) exceeds aquarium gross volume ($grossText L)."
    }
    return null
}

interface AquariumRepository {
    fun observeAquariums(): Flow<List<AquariumSummary>>

    fun observeAquariumDetails(): Flow<List<AquariumDetail>> =
        observeAquariums().map { summaries ->
            summaries.map { summary ->
                AquariumDetail(
                    id = summary.id,
                    name = summary.name,
                    netLitres = null,
                    grossLitres = null,
                    notes = null,
                    createdAt = Instant.EPOCH,
                    profiles = emptyList(),
                )
            }
        }

    fun observeProfiles(): Flow<List<AquariumProfile>> =
        flowOf(emptyList())

    fun observeMeasurements(aquariumId: UUID): Flow<List<MeasurementRecord>> =
        flowOf(emptyList())

    fun observeMaintenanceEvents(aquariumId: UUID): Flow<List<MaintenanceRecord>> =
        flowOf(emptyList())

    fun observeMaintenanceTypeCatalog(): Flow<List<MaintenanceTypeCatalogItem>> =
        flowOf(emptyList())

    fun observeSpeciesCatalog(kind: SpeciesKind? = null): Flow<List<SpeciesCatalogItem>> =
        flowOf(emptyList())

    fun observeStockEntries(aquariumId: UUID): Flow<List<StockEntryRecord>> =
        flowOf(emptyList())

    fun observeProducts(): Flow<List<ProductRecord>> =
        flowOf(emptyList())

    fun observeDoseUnitCatalog(): Flow<List<DoseUnitCatalogItem>> =
        flowOf(emptyList())

    fun observeDosingPlans(aquariumId: UUID): Flow<List<DosingPlanRecord>> =
        flowOf(emptyList())

    fun observeDosingEvents(aquariumId: UUID): Flow<List<DosingEventRecord>> =
        flowOf(emptyList())

    suspend fun ensureCatalogSeeded() {}

    suspend fun createAquarium(draft: AquariumDraft): UUID =
        throw UnsupportedOperationException("createAquarium not implemented")

    suspend fun updateAquarium(id: UUID, draft: AquariumDraft): Unit =
        throw UnsupportedOperationException("updateAquarium not implemented")

    suspend fun deleteAquarium(id: UUID): Unit =
        throw UnsupportedOperationException("deleteAquarium not implemented")

    suspend fun createProfile(name: String): AquariumProfile =
        throw UnsupportedOperationException("createProfile not implemented")

    suspend fun recordMeasurement(aquariumId: UUID, draft: MeasurementDraft): UUID =
        throw UnsupportedOperationException("recordMeasurement not implemented")

    suspend fun deleteMeasurement(id: UUID): Unit =
        throw UnsupportedOperationException("deleteMeasurement not implemented")

    suspend fun recordMaintenanceEvent(aquariumId: UUID, draft: MaintenanceDraft): UUID =
        throw UnsupportedOperationException("recordMaintenanceEvent not implemented")

    suspend fun updateMaintenanceEvent(id: UUID, aquariumId: UUID, draft: MaintenanceDraft): Unit =
        throw UnsupportedOperationException("updateMaintenanceEvent not implemented")

    suspend fun deleteMaintenanceEvent(id: UUID): Unit =
        throw UnsupportedOperationException("deleteMaintenanceEvent not implemented")

    suspend fun createSpecies(draft: SpeciesDraft): SpeciesCatalogItem =
        throw UnsupportedOperationException("createSpecies not implemented")

    suspend fun updateSpecies(id: UUID, draft: SpeciesDraft): SpeciesCatalogItem =
        throw UnsupportedOperationException("updateSpecies not implemented")

    suspend fun deleteSpecies(id: UUID): Unit =
        throw UnsupportedOperationException("deleteSpecies not implemented")

    suspend fun createStockEntry(aquariumId: UUID, draft: StockEntryDraft): UUID =
        throw UnsupportedOperationException("createStockEntry not implemented")

    suspend fun updateStockEntry(id: UUID, aquariumId: UUID, draft: StockEntryDraft): Unit =
        throw UnsupportedOperationException("updateStockEntry not implemented")

    suspend fun markStockExited(id: UUID, exitedAt: Instant? = null): Unit =
        throw UnsupportedOperationException("markStockExited not implemented")

    suspend fun deleteStockEntry(id: UUID): Unit =
        throw UnsupportedOperationException("deleteStockEntry not implemented")

    suspend fun createProduct(name: String): ProductRecord =
        throw UnsupportedOperationException("createProduct not implemented")

    suspend fun updateProduct(id: UUID, name: String): ProductRecord =
        throw UnsupportedOperationException("updateProduct not implemented")

    suspend fun deleteProduct(id: UUID): Unit =
        throw UnsupportedOperationException("deleteProduct not implemented")

    suspend fun createDosingPlan(aquariumId: UUID, draft: DosingPlanDraft): UUID =
        throw UnsupportedOperationException("createDosingPlan not implemented")

    suspend fun updateDosingPlan(id: UUID, aquariumId: UUID, draft: DosingPlanDraft): Unit =
        throw UnsupportedOperationException("updateDosingPlan not implemented")

    suspend fun deleteDosingPlan(id: UUID): Unit =
        throw UnsupportedOperationException("deleteDosingPlan not implemented")

    suspend fun recordDosingFromPlan(aquariumId: UUID, planId: UUID, occurredAt: Instant? = null): UUID =
        throw UnsupportedOperationException("recordDosingFromPlan not implemented")

    suspend fun recordAdHocDosingEvent(aquariumId: UUID, draft: DosingEventDraft): UUID =
        throw UnsupportedOperationException("recordAdHocDosingEvent not implemented")

    suspend fun deleteDosingEvent(id: UUID): Unit =
        throw UnsupportedOperationException("deleteDosingEvent not implemented")

    fun observeBackupScheduleSettings(): Flow<BackupScheduleSettings> =
        flowOf(BackupScheduleSettings())

    suspend fun exportBackupToUri(destinationUri: String): BackupExportResult =
        throw UnsupportedOperationException("exportBackupToUri not implemented")

    suspend fun exportBackupBytes(): ByteArray =
        throw UnsupportedOperationException("exportBackupBytes not implemented")

    suspend fun inspectBackupUri(sourceUri: String): BackupRestorePreview =
        throw UnsupportedOperationException("inspectBackupUri not implemented")

    suspend fun inspectBackupBytes(zipBytes: ByteArray, sourceUri: String? = null): BackupRestorePreview =
        throw UnsupportedOperationException("inspectBackupBytes not implemented")

    suspend fun restoreBackupFromUri(sourceUri: String, confirmReplace: Boolean): BackupRestorePreview =
        throw UnsupportedOperationException("restoreBackupFromUri not implemented")

    suspend fun restoreBackupFromBytes(zipBytes: ByteArray, confirmReplace: Boolean): BackupRestorePreview =
        throw UnsupportedOperationException("restoreBackupFromBytes not implemented")

    suspend fun updateAutoBackupSettings(
        enabled: Boolean,
        treeUri: String?,
        retentionCount: Int = DEFAULT_AUTO_BACKUP_RETENTION_COUNT,
    ): BackupScheduleSettings =
        throw UnsupportedOperationException("updateAutoBackupSettings not implemented")

    suspend fun runAutomaticBackupNow(): BackupScheduleSettings =
        throw UnsupportedOperationException("runAutomaticBackupNow not implemented")
}

data class SpeciesDraft(
    val kind: SpeciesKind,
    val commonName: String,
    val scientificName: String? = null,
) {
    init {
        validateSpecies(commonName, scientificName)
    }
}

data class StockEntryRecord(
    val id: UUID,
    val aquariumId: UUID,
    val species: SpeciesCatalogItem,
    val quantity: BigDecimal? = null,
    val introducedAt: Instant,
    val exitedAt: Instant? = null,
    val notes: String? = null,
) {
    init {
        validateStockEntry(quantity, introducedAt, exitedAt)
        validateOptionalTextLength(notes, "Stock entry notes")
    }

    val isActive: Boolean
        get() = exitedAt == null
}

data class StockEntryDraft(
    val speciesId: UUID,
    val quantity: BigDecimal? = null,
    val introducedAt: Instant? = null,
    val exitedAt: Instant? = null,
    val notes: String? = null,
) {
    init {
        require(quantity == null || quantity.signum() > 0) {
            "Stock quantity must be positive when known"
        }
        if (introducedAt != null && exitedAt != null) {
            require(!exitedAt.isBefore(introducedAt)) {
                "Exit timestamp cannot be before introduction timestamp"
            }
        }
        validateOptionalTextLength(notes, "Stock entry notes")
    }
}

data class ProductRecord(
    val id: UUID,
    val name: String,
) {
    init {
        validateNonBlank(name, "Product name")
    }
}

data class ProductDraft(
    val name: String,
) {
    init {
        validateNonBlank(name, "Product name")
    }
}

data class DosingPlanRecord(
    val id: UUID,
    val aquariumId: UUID,
    val product: ProductRecord,
    val dose: BigDecimal,
    val unit: String,
    val frequency: DosingFrequency,
    val weekdays: Set<DayOfWeek>,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val timeZone: java.time.ZoneId,
) {
    init {
        validateDosingPlan(dose, unit, frequency, weekdays, startDate, endDate)
    }

    fun localDateAt(instant: Instant): LocalDate =
        instant.atZone(timeZone).toLocalDate()

    fun isScheduledOnLocalDate(date: LocalDate): Boolean {
        if (date.isBefore(startDate)) return false
        if (endDate != null && date.isAfter(endDate)) return false
        return when (frequency) {
            DosingFrequency.DAILY -> true
            DosingFrequency.WEEKLY -> date.dayOfWeek in weekdays
        }
    }

    fun isScheduledAtInstant(instant: Instant): Boolean =
        isScheduledOnLocalDate(localDateAt(instant))

    fun scheduledDatesInWindow(fromDate: LocalDate, toDate: LocalDate): List<LocalDate> {
        require(!toDate.isBefore(fromDate)) { "Window end date cannot be before start date" }
        val effectiveStart = if (fromDate.isAfter(startDate)) fromDate else startDate
        val effectiveEnd = if (endDate != null && endDate.isBefore(toDate)) endDate else toDate
        if (effectiveEnd.isBefore(effectiveStart)) return emptyList()
        val result = mutableListOf<LocalDate>()
        var cursor = effectiveStart
        while (!cursor.isAfter(effectiveEnd)) {
            if (isScheduledOnLocalDate(cursor)) {
                result.add(cursor)
            }
            cursor = cursor.plusDays(1)
        }
        return result
    }
}

data class DosingPlanDraft(
    val productId: UUID,
    val dose: BigDecimal,
    val unit: String,
    val frequency: DosingFrequency,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val timeZone: java.time.ZoneId,
) {
    init {
        validateDosingPlan(dose, unit, frequency, weekdays, startDate, endDate)
    }
}

data class DosingEventRecord(
    val id: UUID,
    val aquariumId: UUID,
    val planId: UUID? = null,
    val occurredAt: Instant,
    val productNameSnapshot: String,
    val dose: BigDecimal,
    val unit: String,
) {
    init {
        validateDosingEvent(productNameSnapshot, dose, unit)
    }
}

data class DosingEventDraft(
    val planId: UUID? = null,
    val occurredAt: Instant? = null,
    val productNameSnapshot: String,
    val dose: BigDecimal,
    val unit: String,
) {
    init {
        validateDosingEvent(productNameSnapshot, dose, unit)
    }
}

fun List<SpeciesCatalogItem>.filterByKind(kind: SpeciesKind?): List<SpeciesCatalogItem> =
    if (kind == null) this else filter { it.kind == kind }

fun validateUniqueSpeciesCatalog(
    existing: List<SpeciesCatalogItem>,
    kind: SpeciesKind,
    commonName: String,
    scientificName: String?,
    excludingId: UUID? = null,
) {
    val trimmedCommon = commonName.trim()
    val trimmedScientific = scientificName?.trim()?.takeIf { it.isNotEmpty() }
    validateSpecies(trimmedCommon, trimmedScientific)
    val others = existing.filter { it.id != excludingId }
    require(
        others.none {
            it.kind == kind && it.commonName.trim().equals(trimmedCommon, ignoreCase = true)
        }
    ) {
        "Species '$trimmedCommon' (${kind.name}) already exists in catalog"
    }
    if (trimmedScientific != null) {
        require(
            others.none {
                it.scientificName?.trim()?.equals(trimmedScientific, ignoreCase = true) == true
            }
        ) {
            "Scientific name '$trimmedScientific' already exists in catalog"
        }
    }
}

fun validateNonBlank(value: String, fieldName: String) {
    require(value.isNotBlank()) { "$fieldName cannot be blank" }
    validateTextLength(value, fieldName)
}

fun validateTextLength(value: String, fieldName: String) {
    require(value.length <= MAX_TEXT_FIELD_LENGTH) {
        "$fieldName exceeds maximum length of $MAX_TEXT_FIELD_LENGTH characters"
    }
}

fun validateOptionalTextLength(value: String?, fieldName: String) {
    if (value != null) validateTextLength(value, fieldName)
}

const val MAX_DECIMAL_PRECISION = 64
const val MAX_DECIMAL_ABS_SCALE = 32
const val MAX_DECIMAL_TEXT_LENGTH = 128

fun validateBoundedDecimal(value: BigDecimal, fieldName: String) {
    require(value.precision() <= MAX_DECIMAL_PRECISION) {
        "$fieldName exceeds maximum decimal precision of $MAX_DECIMAL_PRECISION"
    }
    require(value.scale() in -MAX_DECIMAL_ABS_SCALE..MAX_DECIMAL_ABS_SCALE) {
        "$fieldName exceeds supported decimal scale"
    }
    val precision = value.precision()
    val scale = value.scale()
    val plainLength = (if (scale <= 0) {
        precision - scale
    } else if (precision > scale) {
        precision + 1
    } else {
        scale + 2
    }) + if (value.signum() < 0) 1 else 0
    require(plainLength <= MAX_DECIMAL_TEXT_LENGTH) {
        "$fieldName expands beyond $MAX_DECIMAL_TEXT_LENGTH characters"
    }
}

fun parseBoundedDecimal(text: String, fieldName: String): BigDecimal {
    require(text.length <= MAX_DECIMAL_TEXT_LENGTH) {
        "$fieldName exceeds maximum decimal text length of $MAX_DECIMAL_TEXT_LENGTH"
    }
    val value = try {
        BigDecimal(text)
    } catch (e: NumberFormatException) {
        throw IllegalArgumentException("Invalid decimal for $fieldName", e)
    }
    validateBoundedDecimal(value, fieldName)
    return value
}

fun validateAquariumVolumes(netLitres: BigDecimal?, grossLitres: BigDecimal?) {
    netLitres?.let { validateBoundedDecimal(it, "Net litres") }
    grossLitres?.let { validateBoundedDecimal(it, "Gross litres") }
    require(netLitres == null || netLitres.signum() > 0) { "Net litres must be positive" }
    require(grossLitres == null || grossLitres.signum() > 0) { "Gross litres must be positive" }
    require(netLitres == null || grossLitres == null || netLitres <= grossLitres) {
        "Net litres cannot exceed gross litres"
    }
}

fun validateMeasurementReadings(
    ph: BigDecimal?,
    khDkh: BigDecimal?,
    ghDgh: BigDecimal?,
    no2MgL: BigDecimal?,
    no3MgL: BigDecimal?,
    tdsPpm: BigDecimal?,
    temperatureC: BigDecimal?,
) {
    listOfNotNull(ph, khDkh, ghDgh, no2MgL, no3MgL, tdsPpm, temperatureC)
        .forEach { validateBoundedDecimal(it, "Measurement reading") }
    require(
        ph != null ||
            khDkh != null ||
            ghDgh != null ||
            no2MgL != null ||
            no3MgL != null ||
            tdsPpm != null ||
            temperatureC != null
    ) { "At least one measurement reading must be present" }
    require(khDkh == null || khDkh.signum() >= 0) { "KH (dKH) must be non-negative" }
    require(ghDgh == null || ghDgh.signum() >= 0) { "GH (dGH) must be non-negative" }
    require(no2MgL == null || no2MgL.signum() >= 0) { "NO2 (mg/L) must be non-negative" }
    require(no3MgL == null || no3MgL.signum() >= 0) { "NO3 (mg/L) must be non-negative" }
    require(tdsPpm == null || tdsPpm.signum() >= 0) { "TDS (ppm) must be non-negative" }
}

fun validateMaintenanceEvent(type: String, changedLitres: BigDecimal?) {
    changedLitres?.let { validateBoundedDecimal(it, "Changed litres") }
    validateNonBlank(type, "Maintenance event type")
    require(changedLitres == null || changedLitres.signum() > 0) {
        "Changed litres must be positive when present"
    }
}

fun validateDosingPlan(
    dose: BigDecimal,
    unit: String,
    frequency: DosingFrequency,
    weekdays: Set<DayOfWeek>,
    startDate: LocalDate,
    endDate: LocalDate?,
) {
    validateBoundedDecimal(dose, "Dose")
    require(dose.signum() > 0) { "Dose must be positive" }
    validateNonBlank(unit, "Dose unit")
    if (frequency == DosingFrequency.WEEKLY) {
        require(weekdays.isNotEmpty()) { "Weekly dosing plan requires at least one weekday" }
    }
    require(endDate == null || !endDate.isBefore(startDate)) {
        "End date cannot be before start date"
    }
}

fun validateDosingEvent(productNameSnapshot: String, dose: BigDecimal, unit: String) {
    validateBoundedDecimal(dose, "Dose")
    validateNonBlank(productNameSnapshot, "Product name snapshot")
    require(dose.signum() > 0) { "Dose must be positive" }
    validateNonBlank(unit, "Dose unit")
}

fun validateSpecies(commonName: String, scientificName: String?) {
    validateNonBlank(commonName, "Species common name")
    if (scientificName != null) {
        validateNonBlank(scientificName, "Species scientific name")
    }
}

fun validateStockEntry(quantity: BigDecimal?, introducedAt: Instant, exitedAt: Instant?) {
    quantity?.let { validateBoundedDecimal(it, "Stock quantity") }
    require(quantity == null || quantity.signum() > 0) {
        "Stock quantity must be positive when known"
    }
    require(exitedAt == null || !exitedAt.isBefore(introducedAt)) {
        "Exit timestamp cannot be before introduction timestamp"
    }
}
