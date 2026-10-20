package com.pietrondo.aquariummanager.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import com.pietrondo.aquariummanager.domain.AquariumDraft
import com.pietrondo.aquariummanager.domain.AquariumProfile
import com.pietrondo.aquariummanager.domain.CatalogBundle
import com.pietrondo.aquariummanager.domain.DoseUnitDimension
import com.pietrondo.aquariummanager.domain.DosingFrequency
import com.pietrondo.aquariummanager.domain.SpeciesCatalogItem
import com.pietrondo.aquariummanager.domain.SpeciesKind
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CatalogAndCrudRepositoryTest {
    private lateinit var db: AquariumDatabase
    private lateinit var dao: AquariumDao
    private lateinit var repo: RoomAquariumRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AquariumDatabase::class.java,
        ).build()
        dao = db.aquariumDao()
        repo = RoomAquariumRepository(dao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun defaultPackagedCatalogLoadsStableUuidsCanonicalUnitsAndSpecies() {
        val catalog = CatalogJsonParser.loadDefaultCatalog()
        assertEquals(1, catalog.version)
        assertEquals(6, catalog.profileLabels.size)
        assertTrue(
            catalog.profileLabels.any {
                it.id == UUID.fromString("00000000-0000-4000-8000-000000000101") && it.name == "Planted"
            }
        )
        assertEquals(
            setOf(
                "WATER_CHANGE",
                "FILTER_CLEANING",
                "GLASS_CLEANING",
                "TRIMMING",
                "SUBSTRATE_VACUUM",
                "EQUIPMENT_CHECK",
            ),
            catalog.maintenanceTypes.map { it.code }.toSet(),
        )
        val unitDimensions = catalog.doseUnits.associate { it.code to it.dimension }
        assertEquals(DoseUnitDimension.VOLUME, unitDimensions["mL"])
        assertEquals(DoseUnitDimension.MASS, unitDimensions["g"])
        assertEquals(DoseUnitDimension.COUNT, unitDimensions["drops"])
        assertEquals(DoseUnitDimension.COUNT, unitDimensions["tabs"])
        assertEquals(6, catalog.species.size)
        assertTrue(
            catalog.species.any {
                it.id == UUID.fromString("00000000-0000-4000-8000-000000000201") &&
                    it.kind == SpeciesKind.FAUNA &&
                    it.commonName == "Amano Shrimp" &&
                    it.scientificName == "Caridina multidentata"
            }
        )
    }

    @Test
    fun catalogParserRejectsMalformedJsonUnknownDimensionsDuplicateIdsAndInvalidUuids() {
        // Malformed JSON
        assertThrows(IllegalArgumentException::class.java) {
            CatalogJsonParser.parseCatalogJson("{not-valid-json")
        }
        // Wrong format
        assertThrows(IllegalArgumentException::class.java) {
            CatalogJsonParser.parseCatalogJson(
                """{"format":"wrong","version":1,"profileLabels":[],"maintenanceTypes":[],"doseUnits":[],"species":[]}"""
            )
        }
        // Unsupported version
        assertThrows(IllegalArgumentException::class.java) {
            CatalogJsonParser.parseCatalogJson(
                """{"format":"aquarium-manager-catalog","version":99,"profileLabels":[],"maintenanceTypes":[],"doseUnits":[],"species":[]}"""
            )
        }
        // Uppercase non-canonical UUID
        assertThrows(IllegalArgumentException::class.java) {
            CatalogJsonParser.parseCatalogJson(
                """{"format":"aquarium-manager-catalog","version":1,"profileLabels":[{"id":"00000000-0000-4000-8000-00000000010A","name":"Planted"}],"maintenanceTypes":[],"doseUnits":[],"species":[]}"""
            )
        }
        // Unknown dose unit dimension
        assertThrows(IllegalArgumentException::class.java) {
            CatalogJsonParser.parseCatalogJson(
                """{"format":"aquarium-manager-catalog","version":1,"profileLabels":[],"maintenanceTypes":[],"doseUnits":[{"code":"cm","label":"Centimetres","dimension":"LENGTH"}],"species":[]}"""
            )
        }
        // Unknown species kind
        assertThrows(IllegalArgumentException::class.java) {
            CatalogJsonParser.parseCatalogJson(
                """{"format":"aquarium-manager-catalog","version":1,"profileLabels":[],"maintenanceTypes":[],"doseUnits":[],"species":[{"id":"00000000-0000-4000-8000-000000000201","kind":"FUNGI","commonName":"Mushroom"}]}"""
            )
        }
        // Duplicate profile IDs
        assertThrows(IllegalArgumentException::class.java) {
            CatalogJsonParser.parseCatalogJson(
                """{"format":"aquarium-manager-catalog","version":1,"profileLabels":[{"id":"00000000-0000-4000-8000-000000000101","name":"Planted"},{"id":"00000000-0000-4000-8000-000000000101","name":"Dutch"}],"maintenanceTypes":[],"doseUnits":[],"species":[]}"""
            )
        }
    }

    @Test
    fun seedUpgradePreservesUserEditedProfilesAndSpeciesWhileAddingNewCatalogEntries() = runBlocking {
        val v1 = CatalogJsonParser.loadDefaultCatalog()
        repo.seedCatalog(v1)

        val plantedId = UUID.fromString("00000000-0000-4000-8000-000000000101")
        val amanoId = UUID.fromString("00000000-0000-4000-8000-000000000201")

        // User customizes a seeded profile label and a seeded species in Room
        dao.updateProfile(AquariumProfileEntity(plantedId, "Custom Dutch Aquascape"))
        dao.updateSpecies(
            SpeciesEntity(
                id = amanoId,
                kind = SpeciesKind.FAUNA,
                scientificName = "Caridina multidentata",
                commonName = "User Customized Amano",
            )
        )

        val newProfileId = UUID.fromString("00000000-0000-4000-8000-000000000199")
        val newSpeciesId = UUID.fromString("00000000-0000-4000-8000-000000000299")
        val v2 = CatalogBundle(
            version = 2,
            profileLabels = v1.profileLabels.map {
                if (it.id == plantedId) AquariumProfile(plantedId, "Seed v2 Overwrite Attempt") else it
            } + AquariumProfile(newProfileId, "Blackwater Biotope"),
            maintenanceTypes = v1.maintenanceTypes,
            doseUnits = v1.doseUnits,
            species = v1.species.map {
                if (it.id == amanoId) it.copy(commonName = "Seed v2 Amano Overwrite Attempt") else it
            } + SpeciesCatalogItem(
                id = newSpeciesId,
                kind = SpeciesKind.FLORA,
                commonName = "Bucephalandra",
                scientificName = "Bucephalandra sp.",
            ),
        )

        // Upgrade seed must NOT overwrite user-edited records, while adding new UUIDs
        repo.seedCatalog(v2)

        val profilesById = repo.observeProfiles().first().associateBy { it.id }
        assertEquals("Custom Dutch Aquascape", profilesById[plantedId]?.name)
        assertEquals("Blackwater Biotope", profilesById[newProfileId]?.name)

        val speciesById = dao.getAllSpecies().associateBy { it.id }
        assertEquals("User Customized Amano", speciesById[amanoId]?.commonName)
        assertEquals("Bucephalandra", speciesById[newSpeciesId]?.commonName)
    }

    @Test
    fun multiAquariumCrudSupportsUnknownLitresOverlappingProfilesAndDuplicateLabelPrevention() = runBlocking {
        repo.ensureCatalogSeeded()
        val initialProfiles = repo.observeProfiles().first()
        val planted = initialProfiles.first { it.name == "Planted" }
        val shrimp = initialProfiles.first { it.name == "Shrimp" }
        val community = initialProfiles.first { it.name == "Freshwater Community" }

        // Duplicate profile name (case-insensitive) and blank name are rejected
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.createProfile("  planted ") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.createProfile("   ") }
        }

        val customProfile = repo.createProfile("  High-Tech CO2 ")
        assertEquals("High-Tech CO2", customProfile.name)

        val nanoId = repo.createAquarium(
            AquariumDraft(
                name = "  Nano Desk Cube ",
                netLitres = null,
                grossLitres = null,
                notes = "  Unmeasured quarantine bowl  ",
                profileIds = setOf(planted.id, shrimp.id),
            )
        )
        val livingRoomId = repo.createAquarium(
            AquariumDraft(
                name = "Living Room Scape",
                netLitres = BigDecimal("142.5"),
                grossLitres = BigDecimal("180"),
                notes = null,
                profileIds = setOf(planted.id, community.id, customProfile.id),
            )
        )

        val details = repo.observeAquariumDetails().first()
        assertEquals(2, details.size)
        val livingRoom = details.first { it.id == livingRoomId }
        val nano = details.first { it.id == nanoId }

        assertEquals("Nano Desk Cube", nano.name)
        assertNull(nano.netLitres)
        assertNull(nano.grossLitres)
        assertEquals("Unmeasured quarantine bowl", nano.notes)
        assertEquals(listOf("Planted", "Shrimp"), nano.profiles.map { it.name })

        assertEquals("Living Room Scape", livingRoom.name)
        assertEquals(0, BigDecimal("142.5").compareTo(livingRoom.netLitres))
        assertEquals(0, BigDecimal("180").compareTo(livingRoom.grossLitres))
        assertNull(livingRoom.notes)
        assertEquals(
            listOf("Freshwater Community", "High-Tech CO2", "Planted"),
            livingRoom.profiles.map { it.name },
        )

        // Update nano cube with known volumes and updated overlapping profiles
        repo.updateAquarium(
            id = nanoId,
            draft = AquariumDraft(
                name = "Nano Shrimp Cube",
                netLitres = BigDecimal("24.5"),
                grossLitres = BigDecimal("30"),
                notes = "   ",
                profileIds = setOf(shrimp.id, customProfile.id),
            ),
        )

        val updatedNano = repo.observeAquariumDetails().first().first { it.id == nanoId }
        assertEquals("Nano Shrimp Cube", updatedNano.name)
        assertEquals(0, BigDecimal("24.5").compareTo(updatedNano.netLitres))
        assertEquals(0, BigDecimal("30").compareTo(updatedNano.grossLitres))
        assertNull(updatedNano.notes)
        assertEquals(listOf("High-Tech CO2", "Shrimp"), updatedNano.profiles.map { it.name })
    }

    @Test
    fun isolatedHistoriesAcrossTwoAquariumsAndConfirmedCascadeDeletion() = runBlocking {
        repo.ensureCatalogSeeded()
        val profiles = repo.observeProfiles().first()
        val species = dao.getAllSpecies().first()
        val product = ProductEntity(UUID.fromString("00000000-0000-4000-8000-000000000301"), "Prime")
        dao.insertProduct(product)

        val tankA = repo.createAquarium(
            AquariumDraft(
                name = "Alpha Reef",
                netLitres = BigDecimal("90"),
                grossLitres = BigDecimal("100"),
                notes = "Tank A",
                profileIds = setOf(profiles[0].id),
            )
        )
        val tankB = repo.createAquarium(
            AquariumDraft(
                name = "Beta Amazon",
                netLitres = BigDecimal("200"),
                grossLitres = BigDecimal("240"),
                notes = "Tank B",
                profileIds = setOf(profiles[1].id),
            )
        )

        val t1 = Instant.parse("2026-10-19T09:00:00Z")
        val t2 = Instant.parse("2026-10-19T10:00:00Z")

        dao.insertMeasurement(MeasurementEntity(UUID.randomUUID(), tankA, t1, ph = BigDecimal("8.2")))
        dao.insertMeasurement(MeasurementEntity(UUID.randomUUID(), tankB, t2, ph = BigDecimal("6.4")))

        dao.insertMaintenanceEvent(MaintenanceEventEntity(UUID.randomUUID(), tankA, "WATER_CHANGE", t1, BigDecimal("10")))
        dao.insertMaintenanceEvent(MaintenanceEventEntity(UUID.randomUUID(), tankB, "TRIMMING", t2))

        val planA = DosingPlanEntity(
            id = UUID.randomUUID(),
            aquariumId = tankA,
            productId = product.id,
            dose = BigDecimal("2"),
            unit = "mL",
            frequency = DosingFrequency.DAILY,
            weekdays = emptySet(),
            startDate = LocalDate.of(2026, 10, 19),
            endDate = null,
            timeZone = ZoneId.of("UTC"),
        )
        val planB = planA.copy(id = UUID.randomUUID(), aquariumId = tankB, dose = BigDecimal("5"))
        dao.insertDosingPlan(planA)
        dao.insertDosingPlan(planB)
        dao.insertDosingEvent(DosingEventEntity(UUID.randomUUID(), planA.id, tankA, t1, "Prime", BigDecimal("2"), "mL"))
        dao.insertDosingEvent(DosingEventEntity(UUID.randomUUID(), planB.id, tankB, t2, "Prime", BigDecimal("5"), "mL"))
        dao.insertStockEntry(StockEntryEntity(UUID.randomUUID(), tankA, species.id, BigDecimal("4"), t1))
        dao.insertStockEntry(StockEntryEntity(UUID.randomUUID(), tankB, species.id, BigDecimal("12"), t2))

        // Verify strict history isolation between Tank A and Tank B
        assertEquals(listOf(BigDecimal("8.2")), dao.getMeasurementsForAquarium(tankA).map { it.ph })
        assertEquals(listOf(BigDecimal("6.4")), dao.getMeasurementsForAquarium(tankB).map { it.ph })
        assertEquals(listOf("WATER_CHANGE"), dao.getMaintenanceEventsForAquarium(tankA).map { it.type })
        assertEquals(listOf("TRIMMING"), dao.getMaintenanceEventsForAquarium(tankB).map { it.type })

        // Updating Tank A's profiles does not affect Tank A's histories or Tank B
        repo.updateAquarium(
            id = tankA,
            draft = AquariumDraft(
                name = "Alpha Reef Updated",
                netLitres = BigDecimal("92"),
                grossLitres = BigDecimal("100"),
                notes = "Updated",
                profileIds = setOf(profiles[0].id, profiles[1].id),
            ),
        )
        assertEquals(1, dao.getMeasurementsForAquarium(tankA).size)
        assertEquals(1, dao.getMeasurementsForAquarium(tankB).size)

        // Direct unconfirmed DAO delete is still blocked by ForeignKey.RESTRICT
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.deleteAquariumById(tankA) }
        }

        // Confirmed repository deletion cascades Tank A's links and histories while preserving Tank B and shared catalogs
        repo.deleteAquarium(tankA)
        val remainingAquariums = repo.observeAquariumDetails().first()
        assertEquals(listOf(tankB), remainingAquariums.map { it.id })
        assertTrue(dao.getMeasurementsForAquarium(tankA).isEmpty())
        assertTrue(dao.getMaintenanceEventsForAquarium(tankA).isEmpty())
        assertTrue(dao.getDosingPlansForAquarium(tankA).isEmpty())
        assertTrue(dao.getDosingEventsForAquarium(tankA).isEmpty())
        assertTrue(dao.getStockEntriesForAquarium(tankA).isEmpty())
        assertTrue(dao.getProfilesForAquarium(tankA).isEmpty())

        // Tank B's histories and shared catalog entities remain intact
        assertEquals(1, dao.getMeasurementsForAquarium(tankB).size)
        assertEquals(1, dao.getMaintenanceEventsForAquarium(tankB).size)
        assertEquals(1, dao.getDosingPlansForAquarium(tankB).size)
        assertEquals(1, dao.getDosingEventsForAquarium(tankB).size)
        assertEquals(1, dao.getStockEntriesForAquarium(tankB).size)
        assertEquals(6, repo.observeProfiles().first().size)
    }
}
