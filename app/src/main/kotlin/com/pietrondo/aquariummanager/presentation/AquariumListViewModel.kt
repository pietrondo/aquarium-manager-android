package com.pietrondo.aquariummanager.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pietrondo.aquariummanager.domain.AquariumDetail
import com.pietrondo.aquariummanager.domain.AquariumDraft
import com.pietrondo.aquariummanager.domain.AquariumProfile
import com.pietrondo.aquariummanager.domain.AquariumRepository
import com.pietrondo.aquariummanager.domain.AquariumSummary
import com.pietrondo.aquariummanager.domain.DoseUnitCatalogItem
import com.pietrondo.aquariummanager.domain.DosingEventDraft
import com.pietrondo.aquariummanager.domain.DosingEventRecord
import com.pietrondo.aquariummanager.domain.DosingFrequency
import com.pietrondo.aquariummanager.domain.DosingPlanDraft
import com.pietrondo.aquariummanager.domain.DosingPlanRecord
import com.pietrondo.aquariummanager.domain.MaintenanceDraft
import com.pietrondo.aquariummanager.domain.MaintenanceRecord
import com.pietrondo.aquariummanager.domain.MaintenanceTypeCatalogItem
import com.pietrondo.aquariummanager.domain.MeasurementDraft
import com.pietrondo.aquariummanager.domain.MeasurementRecord
import com.pietrondo.aquariummanager.domain.ParameterTrendSummary
import com.pietrondo.aquariummanager.domain.ProductRecord
import com.pietrondo.aquariummanager.domain.SpeciesCatalogItem
import com.pietrondo.aquariummanager.domain.SpeciesDraft
import com.pietrondo.aquariummanager.domain.SpeciesKind
import com.pietrondo.aquariummanager.domain.StockEntryDraft
import com.pietrondo.aquariummanager.domain.StockEntryRecord
import com.pietrondo.aquariummanager.domain.calculateParameterTrends
import com.pietrondo.aquariummanager.domain.evaluateMaintenanceVolumeWarning
import com.pietrondo.aquariummanager.domain.filterByKind
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppTab {
    AQUARIUMS,
    TESTS,
    MAINTENANCE,
    LIVESTOCK,
    SETTINGS,
}

enum class LivestockSection {
    STOCK,
    DOSING,
    SPECIES,
}

data class AquariumEditorState(
    val editingAquariumId: UUID? = null,
    val nameInput: String = "",
    val netLitresInput: String = "",
    val grossLitresInput: String = "",
    val notesInput: String = "",
    val selectedProfileIds: Set<UUID> = emptySet(),
    val newProfileNameInput: String = "",
    val validationError: String? = null,
    val profileValidationError: String? = null,
    val isSaving: Boolean = false,
)

data class MeasurementEditorState(
    val phInput: String = "",
    val khDkhInput: String = "",
    val ghDghInput: String = "",
    val no2MgLInput: String = "",
    val no3MgLInput: String = "",
    val tdsPpmInput: String = "",
    val temperatureCInput: String = "",
    val validationError: String? = null,
    val isSaving: Boolean = false,
)

data class MaintenanceEditorState(
    val editingMaintenanceId: UUID? = null,
    val selectedTypeCode: String = "WATER_CHANGE",
    val changedLitresInput: String = "",
    val notesInput: String = "",
    val volumeWarning: String? = null,
    val validationError: String? = null,
    val isSaving: Boolean = false,
)

data class SpeciesEditorState(
    val editingSpeciesId: UUID? = null,
    val selectedKind: SpeciesKind = SpeciesKind.FAUNA,
    val commonNameInput: String = "",
    val scientificNameInput: String = "",
    val validationError: String? = null,
    val isSaving: Boolean = false,
)

data class StockEditorState(
    val editingStockId: UUID? = null,
    val selectedSpeciesId: UUID? = null,
    val quantityInput: String = "",
    val introducedDateInput: String = "",
    val exitedDateInput: String = "",
    val notesInput: String = "",
    val validationError: String? = null,
    val isSaving: Boolean = false,
)

data class ProductEditorState(
    val editingProductId: UUID? = null,
    val nameInput: String = "",
    val validationError: String? = null,
    val isSaving: Boolean = false,
)

data class DosingPlanEditorState(
    val editingPlanId: UUID? = null,
    val selectedProductId: UUID? = null,
    val doseInput: String = "",
    val selectedUnitCode: String = "mL",
    val frequency: DosingFrequency = DosingFrequency.DAILY,
    val selectedWeekdays: Set<DayOfWeek> = emptySet(),
    val startDateInput: String = "2026-10-19",
    val endDateInput: String = "",
    val timeZoneInput: String = "UTC",
    val validationError: String? = null,
    val isSaving: Boolean = false,
)

data class AdHocDosingEditorState(
    val productNameInput: String = "",
    val doseInput: String = "",
    val selectedUnitCode: String = "mL",
    val validationError: String? = null,
    val isSaving: Boolean = false,
)

sealed interface AquariumListState {
    data object Loading : AquariumListState
    data class Success(
        val aquariums: List<AquariumSummary>,
        val aquariumDetails: List<AquariumDetail> = aquariums.map {
            AquariumDetail(
                id = it.id,
                name = it.name,
                netLitres = null,
                grossLitres = null,
                notes = null,
                createdAt = Instant.EPOCH,
                profiles = emptyList(),
            )
        },
        val availableProfiles: List<AquariumProfile> = emptyList(),
        val selectedAquariumId: UUID? = null,
        val editorState: AquariumEditorState? = null,
        val pendingDeleteAquarium: AquariumDetail? = null,
        val selectedTab: AppTab = AppTab.AQUARIUMS,
        val livestockSection: LivestockSection = LivestockSection.STOCK,
        val measurements: List<MeasurementRecord> = emptyList(),
        val parameterTrends: List<ParameterTrendSummary> = calculateParameterTrends(measurements),
        val maintenanceEvents: List<MaintenanceRecord> = emptyList(),
        val maintenanceTypeCatalog: List<MaintenanceTypeCatalogItem> = emptyList(),
        val speciesCatalog: List<SpeciesCatalogItem> = emptyList(),
        val selectedSpeciesKindFilter: SpeciesKind? = null,
        val stockEntries: List<StockEntryRecord> = emptyList(),
        val products: List<ProductRecord> = emptyList(),
        val doseUnitCatalog: List<DoseUnitCatalogItem> = emptyList(),
        val dosingPlans: List<DosingPlanRecord> = emptyList(),
        val dosingEvents: List<DosingEventRecord> = emptyList(),
        val measurementEditorState: MeasurementEditorState? = null,
        val maintenanceEditorState: MaintenanceEditorState? = null,
        val speciesEditorState: SpeciesEditorState? = null,
        val stockEditorState: StockEditorState? = null,
        val productEditorState: ProductEditorState? = null,
        val dosingPlanEditorState: DosingPlanEditorState? = null,
        val adHocDosingEditorState: AdHocDosingEditorState? = null,
        val actionError: String? = null,
        val backupScheduleSettings: com.pietrondo.aquariummanager.domain.BackupScheduleSettings =
            com.pietrondo.aquariummanager.domain.BackupScheduleSettings(),
        val restorePreview: com.pietrondo.aquariummanager.domain.BackupRestorePreview? = null,
        val isBackupOperationInProgress: Boolean = false,
        val backupStatusMessage: String? = null,
        val showLicenseAndNoticesDialog: Boolean = false,
    ) : AquariumListState {
        val activeAquarium: AquariumDetail?
            get() = aquariumDetails.firstOrNull { it.id == selectedAquariumId }
                ?: aquariumDetails.firstOrNull()

        val activeAquariumId: UUID?
            get() = activeAquarium?.id

        val filteredSpeciesCatalog: List<SpeciesCatalogItem>
            get() = speciesCatalog.filterByKind(selectedSpeciesKindFilter)

        val filteredStockEntries: List<StockEntryRecord>
            get() = if (selectedSpeciesKindFilter == null) {
                stockEntries
            } else {
                stockEntries.filter { it.species.kind == selectedSpeciesKindFilter }
            }
    }
    data object Error : AquariumListState
}

