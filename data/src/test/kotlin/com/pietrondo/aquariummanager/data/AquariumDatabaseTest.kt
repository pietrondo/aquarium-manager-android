package com.pietrondo.aquariummanager.data

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.pietrondo.aquariummanager.domain.DosingFrequency
import com.pietrondo.aquariummanager.domain.SpeciesKind
import java.io.File
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AquariumDatabaseTest {
    private lateinit var db: AquariumDatabase
    @Before fun setUp() { db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AquariumDatabase::class.java).build() }
    @After fun tearDown() { db.close() }

    @Test fun decimalConvertersRejectExpansionBeforePlainStringAndAcceptNormalValues() {
        val converters = AquariumConverters()
        assertEquals("1.25", converters.decimalToText(BigDecimal("1.25")))
        assertThrows(IllegalArgumentException::class.java) {
            converters.decimalToText(BigDecimal("1E+100000000"))
        }
    }

    @Test fun freshDatabaseIsHonestlyEmpty() = runBlocking {
        assertEquals(emptyList<Any>(), RoomAquariumRepository(db.aquariumDao()).observeAquariums().first())
    }

    @Test fun repositoryObservesInsertUpdateAndDeterministicOrder() = runBlocking {
        val dao = db.aquariumDao()
        val repo = RoomAquariumRepository(dao)
        val second = row(2, "Reef")
        val first = row(1, "Reef")
        dao.insert(second); dao.insert(first); dao.insert(row(3, "Amazon"))
        assertEquals(listOf("Amazon", "Reef", "Reef"), repo.observeAquariums().first().map { it.name })
        assertEquals(listOf(row(3, "Amazon").id, first.id, second.id), repo.observeAquariums().first().map { it.id })
        val changes = Channel<List<com.pietrondo.aquariummanager.domain.AquariumSummary>>(Channel.UNLIMITED)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { repo.observeAquariums().collect { changes.send(it) } }
        try {
            withTimeout(10000) {
                assertEquals("Amazon", changes.receive().first().name)
                dao.update(second.copy(name = "Aardvark"))
                assertEquals(second.id, changes.receive().first().id)
            }
        } finally { collector.cancel(); changes.close() }
    }

    @Test fun roomStoresVolumesAsLosslessTextAndUtcTime() = runBlocking {
        val row = row(1, "Reef").copy(netLitres = BigDecimal("12.3456789012345678901234567890"), grossLitres = BigDecimal("20.000"))
        db.aquariumDao().insert(row)
        val loaded = db.aquariumDao().observeAquariums().first().single()
        assertEquals(0, row.netLitres!!.compareTo(loaded.netLitres))
        assertEquals(0, row.grossLitres!!.compareTo(loaded.grossLitres))
        assertEquals(row.createdAt, loaded.createdAt)
        db.openHelper.readableDatabase.query("SELECT typeof(netLitres), netLitres, typeof(createdAt), createdAt FROM aquariums").use {
            assertTrue(it.moveToFirst())
            assertEquals("text", it.getString(0)); assertEquals("12.345678901234567890123456789", it.getString(1))
            assertEquals("text", it.getString(2)); assertEquals("2026-10-19T00:00:00.123456789Z", it.getString(3))
        }
    }

    @Test fun foreignKeysRejectOrphansAndCompositePrimaryKeyRejectsDuplicateLinks(): Unit = runBlocking {
        val dao = db.aquariumDao()
        val missingId = uuid(999)
        val aquarium = row(1, "Planted")
        val profile = AquariumProfileEntity(uuid(10), "High-Tech")
        dao.insert(aquarium)
        dao.insertProfile(profile)

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.insertProfileLink(AquariumProfileLinkEntity(missingId, profile.id)) }
        }
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.insertProfileLink(AquariumProfileLinkEntity(aquarium.id, missingId)) }
        }
        dao.insertProfileLink(AquariumProfileLinkEntity(aquarium.id, profile.id))
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.insertProfileLink(AquariumProfileLinkEntity(aquarium.id, profile.id)) }
        }

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.insertMeasurement(MeasurementEntity(uuid(20), missingId, Instant.EPOCH, ph = BigDecimal("7.0"))) }
        }
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.insertMaintenanceEvent(MaintenanceEventEntity(uuid(21), missingId, "WATER_CHANGE", Instant.EPOCH)) }
        }
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                dao.insertDosingPlan(
                    DosingPlanEntity(
                        uuid(22), aquarium.id, missingId, BigDecimal("1"), "mL",
                        DosingFrequency.DAILY, emptySet(), LocalDate.of(2026, 10, 19), null, ZoneId.of("UTC"),
                    )
                )
            }
        }
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                dao.insertDosingEvent(
                    DosingEventEntity(uuid(23), missingId, aquarium.id, Instant.EPOCH, "Macro", BigDecimal("2"), "mL")
                )
            }
        }
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                dao.insertStockEntry(StockEntryEntity(uuid(24), aquarium.id, missingId, BigDecimal("3"), Instant.EPOCH))
            }
        }
    }

    @Test fun deletionSemanticsEnforceRestrictAndPreserveDosingEventSnapshotsOnPlanDelete(): Unit = runBlocking {
        val dao = db.aquariumDao()
        val aquarium = row(1, "Nano")
        val profile = AquariumProfileEntity(uuid(10), "Shrimp")
        val product = ProductEntity(uuid(20), "Liquid Carbon")
        val species = SpeciesEntity(uuid(30), SpeciesKind.FAUNA, "Caridina logemanni", "Crystal Red Shrimp")
        dao.insert(aquarium)
        dao.insertProfile(profile)
        dao.insertProfileLink(AquariumProfileLinkEntity(aquarium.id, profile.id))
        dao.insertProduct(product)
        dao.insertSpecies(species)

        val plan = DosingPlanEntity(
            uuid(40), aquarium.id, product.id, BigDecimal("1.5"), "mL",
            DosingFrequency.WEEKLY, setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            LocalDate.of(2026, 10, 19), null, ZoneId.of("Europe/Rome"),
        )
        dao.insertDosingPlan(plan)
        val event = DosingEventEntity(
            uuid(41), plan.id, aquarium.id, Instant.parse("2026-10-19T08:00:00Z"),
            "Liquid Carbon", BigDecimal("1.5"), "mL",
        )
        dao.insertDosingEvent(event)
        dao.insertStockEntry(StockEntryEntity(uuid(50), aquarium.id, species.id, null, Instant.parse("2026-10-19T09:00:00Z")))

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.deleteAquariumById(aquarium.id) }
        }
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.deleteProfileById(profile.id) }
        }
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.deleteProductById(product.id) }
        }
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.deleteSpeciesById(species.id) }
        }

        // Removing the profile link removes only the link, allowing the profile to be deleted afterwards
        dao.deleteProfileLink(aquarium.id, profile.id)
        assertEquals(emptyList<AquariumProfileEntity>(), dao.getProfilesForAquarium(aquarium.id))
        dao.deleteProfileById(profile.id)

        // Deleting a dosing plan sets dosing_events.planId to null without losing snapshot fields
        dao.deleteDosingPlanById(plan.id)
        val survivingEvent = dao.getDosingEventsForAquarium(aquarium.id).single()
        assertNull(survivingEvent.planId)
        assertEquals("Liquid Carbon", survivingEvent.productNameSnapshot)
        assertEquals(0, BigDecimal("1.5").compareTo(survivingEvent.dose))
        assertEquals("mL", survivingEvent.unit)
    }

    @Test fun historiesOrderByTimestampDescendingWithDeterministicUuidTieBreaker(): Unit = runBlocking {
        val dao = db.aquariumDao()
        val aquarium = row(1, "Dutch")
        val species = SpeciesEntity(uuid(30), SpeciesKind.FLORA, "Rotala rotundifolia", "Rotala")
        dao.insert(aquarium)
        dao.insertSpecies(species)

        val t1 = Instant.parse("2026-10-19T10:00:00Z")
        val t2 = Instant.parse("2026-10-19T11:00:00Z")

        dao.insertMeasurement(MeasurementEntity(uuid(2), aquarium.id, t1, ph = BigDecimal("6.8"), temperatureC = BigDecimal("-0.5")))
        dao.insertMeasurement(MeasurementEntity(uuid(1), aquarium.id, t1, khDkh = BigDecimal.ZERO))
        dao.insertMeasurement(MeasurementEntity(uuid(3), aquarium.id, t2, no3MgL = BigDecimal("10.25")))
        val measurements = dao.getMeasurementsForAquarium(aquarium.id)
        assertEquals(listOf(uuid(3), uuid(1), uuid(2)), measurements.map { it.id })
        assertNull(measurements[1].ph)
        assertEquals(0, BigDecimal.ZERO.compareTo(measurements[1].khDkh))

        dao.insertMaintenanceEvent(MaintenanceEventEntity(uuid(12), aquarium.id, "TRIM", t1))
        dao.insertMaintenanceEvent(MaintenanceEventEntity(uuid(11), aquarium.id, "WATER_CHANGE", t1, BigDecimal("15.5")))
        dao.insertMaintenanceEvent(MaintenanceEventEntity(uuid(13), aquarium.id, "FILTER_CLEAN", t2))
        assertEquals(listOf(uuid(13), uuid(11), uuid(12)), dao.getMaintenanceEventsForAquarium(aquarium.id).map { it.id })

        dao.insertStockEntry(StockEntryEntity(uuid(22), aquarium.id, species.id, BigDecimal("0.75"), t1))
        dao.insertStockEntry(StockEntryEntity(uuid(21), aquarium.id, species.id, null, t1))
        dao.insertStockEntry(StockEntryEntity(uuid(23), aquarium.id, species.id, BigDecimal("5"), t2))
        val stock = dao.getStockEntriesForAquarium(aquarium.id)
        assertEquals(listOf(uuid(23), uuid(21), uuid(22)), stock.map { it.id })
        assertNull(stock[1].quantity)
        assertEquals(0, BigDecimal("0.75").compareTo(stock[2].quantity))
    }

    @Test fun migration1To2PreservesV1DataAndEnablesV2SchemaNonDestructively(): Unit = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val dbFile = context.getDatabasePath("migration-1-to-2.db")
        dbFile.parentFile?.mkdirs()
        dbFile.delete()

        val schemaDir = File("schemas/com.pietrondo.aquariummanager.data.AquariumDatabase")
            .let { if (it.exists()) it else File("data/schemas/com.pietrondo.aquariummanager.data.AquariumDatabase") }
        val v1Json = JSONObject(File(schemaDir, "1.json").readText())
        val v1DbObj = v1Json.getJSONObject("database")
        val v1Hash = v1DbObj.getString("identityHash")
        val v1Entity = v1DbObj.getJSONArray("entities").getJSONObject(0)
        val createV1Sql = v1Entity.getString("createSql").replace("\${TABLE_NAME}", v1Entity.getString("tableName"))

        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { sqlite ->
            sqlite.execSQL(createV1Sql)
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            sqlite.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$v1Hash')")
            sqlite.execSQL(
                "INSERT INTO aquariums (id, name, netLitres, grossLitres, notes, createdAt) VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf(
                    uuid(1).toString(),
                    "Legacy Reef",
                    "87.6543210987654321",
                    "100",
                    null,
                    "2026-10-18T21:00:00.123456789Z",
                ),
            )
            sqlite.version = 1
        }

        val migrated = Room.databaseBuilder(context, AquariumDatabase::class.java, dbFile.name)
            .addMigrations(*AquariumDatabase.ALL_MIGRATIONS)
            .build()
        try {
            val dao = migrated.aquariumDao()
            val loadedV1 = dao.observeAquariums().first().single()
            assertEquals(uuid(1), loadedV1.id)
            assertEquals("Legacy Reef", loadedV1.name)
            assertEquals(0, BigDecimal("87.6543210987654321").compareTo(loadedV1.netLitres))
            assertEquals(0, BigDecimal("100").compareTo(loadedV1.grossLitres))
            assertNull(loadedV1.notes)
            assertEquals(Instant.parse("2026-10-18T21:00:00.123456789Z"), loadedV1.createdAt)

            dao.insertMeasurement(
                MeasurementEntity(uuid(50), loadedV1.id, Instant.parse("2026-10-19T12:00:00Z"), ph = BigDecimal("8.15"))
            )
        } finally {
            migrated.close()
        }

        // Reopen to verify migrated database and newly inserted v2 rows survive reopen
        val reopened = Room.databaseBuilder(context, AquariumDatabase::class.java, dbFile.name)
            .addMigrations(*AquariumDatabase.ALL_MIGRATIONS)
            .build()
        try {
            val measurements = reopened.aquariumDao().getMeasurementsForAquarium(uuid(1))
            assertEquals(1, measurements.size)
            assertEquals(0, BigDecimal("8.15").compareTo(measurements.single().ph))
        } finally {
            reopened.close()
            dbFile.delete()
        }
    }

    @Test fun openingUnsupportedFutureSchemaVersionFailsWithoutWipingUserData(): Unit = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val dbFile = context.getDatabasePath("unsupported-v99.db")
        dbFile.parentFile?.mkdirs()
        dbFile.delete()

        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { sqlite ->
            sqlite.execSQL("CREATE TABLE aquariums (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL)")
            sqlite.execSQL("INSERT INTO aquariums (id, name) VALUES ('00000000-0000-0000-0000-000000000001', 'Precious Tank')")
            sqlite.version = 99
        }

        val roomDb = Room.databaseBuilder(context, AquariumDatabase::class.java, dbFile.name)
            .addMigrations(*AquariumDatabase.ALL_MIGRATIONS)
            .build()
        try {
            assertThrows(IllegalStateException::class.java) {
                runBlocking { roomDb.aquariumDao().observeAquariums().first() }
            }
        } finally {
            roomDb.close()
        }

        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
            assertEquals(99, sqlite.version)
            sqlite.rawQuery("SELECT name FROM aquariums WHERE id = '00000000-0000-0000-0000-000000000001'", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Precious Tank", cursor.getString(0))
            }
        }
        dbFile.delete()
    }

    private fun uuid(id: Int) = UUID(0, id.toLong())
    private fun row(id: Int, name: String) = AquariumEntity(uuid(id), name, null, null, "Notes", Instant.parse("2026-10-19T00:00:00.123456789Z"))
}
