package com.pietrondo.aquariummanager.presentation

import androidx.lifecycle.ViewModelStore
import com.pietrondo.aquariummanager.domain.AquariumDetail
import com.pietrondo.aquariummanager.domain.AquariumDraft
import com.pietrondo.aquariummanager.domain.AquariumProfile
import com.pietrondo.aquariummanager.domain.AquariumRepository
import com.pietrondo.aquariummanager.domain.AquariumSummary
import com.pietrondo.aquariummanager.domain.MaintenanceDraft
import com.pietrondo.aquariummanager.domain.MaintenanceRecord
import com.pietrondo.aquariummanager.domain.MaintenanceTypeCatalogItem
import com.pietrondo.aquariummanager.domain.MeasurementDraft
import com.pietrondo.aquariummanager.domain.MeasurementRecord
import com.pietrondo.aquariummanager.domain.ParameterTrendDirection
import com.pietrondo.aquariummanager.domain.WaterParameter
import com.pietrondo.aquariummanager.domain.validateNonBlank
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AquariumListViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

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

    private fun vm(source: () -> Flow<List<AquariumSummary>>): AquariumListViewModel {
        val vm = AquariumListViewModel(object : AquariumRepository { override fun observeAquariums() = source() })
        store.put("list", vm)
        return vm
    }

    private fun fakeCrudRepository(): FakeAquariumRepository = FakeAquariumRepository()

    @Test
    fun startsLoadingThenShowsRealList() = runTest {
        val row = AquariumSummary(UUID(0, 1), "Reef")
        val vm = vm { flowOf(listOf(row)) }
        assertEquals(AquariumListState.Loading, vm.state.value)
        runCurrent()
        assertEquals(AquariumListState.Success(listOf(row)), vm.state.value)
    }

    @Test
    fun emptyIsSuccessfulNotFakeData() = runTest {
        val vm = vm { flowOf(emptyList()) }
        runCurrent()
        assertEquals(AquariumListState.Success(emptyList()), vm.state.value)
    }

    @Test
    fun failureDoesNotExposeDatabaseMessageAndRetryRecovers() = runTest {
        var calls = 0
        val vm = vm { flow { if (calls++ == 0) error("SECRET SQL database path"); emit(emptyList()) } }
        runCurrent()
        assertEquals(AquariumListState.Error, vm.state.value)
        assertFalse(vm.state.value.toString().contains("SECRET"))
        vm.retry()
        runCurrent()
        assertEquals(AquariumListState.Success(emptyList()), vm.state.value)
    }

    @Test
    fun cancellationIsNotPresentedAsAnError() = runTest {
        val vm = vm { flow { throw CancellationException("cancelled") } }
        runCurrent()
        assertEquals(AquariumListState.Loading, vm.state.value)
    }

    @Test
    fun repeatedRetryNeverOverlapsCollectorsAndClearCancels() = runTest {
        var active = 0
        var maximum = 0
        val vm = vm {
            flow {
                active++
                maximum = maxOf(maximum, active)
                try {
                    awaitCancellation()
                } finally {
                    active--
                }
            }
        }
        runCurrent()
        repeat(3) { vm.retry() }
        runCurrent()
        assertEquals(1, maximum)
        assertEquals(1, active)
        store.clear()
        runCurrent()
        assertEquals(0, active)
    }

    @Test
    fun createAndEditAquariumWithUnknownLitresAndOverlappingProfiles() = runTest {
        val repo = fakeCrudRepository()
        val vm = AquariumListViewModel(repo).also { store.put("crud", it) }
        runCurrent()

        val initial = vm.state.value as AquariumListState.Success
        val planted = initial.availableProfiles.first { it.name == "Planted" }
        val shrimp = initial.availableProfiles.first { it.name == "Shrimp" }
        val community = initial.availableProfiles.first { it.name == "Freshwater Community" }

        // Open create editor, leave volumes blank (unknown -> null), select overlapping profiles
        vm.openCreateEditor()
        vm.updateNameInput("  Desk Shrimp Bowl  ")
        vm.updateNetLitresInput("   ")
        vm.updateGrossLitresInput("")
        vm.updateNotesInput("  Moss and neocaridina  ")
        vm.toggleProfileSelection(planted.id)
        vm.toggleProfileSelection(shrimp.id)
        vm.saveAquarium()
        runCurrent()

        val afterCreate = vm.state.value as AquariumListState.Success
        assertNull(afterCreate.editorState)
        assertEquals(1, afterCreate.aquariumDetails.size)
        val created = afterCreate.aquariumDetails.single()
        assertEquals("Desk Shrimp Bowl", created.name)
        assertNull(created.netLitres)
        assertNull(created.grossLitres)
        assertEquals("Moss and neocaridina", created.notes)
        assertEquals(listOf("Planted", "Shrimp"), created.profiles.map { it.name })

        // Edit existing aquarium: set volumes and update overlapping profiles
        vm.openEditEditor(created.id)
        val editForm = (vm.state.value as AquariumListState.Success).editorState!!
        assertEquals("Desk Shrimp Bowl", editForm.nameInput)
        assertEquals("", editForm.netLitresInput)
        assertEquals(setOf(planted.id, shrimp.id), editForm.selectedProfileIds)

        vm.updateNameInput("Living Room Shrimp Tank")
        vm.updateNetLitresInput("28.5")
        vm.updateGrossLitresInput("35")
        vm.toggleProfileSelection(planted.id) // deselect Planted
        vm.toggleProfileSelection(community.id) // select Freshwater Community
        vm.saveAquarium()
        runCurrent()

        val afterEdit = vm.state.value as AquariumListState.Success
        assertNull(afterEdit.editorState)
        val updated = afterEdit.aquariumDetails.single()
        assertEquals("Living Room Shrimp Tank", updated.name)
        assertEquals(0, BigDecimal("28.5").compareTo(updated.netLitres))
        assertEquals(0, BigDecimal("35").compareTo(updated.grossLitres))
        assertEquals(listOf("Freshwater Community", "Shrimp"), updated.profiles.map { it.name })
    }

    @Test
    fun validationFailurePreservesTypedInputAndSelectedProfiles() = runTest {
        val repo = fakeCrudRepository()
        val vm = AquariumListViewModel(repo).also { store.put("validation", it) }
        runCurrent()

        val profiles = (vm.state.value as AquariumListState.Success).availableProfiles
        val profileIds = setOf(profiles[0].id, profiles[1].id)

        vm.openCreateEditor()
        vm.updateNameInput("Nano Cube")
        vm.updateNetLitresInput("45")
        vm.updateGrossLitresInput("30") // Invalid: net > gross
        vm.updateNotesInput("Corner desk")
        profileIds.forEach { vm.toggleProfileSelection(it) }

        vm.saveAquarium()
        runCurrent()

        val afterNetExceedsGross = (vm.state.value as AquariumListState.Success).editorState
        assertNotNull(afterNetExceedsGross)
        assertNotNull(afterNetExceedsGross!!.validationError)
        // Typed inputs and profile selections MUST be preserved
        assertEquals("Nano Cube", afterNetExceedsGross.nameInput)
        assertEquals("45", afterNetExceedsGross.netLitresInput)
        assertEquals("30", afterNetExceedsGross.grossLitresInput)
        assertEquals("Corner desk", afterNetExceedsGross.notesInput)
        assertEquals(profileIds, afterNetExceedsGross.selectedProfileIds)

        // Non-numeric litres also preserves all typed input
        vm.updateNetLitresInput("twenty")
        vm.saveAquarium()
        runCurrent()
        val afterNonNumeric = (vm.state.value as AquariumListState.Success).editorState!!
        assertNotNull(afterNonNumeric.validationError)
        assertEquals("Nano Cube", afterNonNumeric.nameInput)
        assertEquals("twenty", afterNonNumeric.netLitresInput)
        assertEquals("30", afterNonNumeric.grossLitresInput)
        assertEquals("Corner desk", afterNonNumeric.notesInput)
        assertEquals(profileIds, afterNonNumeric.selectedProfileIds)

        // Blank name also preserves typed litres, notes, and selected profiles
        vm.updateNameInput("   ")
        vm.updateNetLitresInput("25")
        vm.saveAquarium()
        runCurrent()
        val afterBlankName = (vm.state.value as AquariumListState.Success).editorState!!
        assertNotNull(afterBlankName.validationError)
        assertEquals("   ", afterBlankName.nameInput)
        assertEquals("25", afterBlankName.netLitresInput)
        assertEquals("30", afterBlankName.grossLitresInput)
        assertEquals("Corner desk", afterBlankName.notesInput)
        assertEquals(profileIds, afterBlankName.selectedProfileIds)
    }

    @Test
    fun duplicateOrBlankProfileCreationShowsErrorAndPreservesDraftInput() = runTest {
        val repo = fakeCrudRepository()
        val vm = AquariumListViewModel(repo).also { store.put("dupProfile", it) }
        runCurrent()

        vm.openCreateEditor()
        vm.updateNameInput("Dutch Scape")
        vm.updateNetLitresInput("120")

        // Duplicate profile label (case-insensitive)
        vm.updateNewProfileNameInput("  planted  ")
        vm.addCustomProfile()
        runCurrent()

        val afterDuplicate = (vm.state.value as AquariumListState.Success).editorState!!
        assertNotNull(afterDuplicate.profileValidationError)
        assertEquals("  planted  ", afterDuplicate.newProfileNameInput)
        assertEquals("Dutch Scape", afterDuplicate.nameInput)
        assertEquals("120", afterDuplicate.netLitresInput)

        // Valid new profile label is created and automatically selected on the form
        vm.updateNewProfileNameInput("Blackwater")
        vm.addCustomProfile()
        runCurrent()

        val afterValidProfile = vm.state.value as AquariumListState.Success
        val createdProfile = afterValidProfile.availableProfiles.first { it.name == "Blackwater" }
        val form = afterValidProfile.editorState!!
        assertNull(form.profileValidationError)
        assertEquals("", form.newProfileNameInput)
        assertTrue(createdProfile.id in form.selectedProfileIds)
        assertEquals("Dutch Scape", form.nameInput)
    }

    @Test
    fun deleteAquariumRequiresExplicitConfirmationBeforeRemoving() = runTest {
        val repo = fakeCrudRepository()
        val tankA = repo.createAquarium(AquariumDraft(name = "Alpha Reef", netLitres = BigDecimal("80")))
        val tankB = repo.createAquarium(AquariumDraft(name = "Beta Planted", netLitres = BigDecimal("120")))

        val vm = AquariumListViewModel(repo).also { store.put("delete", it) }
        runCurrent()

        // Requesting delete opens confirmation dialog without deleting yet
        vm.requestDeleteAquarium(tankA)
        val pendingState = vm.state.value as AquariumListState.Success
        assertEquals(tankA, pendingState.pendingDeleteAquarium?.id)
        assertEquals(2, pendingState.aquariumDetails.size)

        // Dismissing confirmation leaves both aquariums untouched
        vm.dismissDeleteConfirmation()
        val dismissedState = vm.state.value as AquariumListState.Success
        assertNull(dismissedState.pendingDeleteAquarium)
        assertEquals(2, dismissedState.aquariumDetails.size)

        // Confirming delete removes only Tank A and keeps Tank B isolated
        vm.requestDeleteAquarium(tankA)
        vm.confirmDeleteAquarium()
        runCurrent()

        val afterDelete = vm.state.value as AquariumListState.Success
        assertNull(afterDelete.pendingDeleteAquarium)
        assertEquals(listOf(tankB), afterDelete.aquariumDetails.map { it.id })
    }

    @Test
    fun waterTestRecordingTrendCalculationNullGapsAndValidationPreservation() = runTest {
        val repo = fakeCrudRepository()
        val tankA = repo.createAquarium(AquariumDraft(name = "Alpha Reef", netLitres = BigDecimal("80")))
        val tankB = repo.createAquarium(AquariumDraft(name = "Beta Shrimp", netLitres = BigDecimal("30")))

        val vm = AquariumListViewModel(repo).also { store.put("waterTests", it) }
        runCurrent()

        vm.selectTab(AppTab.TESTS)
        val onTestsTab = vm.state.value as AquariumListState.Success
        assertEquals(AppTab.TESTS, onTestsTab.selectedTab)
        assertEquals(tankA, onTestsTab.activeAquariumId)

        // Open measurement editor and try saving with all fields blank -> validation error preserving state
        vm.openMeasurementEditor()
        vm.saveMeasurement()
        runCurrent()
        val emptySubmission = (vm.state.value as AquariumListState.Success).measurementEditorState!!
        assertNotNull(emptySubmission.validationError)

        // Invalid numeric text or negative concentration preserves all typed fields
        vm.updatePhInput("7.2")
        vm.updateNo2MgLInput("0")
        vm.updateNo3MgLInput("-5")
        vm.saveMeasurement()
        runCurrent()
        val negativeNo3 = (vm.state.value as AquariumListState.Success).measurementEditorState!!
        assertNotNull(negativeNo3.validationError)
        assertEquals("7.2", negativeNo3.phInput)
        assertEquals("0", negativeNo3.no2MgLInput)
        assertEquals("-5", negativeNo3.no3MgLInput)

        // Valid first reading: pH 7.2, NO2 = 0 (exact zero), NO3 = 10, others unmeasured (null)
        vm.updateNo3MgLInput("10")
        vm.saveMeasurement()
        runCurrent()

        val afterFirst = vm.state.value as AquariumListState.Success
        assertNull(afterFirst.measurementEditorState)
        assertEquals(1, afterFirst.measurements.size)
        val firstRecord = afterFirst.measurements.single()
        assertEquals(0, BigDecimal("7.2").compareTo(firstRecord.ph))
        assertEquals(0, BigDecimal.ZERO.compareTo(firstRecord.no2MgL))
        assertEquals(0, BigDecimal("10").compareTo(firstRecord.no3MgL))
        assertNull(firstRecord.khDkh)

        // Second reading on tankA: pH 7.5, NO3 left blank (explicit null gap!), Temp = 25.0
        vm.openMeasurementEditor()
        vm.updatePhInput("7.5")
        vm.updateTemperatureCInput("25.0")
        vm.saveMeasurement()
        runCurrent()

        val afterSecond = vm.state.value as AquariumListState.Success
        assertEquals(2, afterSecond.measurements.size)
        val trendsByParam = afterSecond.parameterTrends.associateBy { it.parameter }
        val phTrend = trendsByParam.getValue(WaterParameter.PH)
        assertEquals(ParameterTrendDirection.RISING, phTrend.direction)
        assertEquals("↑ Rising (+0.3)", phTrend.accessibilityCue)

        val no3Trend = trendsByParam.getValue(WaterParameter.NO3_MGL)
        assertEquals(1, no3Trend.measuredCount)
        assertEquals(1, no3Trend.missingGapCount)
        assertNull(no3Trend.points.last().value)

        // Switching to tankB shows isolated empty history for tankB
        vm.selectAquarium(tankB)
        runCurrent()
        val onTankB = vm.state.value as AquariumListState.Success
        assertEquals(tankB, onTankB.activeAquariumId)
        assertTrue(onTankB.measurements.isEmpty())
    }

    @Test
    fun maintenanceLoggingEditingAdvisoryVolumeWarningInvalidInputAndAquariumSwitching() = runTest {
        val repo = fakeCrudRepository()
        val tankA = repo.createAquarium(AquariumDraft(name = "Alpha 60P", netLitres = BigDecimal("60")))
        val tankB = repo.createAquarium(AquariumDraft(name = "Beta Cube", netLitres = BigDecimal("25")))

        val vm = AquariumListViewModel(repo).also { store.put("maintenance", it) }
        runCurrent()

        vm.selectTab(AppTab.MAINTENANCE)
        val onMaintTab = vm.state.value as AquariumListState.Success
        assertEquals(AppTab.MAINTENANCE, onMaintTab.selectedTab)
        assertEquals(6, onMaintTab.maintenanceTypeCatalog.size)

        // Open create maintenance editor: entering changedLitres > 60L shows non-blocking volumeWarning
        vm.openCreateMaintenanceEditor()
        vm.selectMaintenanceType("WATER_CHANGE")
        vm.updateChangedLitresInput("75")
        vm.updateMaintenanceNotesInput("  Large reset flush  ")

        val withWarning = (vm.state.value as AquariumListState.Success).maintenanceEditorState!!
        assertNotNull(withWarning.volumeWarning)
        assertTrue(withWarning.volumeWarning!!.contains("60 L"))
        assertNull(withWarning.validationError)

        // Saving succeeds despite advisory warning (no hard cap!)
        vm.saveMaintenanceEvent()
        runCurrent()

        val afterLargeWaterChange = vm.state.value as AquariumListState.Success
        assertNull(afterLargeWaterChange.maintenanceEditorState)
        assertEquals(1, afterLargeWaterChange.maintenanceEvents.size)
        val savedEvent = afterLargeWaterChange.maintenanceEvents.single()
        assertEquals("WATER_CHANGE", savedEvent.type)
        assertEquals(0, BigDecimal("75").compareTo(savedEvent.changedLitres))
        assertEquals("Large reset flush", savedEvent.notes)

        // Invalid numeric input in maintenance editor preserves typed input
        vm.openCreateMaintenanceEditor()
        vm.selectMaintenanceType("FILTER_CLEANING")
        vm.updateChangedLitresInput("invalid-litres")
        vm.updateMaintenanceNotesInput("Cleaned impeller")
        vm.saveMaintenanceEvent()
        runCurrent()

        val afterInvalid = (vm.state.value as AquariumListState.Success).maintenanceEditorState!!
        assertNotNull(afterInvalid.validationError)
        assertEquals("FILTER_CLEANING", afterInvalid.selectedTypeCode)
        assertEquals("invalid-litres", afterInvalid.changedLitresInput)
        assertEquals("Cleaned impeller", afterInvalid.notesInput)

        // Clearing optional changedLitres saves with null volume
        vm.updateChangedLitresInput("")
        vm.saveMaintenanceEvent()
        runCurrent()

        val afterSecond = vm.state.value as AquariumListState.Success
        assertEquals(2, afterSecond.maintenanceEvents.size)

        // Edit the first maintenance event
        vm.openEditMaintenanceEditor(savedEvent.id)
        val editEditor = (vm.state.value as AquariumListState.Success).maintenanceEditorState!!
        assertEquals(savedEvent.id, editEditor.editingMaintenanceId)
        assertEquals("75", editEditor.changedLitresInput)
        assertNotNull(editEditor.volumeWarning)

        vm.updateChangedLitresInput("20")
        val withoutWarning = (vm.state.value as AquariumListState.Success).maintenanceEditorState!!
        assertNull(withoutWarning.volumeWarning)
        vm.saveMaintenanceEvent()
        runCurrent()

        val edited = (vm.state.value as AquariumListState.Success)
            .maintenanceEvents.first { it.id == savedEvent.id }
        assertEquals(0, BigDecimal("20").compareTo(edited.changedLitres))

        // Switching to tankB filters maintenance events to tankB (empty)
        vm.selectAquarium(tankB)
        runCurrent()
        val onTankB = vm.state.value as AquariumListState.Success
        assertTrue(onTankB.maintenanceEvents.isEmpty())

        // Switch back to tankA and delete an event
        vm.selectAquarium(tankA)
        runCurrent()
        vm.deleteMaintenanceEvent(savedEvent.id)
        runCurrent()
        assertEquals(1, (vm.state.value as AquariumListState.Success).maintenanceEvents.size)
    }

    @Test
    fun speciesCatalogAndLivestockStockTrackingWithUnknownQuantityExitedVisibilityAndInputPreservation() = runTest {
        val repo = fakeCrudRepository()
        val tankA = repo.createAquarium(AquariumDraft(name = "Alpha Shrimp", netLitres = BigDecimal("30")))
        val tankB = repo.createAquarium(AquariumDraft(name = "Beta Scape", netLitres = BigDecimal("60")))

        val vm = AquariumListViewModel(repo).also { store.put("livestock", it) }
        runCurrent()

        vm.selectTab(AppTab.LIVESTOCK)
        val initial = vm.state.value as AquariumListState.Success
        assertEquals(AppTab.LIVESTOCK, initial.selectedTab)
        assertEquals(4, initial.speciesCatalog.size)

        // Filter species catalog by FAUNA vs FLORA
        vm.setSpeciesKindFilter(com.pietrondo.aquariummanager.domain.SpeciesKind.FAUNA)
        val faunaState = vm.state.value as AquariumListState.Success
        assertEquals(2, faunaState.filteredSpeciesCatalog.size)
        assertTrue(faunaState.filteredSpeciesCatalog.all { it.kind == com.pietrondo.aquariummanager.domain.SpeciesKind.FAUNA })

        vm.setSpeciesKindFilter(null)

        // Create species: blank common name preserves typed scientific name and selected kind
        vm.openCreateSpeciesEditor(com.pietrondo.aquariummanager.domain.SpeciesKind.FLORA)
        vm.updateSpeciesCommonNameInput("   ")
        vm.updateSpeciesScientificNameInput("Bucephalandra sp.")
        vm.saveSpecies()
        runCurrent()
        val invalidSpeciesEditor = (vm.state.value as AquariumListState.Success).speciesEditorState!!
        assertNotNull(invalidSpeciesEditor.validationError)
        assertEquals("   ", invalidSpeciesEditor.commonNameInput)
        assertEquals("Bucephalandra sp.", invalidSpeciesEditor.scientificNameInput)

        // Valid species creation
        vm.updateSpeciesCommonNameInput("Bucephalandra Kedagang")
        vm.saveSpecies()
        runCurrent()
        val afterSpeciesCreate = vm.state.value as AquariumListState.Success
        assertNull(afterSpeciesCreate.speciesEditorState)
        val buce = afterSpeciesCreate.speciesCatalog.first { it.commonName == "Bucephalandra Kedagang" }

        // Add stock entry with unknown quantity (blank -> null)
        vm.openCreateStockEditor()
        vm.selectStockSpecies(buce.id)
        vm.updateStockQuantityInput("   ")
        vm.updateStockIntroducedDateInput("2026-10-01")
        vm.updateStockNotesInput("  Mounted on lava rock  ")
        vm.saveStockEntry()
        runCurrent()

        val afterUnknownStock = vm.state.value as AquariumListState.Success
        assertNull(afterUnknownStock.stockEditorState)
        assertEquals(1, afterUnknownStock.stockEntries.size)
        val buceEntry = afterUnknownStock.stockEntries.single()
        assertNull(buceEntry.quantity)
        assertTrue(buceEntry.isActive)
        assertEquals("Mounted on lava rock", buceEntry.notes)

        // Add stock entry with invalid non-positive quantity or exit before introduction -> preserves typed fields!
        val amano = afterUnknownStock.speciesCatalog.first { it.commonName == "Amano Shrimp" }
        vm.openCreateStockEditor()
        vm.selectStockSpecies(amano.id)
        vm.updateStockQuantityInput("0")
        vm.updateStockIntroducedDateInput("2026-10-10")
        vm.updateStockExitedDateInput("2026-10-05")
        vm.updateStockNotesInput("Invalid test")
        vm.saveStockEntry()
        runCurrent()
        val invalidZeroQty = (vm.state.value as AquariumListState.Success).stockEditorState!!
        assertNotNull(invalidZeroQty.validationError)
        assertEquals("0", invalidZeroQty.quantityInput)
        assertEquals("2026-10-10", invalidZeroQty.introducedDateInput)
        assertEquals("2026-10-05", invalidZeroQty.exitedDateInput)
        assertEquals("Invalid test", invalidZeroQty.notesInput)

        // Fix quantity to 8, still exit before introduction -> validation error preserved
        vm.updateStockQuantityInput("8")
        vm.saveStockEntry()
        runCurrent()
        val invalidExitBeforeIntro = (vm.state.value as AquariumListState.Success).stockEditorState!!
        assertNotNull(invalidExitBeforeIntro.validationError)
        assertEquals("8", invalidExitBeforeIntro.quantityInput)

        // Clear exit date -> saves active stock entry
        vm.updateStockExitedDateInput("")
        vm.saveStockEntry()
        runCurrent()
        val afterSecondStock = vm.state.value as AquariumListState.Success
        assertEquals(2, afterSecondStock.stockEntries.size)
        val amanoEntry = afterSecondStock.stockEntries.first { it.species.id == amano.id }

        // Mark amano exited -> remains visible in stockEntries with isActive == false
        vm.markStockExited(amanoEntry.id)
        runCurrent()
        val afterExit = vm.state.value as AquariumListState.Success
        assertEquals(2, afterExit.stockEntries.size)
        val exitedAmano = afterExit.stockEntries.first { it.id == amanoEntry.id }
        assertFalse(exitedAmano.isActive)
        assertNotNull(exitedAmano.exitedAt)

        // Edit referenced species (buce) -> updates in place across stockEntries!
        vm.openEditSpeciesEditor(buce.id)
        vm.updateSpeciesCommonNameInput("Bucephalandra Mini Coin")
        vm.saveSpecies()
        runCurrent()
        val afterSpeciesRename = vm.state.value as AquariumListState.Success
        assertEquals(
            "Bucephalandra Mini Coin",
            afterSpeciesRename.stockEntries.first { it.id == buceEntry.id }.species.commonName,
        )

        // Deleting a referenced species sets actionError and keeps the species
        vm.deleteSpecies(buce.id)
        runCurrent()
        val afterBlockedDelete = vm.state.value as AquariumListState.Success
        assertNotNull(afterBlockedDelete.actionError)
        assertTrue(afterBlockedDelete.speciesCatalog.any { it.id == buce.id })
        vm.dismissActionError()
        assertNull((vm.state.value as AquariumListState.Success).actionError)

        // Switching to tankB shows isolated empty stock
        vm.selectAquarium(tankB)
        runCurrent()
        assertTrue((vm.state.value as AquariumListState.Success).stockEntries.isEmpty())
        assertEquals(tankA, buceEntry.aquariumId)
    }

    @Test
    fun productLibraryDosingPlansPlanSnapshotLoggingAdHocEventsAndHistoricalImmutability() = runTest {
        val repo = fakeCrudRepository()
        val tankA = repo.createAquarium(AquariumDraft(name = "Alpha High-Tech", netLitres = BigDecimal("60")))

        val vm = AquariumListViewModel(repo).also { store.put("dosing", it) }
        runCurrent()

        vm.selectTab(AppTab.LIVESTOCK)
        vm.selectLivestockSection(LivestockSection.DOSING)
        val initial = vm.state.value as AquariumListState.Success
        assertEquals(LivestockSection.DOSING, initial.livestockSection)
        assertEquals(4, initial.doseUnitCatalog.size)

        // Create product: blank name preserves state with validationError
        vm.openCreateProductEditor()
        vm.updateProductNameInput("   ")
        vm.saveProduct()
        runCurrent()
        assertNotNull((vm.state.value as AquariumListState.Success).productEditorState?.validationError)

        // Save valid product
        vm.updateProductNameInput("  Seachem Flourish  ")
        vm.saveProduct()
        runCurrent()
        val afterProduct = vm.state.value as AquariumListState.Success
        assertNull(afterProduct.productEditorState)
        val flourish = afterProduct.products.single()
        assertEquals("Seachem Flourish", flourish.name)

        // Create weekly dosing plan: missing weekdays preserves typed dose, dates, and timezone
        vm.openCreateDosingPlanEditor()
        vm.selectDosingPlanProduct(flourish.id)
        vm.updateDosingPlanDoseInput("2.5")
        vm.selectDosingPlanUnit("mL")
        vm.selectDosingPlanFrequency(com.pietrondo.aquariummanager.domain.DosingFrequency.WEEKLY)
        vm.updateDosingPlanStartDateInput("2026-10-19")
        vm.updateDosingPlanTimeZoneInput("Europe/Rome")
        vm.saveDosingPlan()
        runCurrent()

        val missingWeekdays = (vm.state.value as AquariumListState.Success).dosingPlanEditorState!!
        assertNotNull(missingWeekdays.validationError)
        assertEquals("2.5", missingWeekdays.doseInput)
        assertEquals("Europe/Rome", missingWeekdays.timeZoneInput)

        // Toggle Monday & Thursday and save plan
        vm.toggleDosingPlanWeekday(java.time.DayOfWeek.MONDAY)
        vm.toggleDosingPlanWeekday(java.time.DayOfWeek.THURSDAY)
        vm.saveDosingPlan()
        runCurrent()

        val afterPlan = vm.state.value as AquariumListState.Success
        assertNull(afterPlan.dosingPlanEditorState)
        val plan = afterPlan.dosingPlans.single()
        assertEquals("Seachem Flourish", plan.product.name)
        assertEquals(0, BigDecimal("2.5").compareTo(plan.dose))
        assertEquals("mL", plan.unit)

        // 1-tap log dose from plan
        vm.logDoseFromPlanNow(plan.id)
        runCurrent()
        val afterPlanDose = vm.state.value as AquariumListState.Success
        assertEquals(1, afterPlanDose.dosingEvents.size)
        val planEvent = afterPlanDose.dosingEvents.single()
        assertEquals(plan.id, planEvent.planId)
        assertEquals("Seachem Flourish", planEvent.productNameSnapshot)

        // Record ad-hoc dosing event: blank product name or invalid dose preserves typed input
        vm.openAdHocDosingEditor()
        vm.updateAdHocProductNameInput("APT Complete")
        vm.updateAdHocDoseInput("-3")
        vm.selectAdHocDoseUnit("drops")
        vm.saveAdHocDosingEvent()
        runCurrent()
        val invalidAdHoc = (vm.state.value as AquariumListState.Success).adHocDosingEditorState!!
        assertNotNull(invalidAdHoc.validationError)
        assertEquals("APT Complete", invalidAdHoc.productNameInput)
        assertEquals("-3", invalidAdHoc.doseInput)
        assertEquals("drops", invalidAdHoc.selectedUnitCode)

        vm.updateAdHocDoseInput("6")
        vm.saveAdHocDosingEvent()
        runCurrent()
        val afterAdHoc = vm.state.value as AquariumListState.Success
        assertNull(afterAdHoc.adHocDosingEditorState)
        assertEquals(2, afterAdHoc.dosingEvents.size)

        // Rename product and delete plan -> historical dosing events remain untouched!
        vm.openEditProductEditor(flourish.id)
        vm.updateProductNameInput("Flourish Reformed")
        vm.saveProduct()
        runCurrent()

        vm.deleteDosingPlan(plan.id)
        runCurrent()

        val finalState = vm.state.value as AquariumListState.Success
        assertTrue(finalState.dosingPlans.isEmpty())
        val survivingPlanEvent = finalState.dosingEvents.first { it.id == planEvent.id }
        assertNull(survivingPlanEvent.planId)
        assertEquals("Seachem Flourish", survivingPlanEvent.productNameSnapshot)
        assertEquals(0, BigDecimal("2.5").compareTo(survivingPlanEvent.dose))
        assertEquals("mL", survivingPlanEvent.unit)
        assertEquals(tankA, survivingPlanEvent.aquariumId)
    }

    @Test
    fun settingsTabManualExportRestorePreviewAndConfirmReplaceUpdatesState() = runTest {
        val repo = fakeCrudRepository()
        val vm = AquariumListViewModel(repo).also { store.put("settingsBackup", it) }
        runCurrent()

        vm.openCreateEditor()
        vm.updateNameInput("Acquario di Pietro")
        vm.updateNetLitresInput("45")
        vm.saveAquarium()
        runCurrent()

        vm.selectTab(AppTab.SETTINGS)
        assertEquals(AppTab.SETTINGS, (vm.state.value as AquariumListState.Success).selectedTab)

        // Export backup to URI
        val backupUri = "content://fake/backups/manual-1.zip"
        vm.exportBackupToUri(backupUri)
        runCurrent()

        val afterExport = vm.state.value as AquariumListState.Success
        assertFalse(afterExport.isBackupOperationInProgress)
        assertNotNull(afterExport.backupStatusMessage)
        assertTrue(afterExport.backupStatusMessage!!.contains("Verified backup exported"))

        // Mutate live library (add a second tank)
        vm.openCreateEditor()
        vm.updateNameInput("Temporary Tank")
        vm.saveAquarium()
        runCurrent()
        assertEquals(2, (vm.state.value as AquariumListState.Success).aquariumDetails.size)

        // Inspect backup -> populates restorePreview without replacing library yet
        vm.inspectBackupFromUri(backupUri)
        runCurrent()

        val withPreview = vm.state.value as AquariumListState.Success
        assertNotNull(withPreview.restorePreview)
        assertEquals(1, withPreview.restorePreview!!.counts.aquariums)
        assertEquals(2, withPreview.aquariumDetails.size) // Not replaced until confirmed!

        // Dismiss preview cancels without replacing
        vm.dismissRestorePreview()
        assertNull((vm.state.value as AquariumListState.Success).restorePreview)
        assertEquals(2, (vm.state.value as AquariumListState.Success).aquariumDetails.size)

        // Inspect again and explicitly confirm replace
        vm.inspectBackupFromUri(backupUri)
        runCurrent()
        vm.confirmRestoreLibrary()
        runCurrent()

        val afterRestore = vm.state.value as AquariumListState.Success
        assertNull(afterRestore.restorePreview)
        assertEquals(1, afterRestore.aquariumDetails.size)
        assertEquals("Acquario di Pietro", afterRestore.aquariumDetails.single().name)
        assertNotNull(afterRestore.backupStatusMessage)
        assertTrue(afterRestore.backupStatusMessage!!.contains("restored", ignoreCase = true))
    }

    @Test
    fun settingsTabAutoBackupFolderSelectionToggleRunNowAndLicenseDialog() = runTest {
        val repo = fakeCrudRepository()
        val vm = AquariumListViewModel(repo).also { store.put("settingsAuto", it) }
        runCurrent()

        vm.selectTab(AppTab.SETTINGS)
        val initial = vm.state.value as AquariumListState.Success
        assertFalse(initial.backupScheduleSettings.autoBackupEnabled)
        assertNull(initial.backupScheduleSettings.treeUri)
        assertEquals(7, initial.backupScheduleSettings.retentionCount)
        assertFalse(initial.showLicenseAndNoticesDialog)

        // Select backup folder -> enables auto backup and stores treeUri
        val folderUri = "content://fake/tree/daily-backups"
        vm.setAutoBackupTreeUri(folderUri)
        runCurrent()

        val afterFolder = vm.state.value as AquariumListState.Success
        assertTrue(afterFolder.backupScheduleSettings.autoBackupEnabled)
        assertEquals(folderUri, afterFolder.backupScheduleSettings.treeUri)

        // Run automatic backup now
        vm.runAutomaticBackupNow()
        runCurrent()

        val afterRunNow = vm.state.value as AquariumListState.Success
        assertNotNull(afterRunNow.backupScheduleSettings.lastBackupAt)
        assertEquals(1, afterRunNow.backupScheduleSettings.verifiedAutoGenerations.size)

        // Toggle auto-backup off
        vm.toggleAutoBackupEnabled(false)
        runCurrent()
        assertFalse((vm.state.value as AquariumListState.Success).backupScheduleSettings.autoBackupEnabled)

        // Open and close Apache 2.0 License & Third-Party Notices dialog
        vm.openLicenseAndNoticesDialog()
        assertTrue((vm.state.value as AquariumListState.Success).showLicenseAndNoticesDialog)
        vm.closeLicenseAndNoticesDialog()
        assertFalse((vm.state.value as AquariumListState.Success).showLicenseAndNoticesDialog)
    }
}