private data class BaseAquariumSnapshot(
    val details: List<AquariumDetail>,
    val profiles: List<AquariumProfile>,
    val maintenanceTypeCatalog: List<MaintenanceTypeCatalogItem>,
    val speciesCatalog: List<SpeciesCatalogItem>,
    val products: List<ProductRecord>,
    val doseUnitCatalog: List<DoseUnitCatalogItem>,
    val backupScheduleSettings: com.pietrondo.aquariummanager.domain.BackupScheduleSettings,
    val selectedAquariumId: UUID?,
    val activeAquariumId: UUID?,
)

private data class ActiveAquariumHistories(
    val measurements: List<MeasurementRecord> = emptyList(),
    val maintenanceEvents: List<MaintenanceRecord> = emptyList(),
    val stockEntries: List<StockEntryRecord> = emptyList(),
    val dosingPlans: List<DosingPlanRecord> = emptyList(),
    val dosingEvents: List<DosingEventRecord> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class AquariumListViewModel(private val repository: AquariumRepository) : ViewModel() {
    private val mutableState = MutableStateFlow<AquariumListState>(AquariumListState.Loading)
    val state = mutableState.asStateFlow()
    private val retries = MutableStateFlow(0)
    private val selectedAquariumIdFlow = MutableStateFlow<UUID?>(null)

    init {
        viewModelScope.launch {
            // collectLatest cancels and joins the previous observation before retrying.
            retries.collectLatest {
                mutableState.value = AquariumListState.Loading
                try {
                    repository.ensureCatalogSeeded()
                    val catalogsFlow = combine(
                        repository.observeMaintenanceTypeCatalog(),
                        repository.observeSpeciesCatalog(null),
                        repository.observeProducts(),
                        repository.observeDoseUnitCatalog(),
                        repository.observeBackupScheduleSettings(),
                    ) { maintCatalog, species, products, doseUnits, backupSchedule ->
                        CatalogsSnapshot(
                            maintenanceTypeCatalog = maintCatalog,
                            speciesCatalog = species,
                            products = products,
                            doseUnitCatalog = doseUnits,
                            backupScheduleSettings = backupSchedule,
                        )
                    }
                    combine(
                        repository.observeAquariumDetails(),
                        repository.observeProfiles(),
                        catalogsFlow,
                        selectedAquariumIdFlow,
                    ) { details, profiles, catalogs, requestedSelectedId ->
                        val validSelectedId = requestedSelectedId?.takeIf { selId ->
                            details.any { it.id == selId }
                        }
                        val activeId = validSelectedId ?: details.firstOrNull()?.id
                        BaseAquariumSnapshot(
                            details = details,
                            profiles = profiles,
                            maintenanceTypeCatalog = catalogs.maintenanceTypeCatalog,
                            speciesCatalog = catalogs.speciesCatalog,
                            products = catalogs.products,
                            doseUnitCatalog = catalogs.doseUnitCatalog,
                            backupScheduleSettings = catalogs.backupScheduleSettings,
                            selectedAquariumId = validSelectedId,
                            activeAquariumId = activeId,
                        )
                    }.flatMapLatest { base ->
                        val activeId = base.activeAquariumId
                        if (activeId == null) {
                            flowOf(base to ActiveAquariumHistories())
                        } else {
                            combine(
                                repository.observeMeasurements(activeId),
                                repository.observeMaintenanceEvents(activeId),
                                repository.observeStockEntries(activeId),
                                repository.observeDosingPlans(activeId),
                                repository.observeDosingEvents(activeId),
                            ) { measurements, maintenanceEvents, stockEntries, dosingPlans, dosingEvents ->
                                base to ActiveAquariumHistories(
                                    measurements = measurements,
                                    maintenanceEvents = maintenanceEvents,
                                    stockEntries = stockEntries,
                                    dosingPlans = dosingPlans,
                                    dosingEvents = dosingEvents,
                                )
                            }
                        }
                    }.collect { (base, histories) ->
                        val previous = mutableState.value as? AquariumListState.Success
                        val unmodifiableSummaries = Collections.unmodifiableList(base.details.map { it.toSummary() })
                        val unmodifiableDetails = Collections.unmodifiableList(base.details.toList())
                        val unmodifiableProfiles = Collections.unmodifiableList(base.profiles.toList())
                        val unmodifiableMaintCatalog = Collections.unmodifiableList(base.maintenanceTypeCatalog.toList())
                        val unmodifiableSpeciesCatalog = Collections.unmodifiableList(base.speciesCatalog.toList())
                        val unmodifiableProducts = Collections.unmodifiableList(base.products.toList())
                        val unmodifiableDoseUnits = Collections.unmodifiableList(base.doseUnitCatalog.toList())
                        val unmodifiableMeasurements = Collections.unmodifiableList(histories.measurements.toList())
                        val unmodifiableTrends = Collections.unmodifiableList(
                            calculateParameterTrends(unmodifiableMeasurements)
                        )
                        val unmodifiableMaintenanceEvents = Collections.unmodifiableList(histories.maintenanceEvents.toList())
                        val unmodifiableStockEntries = Collections.unmodifiableList(histories.stockEntries.toList())
                        val unmodifiableDosingPlans = Collections.unmodifiableList(histories.dosingPlans.toList())
                        val unmodifiableDosingEvents = Collections.unmodifiableList(histories.dosingEvents.toList())
                        val validPendingDelete = previous?.pendingDeleteAquarium?.let { pending ->
                            unmodifiableDetails.firstOrNull { it.id == pending.id }
                        }
                        if (selectedAquariumIdFlow.value != base.selectedAquariumId) {
                            selectedAquariumIdFlow.value = base.selectedAquariumId
                        }
                        mutableState.value = AquariumListState.Success(
                            aquariums = unmodifiableSummaries,
                            aquariumDetails = unmodifiableDetails,
                            availableProfiles = unmodifiableProfiles,
                            selectedAquariumId = base.selectedAquariumId,
                            editorState = previous?.editorState,
                            pendingDeleteAquarium = validPendingDelete,
                            selectedTab = previous?.selectedTab ?: AppTab.AQUARIUMS,
                            livestockSection = previous?.livestockSection ?: LivestockSection.STOCK,
                            measurements = unmodifiableMeasurements,
                            parameterTrends = unmodifiableTrends,
                            maintenanceEvents = unmodifiableMaintenanceEvents,
                            maintenanceTypeCatalog = unmodifiableMaintCatalog,
                            speciesCatalog = unmodifiableSpeciesCatalog,
                            selectedSpeciesKindFilter = previous?.selectedSpeciesKindFilter,
                            stockEntries = unmodifiableStockEntries,
                            products = unmodifiableProducts,
                            doseUnitCatalog = unmodifiableDoseUnits,
                            dosingPlans = unmodifiableDosingPlans,
                            dosingEvents = unmodifiableDosingEvents,
                            measurementEditorState = previous?.measurementEditorState,
                            maintenanceEditorState = previous?.maintenanceEditorState,
                            speciesEditorState = previous?.speciesEditorState,
                            stockEditorState = previous?.stockEditorState,
                            productEditorState = previous?.productEditorState,
                            dosingPlanEditorState = previous?.dosingPlanEditorState,
                            adHocDosingEditorState = previous?.adHocDosingEditorState,
                            actionError = previous?.actionError,
                            backupScheduleSettings = base.backupScheduleSettings,
                            restorePreview = previous?.restorePreview,
                            isBackupOperationInProgress = previous?.isBackupOperationInProgress ?: false,
                            backupStatusMessage = previous?.backupStatusMessage,
                            showLicenseAndNoticesDialog = previous?.showLicenseAndNoticesDialog ?: false,
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.value = AquariumListState.Error
                }
            }
        }
    }

    fun retry() {
        retries.update { it + 1 }
    }

    fun selectTab(tab: AppTab) {
        updateSuccess { it.copy(selectedTab = tab, actionError = null) }
    }

    fun selectAquarium(id: UUID?) {
        selectedAquariumIdFlow.value = id
        updateSuccess { it.copy(selectedAquariumId = id, actionError = null) }
    }

    fun openCreateEditor() {
        updateSuccess { it.copy(editorState = AquariumEditorState()) }
    }

    fun openEditEditor(aquariumId: UUID) {
        updateSuccess { current ->
            val target = current.aquariumDetails.firstOrNull { it.id == aquariumId } ?: return@updateSuccess current
            current.copy(
                editorState = AquariumEditorState(
                    editingAquariumId = target.id,
                    nameInput = target.name,
                    netLitresInput = target.netLitres?.stripTrailingZeros()?.toPlainString().orEmpty(),
                    grossLitresInput = target.grossLitres?.stripTrailingZeros()?.toPlainString().orEmpty(),
                    notesInput = target.notes.orEmpty(),
                    selectedProfileIds = target.profiles.mapTo(linkedSetOf()) { it.id },
                )
            )
        }
    }

    fun closeEditor() {
        updateSuccess { it.copy(editorState = null) }
    }

    fun updateNameInput(value: String) {
        updateEditor { it.copy(nameInput = value, validationError = null) }
    }

    fun updateNetLitresInput(value: String) {
        updateEditor { it.copy(netLitresInput = value, validationError = null) }
    }

    fun updateGrossLitresInput(value: String) {
        updateEditor { it.copy(grossLitresInput = value, validationError = null) }
    }

    fun updateNotesInput(value: String) {
        updateEditor { it.copy(notesInput = value, validationError = null) }
    }

    fun toggleProfileSelection(profileId: UUID) {
        updateEditor { editor ->
            val updated = if (profileId in editor.selectedProfileIds) {
                editor.selectedProfileIds - profileId
            } else {
                editor.selectedProfileIds + profileId
            }
            editor.copy(selectedProfileIds = updated)
        }
    }

    fun updateNewProfileNameInput(value: String) {
        updateEditor { it.copy(newProfileNameInput = value, profileValidationError = null) }
    }

    fun addCustomProfile() {
        val current = mutableState.value as? AquariumListState.Success ?: return
        val editor = current.editorState ?: return
        val trimmed = editor.newProfileNameInput.trim()
        if (trimmed.isEmpty()) {
            updateEditor { it.copy(profileValidationError = "Profile label cannot be blank.") }
            return
        }
        if (current.availableProfiles.any { it.name.equals(trimmed, ignoreCase = true) }) {
            updateEditor { it.copy(profileValidationError = "Profile label '$trimmed' already exists.") }
            return
        }
        viewModelScope.launch {
            try {
                val created = repository.createProfile(trimmed)
                updateEditor {
                    it.copy(
                        newProfileNameInput = "",
                        profileValidationError = null,
                        selectedProfileIds = it.selectedProfileIds + created.id,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: IllegalArgumentException) {
                updateEditor {
                    it.copy(profileValidationError = e.message ?: "Invalid profile label.")
                }
            } catch (_: Exception) {
                updateEditor {
                    it.copy(profileValidationError = "Could not create profile label. Please try again.")
                }
            }
        }
    }

    fun saveAquarium() {
        val current = mutableState.value as? AquariumListState.Success ?: return
        val editor = current.editorState ?: return
        if (editor.isSaving) return

        val trimmedName = editor.nameInput.trim()
        if (trimmedName.isEmpty()) {
            updateEditor { it.copy(validationError = "Aquarium name cannot be blank.") }
            return
        }

        val netLitres = try {
            parseOptionalDecimal(editor.netLitresInput, "Net litres")
        } catch (e: IllegalArgumentException) {
            updateEditor { it.copy(validationError = e.message) }
            return
        }

        val grossLitres = try {
            parseOptionalDecimal(editor.grossLitresInput, "Gross litres")
        } catch (e: IllegalArgumentException) {
            updateEditor { it.copy(validationError = e.message) }
            return
        }

        val draft = try {
            AquariumDraft(
                name = trimmedName,
                netLitres = netLitres,
                grossLitres = grossLitres,
                notes = editor.notesInput.trim().takeIf { it.isNotEmpty() },
                profileIds = editor.selectedProfileIds,
            )
        } catch (e: IllegalArgumentException) {
            updateEditor { it.copy(validationError = e.message ?: "Invalid aquarium values.") }
            return
        }

        updateEditor { it.copy(isSaving = true, validationError = null) }
        viewModelScope.launch {
            try {
                val editingId = editor.editingAquariumId
                val savedId = if (editingId == null) {
                    repository.createAquarium(draft)
                } else {
                    repository.updateAquarium(editingId, draft)
                    editingId
                }
                selectedAquariumIdFlow.value = savedId
                updateSuccess {
                    it.copy(
                        editorState = null,
                        selectedAquariumId = savedId,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: IllegalArgumentException) {
                updateEditor {
                    it.copy(
                        isSaving = false,
                        validationError = e.message ?: "Invalid aquarium values.",
                    )
                }
            } catch (_: Exception) {
                updateEditor {
                    it.copy(
                        isSaving = false,
                        validationError = "Could not save aquarium. Please try again.",
                    )
                }
            }
        }
    }

    fun requestDeleteAquarium(aquariumId: UUID) {
        updateSuccess { current ->
            val target = current.aquariumDetails.firstOrNull { it.id == aquariumId }
            current.copy(pendingDeleteAquarium = target)
        }
    }

    fun dismissDeleteConfirmation() {
        updateSuccess { it.copy(pendingDeleteAquarium = null) }
    }

    fun confirmDeleteAquarium() {
        val current = mutableState.value as? AquariumListState.Success ?: return
        val target = current.pendingDeleteAquarium ?: return
        viewModelScope.launch {
            try {
                repository.deleteAquarium(target.id)
                if (selectedAquariumIdFlow.value == target.id) {
                    selectedAquariumIdFlow.value = null
                }
                updateSuccess {
                    it.copy(
                        pendingDeleteAquarium = null,
                        selectedAquariumId = it.selectedAquariumId?.takeUnless { sel -> sel == target.id },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                updateSuccess { it.copy(pendingDeleteAquarium = null) }
            }
        }
    }

    fun openMeasurementEditor() {
        val current = mutableState.value as? AquariumListState.Success ?: return
        if (current.activeAquariumId == null) return
        updateSuccess { it.copy(measurementEditorState = MeasurementEditorState()) }
    }

    fun closeMeasurementEditor() {
        updateSuccess { it.copy(measurementEditorState = null) }
    }

    fun updatePhInput(value: String) {
        updateMeasurementEditor { it.copy(phInput = value, validationError = null) }
    }

    fun updateKhDkhInput(value: String) {
        updateMeasurementEditor { it.copy(khDkhInput = value, validationError = null) }
    }

    fun updateGhDghInput(value: String) {
        updateMeasurementEditor { it.copy(ghDghInput = value, validationError = null) }
    }

    fun updateNo2MgLInput(value: String) {
        updateMeasurementEditor { it.copy(no2MgLInput = value, validationError = null) }
    }

    fun updateNo3MgLInput(value: String) {
        updateMeasurementEditor { it.copy(no3MgLInput = value, validationError = null) }
    }

    fun updateTdsPpmInput(value: String) {
        updateMeasurementEditor { it.copy(tdsPpmInput = value, validationError = null) }
    }

    fun updateTemperatureCInput(value: String) {
        updateMeasurementEditor { it.copy(temperatureCInput = value, validationError = null) }
    }

    fun saveMeasurement() {
        val current = mutableState.value as? AquariumListState.Success ?: return
        val aquariumId = current.activeAquariumId ?: return
        val editor = current.measurementEditorState ?: return
        if (editor.isSaving) return

        val draft = try {
            MeasurementDraft(
                ph = parseOptionalDecimal(editor.phInput, "pH"),
                khDkh = parseOptionalDecimal(editor.khDkhInput, "KH (dKH)"),
                ghDgh = parseOptionalDecimal(editor.ghDghInput, "GH (dGH)"),
                no2MgL = parseOptionalDecimal(editor.no2MgLInput, "NO2 (mg/L)"),
                no3MgL = parseOptionalDecimal(editor.no3MgLInput, "NO3 (mg/L)"),
                tdsPpm = parseOptionalDecimal(editor.tdsPpmInput, "TDS (ppm)"),
                temperatureC = parseOptionalDecimal(editor.temperatureCInput, "Temperature (°C)"),
            )
        } catch (e: IllegalArgumentException) {
            updateMeasurementEditor {
                it.copy(validationError = e.message ?: "Invalid water parameter reading.")
            }
            return
        }

        updateMeasurementEditor { it.copy(isSaving = true, validationError = null) }
        viewModelScope.launch {
            try {
                repository.recordMeasurement(aquariumId, draft)
                updateSuccess { it.copy(measurementEditorState = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: IllegalArgumentException) {
                updateMeasurementEditor {
                    it.copy(
                        isSaving = false,
                        validationError = e.message ?: "Invalid water parameter reading.",
                    )
                }
            } catch (_: Exception) {
                updateMeasurementEditor {
                    it.copy(
                        isSaving = false,
                        validationError = "Could not save water test. Please try again.",
                    )
                }
            }
        }
    }

    fun deleteMeasurement(measurementId: UUID) {
        viewModelScope.launch {
            try {
                repository.deleteMeasurement(measurementId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep current list on failure
            }
        }
    }

    fun openCreateMaintenanceEditor() {
        val current = mutableState.value as? AquariumListState.Success ?: return
        if (current.activeAquariumId == null) return
        val defaultType = current.maintenanceTypeCatalog.firstOrNull()?.code ?: "WATER_CHANGE"
        updateSuccess {
            it.copy(
                maintenanceEditorState = MaintenanceEditorState(
                    selectedTypeCode = defaultType,
                )
            )
        }
    }

    fun openEditMaintenanceEditor(eventId: UUID) {
        updateSuccess { current ->
            val target = current.maintenanceEvents.firstOrNull { it.id == eventId }
                ?: return@updateSuccess current
            val activeAquarium = current.activeAquarium
            val changedText = target.changedLitres?.stripTrailingZeros()?.toPlainString().orEmpty()
            val warning = evaluateMaintenanceVolumeWarning(
                changedLitres = target.changedLitres,
                netLitres = activeAquarium?.netLitres,
                grossLitres = activeAquarium?.grossLitres,
            )
            current.copy(
                maintenanceEditorState = MaintenanceEditorState(
                    editingMaintenanceId = target.id,
                    selectedTypeCode = target.type,
                    changedLitresInput = changedText,
                    notesInput = target.notes.orEmpty(),
                    volumeWarning = warning,
                )
            )
        }
    }

    fun closeMaintenanceEditor() {
        updateSuccess { it.copy(maintenanceEditorState = null) }
    }

    fun selectMaintenanceType(typeCode: String) {
        updateMaintenanceEditor { it.copy(selectedTypeCode = typeCode, validationError = null) }
    }

    fun updateChangedLitresInput(value: String) {
        updateSuccess { current ->
            val editor = current.maintenanceEditorState ?: return@updateSuccess current
            val parsedLitres = try {
                parseOptionalDecimal(value, "Changed litres")
            } catch (_: IllegalArgumentException) {
                null
            }
            val activeAquarium = current.activeAquarium
            val warning = evaluateMaintenanceVolumeWarning(
                changedLitres = parsedLitres,
                netLitres = activeAquarium?.netLitres,
                grossLitres = activeAquarium?.grossLitres,
            )
            current.copy(
                maintenanceEditorState = editor.copy(
                    changedLitresInput = value,
                    volumeWarning = warning,
                    validationError = null,
                )
            )
        }
    }

    fun updateMaintenanceNotesInput(value: String) {
        updateMaintenanceEditor { it.copy(notesInput = value, validationError = null) }
    }

    fun saveMaintenanceEvent() {
        val current = mutableState.value as? AquariumListState.Success ?: return
        val aquariumId = current.activeAquariumId ?: return
        val editor = current.maintenanceEditorState ?: return
        if (editor.isSaving) return

        val trimmedType = editor.selectedTypeCode.trim()
        if (trimmedType.isEmpty()) {
            updateMaintenanceEditor { it.copy(validationError = "Maintenance type cannot be blank.") }
            return
        }
        if (current.maintenanceTypeCatalog.isNotEmpty() &&
            current.maintenanceTypeCatalog.none { it.code == trimmedType }
        ) {
            updateMaintenanceEditor { it.copy(validationError = "Unknown maintenance type: $trimmedType") }
            return
        }

        val changedLitres = try {
            parseOptionalDecimal(editor.changedLitresInput, "Changed litres")
        } catch (e: IllegalArgumentException) {
            updateMaintenanceEditor { it.copy(validationError = e.message) }
            return
        }

        val draft = try {
            MaintenanceDraft(
                type = trimmedType,
                changedLitres = changedLitres,
                notes = editor.notesInput.trim().takeIf { it.isNotEmpty() },
            )
        } catch (e: IllegalArgumentException) {
            updateMaintenanceEditor {
                it.copy(validationError = e.message ?: "Invalid maintenance event values.")
            }
            return
        }

        updateMaintenanceEditor { it.copy(isSaving = true, validationError = null) }
        viewModelScope.launch {
            try {
                val editingId = editor.editingMaintenanceId
                if (editingId == null) {
                    repository.recordMaintenanceEvent(aquariumId, draft)
                } else {
                    repository.updateMaintenanceEvent(editingId, aquariumId, draft)
                }
                updateSuccess { it.copy(maintenanceEditorState = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: IllegalArgumentException) {
                updateMaintenanceEditor {
                    it.copy(
                        isSaving = false,
                        validationError = e.message ?: "Invalid maintenance event values.",
                    )
                }
            } catch (_: Exception) {
                updateMaintenanceEditor {
                    it.copy(
                        isSaving = false,
                        validationError = "Could not save maintenance event. Please try again.",
                    )
                }
            }
        }
    }

    fun deleteMaintenanceEvent(eventId: UUID) {
        viewModelScope.launch {
            try {
                repository.deleteMaintenanceEvent(eventId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep current list on failure
            }
        }
    }

    fun selectLivestockSection(section: LivestockSection) {
        updateSuccess { it.copy(livestockSection = section, actionError = null) }
    }

    fun setSpeciesKindFilter(kind: SpeciesKind?) {
        updateSuccess { it.copy(selectedSpeciesKindFilter = kind) }
    }

    fun dismissActionError() {
        updateSuccess { it.copy(actionError = null) }
    }

    fun openCreateSpeciesEditor(defaultKind: SpeciesKind = SpeciesKind.FAUNA) {
        val current = mutableState.value as? AquariumListState.Success ?: return
        val initialKind = current.selectedSpeciesKindFilter ?: defaultKind
        updateSuccess {
            it.copy(
                speciesEditorState = SpeciesEditorState(
                    selectedKind = initialKind,
                ),
                actionError = null,
            )
        }
    }

    fun openEditSpeciesEditor(speciesId: UUID) {
        updateSuccess { current ->
            val target = current.speciesCatalog.firstOrNull { it.id == speciesId }
                ?: return@updateSuccess current
            current.copy(
                speciesEditorState = SpeciesEditorState(
                    editingSpeciesId = target.id,
                    selectedKind = target.kind,
                    commonNameInput = target.commonName,
                    scientificNameInput = target.scientificName.orEmpty(),
                ),
                actionError = null,
            )
        }
    }

    fun closeSpeciesEditor() {
        updateSuccess { it.copy(speciesEditorState = null) }
    }

    fun selectSpeciesEditorKind(kind: SpeciesKind) {
        updateSpeciesEditor { it.copy(selectedKind = kind, validationError = null) }
    }

    fun updateSpeciesCommonNameInput(value: String) {
        updateSpeciesEditor { it.copy(commonNameInput = value, validationError = null) }
    }

    fun updateSpeciesScientificNameInput(value: String) {
        updateSpeciesEditor { it.copy(scientificNameInput = value, validationError = null) }
    }

    fun saveSpecies() {
        val current = mutableState.value as? AquariumListState.Success ?: return
        val editor = current.speciesEditorState ?: return
        if (editor.isSaving) return

        val trimmedCommon = editor.commonNameInput.trim()
        if (trimmedCommon.isEmpty()) {
            updateSpeciesEditor { it.copy(validationError = "Species common name cannot be blank.") }
            return
        }
        val trimmedScientific = editor.scientificNameInput.trim().takeIf { it.isNotEmpty() }

        val draft = try {
            com.pietrondo.aquariummanager.domain.validateUniqueSpeciesCatalog(
                existing = current.speciesCatalog,
                kind = editor.selectedKind,
                commonName = trimmedCommon,
                scientificName = trimmedScientific,
                excludingId = editor.editingSpeciesId,
            )
            SpeciesDraft(
                kind = editor.selectedKind,
                commonName = trimmedCommon,
                scientificName = trimmedScientific,
            )
        } catch (e: IllegalArgumentException) {
            updateSpeciesEditor {
                it.copy(validationError = e.message ?: "Invalid species details.")
            }
            return
        }

        updateSpeciesEditor { it.copy(isSaving = true, validationError = null) }
        viewModelScope.launch {
            try {
                val editingId = editor.editingSpeciesId
                if (editingId == null) {
                    repository.createSpecies(draft)
                } else {
                    repository.updateSpecies(editingId, draft)
                }
                updateSuccess { it.copy(speciesEditorState = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: IllegalArgumentException) {
                updateSpeciesEditor {
                    it.copy(
                        isSaving = false,
                        validationError = e.message ?: "Invalid species details.",
                    )
                }
            } catch (_: Exception) {
                updateSpeciesEditor {
                    it.copy(
                        isSaving = false,
                        validationError = "Could not save species. Please try again.",
                    )
                }
            }
        }
    }

    fun deleteSpecies(speciesId: UUID) {
        viewModelScope.launch {
            try {
                repository.deleteSpecies(speciesId)
                updateSuccess { it.copy(actionError = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: IllegalArgumentException) {
                updateSuccess {
                    it.copy(actionError = e.message ?: "Cannot delete species referenced by stock history.")
                }
            } catch (_: Exception) {
                updateSuccess {
                    it.copy(actionError = "Could not delete species. Please try again.")
                }
            }
        }
    }

    fun openCreateStockEditor() {
        val current = mutableState.value as? AquariumListState.Success ?: return
        if (current.activeAquariumId == null) return
        val defaultSpeciesId = current.filteredSpeciesCatalog.firstOrNull()?.id
            ?: current.speciesCatalog.firstOrNull()?.id
        updateSuccess {
            it.copy(
                stockEditorState = StockEditorState(
                    selectedSpeciesId = defaultSpeciesId,
                    introducedDateInput = LocalDate.now(ZoneOffset.UTC).toString(),
                ),
                actionError = null,
            )
        }
    }

    fun openEditStockEditor(stockId: UUID) {
        updateSuccess { current ->
            val target = current.stockEntries.firstOrNull { it.id == stockId }
                ?: return@updateSuccess current
            current.copy(
                stockEditorState = StockEditorState(
                    editingStockId = target.id,
                    selectedSpeciesId = target.species.id,
                    quantityInput = target.quantity?.stripTrailingZeros()?.toPlainString().orEmpty(),
                    introducedDateInput = target.introducedAt.atZone(ZoneOffset.UTC).toLocalDate().toString(),
                    exitedDateInput = target.exitedAt?.atZone(ZoneOffset.UTC)?.toLocalDate()?.toString().orEmpty(),
                    notesInput = target.notes.orEmpty(),
                ),
                actionError = null,
            )
        }
    }

    fun closeStockEditor() {
        updateSuccess { it.copy(stockEditorState = null) }
    }

    fun selectStockSpecies(speciesId: UUID) {
        updateStockEditor { it.copy(selectedSpeciesId = speciesId, validationError = null) }
    }

    fun updateStockQuantityInput(value: String) {
        updateStockEditor { it.copy(quantityInput = value, validationError = null) }
    }

    fun updateStockIntroducedDateInput(value: String) {
        updateStockEditor { it.copy(introducedDateInput = value, validationError = null) }
    }

    fun updateStockExitedDateInput(value: String) {
        updateStockEditor { it.copy(exitedDateInput = value, validationError = null) }
    }

    fun updateStockNotesInput(value: String) {
        updateStockEditor { it.copy(notesInput = value, validationError = null) }
    }

    fun saveStockEntry() {
        val current = mutableState.value as? AquariumListState.Success ?: return
        val aquariumId = current.activeAquariumId ?: return
        val editor = current.stockEditorState ?: return
        if (editor.isSaving) return

        val speciesId = editor.selectedSpeciesId
        if (speciesId == null) {
            updateStockEditor { it.copy(validationError = "Please select a fauna or flora species.") }
            return
        }

        val quantity = try {
            parseOptionalDecimal(editor.quantityInput, "Quantity")
        } catch (e: IllegalArgumentException) {
            updateStockEditor { it.copy(validationError = e.message) }
            return
        }

        val introducedAt = try {
            parseOptionalInstantInput(editor.introducedDateInput, "Introduction date")
        } catch (e: IllegalArgumentException) {
            updateStockEditor { it.copy(validationError = e.message) }
            return
        }

        val exitedAt = try {
            parseOptionalInstantInput(editor.exitedDateInput, "Exit date")
        } catch (e: IllegalArgumentException) {
            updateStockEditor { it.copy(validationError = e.message) }
            return
        }

        val draft = try {
            StockEntryDraft(
                speciesId = speciesId,
                quantity = quantity,
                introducedAt = introducedAt,
                exitedAt = exitedAt,
                notes = editor.notesInput.trim().takeIf { it.isNotEmpty() },
            )
        } catch (e: IllegalArgumentException) {
            updateStockEditor {
                it.copy(validationError = e.message ?: "Invalid stock entry values.")
            }
            return
        }

        updateStockEditor { it.copy(isSaving = true, validationError = null) }
        viewModelScope.launch {
            try {
                val editingId = editor.editingStockId
                if (editingId == null) {
                    repository.createStockEntry(aquariumId, draft)
                } else {
                    repository.updateStockEntry(editingId, aquariumId, draft)
                }
                updateSuccess { it.copy(stockEditorState = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: IllegalArgumentException) {
                updateStockEditor {
                    it.copy(
                        isSaving = false,
                        validationError = e.message ?: "Invalid stock entry values.",
                    )
                }
            } catch (_: Exception) {
                updateStockEditor {
                    it.copy(
                        isSaving = false,
                        validationError = "Could not save stock entry. Please try again.",
                    )
                }
            }
        }
    }

    fun markStockExited(stockId: UUID) {
        viewModelScope.launch {
            try {
                repository.markStockExited(stockId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: IllegalArgumentException) {
                updateSuccess { it.copy(actionError = e.message) }
            } catch (_: Exception) {
                // Keep current list on failure
            }
        }
    }

    fun deleteStockEntry(stockId: UUID) {
        viewModelScope.launch {
            try {
                repository.deleteStockEntry(stockId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep current list on failure
            }
        }
    }

    fun openCreateProductEditor() {
        updateSuccess {
            it.copy(
                productEditorState = ProductEditorState(),
                actionError = null,
            )
        }
    }

    fun openEditProductEditor(productId: UUID) {
        updateSuccess { current ->
            val target = current.products.firstOrNull { it.id == productId }
                ?: return@updateSuccess current
            current.copy(
                productEditorState = ProductEditorState(
                    editingProductId = target.id,
                    nameInput = target.name,
                ),
                actionError = null,
            )
        }
    }

    fun closeProductEditor() {
        updateSuccess { it.copy(productEditorState = null) }
    }

    fun updateProductNameInput(value: String) {
        updateProductEditor { it.copy(nameInput = value, validationError = null) }
    }

    fun saveProduct() {
        val current = mutableState.value as? AquariumListState.Success ?: return
        val editor = current.productEditorState ?: return
        if (editor.isSaving) return

        val trimmed = editor.nameInput.trim()
        if (trimmed.isEmpty()) {
            updateProductEditor { it.copy(validationError = "Product name cannot be blank.") }
            return
        }
        val duplicate = current.products.any {
            it.id != editor.editingProductId && it.name.equals(trimmed, ignoreCase = true)
        }
        if (duplicate) {
            updateProductEditor { it.copy(validationError = "Product '$trimmed' already exists.") }
            return
        }

        updateProductEditor { it.copy(isSaving = true, validationError = null) }
        viewModelScope.launch {
            try {
                val editingId = editor.editingProductId
                if (editingId == null) {
                    repository.createProduct(trimmed)
                } else {
                    repository.updateProduct(editingId, trimmed)
                }
                updateSuccess { it.copy(productEditorState = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: IllegalArgumentException) {
                updateProductEditor {
                    it.copy(
                        isSaving = false,
                        validationError = e.message ?: "Invalid product name.",
                    )
                }
            } catch (_: Exception) {
                updateProductEditor {
                    it.copy(
                        isSaving = false,
                        validationError = "Could not save product. Please try again.",
                    )
                }
            }
        }
    }

    fun deleteProduct(productId: UUID) {
        viewModelScope.launch {
            try {
                repository.deleteProduct(productId)
                updateSuccess { it.copy(actionError = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: IllegalArgumentException) {
                updateSuccess {
                    it.copy(actionError = e.message ?: "Cannot delete product referenced by dosing plans.")
                }
            } catch (_: Exception) {
                updateSuccess {
                    it.copy(actionError = "Could not delete product. Please try again.")
                }
            }
        }
    }

    fun openCreateDosingPlanEditor() {
        val current = mutableState.value as? AquariumListState.Success ?: return
        if (current.activeAquariumId == null) return
        val defaultProductId = current.products.firstOrNull()?.id
        val defaultUnit = current.doseUnitCatalog.firstOrNull()?.code ?: "mL"
        val defaultZone = try {
            ZoneId.systemDefault().id
        } catch (_: Exception) {
            "UTC"
        }
        updateSuccess {
            it.copy(
                dosingPlanEditorState = DosingPlanEditorState(
                    selectedProductId = defaultProductId,
                    selectedUnitCode = defaultUnit,
                    startDateInput = LocalDate.now(ZoneOffset.UTC).toString(),
                    timeZoneInput = defaultZone,
                ),
                actionError = null,
            )
        }
    }

    fun openEditDosingPlanEditor(planId: UUID) {
        updateSuccess { current ->
            val target = current.dosingPlans.firstOrNull { it.id == planId }
                ?: return@updateSuccess current
            current.copy(
                dosingPlanEditorState = DosingPlanEditorState(
                    editingPlanId = target.id,
                    selectedProductId = target.product.id,
                    doseInput = target.dose.stripTrailingZeros().toPlainString(),
                    selectedUnitCode = target.unit,
                    frequency = target.frequency,
                    selectedWeekdays = target.weekdays,
                    startDateInput = target.startDate.toString(),
                    endDateInput = target.endDate?.toString().orEmpty(),
                    timeZoneInput = target.timeZone.id,
                ),
                actionError = null,
            )
        }
    }

    fun closeDosingPlanEditor() {
        updateSuccess { it.copy(dosingPlanEditorState = null) }
    }

    fun selectDosingPlanProduct(productId: UUID) {
        updateDosingPlanEditor { it.copy(selectedProductId = productId, validationError = null) }
    }

    fun updateDosingPlanDoseInput(value: String) {
        updateDosingPlanEditor { it.copy(doseInput = value, validationError = null) }
    }

    fun selectDosingPlanUnit(unitCode: String) {
        updateDosingPlanEditor { it.copy(selectedUnitCode = unitCode, validationError = null) }
    }

    fun selectDosingPlanFrequency(frequency: DosingFrequency) {
        updateDosingPlanEditor { it.copy(frequency = frequency, validationError = null) }
    }

    fun toggleDosingPlanWeekday(day: DayOfWeek) {
        updateDosingPlanEditor { editor ->
            val updated = if (day in editor.selectedWeekdays) {
                editor.selectedWeekdays - day
            } else {
                editor.selectedWeekdays + day
            }
            editor.copy(selectedWeekdays = updated, validationError = null)
        }
    }

    fun updateDosingPlanStartDateInput(value: String) {
        updateDosingPlanEditor { it.copy(startDateInput = value, validationError = null) }
    }

    fun updateDosingPlanEndDateInput(value: String) {
        updateDosingPlanEditor { it.copy(endDateInput = value, validationError = null) }
    }

    fun updateDosingPlanTimeZoneInput(value: String) {
        updateDosingPlanEditor { it.copy(timeZoneInput = value, validationError = null) }
    }

    fun saveDosingPlan() {
        val current = mutableState.value as? AquariumListState.Success ?: return
        val aquariumId = current.activeAquariumId ?: return
        val editor = current.dosingPlanEditorState ?: return
        if (editor.isSaving) return

        val productId = editor.selectedProductId
        if (productId == null) {
            updateDosingPlanEditor { it.copy(validationError = "Please select or create a product first.") }
            return
        }

        val dose = try {
            parseOptionalDecimal(editor.doseInput, "Dose")
                ?: throw IllegalArgumentException("Dose cannot be blank")
        } catch (e: IllegalArgumentException) {
            updateDosingPlanEditor { it.copy(validationError = e.message) }
            return
        }

        val trimmedUnit = editor.selectedUnitCode.trim()
        if (trimmedUnit.isEmpty()) {
            updateDosingPlanEditor { it.copy(validationError = "Dose unit cannot be blank.") }
            return
        }
        if (current.doseUnitCatalog.isNotEmpty() && current.doseUnitCatalog.none { it.code == trimmedUnit }) {
            updateDosingPlanEditor { it.copy(validationError = "Unknown dose unit: $trimmedUnit") }
            return
        }

        val startDate = try {
            parseRequiredLocalDate(editor.startDateInput, "Start date")
        } catch (e: IllegalArgumentException) {
            updateDosingPlanEditor { it.copy(validationError = e.message) }
            return
        }

        val endDate = try {
            parseOptionalLocalDate(editor.endDateInput, "End date")
        } catch (e: IllegalArgumentException) {
            updateDosingPlanEditor { it.copy(validationError = e.message) }
            return
        }

        val timeZone = try {
            val rawZone = editor.timeZoneInput.trim()
            if (rawZone.isEmpty()) throw IllegalArgumentException("IANA time zone cannot be blank")
            ZoneId.of(rawZone)
        } catch (e: Exception) {
            updateDosingPlanEditor {
                it.copy(validationError = "Invalid IANA time zone: '${editor.timeZoneInput.trim()}'")
            }
            return
        }

        val draft = try {
            DosingPlanDraft(
                productId = productId,
                dose = dose,
                unit = trimmedUnit,
                frequency = editor.frequency,
                weekdays = if (editor.frequency == DosingFrequency.DAILY) emptySet() else editor.selectedWeekdays,
                startDate = startDate,
                endDate = endDate,
                timeZone = timeZone,
            )
        } catch (e: IllegalArgumentException) {
            updateDosingPlanEditor {
                it.copy(validationError = e.message ?: "Invalid dosing plan values.")
            }
            return
        }

        updateDosingPlanEditor { it.copy(isSaving = true, validationError = null) }
        viewModelScope.launch {
            try {
                val editingId = editor.editingPlanId
                if (editingId == null) {
                    repository.createDosingPlan(aquariumId, draft)
                } else {
                    repository.updateDosingPlan(editingId, aquariumId, draft)
                }
                updateSuccess { it.copy(dosingPlanEditorState = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: IllegalArgumentException) {
                updateDosingPlanEditor {
                    it.copy(
                        isSaving = false,
                        validationError = e.message ?: "Invalid dosing plan values.",
                    )
                }
            } catch (_: Exception) {
                updateDosingPlanEditor {
                    it.copy(
                        isSaving = false,
                        validationError = "Could not save dosing plan. Please try again.",
                    )
                }
            }
        }
    }

    fun deleteDosingPlan(planId: UUID) {
        viewModelScope.launch {
            try {
                repository.deleteDosingPlan(planId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep current list on failure
            }
        }
    }

    fun logDoseFromPlanNow(planId: UUID) {
        val current = mutableState.value as? AquariumListState.Success ?: return
        val aquariumId = current.activeAquariumId ?: return
        viewModelScope.launch {
            try {
                repository.recordDosingFromPlan(aquariumId = aquariumId, planId = planId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: IllegalArgumentException) {
                updateSuccess { it.copy(actionError = e.message) }
            } catch (_: Exception) {
                updateSuccess { it.copy(actionError = "Could not log dose from plan.") }
            }
        }
    }

    fun openAdHocDosingEditor() {
        val current = mutableState.value as? AquariumListState.Success ?: return
        if (current.activeAquariumId == null) return
        val defaultUnit = current.doseUnitCatalog.firstOrNull()?.code ?: "mL"
        val defaultProductName = current.products.firstOrNull()?.name.orEmpty()
        updateSuccess {
            it.copy(
                adHocDosingEditorState = AdHocDosingEditorState(
                    productNameInput = defaultProductName,
                    selectedUnitCode = defaultUnit,
                ),
                actionError = null,
            )
        }
    }

    fun closeAdHocDosingEditor() {
        updateSuccess { it.copy(adHocDosingEditorState = null) }
    }

    fun updateAdHocProductNameInput(value: String) {
        updateAdHocDosingEditor { it.copy(productNameInput = value, validationError = null) }
    }

    fun updateAdHocDoseInput(value: String) {
        updateAdHocDosingEditor { it.copy(doseInput = value, validationError = null) }
    }

    fun selectAdHocDoseUnit(unitCode: String) {
        updateAdHocDosingEditor { it.copy(selectedUnitCode = unitCode, validationError = null) }
    }

    fun saveAdHocDosingEvent() {
        val current = mutableState.value as? AquariumListState.Success ?: return
        val aquariumId = current.activeAquariumId ?: return
        val editor = current.adHocDosingEditorState ?: return
        if (editor.isSaving) return

        val trimmedProduct = editor.productNameInput.trim()
        if (trimmedProduct.isEmpty()) {
            updateAdHocDosingEditor { it.copy(validationError = "Product name cannot be blank.") }
            return
        }

        val dose = try {
            parseOptionalDecimal(editor.doseInput, "Dose")
                ?: throw IllegalArgumentException("Dose cannot be blank")
        } catch (e: IllegalArgumentException) {
            updateAdHocDosingEditor { it.copy(validationError = e.message) }
            return
        }

        val trimmedUnit = editor.selectedUnitCode.trim()
        if (trimmedUnit.isEmpty()) {
            updateAdHocDosingEditor { it.copy(validationError = "Dose unit cannot be blank.") }
            return
        }
        if (current.doseUnitCatalog.isNotEmpty() && current.doseUnitCatalog.none { it.code == trimmedUnit }) {
            updateAdHocDosingEditor { it.copy(validationError = "Unknown dose unit: $trimmedUnit") }
            return
        }

        val draft = try {
            DosingEventDraft(
                planId = null,
                productNameSnapshot = trimmedProduct,
                dose = dose,
                unit = trimmedUnit,
            )
        } catch (e: IllegalArgumentException) {
            updateAdHocDosingEditor {
                it.copy(validationError = e.message ?: "Invalid dosing values.")
            }
            return
        }

        updateAdHocDosingEditor { it.copy(isSaving = true, validationError = null) }
        viewModelScope.launch {
            try {
                repository.recordAdHocDosingEvent(aquariumId, draft)
                updateSuccess { it.copy(adHocDosingEditorState = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: IllegalArgumentException) {
                updateAdHocDosingEditor {
                    it.copy(
                        isSaving = false,
                        validationError = e.message ?: "Invalid dosing values.",
                    )
                }
            } catch (_: Exception) {
                updateAdHocDosingEditor {
                    it.copy(
                        isSaving = false,
                        validationError = "Could not record dosing event. Please try again.",
                    )
                }
            }
        }
    }

    fun deleteDosingEvent(eventId: UUID) {
        viewModelScope.launch {
            try {
                repository.deleteDosingEvent(eventId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep current list on failure
            }
        }
    }

    fun exportBackupToUri(destinationUri: String) {
        val current = mutableState.value as? AquariumListState.Success ?: return
        if (current.isBackupOperationInProgress) return
        updateSuccess {
            it.copy(
                isBackupOperationInProgress = true,
                actionError = null,
                backupStatusMessage = null,
            )
        }
        viewModelScope.launch {
            try {
                val result = repository.exportBackupToUri(destinationUri)
                val shaPrefix = result.manifest.librarySha256.take(8)
                updateSuccess {
                    it.copy(
                        isBackupOperationInProgress = false,
                        backupStatusMessage =
                            "Verified backup exported (${result.manifest.counts.totalRows} records, SHA-256 $shaPrefix…)",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                updateSuccess {
                    it.copy(
                        isBackupOperationInProgress = false,
                        actionError = e.message ?: "Could not export backup archive.",
                    )
                }
            }
        }
    }

    fun inspectBackupFromUri(sourceUri: String) {
        val current = mutableState.value as? AquariumListState.Success ?: return
        if (current.isBackupOperationInProgress) return
        updateSuccess {
            it.copy(
                isBackupOperationInProgress = true,
                actionError = null,
                restorePreview = null,
            )
        }
        viewModelScope.launch {
            try {
                val preview = repository.inspectBackupUri(sourceUri)
                updateSuccess {
                    it.copy(
                        isBackupOperationInProgress = false,
                        restorePreview = preview,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                updateSuccess {
                    it.copy(
                        isBackupOperationInProgress = false,
                        restorePreview = null,
                        actionError = e.message ?: "Selected file is not a valid Aquarium Manager backup.",
                    )
                }
            }
        }
    }

    fun dismissRestorePreview() {
        updateSuccess { it.copy(restorePreview = null) }
    }

    fun confirmRestoreLibrary() {
        val current = mutableState.value as? AquariumListState.Success ?: return
        if (current.isBackupOperationInProgress) return
        val preview = current.restorePreview ?: return
        val sourceUri = preview.sourceUri ?: return

        updateSuccess {
            it.copy(
                isBackupOperationInProgress = true,
                actionError = null,
            )
        }
        viewModelScope.launch {
            try {
                val restored = repository.restoreBackupFromUri(sourceUri = sourceUri, confirmReplace = true)
                selectedAquariumIdFlow.value = null
                updateSuccess {
                    it.copy(
                        isBackupOperationInProgress = false,
                        restorePreview = null,
                        selectedAquariumId = null,
                        backupStatusMessage =
                            "Library restored (${restored.totalRows} records). Safety snapshot saved.",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                updateSuccess {
                    it.copy(
                        isBackupOperationInProgress = false,
                        restorePreview = null,
                        actionError = e.message ?: "Restore failed. Existing library was kept.",
                    )
                }
            }
        }
    }

    fun toggleAutoBackupEnabled(enabled: Boolean) {
        val current = mutableState.value as? AquariumListState.Success ?: return
        val settings = current.backupScheduleSettings
        viewModelScope.launch {
            try {
                val updated = repository.updateAutoBackupSettings(
                    enabled = enabled,
                    treeUri = settings.treeUri,
                    retentionCount = settings.retentionCount,
                )
                updateSuccess { it.copy(backupScheduleSettings = updated, actionError = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                updateSuccess { it.copy(actionError = e.message ?: "Could not update automatic backup settings.") }
            }
        }
    }

    fun setAutoBackupTreeUri(treeUri: String?) {
        val current = mutableState.value as? AquariumListState.Success ?: return
        val settings = current.backupScheduleSettings
        val enableOnSelect = if (!treeUri.isNullOrBlank()) true else settings.autoBackupEnabled
        viewModelScope.launch {
            try {
                val updated = repository.updateAutoBackupSettings(
                    enabled = enableOnSelect,
                    treeUri = treeUri,
                    retentionCount = settings.retentionCount,
                )
                updateSuccess { it.copy(backupScheduleSettings = updated, actionError = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                updateSuccess { it.copy(actionError = e.message ?: "Could not configure automatic backup folder.") }
            }
        }
    }

    fun runAutomaticBackupNow() {
        val current = mutableState.value as? AquariumListState.Success ?: return
        if (current.isBackupOperationInProgress) return
        updateSuccess { it.copy(isBackupOperationInProgress = true, actionError = null) }
        viewModelScope.launch {
            try {
                val updated = repository.runAutomaticBackupNow()
                updateSuccess {
                    it.copy(
                        isBackupOperationInProgress = false,
                        backupScheduleSettings = updated,
                        backupStatusMessage = updated.lastBackupStatus,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                updateSuccess {
                    it.copy(
                        isBackupOperationInProgress = false,
                        actionError = e.message ?: "Automatic backup failed.",
                    )
                }
            }
        }
    }

    fun dismissBackupStatusMessage() {
        updateSuccess { it.copy(backupStatusMessage = null) }
    }

    fun openLicenseAndNoticesDialog() {
        updateSuccess { it.copy(showLicenseAndNoticesDialog = true) }
    }

    fun closeLicenseAndNoticesDialog() {
        updateSuccess { it.copy(showLicenseAndNoticesDialog = false) }
    }

    private fun parseOptionalDecimal(rawInput: String, fieldName: String): BigDecimal? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null
        val normalized = trimmed.replace(',', '.')
        return try {
            BigDecimal(normalized)
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("$fieldName must be a valid number", e)
        }
    }

    private fun parseOptionalInstantInput(rawInput: String, fieldName: String): Instant? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null
        return try {
            if (trimmed.contains('T')) {
                Instant.parse(trimmed)
            } else {
                LocalDate.parse(trimmed).atStartOfDay(ZoneOffset.UTC).toInstant()
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("$fieldName must use YYYY-MM-DD format", e)
        }
    }

    private fun parseRequiredLocalDate(rawInput: String, fieldName: String): LocalDate {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("$fieldName cannot be blank")
        }
        return try {
            LocalDate.parse(trimmed)
        } catch (e: Exception) {
            throw IllegalArgumentException("$fieldName must use YYYY-MM-DD format", e)
        }
    }

    private fun parseOptionalLocalDate(rawInput: String, fieldName: String): LocalDate? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null
        return try {
            LocalDate.parse(trimmed)
        } catch (e: Exception) {
            throw IllegalArgumentException("$fieldName must use YYYY-MM-DD format", e)
        }
    }

    private inline fun updateSuccess(
        transform: (AquariumListState.Success) -> AquariumListState.Success,
    ) {
        mutableState.update { current ->
            if (current is AquariumListState.Success) transform(current) else current
        }
    }

    private inline fun updateEditor(
        transform: (AquariumEditorState) -> AquariumEditorState,
    ) {
        updateSuccess { current ->
            val editor = current.editorState ?: return@updateSuccess current
            current.copy(editorState = transform(editor))
        }
    }

    private inline fun updateMeasurementEditor(
        transform: (MeasurementEditorState) -> MeasurementEditorState,
    ) {
        updateSuccess { current ->
            val editor = current.measurementEditorState ?: return@updateSuccess current
            current.copy(measurementEditorState = transform(editor))
        }
    }

    private inline fun updateMaintenanceEditor(
        transform: (MaintenanceEditorState) -> MaintenanceEditorState,
    ) {
        updateSuccess { current ->
            val editor = current.maintenanceEditorState ?: return@updateSuccess current
            current.copy(maintenanceEditorState = transform(editor))
        }
    }

    private inline fun updateSpeciesEditor(
        transform: (SpeciesEditorState) -> SpeciesEditorState,
    ) {
        updateSuccess { current ->
            val editor = current.speciesEditorState ?: return@updateSuccess current
            current.copy(speciesEditorState = transform(editor))
        }
    }

    private inline fun updateStockEditor(
        transform: (StockEditorState) -> StockEditorState,
    ) {
        updateSuccess { current ->
            val editor = current.stockEditorState ?: return@updateSuccess current
            current.copy(stockEditorState = transform(editor))
        }
    }

    private inline fun updateProductEditor(
        transform: (ProductEditorState) -> ProductEditorState,
    ) {
        updateSuccess { current ->
            val editor = current.productEditorState ?: return@updateSuccess current
            current.copy(productEditorState = transform(editor))
        }
    }

    private inline fun updateDosingPlanEditor(
        transform: (DosingPlanEditorState) -> DosingPlanEditorState,
    ) {
        updateSuccess { current ->
            val editor = current.dosingPlanEditorState ?: return@updateSuccess current
            current.copy(dosingPlanEditorState = transform(editor))
        }
    }

    private inline fun updateAdHocDosingEditor(
        transform: (AdHocDosingEditorState) -> AdHocDosingEditorState,
    ) {
        updateSuccess { current ->
            val editor = current.adHocDosingEditorState ?: return@updateSuccess current
            current.copy(adHocDosingEditorState = transform(editor))
        }
    }
}

private data class CatalogsSnapshot(
    val maintenanceTypeCatalog: List<MaintenanceTypeCatalogItem>,
    val speciesCatalog: List<SpeciesCatalogItem>,
    val products: List<ProductRecord>,
    val doseUnitCatalog: List<DoseUnitCatalogItem>,
    val backupScheduleSettings: com.pietrondo.aquariummanager.domain.BackupScheduleSettings,
)
