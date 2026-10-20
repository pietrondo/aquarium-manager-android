package com.pietrondo.aquariummanager.data

import androidx.room.Room
import com.pietrondo.aquariummanager.domain.AquariumDraft
import com.pietrondo.aquariummanager.domain.MaintenanceDraft
import com.pietrondo.aquariummanager.domain.MeasurementDraft
import java.math.BigDecimal
import java.time.Instant
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
class MeasurementsAndMaintenanceRepositoryTest {
    private lateinit var db: AquariumDatabase
    private lateinit var dao: AquariumDao
    private lateinit var repo: RoomAquariumRepository
    private var currentInstant: Instant = Instant.parse("2026-10-19T12:00:00Z")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AquariumDatabase::class.java,
        ).build()
        dao = db.aquariumDao()
        repo = RoomAquariumRepository(dao, clock = { currentInstant })
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun measurementRoundtripPreservesNullVsZeroSortsSameTimestampsDeterministicallyAndIsolatesAquariums() = runBlocking {
        val tankA = repo.createAquarium(AquariumDraft(name = "Alpha Reef", netLitres = BigDecimal("80")))
        val tankB = repo.createAquarium(AquariumDraft(name = "Beta Shrimp", netLitres = BigDecimal("30")))

        val t1 = Instant.parse("2026-10-19T08:00:00Z")
        val t2 = Instant.parse("2026-10-19T10:00:00Z")

        val m1 = repo.recordMeasurement(
            aquariumId = tankA,
            draft = MeasurementDraft(
                recordedAt = t1,
                ph = BigDecimal("8.15"),
                khDkh = BigDecimal("8.5"),
                ghDgh = null,
                no2MgL = BigDecimal.ZERO,
                no3MgL = BigDecimal("2.50"),
                tdsPpm = null,
                temperatureC = BigDecimal("25.2"),
            ),
        )

        // Two measurements at the exact same timestamp t2 on tankA to verify deterministic tie-breaking (recordedAt DESC, id ASC)
        val sameTimeId1 = UUID.fromString("00000000-0000-4000-8000-000000000001")
        val sameTimeId2 = UUID.fromString("00000000-0000-4000-8000-000000000002")
        dao.insertMeasurement(
            MeasurementEntity(
                id = sameTimeId2,
                aquariumId = tankA,
                recordedAt = t2,
                ph = BigDecimal("8.20"),
                no2MgL = BigDecimal.ZERO,
            )
        )
        dao.insertMeasurement(
            MeasurementEntity(
                id = sameTimeId1,
                aquariumId = tankA,
                recordedAt = t2,
                ph = BigDecimal("8.25"),
                no2MgL = null,
            )
        )

        // Separate measurement on tankB
        val tankBMId = repo.recordMeasurement(
            aquariumId = tankB,
            draft = MeasurementDraft(
                recordedAt = t2,
                ph = BigDecimal("6.4"),
                khDkh = BigDecimal.ZERO,
                ghDgh = BigDecimal("5.0"),
                tdsPpm = BigDecimal("140"),
            ),
        )

        val tankAHistory = repo.observeMeasurements(tankA).first()
        val tankBHistory = repo.observeMeasurements(tankB).first()

        // Deterministic sort: t2 (sameTimeId1), t2 (sameTimeId2), t1 (m1)
        assertEquals(listOf(sameTimeId1, sameTimeId2, m1), tankAHistory.map { it.id })

        // Verify null vs zero roundtrip on m1
        val restoredM1 = tankAHistory.last()
        assertEquals(0, BigDecimal("8.15").compareTo(restoredM1.ph))
        assertEquals(0, BigDecimal("8.5").compareTo(restoredM1.khDkh))
        assertNull(restoredM1.ghDgh)
        assertEquals(0, BigDecimal.ZERO.compareTo(restoredM1.no2MgL))
        assertEquals(0, BigDecimal("2.5").compareTo(restoredM1.no3MgL))
        assertNull(restoredM1.tdsPpm)
        assertEquals(0, BigDecimal("25.2").compareTo(restoredM1.temperatureC))

        // Verify strict isolation of tankB
        assertEquals(listOf(tankBMId), tankBHistory.map { it.id })
        assertEquals(0, BigDecimal.ZERO.compareTo(tankBHistory.single().khDkh))
        assertNull(tankBHistory.single().no2MgL)

        // Delete a measurement on tankA and confirm tankB is untouched
        repo.deleteMeasurement(sameTimeId2)
        assertEquals(listOf(sameTimeId1, m1), repo.observeMeasurements(tankA).first().map { it.id })
        assertEquals(1, repo.observeMeasurements(tankB).first().size)
    }

    @Test
    fun maintenanceCrudSupportsOptionalVolumeEditingCatalogValidationAndAquariumFiltering() = runBlocking {
        val tankA = repo.createAquarium(AquariumDraft(name = "Alpha Planted", netLitres = BigDecimal("60")))
        val tankB = repo.createAquarium(AquariumDraft(name = "Beta Cube", netLitres = BigDecimal("25")))

        val catalogTypes = repo.observeMaintenanceTypeCatalog().first()
        assertEquals(
            listOf(
                "WATER_CHANGE",
                "FILTER_CLEANING",
                "GLASS_CLEANING",
                "TRIMMING",
                "SUBSTRATE_VACUUM",
                "EQUIPMENT_CHECK",
            ),
            catalogTypes.map { it.code },
        )

        // Unknown maintenance type code is rejected
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repo.recordMaintenanceEvent(tankA, MaintenanceDraft(type = "UNKNOWN_TYPE"))
            }
        }

        val t1 = Instant.parse("2026-10-18T18:00:00Z")
        val t2 = Instant.parse("2026-10-19T18:00:00Z")

        // Create maintenance event without changedLitres (optional null)
        val filterEventId = repo.recordMaintenanceEvent(
            aquariumId = tankA,
            draft = MaintenanceDraft(
                type = "FILTER_CLEANING",
                occurredAt = t1,
                changedLitres = null,
                notes = "  Rinsed biomedia in tank water  ",
            ),
        )

        // Create maintenance event with changedLitres exceeding net volume (allowed, no hard cap!)
        val waterChangeId = repo.recordMaintenanceEvent(
            aquariumId = tankA,
            draft = MaintenanceDraft(
                type = "WATER_CHANGE",
                occurredAt = t2,
                changedLitres = BigDecimal("75.5"),
                notes = "100%+ sequential flush",
            ),
        )

        // Create maintenance event on tankB
        val tankBEventId = repo.recordMaintenanceEvent(
            aquariumId = tankB,
            draft = MaintenanceDraft(
                type = "TRIMMING",
                occurredAt = t2,
                changedLitres = null,
                notes = "Trimmed rotala",
            ),
        )

        val tankAEvents = repo.observeMaintenanceEvents(tankA).first()
        assertEquals(listOf(waterChangeId, filterEventId), tankAEvents.map { it.id })
        assertEquals(0, BigDecimal("75.5").compareTo(tankAEvents[0].changedLitres))
        assertNull(tankAEvents[1].changedLitres)
        assertEquals("Rinsed biomedia in tank water", tankAEvents[1].notes)

        // Tank B filtering isolation
        val tankBEvents = repo.observeMaintenanceEvents(tankB).first()
        assertEquals(listOf(tankBEventId), tankBEvents.map { it.id })

        // Edit existing maintenance event on tankA
        repo.updateMaintenanceEvent(
            id = filterEventId,
            aquariumId = tankA,
            draft = MaintenanceDraft(
                type = "WATER_CHANGE",
                occurredAt = t1,
                changedLitres = BigDecimal("15"),
                notes = "   ",
            ),
        )

        val afterEdit = repo.observeMaintenanceEvents(tankA).first().first { it.id == filterEventId }
        assertEquals("WATER_CHANGE", afterEdit.type)
        assertEquals(0, BigDecimal("15").compareTo(afterEdit.changedLitres))
        assertNull(afterEdit.notes)

        // Delete event on tankA
        repo.deleteMaintenanceEvent(waterChangeId)
        assertEquals(listOf(filterEventId), repo.observeMaintenanceEvents(tankA).first().map { it.id })
        assertEquals(listOf(tankBEventId), repo.observeMaintenanceEvents(tankB).first().map { it.id })
    }
}