private class FakeAquariumRepository : AquariumRepository {
    private var tick = 1L
    private val profilesFlow = MutableStateFlow(
        listOf(
            AquariumProfile(UUID.fromString("00000000-0000-4000-8000-000000000101"), "Planted"),
            AquariumProfile(UUID.fromString("00000000-0000-4000-8000-000000000102"), "Freshwater Community"),
            AquariumProfile(UUID.fromString("00000000-0000-4000-8000-000000000103"), "Shrimp"),
        )
    )
    private val maintenanceCatalogFlow = MutableStateFlow(
        listOf(
            MaintenanceTypeCatalogItem("WATER_CHANGE", "Water change"),
            MaintenanceTypeCatalogItem("FILTER_CLEANING", "Filter maintenance"),
            MaintenanceTypeCatalogItem("GLASS_CLEANING", "Glass cleaning"),
            MaintenanceTypeCatalogItem("TRIMMING", "Plant trimming"),
            MaintenanceTypeCatalogItem("SUBSTRATE_VACUUM", "Substrate vacuum"),
            MaintenanceTypeCatalogItem("EQUIPMENT_CHECK", "Equipment check"),
        )
    )
    private val doseUnitsFlow = MutableStateFlow(
        listOf(
            com.pietrondo.aquariummanager.domain.DoseUnitCatalogItem("mL", "Millilitres (mL)", com.pietrondo.aquariummanager.domain.DoseUnitDimension.VOLUME),
            com.pietrondo.aquariummanager.domain.DoseUnitCatalogItem("g", "Grams (g)", com.pietrondo.aquariummanager.domain.DoseUnitDimension.MASS),
            com.pietrondo.aquariummanager.domain.DoseUnitCatalogItem("drops", "Drops", com.pietrondo.aquariummanager.domain.DoseUnitDimension.COUNT),
            com.pietrondo.aquariummanager.domain.DoseUnitCatalogItem("tabs", "Tablets / Capsules", com.pietrondo.aquariummanager.domain.DoseUnitDimension.COUNT),
        )
    )
    private val speciesFlow = MutableStateFlow(
        listOf(
            com.pietrondo.aquariummanager.domain.SpeciesCatalogItem(
                UUID.fromString("00000000-0000-4000-8000-000000000201"),
                com.pietrondo.aquariummanager.domain.SpeciesKind.FAUNA,
                "Amano Shrimp",
                "Caridina multidentata",
            ),
            com.pietrondo.aquariummanager.domain.SpeciesCatalogItem(
                UUID.fromString("00000000-0000-4000-8000-000000000202"),
                com.pietrondo.aquariummanager.domain.SpeciesKind.FAUNA,
                "Cherry Shrimp",
                "Neocaridina davidi",
            ),
            com.pietrondo.aquariummanager.domain.SpeciesCatalogItem(
                UUID.fromString("00000000-0000-4000-8000-000000000204"),
                com.pietrondo.aquariummanager.domain.SpeciesKind.FLORA,
                "Java Fern",
                "Microsorum pteropus",
            ),
            com.pietrondo.aquariummanager.domain.SpeciesCatalogItem(
                UUID.fromString("00000000-0000-4000-8000-000000000205"),
                com.pietrondo.aquariummanager.domain.SpeciesKind.FLORA,
                "Anubias Nana",
                "Anubias barteri var. nana",
            ),
        )
    )
    private val aquariumsFlow = MutableStateFlow<List<AquariumDetail>>(emptyList())
    private val measurementsFlow = MutableStateFlow<List<MeasurementRecord>>(emptyList())
    private val maintenanceFlow = MutableStateFlow<List<MaintenanceRecord>>(emptyList())
    private val stockFlow = MutableStateFlow<List<com.pietrondo.aquariummanager.domain.StockEntryRecord>>(emptyList())
    private val productsFlow = MutableStateFlow<List<com.pietrondo.aquariummanager.domain.ProductRecord>>(emptyList())
    private val dosingPlansFlow = MutableStateFlow<List<com.pietrondo.aquariummanager.domain.DosingPlanRecord>>(emptyList())
    private val dosingEventsFlow = MutableStateFlow<List<com.pietrondo.aquariummanager.domain.DosingEventRecord>>(emptyList())

