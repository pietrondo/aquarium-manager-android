package com.pietrondo.aquariummanager.data

import androidx.room.Room
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
    private fun row(id: Int, name: String) = AquariumEntity(UUID(0, id.toLong()), name, null, null, "Notes", Instant.parse("2026-10-19T00:00:00.123456789Z"))
}
