package com.pietrondo.aquariummanager.data

import androidx.room.Room
import com.pietrondo.aquariummanager.domain.AquariumDraft
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NfcTagRepositoryTest {
    private lateinit var db: AquariumDatabase
    private lateinit var dao: AquariumDao
    private lateinit var repo: RoomAquariumRepository

    private val tagId = "04a1b2c3d4e5f6"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AquariumDatabase::class.java,
        ).build()
        dao = db.aquariumDao()
        repo = RoomAquariumRepository(dao, clock = { Instant.parse("2026-11-02T10:00:00Z") })
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun boundTagIsObservableAndResolvesItsAquarium() = runBlocking {
        val tank = repo.createAquarium(AquariumDraft(name = "Reef"))

        val binding = repo.bindNfcTag(tank, tagId)

        assertEquals(tagId, binding.tagId)
        assertEquals(tank, binding.aquariumId)
        assertEquals(tank, repo.findAquariumIdForNfcTag(tagId))
        assertEquals(listOf(binding), repo.observeNfcTagBindings().first())
    }

    @Test
    fun tagIdIsNormalizedBeforeItIsStored() = runBlocking {
        val tank = repo.createAquarium(AquariumDraft(name = "Planted"))

        repo.bindNfcTag(tank, "  04A1B2C3D4E5F6  ")

        assertEquals(tagId, repo.observeNfcTagBindings().first().single().tagId)
        assertEquals(tank, repo.findAquariumIdForNfcTag(tagId))
    }

    @Test
    fun rebindingTheSameTagMovesItInsteadOfDuplicatingIt() = runBlocking {
        val first = repo.createAquarium(AquariumDraft(name = "First"))
        val second = repo.createAquarium(AquariumDraft(name = "Second"))

        repo.bindNfcTag(first, tagId)
        repo.bindNfcTag(second, tagId)

        val bindings = repo.observeNfcTagBindings().first()
        assertEquals(1, bindings.size)
        assertEquals(second, bindings.single().aquariumId)
        assertEquals(second, repo.findAquariumIdForNfcTag(tagId))
    }

    @Test
    fun unbindingRemovesTheTag() = runBlocking {
        val tank = repo.createAquarium(AquariumDraft(name = "Reef"))
        repo.bindNfcTag(tank, tagId)

        repo.unbindNfcTag(tagId)

        assertTrue(repo.observeNfcTagBindings().first().isEmpty())
        assertNull(repo.findAquariumIdForNfcTag(tagId))
    }

    @Test
    fun unknownAndMalformedTagsResolveToNothing() = runBlocking {
        repo.createAquarium(AquariumDraft(name = "Reef"))

        assertNull(repo.findAquariumIdForNfcTag("04ffffffffffff"))
        assertNull(repo.findAquariumIdForNfcTag("not-hex"))
    }

    @Test
    fun deletingTheAquariumRemovesItsTagBindings() = runBlocking {
        val tank = repo.createAquarium(AquariumDraft(name = "Reef"))
        repo.bindNfcTag(tank, tagId)

        repo.deleteAquarium(tank)

        assertTrue(repo.observeNfcTagBindings().first().isEmpty())
    }

    @Test
    fun restoringTheLibraryKeepsTheTagBindingsOfTheSurvivingAquariums() = runBlocking {
        val tank = repo.createAquarium(AquariumDraft(name = "Reef"))
        val other = repo.createAquarium(AquariumDraft(name = "Planted"))
        repo.bindNfcTag(tank, tagId)
        repo.bindNfcTag(other, "05a1b2c3d4e5f6")

        val snapshot = dao.readFullLibrarySnapshot()
        dao.replaceEntireLibrary(snapshot)

        val bindings = repo.observeNfcTagBindings().first()
        assertEquals(2, bindings.size)
        assertEquals(tank, bindings.first { it.tagId == tagId }.aquariumId)
        assertEquals(other, bindings.first { it.tagId == "05a1b2c3d4e5f6" }.aquariumId)
    }

    @Test
    fun restoringALibraryWithoutTheAquariumDropsItsTagBinding() = runBlocking {
        val tank = repo.createAquarium(AquariumDraft(name = "Reef"))
        repo.bindNfcTag(tank, tagId)
        val snapshot = dao.readFullLibrarySnapshot()

        dao.replaceEntireLibrary(
            snapshot.copy(aquariums = snapshot.aquariums.filterNot { it.id == tank }),
        )

        assertTrue(repo.observeNfcTagBindings().first().isEmpty())
    }
}