    override fun observeAquariums(): Flow<List<AquariumSummary>> =
        aquariumsFlow.map { list -> list.map { it.toSummary() } }

    override fun observeAquariumDetails(): Flow<List<AquariumDetail>> = aquariumsFlow

    override fun observeProfiles(): Flow<List<AquariumProfile>> = profilesFlow

    override fun observeMeasurements(aquariumId: UUID): Flow<List<MeasurementRecord>> =
        measurementsFlow.map { all ->
            all.filter { it.aquariumId == aquariumId }
                .sortedWith(compareByDescending<MeasurementRecord> { it.recordedAt }.thenBy { it.id })
        }

    override fun observeMaintenanceEvents(aquariumId: UUID): Flow<List<MaintenanceRecord>> =
        maintenanceFlow.map { all ->
            all.filter { it.aquariumId == aquariumId }
                .sortedWith(compareByDescending<MaintenanceRecord> { it.occurredAt }.thenBy { it.id })
        }

    override fun observeMaintenanceTypeCatalog(): Flow<List<MaintenanceTypeCatalogItem>> =
        maintenanceCatalogFlow

    override fun observeSpeciesCatalog(kind: com.pietrondo.aquariummanager.domain.SpeciesKind?): Flow<List<com.pietrondo.aquariummanager.domain.SpeciesCatalogItem>> =
        speciesFlow.map { list -> if (kind == null) list else list.filter { it.kind == kind } }

