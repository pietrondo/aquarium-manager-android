package com.pietrondo.aquariummanager.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pietrondo.aquariummanager.domain.AquariumDetail
import com.pietrondo.aquariummanager.domain.AquariumProfile
import com.pietrondo.aquariummanager.domain.AquariumSummary
import com.pietrondo.aquariummanager.domain.DosingEventRecord
import com.pietrondo.aquariummanager.domain.DosingFrequency
import com.pietrondo.aquariummanager.domain.DosingPlanRecord
import com.pietrondo.aquariummanager.domain.MaintenanceRecord
import com.pietrondo.aquariummanager.domain.MeasurementRecord
import com.pietrondo.aquariummanager.domain.ProductRecord
import com.pietrondo.aquariummanager.domain.SpeciesCatalogItem
import com.pietrondo.aquariummanager.domain.SpeciesKind
import com.pietrondo.aquariummanager.domain.parseBoundedDecimal
import com.pietrondo.aquariummanager.domain.validateBoundedDecimal
import com.pietrondo.aquariummanager.domain.StockEntryRecord
import com.pietrondo.aquariummanager.domain.validateAquariumVolumes
import com.pietrondo.aquariummanager.domain.validateDosingEvent
import com.pietrondo.aquariummanager.domain.validateDosingPlan
import com.pietrondo.aquariummanager.domain.validateMaintenanceEvent
import com.pietrondo.aquariummanager.domain.validateMeasurementReadings
import com.pietrondo.aquariummanager.domain.validateNonBlank
import com.pietrondo.aquariummanager.domain.validateSpecies
import com.pietrondo.aquariummanager.domain.validateStockEntry
import com.pietrondo.aquariummanager.domain.validateUniqueSpeciesCatalog
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "aquariums")
data class AquariumEntity(
    @PrimaryKey val id: UUID,
    val name: String,
    val netLitres: BigDecimal?,
    val grossLitres: BigDecimal?,
    val notes: String?,
    val createdAt: Instant,
) {
    init {
        validateNonBlank(name, "Aquarium name")
        validateAquariumVolumes(netLitres, grossLitres)
    }
}

@Entity(tableName = "aquarium_profiles")
data class AquariumProfileEntity(
    @PrimaryKey val id: UUID,
    val name: String,
) {
    init {
        validateNonBlank(name, "Aquarium profile name")
    }
}

