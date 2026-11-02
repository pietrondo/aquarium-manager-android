package com.pietrondo.aquariummanager.presentation

import androidx.lifecycle.ViewModelStore
import com.pietrondo.aquariummanager.domain.AquariumDetail
import com.pietrondo.aquariummanager.domain.AquariumRepository
import com.pietrondo.aquariummanager.domain.AquariumSummary
import com.pietrondo.aquariummanager.domain.NfcTagBinding
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AquariumListNfcViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val reefId = UUID.fromString("00000000-0000-4000-8000-000000000401")
    private val plantedId = UUID.fromString("00000000-0000-4000-8000-000000000402")
    private val tagId = "04a1b2c3d4e5f6"

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun scanningABoundTagSelectsItsAquarium() = runTest {
        val repo = FakeNfcRepository()
        repo.addAquarium(reefId, "Reef")
        repo.addAquarium(plantedId, "Planted")
        repo.bind(reefId, tagId)
        val vm = viewModel(repo)
        runCurrent()

        vm.onNfcTagScanned(tagId)
        runCurrent()

        val state = vm.state.value as AquariumListState.Success
        assertEquals(reefId, state.selectedAquariumId)
        assertEquals(NfcStatus.AquariumSelected("Reef"), state.nfcStatus)
    }

    @Test
    fun scanningAnUnknownTagReportsItAndKeepsTheSelection() = runTest {
        val repo = FakeNfcRepository()
        repo.addAquarium(reefId, "Reef")
        val vm = viewModel(repo)
        runCurrent()

        vm.onNfcTagScanned("04ffffffffffff")
        runCurrent()

        val state = vm.state.value as AquariumListState.Success
        assertEquals(NfcStatus.UnknownTag("04ffffffffffff"), state.nfcStatus)
        assertNull(state.selectedAquariumId)
    }

    @Test
    fun armedAssignmentBindsTheTagToTheActiveAquarium() = runTest {
        val repo = FakeNfcRepository()
        repo.addAquarium(reefId, "Reef")
        val vm = viewModel(repo)
        runCurrent()

        vm.startNfcTagAssignment()
        runCurrent()
        assertEquals(true, (vm.state.value as AquariumListState.Success).isNfcAssigningTag)

        vm.onNfcTagScanned(tagId)
        runCurrent()

        val state = vm.state.value as AquariumListState.Success
        assertEquals(false, state.isNfcAssigningTag)
        assertEquals(reefId, repo.boundAquariumId(tagId))
        assertEquals(NfcStatus.TagBound("Reef", tagId), state.nfcStatus)
    }

    @Test
    fun unbindingRemovesTheTagFromTheAquarium() = runTest {
        val repo = FakeNfcRepository()
        repo.addAquarium(reefId, "Reef")
        repo.bind(reefId, tagId)
        val vm = viewModel(repo)
        runCurrent()

        vm.unbindNfcTag(tagId)
        runCurrent()

        assertNull(repo.boundAquariumId(tagId))
        assertEquals(NfcStatus.TagUnbound(tagId), (vm.state.value as AquariumListState.Success).nfcStatus)
    }

    @Test
    fun tagIdIsGroupedInBytePairsForReading() {
        assertEquals("04 a1 b2 c3 d4 e5 f6", formatNfcTagId("04a1b2c3d4e5f6"))
        assertEquals("04 a1", formatNfcTagId("04a1"))
    }

    @Test
    fun backupFolderIsShownByNameInsteadOfTheRawTreeUri() {
        assertEquals(
            "Documents",
            backupFolderName("content://com.android.externalstorage.documents/tree/primary%3ADocuments"),
        )
        assertEquals(
            "Backups",
            backupFolderName("content://com.android.externalstorage.documents/tree/primary%3ABackups"),
        )
    }

    private fun viewModel(repo: AquariumRepository): AquariumListViewModel {
        val vm = AquariumListViewModel(repo)
        store.put("nfc", vm)
        return vm
    }

    private class FakeNfcRepository : AquariumRepository {
        private val aquariums = MutableStateFlow<List<AquariumDetail>>(emptyList())
        private val bindings = MutableStateFlow<List<NfcTagBinding>>(emptyList())

        fun addAquarium(id: UUID, name: String) {
            aquariums.value = aquariums.value + AquariumDetail(
                id = id,
                name = name,
                netLitres = null,
                grossLitres = null,
                notes = null,
                createdAt = Instant.parse("2026-11-02T10:00:00Z"),
                profiles = emptyList(),
            )
        }

        fun bind(aquariumId: UUID, tagId: String) {
            bindings.value = bindings.value + NfcTagBinding(
                tagId = tagId,
                aquariumId = aquariumId,
                createdAt = Instant.parse("2026-11-02T10:00:00Z"),
            )
        }

        fun boundAquariumId(tagId: String): UUID? =
            bindings.value.firstOrNull { it.tagId == tagId }?.aquariumId

        override fun observeAquariums(): Flow<List<AquariumSummary>> =
            aquariums.map { list -> list.map { it.toSummary() } }

        override fun observeAquariumDetails(): Flow<List<AquariumDetail>> = aquariums

        override fun observeNfcTagBindings(): Flow<List<NfcTagBinding>> = bindings

        override suspend fun bindNfcTag(
            aquariumId: UUID,
            tagId: String,
            label: String?,
        ): NfcTagBinding {
            val binding = NfcTagBinding(
                tagId = tagId,
                aquariumId = aquariumId,
                label = label,
                createdAt = Instant.parse("2026-11-02T10:00:00Z"),
            )
            bindings.value = bindings.value.filterNot { it.tagId == tagId } + binding
            return binding
        }

        override suspend fun unbindNfcTag(tagId: String) {
            bindings.value = bindings.value.filterNot { it.tagId == tagId }
        }

        override suspend fun findAquariumIdForNfcTag(tagId: String): UUID? = boundAquariumId(tagId)

        override fun observeBackupScheduleSettings(): Flow<com.pietrondo.aquariummanager.domain.BackupScheduleSettings> =
            flowOf(com.pietrondo.aquariummanager.domain.BackupScheduleSettings())
    }
}