    override fun observeStockEntries(aquariumId: UUID): Flow<List<com.pietrondo.aquariummanager.domain.StockEntryRecord>> =
        kotlinx.coroutines.flow.combine(stockFlow, speciesFlow) { entries, speciesList ->
            val speciesById = speciesList.associateBy { it.id }
            entries.filter { it.aquariumId == aquariumId }
                .mapNotNull { entry -> speciesById[entry.species.id]?.let { entry.copy(species = it) } }
                .sortedWith(compareByDescending<com.pietrondo.aquariummanager.domain.StockEntryRecord> { it.introducedAt }.thenBy { it.id })
        }

    override fun observeProducts(): Flow<List<com.pietrondo.aquariummanager.domain.ProductRecord>> = productsFlow

    override fun observeDoseUnitCatalog(): Flow<List<com.pietrondo.aquariummanager.domain.DoseUnitCatalogItem>> = doseUnitsFlow

    override fun observeDosingPlans(aquariumId: UUID): Flow<List<com.pietrondo.aquariummanager.domain.DosingPlanRecord>> =
        kotlinx.coroutines.flow.combine(dosingPlansFlow, productsFlow) { plans, products ->
            val productsById = products.associateBy { it.id }
            plans.filter { it.aquariumId == aquariumId }
                .mapNotNull { plan -> productsById[plan.product.id]?.let { plan.copy(product = it) } }
        }