@Entity(
    tableName = "aquarium_profile_links",
    primaryKeys = ["aquariumId", "profileId"],
    foreignKeys = [
        ForeignKey(
            entity = AquariumEntity::class,
            parentColumns = ["id"],
            childColumns = ["aquariumId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = AquariumProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["aquariumId"]),
        Index(value = ["profileId"]),
    ],
)
data class AquariumProfileLinkEntity(
    val aquariumId: UUID,
    val profileId: UUID,
)

@Entity(
    tableName = "measurements",
    foreignKeys = [
        ForeignKey(
            entity = AquariumEntity::class,
            parentColumns = ["id"],
            childColumns = ["aquariumId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["aquariumId"]),
        Index(value = ["aquariumId", "recordedAt"]),
    ],
)
data class MeasurementEntity(
    @PrimaryKey val id: UUID,
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

@Entity(
    tableName = "maintenance_events",
    foreignKeys = [
        ForeignKey(
            entity = AquariumEntity::class,
            parentColumns = ["id"],
            childColumns = ["aquariumId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["aquariumId"]),
        Index(value = ["aquariumId", "occurredAt"]),
    ],
)
data class MaintenanceEventEntity(
    @PrimaryKey val id: UUID,
    val aquariumId: UUID,
    val type: String,
    val occurredAt: Instant,
    val changedLitres: BigDecimal? = null,
    val notes: String? = null,
) {
    init {
        validateMaintenanceEvent(type, changedLitres)
    }
}

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: UUID,
    val name: String,
) {
    init {
        validateNonBlank(name, "Product name")
    }
}

@Entity(
    tableName = "dosing_plans",
    foreignKeys = [
        ForeignKey(
            entity = AquariumEntity::class,
            parentColumns = ["id"],
            childColumns = ["aquariumId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["aquariumId"]),
        Index(value = ["productId"]),
    ],
)
data class DosingPlanEntity(
    @PrimaryKey val id: UUID,
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
    }
}

@Entity(
    tableName = "dosing_events",
    foreignKeys = [
        ForeignKey(
            entity = DosingPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = AquariumEntity::class,
            parentColumns = ["id"],
            childColumns = ["aquariumId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["planId"]),
        Index(value = ["aquariumId"]),
        Index(value = ["aquariumId", "occurredAt"]),
    ],
)
data class DosingEventEntity(
    @PrimaryKey val id: UUID,
    val planId: UUID?,
    val aquariumId: UUID,
    val occurredAt: Instant,
    val productNameSnapshot: String,
    val dose: BigDecimal,
    val unit: String,
) {
    init {
        validateDosingEvent(productNameSnapshot, dose, unit)
    }
}

@Entity(tableName = "species")
data class SpeciesEntity(
    @PrimaryKey val id: UUID,
    val kind: SpeciesKind,
    val scientificName: String? = null,
    val commonName: String,
) {
    init {
        validateSpecies(commonName, scientificName)
    }
}

@Entity(
    tableName = "stock_entries",
    foreignKeys = [
        ForeignKey(
            entity = AquariumEntity::class,
            parentColumns = ["id"],
            childColumns = ["aquariumId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = SpeciesEntity::class,
            parentColumns = ["id"],
            childColumns = ["speciesId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["aquariumId"]),
        Index(value = ["speciesId"]),
        Index(value = ["aquariumId", "introducedAt"]),
    ],
)
data class StockEntryEntity(
    @PrimaryKey val id: UUID,
    val aquariumId: UUID,
    val speciesId: UUID,
    val quantity: BigDecimal? = null,
    val introducedAt: Instant,
    val exitedAt: Instant? = null,
    val notes: String? = null,
) {
    init {
        validateStockEntry(quantity, introducedAt, exitedAt)
    }
}

fun AquariumEntity.toSummary() = AquariumSummary(id, name)

fun AquariumProfileEntity.toDomain() = AquariumProfile(id = id, name = name)

fun AquariumEntity.toDetail(profiles: List<AquariumProfile>) = AquariumDetail(
    id = id,
    name = name,
    netLitres = netLitres,
    grossLitres = grossLitres,
    notes = notes,
    createdAt = createdAt,
    profiles = profiles,
)

fun MeasurementEntity.toDomain() = MeasurementRecord(
    id = id,
    aquariumId = aquariumId,
    recordedAt = recordedAt,
    ph = ph,
    khDkh = khDkh,
    ghDgh = ghDgh,
    no2MgL = no2MgL,
    no3MgL = no3MgL,
    tdsPpm = tdsPpm,
    temperatureC = temperatureC,
)

fun MaintenanceEventEntity.toDomain() = MaintenanceRecord(
    id = id,
    aquariumId = aquariumId,
    type = type,
    occurredAt = occurredAt,
    changedLitres = changedLitres,
    notes = notes,
)

fun SpeciesEntity.toDomain() = SpeciesCatalogItem(
    id = id,
    kind = kind,
    commonName = commonName,
    scientificName = scientificName,
)

fun StockEntryEntity.toDomain(species: SpeciesCatalogItem) = StockEntryRecord(
    id = id,
    aquariumId = aquariumId,
    species = species,
    quantity = quantity,
    introducedAt = introducedAt,
    exitedAt = exitedAt,
    notes = notes,
)

fun ProductEntity.toDomain() = ProductRecord(
    id = id,
    name = name,
)

fun DosingPlanEntity.toDomain(product: ProductRecord) = DosingPlanRecord(
    id = id,
    aquariumId = aquariumId,
    product = product,
    dose = dose,
    unit = unit,
    frequency = frequency,
    weekdays = weekdays,
    startDate = startDate,
    endDate = endDate,
    timeZone = timeZone,
)

fun DosingEventEntity.toDomain() = DosingEventRecord(
    id = id,
    aquariumId = aquariumId,
    planId = planId,
    occurredAt = occurredAt,
    productNameSnapshot = productNameSnapshot,
    dose = dose,
    unit = unit,
)

class AquariumConverters {
    @TypeConverter fun decimalToText(value: BigDecimal?): String? = value?.let {
        validateBoundedDecimal(it, "Stored decimal")
        it.stripTrailingZeros().toPlainString()
    }
    @TypeConverter fun textToDecimal(value: String?): BigDecimal? = value?.let {
        parseBoundedDecimal(it, "Stored decimal")
    }
    @TypeConverter fun uuidToText(value: UUID?): String? = value?.toString()
    @TypeConverter fun textToUuid(value: String?): UUID? {
        if (value == null) return null
        val uuid = UUID.fromString(value)
        require(uuid.toString() == value) { "UUID must use canonical lowercase format" }
        return uuid
    }
    @TypeConverter fun instantToText(value: Instant?): String? = value?.toString()
    @TypeConverter fun textToInstant(value: String?): Instant? = value?.let(Instant::parse)
    @TypeConverter fun localDateToText(value: LocalDate?): String? = value?.toString()
    @TypeConverter fun textToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
    @TypeConverter fun zoneIdToText(value: ZoneId?): String? = value?.id
    @TypeConverter fun textToZoneId(value: String?): ZoneId? = value?.let(ZoneId::of)
    @TypeConverter fun speciesKindToText(value: SpeciesKind?): String? = value?.name
    @TypeConverter fun textToSpeciesKind(value: String?): SpeciesKind? = value?.let(SpeciesKind::valueOf)
    @TypeConverter fun dosingFrequencyToText(value: DosingFrequency?): String? = value?.name
    @TypeConverter fun textToDosingFrequency(value: String?): DosingFrequency? = value?.let(DosingFrequency::valueOf)
    @TypeConverter fun weekdaysToText(value: Set<DayOfWeek>?): String? =
        value?.map { it.value }?.sorted()?.joinToString(",")
    @TypeConverter fun textToWeekdays(value: String?): Set<DayOfWeek>? {
        if (value == null) return null
        if (value.isEmpty()) return emptySet()
        val numbers = value.split(",").map { token ->
            token.toIntOrNull() ?: throw IllegalArgumentException("Invalid weekday token: $token")
        }
        var previous = 0
        val result = linkedSetOf<DayOfWeek>()
        for (n in numbers) {
            require(n in 1..7) { "Weekday ISO number out of range 1..7: $n" }
            require(n > previous) { "Weekdays must be strictly increasing and unique: $value" }
            previous = n
            result.add(DayOfWeek.of(n))
        }
        return result
    }
}

@Dao
interface AquariumDao {
    @Query("SELECT * FROM aquariums ORDER BY name ASC, id ASC")
    fun observeAquariums(): Flow<List<AquariumEntity>>
    @Query("SELECT * FROM aquariums WHERE id = :id")
    suspend fun getAquariumById(id: UUID): AquariumEntity?
    @Insert suspend fun insert(aquarium: AquariumEntity)
    @Update suspend fun update(aquarium: AquariumEntity)
    @Query("DELETE FROM aquariums WHERE id = :id")
    suspend fun deleteAquariumById(id: UUID)

    @Query("SELECT * FROM aquarium_profiles ORDER BY name ASC, id ASC")
    fun observeProfiles(): Flow<List<AquariumProfileEntity>>
    @Query("SELECT * FROM aquarium_profiles ORDER BY name ASC, id ASC")
    suspend fun getAllProfiles(): List<AquariumProfileEntity>
    @Insert suspend fun insertProfile(profile: AquariumProfileEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProfileIgnore(profile: AquariumProfileEntity): Long
    @Update suspend fun updateProfile(profile: AquariumProfileEntity)
    @Query("DELETE FROM aquarium_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: UUID)
    @Query("SELECT * FROM aquarium_profile_links")
    fun observeAllProfileLinks(): Flow<List<AquariumProfileLinkEntity>>
    @Insert suspend fun insertProfileLink(link: AquariumProfileLinkEntity)
    @Query("DELETE FROM aquarium_profile_links WHERE aquariumId = :aquariumId AND profileId = :profileId")
    suspend fun deleteProfileLink(aquariumId: UUID, profileId: UUID)
    @Query("DELETE FROM aquarium_profile_links WHERE aquariumId = :aquariumId")
    suspend fun deleteProfileLinksForAquarium(aquariumId: UUID)
    @Query("SELECT p.* FROM aquarium_profiles p INNER JOIN aquarium_profile_links l ON p.id = l.profileId WHERE l.aquariumId = :aquariumId ORDER BY p.name ASC, p.id ASC")
    suspend fun getProfilesForAquarium(aquariumId: UUID): List<AquariumProfileEntity>

    @Insert suspend fun insertMeasurement(measurement: MeasurementEntity)
    @Query("SELECT * FROM measurements WHERE aquariumId = :aquariumId ORDER BY recordedAt DESC, id ASC")
    fun observeMeasurementsForAquarium(aquariumId: UUID): Flow<List<MeasurementEntity>>
    @Query("SELECT * FROM measurements WHERE aquariumId = :aquariumId ORDER BY recordedAt DESC, id ASC")
    suspend fun getMeasurementsForAquarium(aquariumId: UUID): List<MeasurementEntity>
    @Query("DELETE FROM measurements WHERE id = :id")
    suspend fun deleteMeasurementById(id: UUID)
    @Query("DELETE FROM measurements WHERE aquariumId = :aquariumId")
    suspend fun deleteMeasurementsForAquarium(aquariumId: UUID)

    @Insert suspend fun insertMaintenanceEvent(event: MaintenanceEventEntity)
    @Update suspend fun updateMaintenanceEvent(event: MaintenanceEventEntity)
    @Query("SELECT * FROM maintenance_events WHERE id = :id")
    suspend fun getMaintenanceEventById(id: UUID): MaintenanceEventEntity?
    @Query("SELECT * FROM maintenance_events WHERE aquariumId = :aquariumId ORDER BY occurredAt DESC, id ASC")
    fun observeMaintenanceEventsForAquarium(aquariumId: UUID): Flow<List<MaintenanceEventEntity>>
    @Query("SELECT * FROM maintenance_events WHERE aquariumId = :aquariumId ORDER BY occurredAt DESC, id ASC")
    suspend fun getMaintenanceEventsForAquarium(aquariumId: UUID): List<MaintenanceEventEntity>
    @Query("DELETE FROM maintenance_events WHERE id = :id")
    suspend fun deleteMaintenanceEventById(id: UUID)
    @Query("DELETE FROM maintenance_events WHERE aquariumId = :aquariumId")
    suspend fun deleteMaintenanceEventsForAquarium(aquariumId: UUID)

    @Query("SELECT * FROM products ORDER BY name ASC, id ASC")
    fun observeAllProducts(): Flow<List<ProductEntity>>
    @Query("SELECT * FROM products ORDER BY name ASC, id ASC")
    suspend fun getAllProducts(): List<ProductEntity>
    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductById(id: UUID): ProductEntity?
    @Insert suspend fun insertProduct(product: ProductEntity)
    @Update suspend fun updateProduct(product: ProductEntity)
    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProductById(id: UUID)
    @Query("SELECT COUNT(*) FROM dosing_plans WHERE productId = :productId")
    suspend fun countDosingPlansForProduct(productId: UUID): Int

    @Insert suspend fun insertDosingPlan(plan: DosingPlanEntity)
    @Update suspend fun updateDosingPlan(plan: DosingPlanEntity)
    @Query("SELECT * FROM dosing_plans WHERE id = :id")
    suspend fun getDosingPlanById(id: UUID): DosingPlanEntity?
    @Query("DELETE FROM dosing_plans WHERE id = :id")
    suspend fun deleteDosingPlanById(id: UUID)
    @Query("DELETE FROM dosing_plans WHERE aquariumId = :aquariumId")
    suspend fun deleteDosingPlansForAquarium(aquariumId: UUID)
    @Query("SELECT * FROM dosing_plans WHERE aquariumId = :aquariumId ORDER BY startDate DESC, id ASC")
    fun observeDosingPlansForAquarium(aquariumId: UUID): Flow<List<DosingPlanEntity>>
    @Query("SELECT * FROM dosing_plans WHERE aquariumId = :aquariumId ORDER BY startDate DESC, id ASC")
    suspend fun getDosingPlansForAquarium(aquariumId: UUID): List<DosingPlanEntity>

    @Insert suspend fun insertDosingEvent(event: DosingEventEntity)
    @Query("SELECT * FROM dosing_events WHERE aquariumId = :aquariumId ORDER BY occurredAt DESC, id ASC")
    fun observeDosingEventsForAquarium(aquariumId: UUID): Flow<List<DosingEventEntity>>
    @Query("SELECT * FROM dosing_events WHERE aquariumId = :aquariumId ORDER BY occurredAt DESC, id ASC")
    suspend fun getDosingEventsForAquarium(aquariumId: UUID): List<DosingEventEntity>
    @Query("DELETE FROM dosing_events WHERE id = :id")
    suspend fun deleteDosingEventById(id: UUID)
    @Query("DELETE FROM dosing_events WHERE aquariumId = :aquariumId")
    suspend fun deleteDosingEventsForAquarium(aquariumId: UUID)

    @Query("SELECT * FROM species ORDER BY commonName ASC, id ASC")
    fun observeAllSpecies(): Flow<List<SpeciesEntity>>
    @Query("SELECT * FROM species ORDER BY commonName ASC, id ASC")
    suspend fun getAllSpecies(): List<SpeciesEntity>
    @Query("SELECT * FROM species WHERE id = :id")
    suspend fun getSpeciesById(id: UUID): SpeciesEntity?
    @Insert suspend fun insertSpecies(species: SpeciesEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSpeciesIgnore(species: SpeciesEntity): Long
    @Update suspend fun updateSpecies(species: SpeciesEntity)
    @Query("DELETE FROM species WHERE id = :id")
    suspend fun deleteSpeciesById(id: UUID)
    @Query("SELECT COUNT(*) FROM stock_entries WHERE speciesId = :speciesId")
    suspend fun countStockEntriesForSpecies(speciesId: UUID): Int

    @Insert suspend fun insertStockEntry(entry: StockEntryEntity)
    @Update suspend fun updateStockEntry(entry: StockEntryEntity)
    @Query("SELECT * FROM stock_entries WHERE id = :id")
    suspend fun getStockEntryById(id: UUID): StockEntryEntity?
    @Query("SELECT * FROM stock_entries WHERE aquariumId = :aquariumId ORDER BY introducedAt DESC, id ASC")
    fun observeStockEntriesForAquarium(aquariumId: UUID): Flow<List<StockEntryEntity>>
    @Query("SELECT * FROM stock_entries WHERE aquariumId = :aquariumId ORDER BY introducedAt DESC, id ASC")
    suspend fun getStockEntriesForAquarium(aquariumId: UUID): List<StockEntryEntity>
    @Query("DELETE FROM stock_entries WHERE id = :id")
    suspend fun deleteStockEntryById(id: UUID)
    @Query("DELETE FROM stock_entries WHERE aquariumId = :aquariumId")
    suspend fun deleteStockEntriesForAquarium(aquariumId: UUID)

    @Transaction
    suspend fun seedCatalogEntities(
        profiles: List<AquariumProfileEntity>,
        species: List<SpeciesEntity>,
    ) {
        val existingProfiles = getAllProfiles()
        val existingIds = existingProfiles.mapTo(mutableSetOf()) { it.id }
        val existingNamesLower = existingProfiles.mapTo(mutableSetOf()) { it.name.trim().lowercase() }
        for (profile in profiles) {
            val normalizedName = profile.name.trim().lowercase()
            if (profile.id !in existingIds && normalizedName !in existingNamesLower) {
                insertProfileIgnore(profile)
                existingIds.add(profile.id)
                existingNamesLower.add(normalizedName)
            }
        }
        val existingSpecies = getAllSpecies()
        val existingSpeciesIds = existingSpecies.mapTo(mutableSetOf()) { it.id }
        val existingCommonKeys = existingSpecies.mapTo(mutableSetOf()) {
            "${it.kind.name}:${it.commonName.trim().lowercase()}"
        }
        val existingScientificLower = existingSpecies.mapNotNullTo(mutableSetOf()) {
            it.scientificName?.trim()?.lowercase()
        }
        for (item in species) {
            val commonKey = "${item.kind.name}:${item.commonName.trim().lowercase()}"
            val sciLower = item.scientificName?.trim()?.lowercase()
            if (item.id !in existingSpeciesIds &&
                commonKey !in existingCommonKeys &&
                (sciLower == null || sciLower !in existingScientificLower)
            ) {
                insertSpeciesIgnore(item)
                existingSpeciesIds.add(item.id)
                existingCommonKeys.add(commonKey)
                if (sciLower != null) {
                    existingScientificLower.add(sciLower)
                }
            }
        }
    }

    @Transaction
    suspend fun insertAquariumWithProfiles(
        aquarium: AquariumEntity,
        profileIds: Set<UUID>,
    ) {
        insert(aquarium)
        for (profileId in profileIds) {
            insertProfileLink(AquariumProfileLinkEntity(aquarium.id, profileId))
        }
    }

    @Transaction
    suspend fun updateAquariumWithProfiles(
        id: UUID,
        name: String,
        netLitres: BigDecimal?,
        grossLitres: BigDecimal?,
        notes: String?,
        profileIds: Set<UUID>,
    ) {
        val existing = getAquariumById(id) ?: throw IllegalArgumentException("Aquarium not found: $id")
        update(
            existing.copy(
                name = name,
                netLitres = netLitres,
                grossLitres = grossLitres,
                notes = notes,
            )
        )
        deleteProfileLinksForAquarium(id)
        for (profileId in profileIds) {
            insertProfileLink(AquariumProfileLinkEntity(id, profileId))
        }
    }

    @Transaction
    suspend fun deleteAquariumWithHistories(id: UUID) {
        deleteDosingEventsForAquarium(id)
        deleteDosingPlansForAquarium(id)
        deleteMeasurementsForAquarium(id)
        deleteMaintenanceEventsForAquarium(id)
        deleteStockEntriesForAquarium(id)
        deleteProfileLinksForAquarium(id)
        deleteAquariumById(id)
    }

    @Transaction
    suspend fun createUniqueProfile(id: UUID, trimmedName: String): AquariumProfileEntity {
        val existing = getAllProfiles()
        require(existing.none { it.name.equals(trimmedName, ignoreCase = true) }) {
            "Profile label '$trimmedName' already exists"
        }
        val entity = AquariumProfileEntity(id = id, name = trimmedName)
        insertProfile(entity)
        return entity
    }

    @Transaction
    suspend fun createUniqueSpecies(
        id: UUID,
        kind: SpeciesKind,
        commonName: String,
        scientificName: String?,
    ): SpeciesEntity {
        val existing = getAllSpecies().map { it.toDomain() }
        validateUniqueSpeciesCatalog(
            existing = existing,
            kind = kind,
            commonName = commonName,
            scientificName = scientificName,
        )
        val entity = SpeciesEntity(
            id = id,
            kind = kind,
            commonName = commonName,
            scientificName = scientificName,
        )
        insertSpecies(entity)
        return entity
    }

    @Transaction
    suspend fun updateUniqueSpecies(
        id: UUID,
        kind: SpeciesKind,
        commonName: String,
        scientificName: String?,
    ): SpeciesEntity {
        val current = getSpeciesById(id)
            ?: throw IllegalArgumentException("Species not found: $id")
        val existing = getAllSpecies().map { it.toDomain() }
        validateUniqueSpeciesCatalog(
            existing = existing,
            kind = kind,
            commonName = commonName,
            scientificName = scientificName,
            excludingId = id,
        )
        val updated = current.copy(
            kind = kind,
            commonName = commonName,
            scientificName = scientificName,
        )
        updateSpecies(updated)
        return updated
    }

    @Transaction
    suspend fun deleteUnreferencedSpecies(id: UUID) {
        val count = countStockEntriesForSpecies(id)
        require(count == 0) {
            "Cannot delete species because it is referenced by $count stock entry record(s)"
        }
        deleteSpeciesById(id)
    }

    @Transaction
    suspend fun createUniqueProduct(id: UUID, trimmedName: String): ProductEntity {
        val existing = getAllProducts()
        require(existing.none { it.name.equals(trimmedName, ignoreCase = true) }) {
            "Product '$trimmedName' already exists"
        }
        val entity = ProductEntity(id = id, name = trimmedName)
        insertProduct(entity)
        return entity
    }

    @Transaction
    suspend fun updateUniqueProduct(id: UUID, trimmedName: String): ProductEntity {
        val current = getProductById(id)
            ?: throw IllegalArgumentException("Product not found: $id")
        val existing = getAllProducts()
        require(existing.none { it.id != id && it.name.equals(trimmedName, ignoreCase = true) }) {
            "Product '$trimmedName' already exists"
        }
        val updated = current.copy(name = trimmedName)
        updateProduct(updated)
        return updated
    }

    @Transaction
    suspend fun deleteUnreferencedProduct(id: UUID) {
        val count = countDosingPlansForProduct(id)
        require(count == 0) {
            "Cannot delete product because it is referenced by $count dosing plan(s)"
        }
        deleteProductById(id)
    }

    @Transaction
    suspend fun recordPlanDosingSnapshot(
        eventId: UUID,
        aquariumId: UUID,
        planId: UUID,
        occurredAt: Instant,
    ) {
        val plan = getDosingPlanById(planId)
            ?: throw IllegalArgumentException("Dosing plan not found: $planId")
        require(plan.aquariumId == aquariumId) {
            "Dosing plan $planId belongs to aquarium ${plan.aquariumId}, not $aquariumId"
        }
        val product = getProductById(plan.productId)
            ?: throw IllegalArgumentException("Product not found for dosing plan: ${plan.productId}")
        val event = DosingEventEntity(
            id = eventId,
            planId = plan.id,
            aquariumId = aquariumId,
            occurredAt = occurredAt,
            productNameSnapshot = product.name,
            dose = plan.dose,
            unit = plan.unit,
        )
        insertDosingEvent(event)
    }

    @Query("SELECT * FROM aquariums ORDER BY id ASC")
    suspend fun getAllAquariumsOrdered(): List<AquariumEntity>

    @Query("SELECT * FROM aquarium_profiles ORDER BY id ASC")
    suspend fun getAllProfilesOrdered(): List<AquariumProfileEntity>

    @Query("SELECT * FROM aquarium_profile_links ORDER BY aquariumId ASC, profileId ASC")
    suspend fun getAllProfileLinksOrdered(): List<AquariumProfileLinkEntity>

    @Query("SELECT * FROM measurements ORDER BY id ASC")
    suspend fun getAllMeasurementsOrdered(): List<MeasurementEntity>

    @Query("SELECT * FROM maintenance_events ORDER BY id ASC")
    suspend fun getAllMaintenanceEventsOrdered(): List<MaintenanceEventEntity>

    @Query("SELECT * FROM products ORDER BY id ASC")
    suspend fun getAllProductsOrdered(): List<ProductEntity>

    @Query("SELECT * FROM dosing_plans ORDER BY id ASC")
    suspend fun getAllDosingPlansOrdered(): List<DosingPlanEntity>

    @Query("SELECT * FROM dosing_events ORDER BY id ASC")
    suspend fun getAllDosingEventsOrdered(): List<DosingEventEntity>

    @Query("SELECT * FROM species ORDER BY id ASC")
    suspend fun getAllSpeciesOrdered(): List<SpeciesEntity>

    @Query("SELECT * FROM stock_entries ORDER BY id ASC")
    suspend fun getAllStockEntriesOrdered(): List<StockEntryEntity>

    @Query("DELETE FROM dosing_events")
    suspend fun deleteAllDosingEvents()

    @Query("DELETE FROM dosing_plans")
    suspend fun deleteAllDosingPlans()

    @Query("DELETE FROM measurements")
    suspend fun deleteAllMeasurements()

    @Query("DELETE FROM maintenance_events")
    suspend fun deleteAllMaintenanceEvents()

    @Query("DELETE FROM stock_entries")
    suspend fun deleteAllStockEntries()

    @Query("DELETE FROM aquarium_profile_links")
    suspend fun deleteAllProfileLinks()

    @Query("DELETE FROM aquariums")
    suspend fun deleteAllAquariums()

    @Query("DELETE FROM aquarium_profiles")
    suspend fun deleteAllProfiles()

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()

    @Query("DELETE FROM species")
    suspend fun deleteAllSpecies()

    @Transaction
    suspend fun readFullLibrarySnapshot(): com.pietrondo.aquariummanager.domain.LibrarySnapshot {
        val aquariums = getAllAquariumsOrdered().map {
            com.pietrondo.aquariummanager.domain.AquariumSnapshotRecord(
                id = it.id,
                name = it.name,
                netLitres = it.netLitres,
                grossLitres = it.grossLitres,
                notes = it.notes,
                createdAt = it.createdAt,
            )
        }
        val profiles = getAllProfilesOrdered().map {
            com.pietrondo.aquariummanager.domain.AquariumProfileSnapshotRecord(
                id = it.id,
                name = it.name,
            )
        }
        val links = getAllProfileLinksOrdered().map {
            com.pietrondo.aquariummanager.domain.AquariumProfileLinkSnapshotRecord(
                aquariumId = it.aquariumId,
                profileId = it.profileId,
            )
        }
        val measurements = getAllMeasurementsOrdered().map {
            com.pietrondo.aquariummanager.domain.MeasurementSnapshotRecord(
                id = it.id,
                aquariumId = it.aquariumId,
                recordedAt = it.recordedAt,
                ph = it.ph,
                khDkh = it.khDkh,
                ghDgh = it.ghDgh,
                no2MgL = it.no2MgL,
                no3MgL = it.no3MgL,
                tdsPpm = it.tdsPpm,
                temperatureC = it.temperatureC,
            )
        }
        val maintenanceEvents = getAllMaintenanceEventsOrdered().map {
            com.pietrondo.aquariummanager.domain.MaintenanceEventSnapshotRecord(
                id = it.id,
                aquariumId = it.aquariumId,
                type = it.type,
                occurredAt = it.occurredAt,
                changedLitres = it.changedLitres,
                notes = it.notes,
            )
        }
        val products = getAllProductsOrdered().map {
            com.pietrondo.aquariummanager.domain.ProductSnapshotRecord(
                id = it.id,
                name = it.name,
            )
        }
        val dosingPlans = getAllDosingPlansOrdered().map {
            com.pietrondo.aquariummanager.domain.DosingPlanSnapshotRecord(
                id = it.id,
                aquariumId = it.aquariumId,
                productId = it.productId,
                dose = it.dose,
                unit = it.unit,
                frequency = it.frequency,
                weekdays = it.weekdays,
                startDate = it.startDate,
                endDate = it.endDate,
                timeZone = it.timeZone,
            )
        }
        val dosingEvents = getAllDosingEventsOrdered().map {
            com.pietrondo.aquariummanager.domain.DosingEventSnapshotRecord(
                id = it.id,
                planId = it.planId,
                aquariumId = it.aquariumId,
                occurredAt = it.occurredAt,
                productNameSnapshot = it.productNameSnapshot,
                dose = it.dose,
                unit = it.unit,
            )
        }
        val species = getAllSpeciesOrdered().map {
            com.pietrondo.aquariummanager.domain.SpeciesSnapshotRecord(
                id = it.id,
                kind = it.kind,
                scientificName = it.scientificName,
                commonName = it.commonName,
            )
        }
        val stockEntries = getAllStockEntriesOrdered().map {
            com.pietrondo.aquariummanager.domain.StockEntrySnapshotRecord(
                id = it.id,
                aquariumId = it.aquariumId,
                speciesId = it.speciesId,
                quantity = it.quantity,
                introducedAt = it.introducedAt,
                exitedAt = it.exitedAt,
                notes = it.notes,
            )
        }
        return com.pietrondo.aquariummanager.domain.LibrarySnapshot(
            aquariums = aquariums,
            aquariumProfiles = profiles,
            aquariumProfileLinks = links,
            measurements = measurements,
            maintenanceEvents = maintenanceEvents,
            products = products,
            dosingPlans = dosingPlans,
            dosingEvents = dosingEvents,
            species = species,
            stockEntries = stockEntries,
        )
    }

    @Transaction
    suspend fun replaceEntireLibrary(
        snapshot: com.pietrondo.aquariummanager.domain.LibrarySnapshot,
        afterClearHook: (() -> Unit)? = null,
    ) {
        // Delete existing rows in child-to-parent FK-safe order
        deleteAllDosingEvents()
        deleteAllDosingPlans()
        deleteAllMeasurements()
        deleteAllMaintenanceEvents()
        deleteAllStockEntries()
        deleteAllProfileLinks()
        deleteAllAquariums()
        deleteAllProfiles()
        deleteAllProducts()
        deleteAllSpecies()

        afterClearHook?.invoke()

        // Insert validated rows in parent-to-child FK-safe order
        for (item in snapshot.aquariums) {
            insert(
                AquariumEntity(
                    id = item.id,
                    name = item.name,
                    netLitres = item.netLitres,
                    grossLitres = item.grossLitres,
                    notes = item.notes,
                    createdAt = item.createdAt,
                )
            )
        }
        for (item in snapshot.aquariumProfiles) {
            insertProfile(
                AquariumProfileEntity(
                    id = item.id,
                    name = item.name,
                )
            )
        }
        for (item in snapshot.aquariumProfileLinks) {
            insertProfileLink(
                AquariumProfileLinkEntity(
                    aquariumId = item.aquariumId,
                    profileId = item.profileId,
                )
            )
        }
        for (item in snapshot.products) {
            insertProduct(
                ProductEntity(
                    id = item.id,
                    name = item.name,
                )
            )
        }
        for (item in snapshot.species) {
            insertSpecies(
                SpeciesEntity(
                    id = item.id,
                    kind = item.kind,
                    scientificName = item.scientificName,
                    commonName = item.commonName,
                )
            )
        }
        for (item in snapshot.measurements) {
            insertMeasurement(
                MeasurementEntity(
                    id = item.id,
                    aquariumId = item.aquariumId,
                    recordedAt = item.recordedAt,
                    ph = item.ph,
                    khDkh = item.khDkh,
                    ghDgh = item.ghDgh,
                    no2MgL = item.no2MgL,
                    no3MgL = item.no3MgL,
                    tdsPpm = item.tdsPpm,
                    temperatureC = item.temperatureC,
                )
            )
        }
        for (item in snapshot.maintenanceEvents) {
            insertMaintenanceEvent(
                MaintenanceEventEntity(
                    id = item.id,
                    aquariumId = item.aquariumId,
                    type = item.type,
                    occurredAt = item.occurredAt,
                    changedLitres = item.changedLitres,
                    notes = item.notes,
                )
            )
        }
        for (item in snapshot.dosingPlans) {
            insertDosingPlan(
                DosingPlanEntity(
                    id = item.id,
                    aquariumId = item.aquariumId,
                    productId = item.productId,
                    dose = item.dose,
                    unit = item.unit,
                    frequency = item.frequency,
                    weekdays = item.weekdays,
                    startDate = item.startDate,
                    endDate = item.endDate,
                    timeZone = item.timeZone,
                )
            )
        }
        for (item in snapshot.dosingEvents) {
            insertDosingEvent(
                DosingEventEntity(
                    id = item.id,
                    planId = item.planId,
                    aquariumId = item.aquariumId,
                    occurredAt = item.occurredAt,
                    productNameSnapshot = item.productNameSnapshot,
                    dose = item.dose,
                    unit = item.unit,
                )
            )
        }
        for (item in snapshot.stockEntries) {
            insertStockEntry(
                StockEntryEntity(
                    id = item.id,
                    aquariumId = item.aquariumId,
                    speciesId = item.speciesId,
                    quantity = item.quantity,
                    introducedAt = item.introducedAt,
                    exitedAt = item.exitedAt,
                    notes = item.notes,
                )
            )
        }
    }
}

@Database(
    entities = [
        AquariumEntity::class,
        AquariumProfileEntity::class,
        AquariumProfileLinkEntity::class,
        MeasurementEntity::class,
        MaintenanceEventEntity::class,
        ProductEntity::class,
        DosingPlanEntity::class,
        DosingEventEntity::class,
        SpeciesEntity::class,
        StockEntryEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(AquariumConverters::class)
abstract class AquariumDatabase : RoomDatabase() {
    abstract fun aquariumDao(): AquariumDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `aquarium_profiles` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `aquarium_profile_links` (`aquariumId` TEXT NOT NULL, `profileId` TEXT NOT NULL, PRIMARY KEY(`aquariumId`, `profileId`), FOREIGN KEY(`aquariumId`) REFERENCES `aquariums`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`profileId`) REFERENCES `aquarium_profiles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_aquarium_profile_links_aquariumId` ON `aquarium_profile_links` (`aquariumId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_aquarium_profile_links_profileId` ON `aquarium_profile_links` (`profileId`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `measurements` (`id` TEXT NOT NULL, `aquariumId` TEXT NOT NULL, `recordedAt` TEXT NOT NULL, `ph` TEXT, `khDkh` TEXT, `ghDgh` TEXT, `no2MgL` TEXT, `no3MgL` TEXT, `tdsPpm` TEXT, `temperatureC` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`aquariumId`) REFERENCES `aquariums`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_measurements_aquariumId` ON `measurements` (`aquariumId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_measurements_aquariumId_recordedAt` ON `measurements` (`aquariumId`, `recordedAt`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `maintenance_events` (`id` TEXT NOT NULL, `aquariumId` TEXT NOT NULL, `type` TEXT NOT NULL, `occurredAt` TEXT NOT NULL, `changedLitres` TEXT, `notes` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`aquariumId`) REFERENCES `aquariums`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_maintenance_events_aquariumId` ON `maintenance_events` (`aquariumId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_maintenance_events_aquariumId_occurredAt` ON `maintenance_events` (`aquariumId`, `occurredAt`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `products` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `dosing_plans` (`id` TEXT NOT NULL, `aquariumId` TEXT NOT NULL, `productId` TEXT NOT NULL, `dose` TEXT NOT NULL, `unit` TEXT NOT NULL, `frequency` TEXT NOT NULL, `weekdays` TEXT NOT NULL, `startDate` TEXT NOT NULL, `endDate` TEXT, `timeZone` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`aquariumId`) REFERENCES `aquariums`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_dosing_plans_aquariumId` ON `dosing_plans` (`aquariumId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_dosing_plans_productId` ON `dosing_plans` (`productId`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `dosing_events` (`id` TEXT NOT NULL, `planId` TEXT, `aquariumId` TEXT NOT NULL, `occurredAt` TEXT NOT NULL, `productNameSnapshot` TEXT NOT NULL, `dose` TEXT NOT NULL, `unit` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`planId`) REFERENCES `dosing_plans`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`aquariumId`) REFERENCES `aquariums`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_dosing_events_planId` ON `dosing_events` (`planId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_dosing_events_aquariumId` ON `dosing_events` (`aquariumId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_dosing_events_aquariumId_occurredAt` ON `dosing_events` (`aquariumId`, `occurredAt`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `species` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `scientificName` TEXT, `commonName` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stock_entries` (`id` TEXT NOT NULL, `aquariumId` TEXT NOT NULL, `speciesId` TEXT NOT NULL, `quantity` TEXT, `introducedAt` TEXT NOT NULL, `exitedAt` TEXT, `notes` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`aquariumId`) REFERENCES `aquariums`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`speciesId`) REFERENCES `species`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_stock_entries_aquariumId` ON `stock_entries` (`aquariumId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_stock_entries_speciesId` ON `stock_entries` (`speciesId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_stock_entries_aquariumId_introducedAt` ON `stock_entries` (`aquariumId`, `introducedAt`)"
                )
            }
        }
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
    }
}
