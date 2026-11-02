package com.pietrondo.aquariummanager.data

import androidx.room.Room
import com.pietrondo.aquariummanager.domain.AquariumDraft
import com.pietrondo.aquariummanager.domain.CUSTOM_MAINTENANCE_TYPE_PREFIX
import com.pietrondo.aquariummanager.domain.MaintenanceDraft
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
class CustomMaintenanceTypeRepositoryTest {
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
        repo = RoomAquariumRepository(dao, clock = { Instant.parse("2026-10-27T10:00:00Z") })
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun customMaintenanceTypeIsReusableOnEveryTankAndRejectsDuplicateLabels() {
        runBlocking {
            val created = repo.createMaintenanceType("Catappa")

            assertEquals(CUSTOM_MAINTENANCE_TYPE_PREFIX + "Catappa", created.code)
            assertEquals("Catappa", created.label)
            assertTrue(repo.observeMaintenanceTypeCatalog().first().contains(created))

            val tank = repo.createAquarium(AquariumDraft(name = "Alpha"))
            repo.recordMaintenanceEvent(tank, MaintenanceDraft(type = created.code))

            val events = repo.observeMaintenanceEvents(tank).first()
            assertEquals(created.code, events.single().type)

            val duplicate = assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repo.createMaintenanceType("  catappa ") }
            }
            assertTrue(duplicate.message.orEmpty().contains("already exists"))
        }
    }

    @Test
    fun unknownMaintenanceTypeCodeIsStillRejected() {
        runBlocking {
            val tank = repo.createAquarium(AquariumDraft(name = "Beta"))

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repo.recordMaintenanceEvent(tank, MaintenanceDraft(type = "Catappa")) }
            }
        }
    }

    @Test
    fun customMaintenanceTypeTravelsWithTheBackupAndSurvivesARestore() {
        runBlocking {
            val tank = repo.createAquarium(AquariumDraft(name = "Alpha"))
            val type = repo.createMaintenanceType("Catappa")
            repo.recordMaintenanceEvent(tank, MaintenanceDraft(type = type.code))

            // Regression: the packaged catalog does not know the custom code, so the
            // archive has to carry the type itself or the backup is rejected.
            val bytes = repo.exportBackupBytes()
            val preview = repo.inspectBackupBytes(bytes)
            assertEquals(1, preview.counts.maintenanceTypes)

            repo.restoreBackupFromBytes(bytes, confirmReplace = true)

            val events = repo.observeMaintenanceEvents(tank).first()
            assertEquals(type.code, events.single().type)
            assertTrue(repo.observeMaintenanceTypeCatalog().first().contains(type))
        }
    }
}