    override fun observeDosingEvents(aquariumId: UUID): Flow<List<com.pietrondo.aquariummanager.domain.DosingEventRecord>> =
        dosingEventsFlow.map { all ->
            all.filter { it.aquariumId == aquariumId }
                .sortedWith(compareByDescending<com.pietrondo.aquariummanager.domain.DosingEventRecord> { it.occurredAt }.thenBy { it.id })
        }

    override suspend fun createAquarium(draft: AquariumDraft): UUID {
        val id = UUID.randomUUID()
        val assigned = profilesFlow.value
            .filter { it.id in draft.profileIds }
            .sortedBy { it.name }
        val detail = AquariumDetail(
            id = id,
            name = draft.name.trim(),
            netLitres = draft.netLitres,
            grossLitres = draft.grossLitres,
            notes = draft.notes?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = Instant.EPOCH,
            profiles = assigned,
        )
        aquariumsFlow.value = (aquariumsFlow.value + detail).sortedBy { it.name }
        return id
    }

    override suspend fun updateAquarium(id: UUID, draft: AquariumDraft) {
        val assigned = profilesFlow.value
            .filter { it.id in draft.profileIds }
            .sortedBy { it.name }
        aquariumsFlow.value = aquariumsFlow.value.map { existing ->
            if (existing.id == id) {
                existing.copy(
                    name = draft.name.trim(),
                    netLitres = draft.netLitres,
                    grossLitres = draft.grossLitres,
                    notes = draft.notes?.trim()?.takeIf { it.isNotEmpty() },
                    profiles = assigned,
                )
            } else {
                existing
            }
        }.sortedBy { it.name }
    }

    override suspend fun deleteAquarium(id: UUID) {
        aquariumsFlow.value = aquariumsFlow.value.filterNot { it.id == id }
        measurementsFlow.value = measurementsFlow.value.filterNot { it.aquariumId == id }
        maintenanceFlow.value = maintenanceFlow.value.filterNot { it.aquariumId == id }
        stockFlow.value = stockFlow.value.filterNot { it.aquariumId == id }
        dosingPlansFlow.value = dosingPlansFlow.value.filterNot { it.aquariumId == id }
        dosingEventsFlow.value = dosingEventsFlow.value.filterNot { it.aquariumId == id }
    }

    override suspend fun createProfile(name: String): AquariumProfile {
        val trimmed = name.trim()
        validateNonBlank(trimmed, "Aquarium profile name")
        require(profilesFlow.value.none { it.name.equals(trimmed, ignoreCase = true) }) {
            "Profile label '$trimmed' already exists"
        }
        val created = AquariumProfile(UUID.randomUUID(), trimmed)
        profilesFlow.value = (profilesFlow.value + created).sortedBy { it.name }
        return created
    }

    override suspend fun recordMeasurement(aquariumId: UUID, draft: MeasurementDraft): UUID {
        val id = UUID.randomUUID()
        val record = MeasurementRecord(
            id = id,
            aquariumId = aquariumId,
            recordedAt = draft.recordedAt ?: Instant.ofEpochSecond(tick++),
            ph = draft.ph,
            khDkh = draft.khDkh,
            ghDgh = draft.ghDgh,
            no2MgL = draft.no2MgL,
            no3MgL = draft.no3MgL,
            tdsPpm = draft.tdsPpm,
            temperatureC = draft.temperatureC,
        )
        measurementsFlow.value = measurementsFlow.value + record
        return id
    }

    override suspend fun deleteMeasurement(id: UUID) {
        measurementsFlow.value = measurementsFlow.value.filterNot { it.id == id }
    }

    override suspend fun recordMaintenanceEvent(aquariumId: UUID, draft: MaintenanceDraft): UUID {
        val id = UUID.randomUUID()
        val record = MaintenanceRecord(
            id = id,
            aquariumId = aquariumId,
            type = draft.type.trim(),
            occurredAt = draft.occurredAt ?: Instant.ofEpochSecond(tick++),
            changedLitres = draft.changedLitres,
            notes = draft.notes?.trim()?.takeIf { it.isNotEmpty() },
        )
        maintenanceFlow.value = maintenanceFlow.value + record
        return id
    }

    override suspend fun updateMaintenanceEvent(id: UUID, aquariumId: UUID, draft: MaintenanceDraft) {
        maintenanceFlow.value = maintenanceFlow.value.map { existing ->
            if (existing.id == id) {
                existing.copy(
                    aquariumId = aquariumId,
                    type = draft.type.trim(),
                    occurredAt = draft.occurredAt ?: existing.occurredAt,
                    changedLitres = draft.changedLitres,
                    notes = draft.notes?.trim()?.takeIf { it.isNotEmpty() },
                )
            } else {
                existing
            }
        }
    }

    override suspend fun deleteMaintenanceEvent(id: UUID) {
        maintenanceFlow.value = maintenanceFlow.value.filterNot { it.id == id }
    }

    override suspend fun createSpecies(draft: com.pietrondo.aquariummanager.domain.SpeciesDraft): com.pietrondo.aquariummanager.domain.SpeciesCatalogItem {
        val trimmedCommon = draft.commonName.trim()
        val trimmedSci = draft.scientificName?.trim()?.takeIf { it.isNotEmpty() }
        com.pietrondo.aquariummanager.domain.validateUniqueSpeciesCatalog(
            speciesFlow.value,
            draft.kind,
            trimmedCommon,
            trimmedSci,
        )
        val created = com.pietrondo.aquariummanager.domain.SpeciesCatalogItem(
            id = UUID.randomUUID(),
            kind = draft.kind,
            commonName = trimmedCommon,
            scientificName = trimmedSci,
        )
        speciesFlow.value = (speciesFlow.value + created).sortedBy { it.commonName }
        return created
    }

    override suspend fun updateSpecies(id: UUID, draft: com.pietrondo.aquariummanager.domain.SpeciesDraft): com.pietrondo.aquariummanager.domain.SpeciesCatalogItem {
        val trimmedCommon = draft.commonName.trim()
        val trimmedSci = draft.scientificName?.trim()?.takeIf { it.isNotEmpty() }
        com.pietrondo.aquariummanager.domain.validateUniqueSpeciesCatalog(
            speciesFlow.value,
            draft.kind,
            trimmedCommon,
            trimmedSci,
            excludingId = id,
        )
        val updated = com.pietrondo.aquariummanager.domain.SpeciesCatalogItem(
            id = id,
            kind = draft.kind,
            commonName = trimmedCommon,
            scientificName = trimmedSci,
        )
        speciesFlow.value = speciesFlow.value.map { if (it.id == id) updated else it }.sortedBy { it.commonName }
        return updated
    }

    override suspend fun deleteSpecies(id: UUID) {
        val refCount = stockFlow.value.count { it.species.id == id }
        require(refCount == 0) { "Cannot delete species because it is referenced by $refCount stock entry record(s)" }
        speciesFlow.value = speciesFlow.value.filterNot { it.id == id }
    }

    override suspend fun createStockEntry(aquariumId: UUID, draft: com.pietrondo.aquariummanager.domain.StockEntryDraft): UUID {
        val species = speciesFlow.value.firstOrNull { it.id == draft.speciesId }
            ?: throw IllegalArgumentException("Species not found")
        val id = UUID.randomUUID()
        val record = com.pietrondo.aquariummanager.domain.StockEntryRecord(
            id = id,
            aquariumId = aquariumId,
            species = species,
            quantity = draft.quantity,
            introducedAt = draft.introducedAt ?: Instant.ofEpochSecond(tick++),
            exitedAt = draft.exitedAt,
            notes = draft.notes?.trim()?.takeIf { it.isNotEmpty() },
        )
        stockFlow.value = stockFlow.value + record
        return id
    }

    override suspend fun updateStockEntry(id: UUID, aquariumId: UUID, draft: com.pietrondo.aquariummanager.domain.StockEntryDraft) {
        val species = speciesFlow.value.firstOrNull { it.id == draft.speciesId }
            ?: throw IllegalArgumentException("Species not found")
        stockFlow.value = stockFlow.value.map { existing ->
            if (existing.id == id) {
                existing.copy(
                    aquariumId = aquariumId,
                    species = species,
                    quantity = draft.quantity,
                    introducedAt = draft.introducedAt ?: existing.introducedAt,
                    exitedAt = draft.exitedAt,
                    notes = draft.notes?.trim()?.takeIf { it.isNotEmpty() },
                )
            } else {
                existing
            }
        }
    }

    override suspend fun markStockExited(id: UUID, exitedAt: Instant?) {
        stockFlow.value = stockFlow.value.map { existing ->
            if (existing.id == id) {
                val resolvedExit = exitedAt ?: Instant.ofEpochSecond(tick++).let { now ->
                    if (now.isBefore(existing.introducedAt)) existing.introducedAt else now
                }
                existing.copy(exitedAt = resolvedExit)
            } else {
                existing
            }
        }
    }

    override suspend fun deleteStockEntry(id: UUID) {
        stockFlow.value = stockFlow.value.filterNot { it.id == id }
    }

    override suspend fun createProduct(name: String): com.pietrondo.aquariummanager.domain.ProductRecord {
        val trimmed = name.trim()
        validateNonBlank(trimmed, "Product name")
        require(productsFlow.value.none { it.name.equals(trimmed, ignoreCase = true) }) {
            "Product '$trimmed' already exists"
        }
        val created = com.pietrondo.aquariummanager.domain.ProductRecord(UUID.randomUUID(), trimmed)
        productsFlow.value = (productsFlow.value + created).sortedBy { it.name }
        return created
    }

    override suspend fun updateProduct(id: UUID, name: String): com.pietrondo.aquariummanager.domain.ProductRecord {
        val trimmed = name.trim()
        validateNonBlank(trimmed, "Product name")
        require(productsFlow.value.none { it.id != id && it.name.equals(trimmed, ignoreCase = true) }) {
            "Product '$trimmed' already exists"
        }
        val updated = com.pietrondo.aquariummanager.domain.ProductRecord(id, trimmed)
        productsFlow.value = productsFlow.value.map { if (it.id == id) updated else it }.sortedBy { it.name }
        return updated
    }

    override suspend fun deleteProduct(id: UUID) {
        val refCount = dosingPlansFlow.value.count { it.product.id == id }
        require(refCount == 0) { "Cannot delete product because it is referenced by $refCount dosing plan(s)" }
        productsFlow.value = productsFlow.value.filterNot { it.id == id }
    }

    override suspend fun createDosingPlan(aquariumId: UUID, draft: com.pietrondo.aquariummanager.domain.DosingPlanDraft): UUID {
        val product = productsFlow.value.firstOrNull { it.id == draft.productId }
            ?: throw IllegalArgumentException("Product not found")
        val id = UUID.randomUUID()
        val record = com.pietrondo.aquariummanager.domain.DosingPlanRecord(
            id = id,
            aquariumId = aquariumId,
            product = product,
            dose = draft.dose,
            unit = draft.unit.trim(),
            frequency = draft.frequency,
            weekdays = draft.weekdays,
            startDate = draft.startDate,
            endDate = draft.endDate,
            timeZone = draft.timeZone,
        )
        dosingPlansFlow.value = dosingPlansFlow.value + record
        return id
    }

    override suspend fun updateDosingPlan(id: UUID, aquariumId: UUID, draft: com.pietrondo.aquariummanager.domain.DosingPlanDraft) {
        val product = productsFlow.value.firstOrNull { it.id == draft.productId }
            ?: throw IllegalArgumentException("Product not found")
        dosingPlansFlow.value = dosingPlansFlow.value.map { existing ->
            if (existing.id == id) {
                existing.copy(
                    aquariumId = aquariumId,
                    product = product,
                    dose = draft.dose,
                    unit = draft.unit.trim(),
                    frequency = draft.frequency,
                    weekdays = draft.weekdays,
                    startDate = draft.startDate,
                    endDate = draft.endDate,
                    timeZone = draft.timeZone,
                )
            } else {
                existing
            }
        }
    }

    override suspend fun deleteDosingPlan(id: UUID) {
        dosingPlansFlow.value = dosingPlansFlow.value.filterNot { it.id == id }
        dosingEventsFlow.value = dosingEventsFlow.value.map { event ->
            if (event.planId == id) event.copy(planId = null) else event
        }
    }

    override suspend fun recordDosingFromPlan(aquariumId: UUID, planId: UUID, occurredAt: Instant?): UUID {
        val plan = dosingPlansFlow.value.firstOrNull { it.id == planId }
            ?: throw IllegalArgumentException("Dosing plan not found")
        require(plan.aquariumId == aquariumId) { "Plan belongs to different aquarium" }
        val product = productsFlow.value.firstOrNull { it.id == plan.product.id } ?: plan.product
        val id = UUID.randomUUID()
        val event = com.pietrondo.aquariummanager.domain.DosingEventRecord(
            id = id,
            aquariumId = aquariumId,
            planId = plan.id,
            occurredAt = occurredAt ?: Instant.ofEpochSecond(tick++),
            productNameSnapshot = product.name,
            dose = plan.dose,
            unit = plan.unit,
        )
        dosingEventsFlow.value = dosingEventsFlow.value + event
        return id
    }

    override suspend fun recordAdHocDosingEvent(aquariumId: UUID, draft: com.pietrondo.aquariummanager.domain.DosingEventDraft): UUID {
        val id = UUID.randomUUID()
        val event = com.pietrondo.aquariummanager.domain.DosingEventRecord(
            id = id,
            aquariumId = aquariumId,
            planId = null,
            occurredAt = draft.occurredAt ?: Instant.ofEpochSecond(tick++),
            productNameSnapshot = draft.productNameSnapshot.trim(),
            dose = draft.dose,
            unit = draft.unit.trim(),
        )
        dosingEventsFlow.value = dosingEventsFlow.value + event
        return id
    }

    override suspend fun deleteDosingEvent(id: UUID) {
        dosingEventsFlow.value = dosingEventsFlow.value.filterNot { it.id == id }
    }

    private data class SavedFakeBackup(
        val preview: com.pietrondo.aquariummanager.domain.BackupRestorePreview,
        val aquariums: List<AquariumDetail>,
        val profiles: List<AquariumProfile>,
        val measurements: List<MeasurementRecord>,
        val maintenance: List<MaintenanceRecord>,
        val stock: List<com.pietrondo.aquariummanager.domain.StockEntryRecord>,
        val products: List<com.pietrondo.aquariummanager.domain.ProductRecord>,
        val dosingPlans: List<com.pietrondo.aquariummanager.domain.DosingPlanRecord>,
        val dosingEvents: List<com.pietrondo.aquariummanager.domain.DosingEventRecord>,
        val species: List<com.pietrondo.aquariummanager.domain.SpeciesCatalogItem>,
    )

    private val savedBackups = mutableMapOf<String, SavedFakeBackup>()
    private val scheduleFlow = MutableStateFlow(com.pietrondo.aquariummanager.domain.BackupScheduleSettings())

    override fun observeBackupScheduleSettings(): Flow<com.pietrondo.aquariummanager.domain.BackupScheduleSettings> =
        scheduleFlow

    override suspend fun exportBackupToUri(destinationUri: String): com.pietrondo.aquariummanager.domain.BackupExportResult {
        val counts = com.pietrondo.aquariummanager.domain.BackupTableCounts(
            aquariums = aquariumsFlow.value.size,
            aquariumProfiles = profilesFlow.value.size,
            aquariumProfileLinks = aquariumsFlow.value.sumOf { it.profiles.size },
            measurements = measurementsFlow.value.size,
            maintenanceEvents = maintenanceFlow.value.size,
            products = productsFlow.value.size,
            dosingPlans = dosingPlansFlow.value.size,
            dosingEvents = dosingEventsFlow.value.size,
            species = speciesFlow.value.size,
            stockEntries = stockFlow.value.size,
        )
        val createdAt = Instant.ofEpochSecond(tick++)
        val sha256 = "0123456789abcdef".repeat(4)
        val manifest = com.pietrondo.aquariummanager.domain.BackupManifest(
            appVersion = "0.1.0",
            createdAt = createdAt,
            counts = counts,
            librarySha256 = sha256,
        )
        val preview = com.pietrondo.aquariummanager.domain.BackupRestorePreview(
            formatVersion = 1,
            appVersion = "0.1.0",
            createdAt = createdAt,
            counts = counts,
            librarySha256 = sha256,
            sourceUri = destinationUri,
        )
        savedBackups[destinationUri] = SavedFakeBackup(
            preview = preview,
            aquariums = aquariumsFlow.value,
            profiles = profilesFlow.value,
            measurements = measurementsFlow.value,
            maintenance = maintenanceFlow.value,
            stock = stockFlow.value,
            products = productsFlow.value,
            dosingPlans = dosingPlansFlow.value,
            dosingEvents = dosingEventsFlow.value,
            species = speciesFlow.value,
        )
        return com.pietrondo.aquariummanager.domain.BackupExportResult(
            destinationUri = destinationUri,
            manifest = manifest,
        )
    }

    override suspend fun inspectBackupUri(sourceUri: String): com.pietrondo.aquariummanager.domain.BackupRestorePreview {
        val saved = savedBackups[sourceUri] ?: throw IllegalArgumentException("Backup not found: $sourceUri")
        return saved.preview.copy(sourceUri = sourceUri)
    }

    override suspend fun restoreBackupFromUri(
        sourceUri: String,
        confirmReplace: Boolean,
    ): com.pietrondo.aquariummanager.domain.BackupRestorePreview {
        require(confirmReplace) { "Confirmation required" }
        val saved = savedBackups[sourceUri] ?: throw IllegalArgumentException("Backup not found: $sourceUri")
        aquariumsFlow.value = saved.aquariums
        profilesFlow.value = saved.profiles
        measurementsFlow.value = saved.measurements
        maintenanceFlow.value = saved.maintenance
        stockFlow.value = saved.stock
        productsFlow.value = saved.products
        dosingPlansFlow.value = saved.dosingPlans
        dosingEventsFlow.value = saved.dosingEvents
        speciesFlow.value = saved.species
        return saved.preview.copy(sourceUri = sourceUri)
    }

    override suspend fun updateAutoBackupSettings(
        enabled: Boolean,
        treeUri: String?,
        retentionCount: Int,
    ): com.pietrondo.aquariummanager.domain.BackupScheduleSettings {
        val updated = scheduleFlow.value.copy(
            autoBackupEnabled = enabled,
            treeUri = treeUri,
            retentionCount = retentionCount,
            lastBackupStatus = if (enabled) "Automatic daily backup enabled" else "Automatic daily backup paused",
        )
        scheduleFlow.value = updated
        return updated
    }

    override suspend fun runAutomaticBackupNow(): com.pietrondo.aquariummanager.domain.BackupScheduleSettings {
        val current = scheduleFlow.value
        val tree = current.treeUri ?: return current.copy(lastBackupStatus = "Folder permission required")
        val now = Instant.ofEpochSecond(tick++)
        val autoUri = "$tree/auto-${now.epochSecond}.zip"
        exportBackupToUri(autoUri)
        val retained = (current.verifiedAutoGenerations + autoUri).takeLast(current.retentionCount)
        val updated = current.copy(
            lastBackupAt = now,
            lastBackupStatus = "Verified automatic backup saved",
            verifiedAutoGenerations = retained,
        )
        scheduleFlow.value = updated
        return updated
    }
}
