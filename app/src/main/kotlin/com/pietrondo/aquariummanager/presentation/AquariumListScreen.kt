package com.pietrondo.aquariummanager.presentation

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pietrondo.aquariummanager.R
import com.pietrondo.aquariummanager.domain.APACHE_LICENSE_AND_NOTICES_TEXT
import com.pietrondo.aquariummanager.domain.AquariumDetail
import com.pietrondo.aquariummanager.domain.AquariumProfile
import com.pietrondo.aquariummanager.domain.BIOLOGICAL_AND_DOSING_DISCLAIMER
import com.pietrondo.aquariummanager.domain.BackupRestorePreview
import com.pietrondo.aquariummanager.domain.DoseUnitCatalogItem
import com.pietrondo.aquariummanager.domain.DosingEventRecord
import com.pietrondo.aquariummanager.domain.DosingFrequency
import com.pietrondo.aquariummanager.domain.DosingPlanRecord
import com.pietrondo.aquariummanager.domain.MaintenanceRecord
import com.pietrondo.aquariummanager.domain.MaintenanceTypeCatalogItem
import com.pietrondo.aquariummanager.domain.MeasurementRecord
import com.pietrondo.aquariummanager.domain.OFFLINE_PRIVACY_SUMMARY
import com.pietrondo.aquariummanager.domain.ParameterTrendDirection
import com.pietrondo.aquariummanager.domain.ParameterTrendSummary
import com.pietrondo.aquariummanager.domain.ProductRecord
import com.pietrondo.aquariummanager.domain.SpeciesCatalogItem
import com.pietrondo.aquariummanager.domain.SpeciesKind
import com.pietrondo.aquariummanager.domain.StockEntryRecord
import com.pietrondo.aquariummanager.domain.UNENCRYPTED_BACKUP_DISCLOSURE
import com.pietrondo.aquariummanager.domain.WaterParameter
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

private val OceanicColorScheme = lightColorScheme(
    primary = Color(0xFF006778),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB2EBF2),
    onPrimaryContainer = Color(0xFF001F25),
    secondary = Color(0xFF2E6B5E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB4F1E1),
    onSecondaryContainer = Color(0xFF00201A),
    tertiary = Color(0xFF3F5F90),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD7E2FF),
    onTertiaryContainer = Color(0xFF001B3F),
    background = Color(0xFFF5FAFB),
    onBackground = Color(0xFF171D1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171D1E),
    surfaceVariant = Color(0xFFDBE4E6),
    onSurfaceVariant = Color(0xFF3F484A),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val UtcTimestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC)

@Composable
fun AquariumTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OceanicColorScheme,
        content = content,
    )
}

@Composable
fun AquariumListScreen(viewModel: AquariumListViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val successState = state as? AquariumListState.Success
    val context = LocalContext.current

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackupToUri(uri.toString())
        }
    }

    val inspectBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.inspectBackupFromUri(uri.toString())
        }
    }

    val selectBackupFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(treeUri, flags)
            } catch (_: Exception) {
                // Best-effort persistable URI permission grant
            }
            viewModel.setAutoBackupTreeUri(treeUri.toString())
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (successState != null) {
                NavigationBar {
                    NavigationBarItem(
                        selected = successState.selectedTab == AppTab.AQUARIUMS,
                        onClick = { viewModel.selectTab(AppTab.AQUARIUMS) },
                        icon = { PixelIcon(R.drawable.ic_aquariums) },
                        label = { Text(stringResource(R.string.nav_aquariums)) },
                    )
                    NavigationBarItem(
                        selected = successState.selectedTab == AppTab.TESTS,
                        onClick = { viewModel.selectTab(AppTab.TESTS) },
                        icon = { PixelIcon(R.drawable.ic_tests) },
                        label = { Text(stringResource(R.string.nav_tests)) },
                    )
                    NavigationBarItem(
                        selected = successState.selectedTab == AppTab.MAINTENANCE,
                        onClick = { viewModel.selectTab(AppTab.MAINTENANCE) },
                        icon = { PixelIcon(R.drawable.ic_maintenance) },
                        label = { Text(stringResource(R.string.nav_maintenance)) },
                    )
                    NavigationBarItem(
                        selected = successState.selectedTab == AppTab.LIVESTOCK,
                        onClick = { viewModel.selectTab(AppTab.LIVESTOCK) },
                        icon = { PixelIcon(R.drawable.ic_livestock) },
                        label = { Text(stringResource(R.string.nav_livestock)) },
                    )
                    NavigationBarItem(
                        selected = successState.selectedTab == AppTab.SETTINGS,
                        onClick = { viewModel.selectTab(AppTab.SETTINGS) },
                        icon = { PixelIcon(R.drawable.ic_settings) },
                        label = { Text(stringResource(R.string.nav_settings)) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (successState != null) {
                when (successState.selectedTab) {
                    AppTab.AQUARIUMS -> {
                        FloatingActionButton(
                            onClick = viewModel::openCreateEditor,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            Text(
                                text = "+ " + stringResource(R.string.add_aquarium),
                                modifier = Modifier.padding(horizontal = 16.dp),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    AppTab.TESTS -> {
                        if (successState.activeAquarium != null) {
                            FloatingActionButton(
                                onClick = viewModel::openMeasurementEditor,
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ) {
                                Text(
                                    text = "+ " + stringResource(R.string.log_water_test),
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    AppTab.MAINTENANCE -> {
                        if (successState.activeAquarium != null) {
                            FloatingActionButton(
                                onClick = viewModel::openCreateMaintenanceEditor,
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ) {
                                Text(
                                    text = "+ " + stringResource(R.string.log_maintenance),
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    AppTab.LIVESTOCK -> {
                        when (successState.livestockSection) {
                            LivestockSection.STOCK -> {
                                if (successState.activeAquarium != null) {
                                    FloatingActionButton(
                                        onClick = viewModel::openCreateStockEditor,
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ) {
                                        Text(
                                            text = "+ " + stringResource(R.string.add_stock_entry),
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }

                            LivestockSection.DOSING -> {
                                if (successState.activeAquarium != null) {
                                    FloatingActionButton(
                                        onClick = viewModel::openAdHocDosingEditor,
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ) {
                                        Text(
                                            text = "+ " + stringResource(R.string.log_adhoc_dose),
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }

                            LivestockSection.SPECIES -> {
                                FloatingActionButton(
                                    onClick = { viewModel.openCreateSpeciesEditor() },
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ) {
                                    Text(
                                        text = "+ " + stringResource(R.string.add_species),
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }

                    AppTab.SETTINGS -> Unit
                }
            }
        },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val current = state) {
                AquariumListState.Loading -> {
                    val loading = stringResource(R.string.aquariums_loading)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(Modifier.semantics { contentDescription = loading })
                        Text(loading, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                AquariumListState.Error -> {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.aquariums_error),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Button(onClick = viewModel::retry) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }

                is AquariumListState.Success -> {
                    current.actionError?.let { errorMessage ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = errorMessage,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = viewModel::dismissActionError) {
                                    Text(stringResource(R.string.dismiss))
                                }
                            }
                        }
                    }

                    current.backupStatusMessage?.let { statusMessage ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = statusMessage,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = viewModel::dismissBackupStatusMessage) {
                                    Text(stringResource(R.string.dismiss))
                                }
                            }
                        }
                    }

                    when (current.selectedTab) {
                        AppTab.AQUARIUMS -> {
                            AquariumsOverviewTab(
                                state = current,
                                onCreateFirst = viewModel::openCreateEditor,
                                onSelectAquarium = viewModel::selectAquarium,
                                onEditAquarium = viewModel::openEditEditor,
                                onDeleteAquarium = viewModel::requestDeleteAquarium,
                                onOpenWaterTests = { id ->
                                    viewModel.selectAquarium(id)
                                    viewModel.selectTab(AppTab.TESTS)
                                },
                                onOpenMaintenance = { id ->
                                    viewModel.selectAquarium(id)
                                    viewModel.selectTab(AppTab.MAINTENANCE)
                                },
                            )
                        }

                        AppTab.TESTS -> {
                            WaterTestsTab(
                                state = current,
                                onCreateFirstAquarium = {
                                    viewModel.selectTab(AppTab.AQUARIUMS)
                                    viewModel.openCreateEditor()
                                },
                                onSelectAquarium = viewModel::selectAquarium,
                                onLogWaterTest = viewModel::openMeasurementEditor,
                                onDeleteMeasurement = viewModel::deleteMeasurement,
                            )
                        }

                        AppTab.MAINTENANCE -> {
                            MaintenanceTab(
                                state = current,
                                onCreateFirstAquarium = {
                                    viewModel.selectTab(AppTab.AQUARIUMS)
                                    viewModel.openCreateEditor()
                                },
                                onSelectAquarium = viewModel::selectAquarium,
                                onLogMaintenance = viewModel::openCreateMaintenanceEditor,
                                onEditMaintenance = viewModel::openEditMaintenanceEditor,
                                onDeleteMaintenance = viewModel::deleteMaintenanceEvent,
                            )
                        }

                        AppTab.LIVESTOCK -> {
                            LivestockAndDosingTab(
                                state = current,
                                onCreateFirstAquarium = {
                                    viewModel.selectTab(AppTab.AQUARIUMS)
                                    viewModel.openCreateEditor()
                                },
                                onSelectAquarium = viewModel::selectAquarium,
                                onSelectSection = viewModel::selectLivestockSection,
                                onSelectKindFilter = viewModel::setSpeciesKindFilter,
                                onAddStockEntry = viewModel::openCreateStockEditor,
                                onEditStockEntry = viewModel::openEditStockEditor,
                                onMarkStockExited = viewModel::markStockExited,
                                onDeleteStockEntry = viewModel::deleteStockEntry,
                                onAddSpecies = { viewModel.openCreateSpeciesEditor() },
                                onEditSpecies = viewModel::openEditSpeciesEditor,
                                onDeleteSpecies = viewModel::deleteSpecies,
                                onAddProduct = viewModel::openCreateProductEditor,
                                onEditProduct = viewModel::openEditProductEditor,
                                onDeleteProduct = viewModel::deleteProduct,
                                onAddDosingPlan = viewModel::openCreateDosingPlanEditor,
                                onEditDosingPlan = viewModel::openEditDosingPlanEditor,
                                onDeleteDosingPlan = viewModel::deleteDosingPlan,
                                onLogDoseFromPlanNow = viewModel::logDoseFromPlanNow,
                                onLogAdHocDose = viewModel::openAdHocDosingEditor,
                                onDeleteDosingEvent = viewModel::deleteDosingEvent,
                            )
                        }

                        AppTab.SETTINGS -> {
                            SettingsAndDisclosuresTab(
                                state = current,
                                onExportBackupClick = {
                                    val defaultFileName =
                                        "aquarium-backup-${Instant.now().epochSecond}.zip"
                                    exportBackupLauncher.launch(defaultFileName)
                                },
                                onInspectRestoreClick = {
                                    inspectBackupLauncher.launch(
                                        arrayOf("application/zip", "application/octet-stream")
                                    )
                                },
                                onToggleAutoBackup = viewModel::toggleAutoBackupEnabled,
                                onSelectBackupFolderClick = {
                                    selectBackupFolderLauncher.launch(null)
                                },
                                onRunAutoBackupNowClick = viewModel::runAutomaticBackupNow,
                                onOpenLicenseDialog = viewModel::openLicenseAndNoticesDialog,
                            )
                        }
                    }

                    current.restorePreview?.let { preview ->
                        RestoreBackupPreviewDialog(
                            preview = preview,
                            isBusy = current.isBackupOperationInProgress,
                            onConfirmReplace = viewModel::confirmRestoreLibrary,
                            onDismiss = viewModel::dismissRestorePreview,
                        )
                    }

                    if (current.showLicenseAndNoticesDialog) {
                        LicenseAndNoticesDialog(
                            onDismiss = viewModel::closeLicenseAndNoticesDialog,
                        )
                    }

                    current.editorState?.let { editor ->
                        AquariumEditorDialog(
                            editor = editor,
                            availableProfiles = current.availableProfiles,
                            onDismiss = viewModel::closeEditor,
                            onNameChange = viewModel::updateNameInput,
                            onNetLitresChange = viewModel::updateNetLitresInput,
                            onGrossLitresChange = viewModel::updateGrossLitresInput,
                            onNotesChange = viewModel::updateNotesInput,
                            onToggleProfile = viewModel::toggleProfileSelection,
                            onNewProfileNameChange = viewModel::updateNewProfileNameInput,
                            onAddCustomProfile = viewModel::addCustomProfile,
                            onSave = viewModel::saveAquarium,
                        )
                    }

                    current.pendingDeleteAquarium?.let { target ->
                        DeleteAquariumConfirmationDialog(
                            aquarium = target,
                            onConfirm = viewModel::confirmDeleteAquarium,
                            onDismiss = viewModel::dismissDeleteConfirmation,
                        )
                    }

                    current.measurementEditorState?.let { measurementEditor ->
                        MeasurementEditorDialog(
                            editor = measurementEditor,
                            aquariumName = current.activeAquarium?.name.orEmpty(),
                            onDismiss = viewModel::closeMeasurementEditor,
                            onPhChange = viewModel::updatePhInput,
                            onKhChange = viewModel::updateKhDkhInput,
                            onGhChange = viewModel::updateGhDghInput,
                            onNo2Change = viewModel::updateNo2MgLInput,
                            onNo3Change = viewModel::updateNo3MgLInput,
                            onTdsChange = viewModel::updateTdsPpmInput,
                            onTemperatureChange = viewModel::updateTemperatureCInput,
                            onSave = viewModel::saveMeasurement,
                        )
                    }

                    current.maintenanceEditorState?.let { maintenanceEditor ->
                        MaintenanceEditorDialog(
                            editor = maintenanceEditor,
                            aquariumName = current.activeAquarium?.name.orEmpty(),
                            catalog = current.maintenanceTypeCatalog,
                            onDismiss = viewModel::closeMaintenanceEditor,
                            onSelectType = viewModel::selectMaintenanceType,
                            onChangedLitresChange = viewModel::updateChangedLitresInput,
                            onNotesChange = viewModel::updateMaintenanceNotesInput,
                            onSave = viewModel::saveMaintenanceEvent,
                        )
                    }

                    current.speciesEditorState?.let { speciesEditor ->
                        SpeciesEditorDialog(
                            editor = speciesEditor,
                            onDismiss = viewModel::closeSpeciesEditor,
                            onSelectKind = viewModel::selectSpeciesEditorKind,
                            onCommonNameChange = viewModel::updateSpeciesCommonNameInput,
                            onScientificNameChange = viewModel::updateSpeciesScientificNameInput,
                            onSave = viewModel::saveSpecies,
                        )
                    }

                    current.stockEditorState?.let { stockEditor ->
                        StockEditorDialog(
                            editor = stockEditor,
                            aquariumName = current.activeAquarium?.name.orEmpty(),
                            speciesCatalog = current.speciesCatalog,
                            onDismiss = viewModel::closeStockEditor,
                            onSelectSpecies = viewModel::selectStockSpecies,
                            onQuantityChange = viewModel::updateStockQuantityInput,
                            onIntroducedDateChange = viewModel::updateStockIntroducedDateInput,
                            onExitedDateChange = viewModel::updateStockExitedDateInput,
                            onNotesChange = viewModel::updateStockNotesInput,
                            onSave = viewModel::saveStockEntry,
                        )
                    }

                    current.productEditorState?.let { productEditor ->
                        ProductEditorDialog(
                            editor = productEditor,
                            onDismiss = viewModel::closeProductEditor,
                            onNameChange = viewModel::updateProductNameInput,
                            onSave = viewModel::saveProduct,
                        )
                    }

                    current.dosingPlanEditorState?.let { planEditor ->
                        DosingPlanEditorDialog(
                            editor = planEditor,
                            aquariumName = current.activeAquarium?.name.orEmpty(),
                            products = current.products,
                            doseUnits = current.doseUnitCatalog,
                            onDismiss = viewModel::closeDosingPlanEditor,
                            onSelectProduct = viewModel::selectDosingPlanProduct,
                            onDoseChange = viewModel::updateDosingPlanDoseInput,
                            onSelectUnit = viewModel::selectDosingPlanUnit,
                            onSelectFrequency = viewModel::selectDosingPlanFrequency,
                            onToggleWeekday = viewModel::toggleDosingPlanWeekday,
                            onStartDateChange = viewModel::updateDosingPlanStartDateInput,
                            onEndDateChange = viewModel::updateDosingPlanEndDateInput,
                            onTimeZoneChange = viewModel::updateDosingPlanTimeZoneInput,
                            onSave = viewModel::saveDosingPlan,
                        )
                    }

                    current.adHocDosingEditorState?.let { adHocEditor ->
                        AdHocDosingEditorDialog(
                            editor = adHocEditor,
                            aquariumName = current.activeAquarium?.name.orEmpty(),
                            doseUnits = current.doseUnitCatalog,
                            onDismiss = viewModel::closeAdHocDosingEditor,
                            onProductNameChange = viewModel::updateAdHocProductNameInput,
                            onDoseChange = viewModel::updateAdHocDoseInput,
                            onSelectUnit = viewModel::selectAdHocDoseUnit,
                            onSave = viewModel::saveAdHocDosingEvent,
                        )
                    }

                }
            }
        }
    }
}

@Composable
private fun PixelIcon(resId: Int, size: Dp = 24.dp) {
    Image(
        painter = painterResource(resId),
        contentDescription = null,
        modifier = Modifier.size(size),
    )
}

@Composable
private fun AquariumsOverviewTab(
    state: AquariumListState.Success,
    onCreateFirst: () -> Unit,
    onSelectAquarium: (UUID) -> Unit,
    onEditAquarium: (UUID) -> Unit,
    onDeleteAquarium: (UUID) -> Unit,
    onOpenWaterTests: (UUID) -> Unit,
    onOpenMaintenance: (UUID) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelIcon(R.drawable.ic_aquariums, 32.dp)
                Text(
                    text = stringResource(R.string.aquariums_title),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = stringResource(R.string.aquariums_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.aquariumDetails.isEmpty()) {
            EmptyAquariumCard(onCreateFirst = onCreateFirst)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(state.aquariumDetails, key = { it.id.toString() }) { aquarium ->
                    AquariumDetailCard(
                        aquarium = aquarium,
                        isSelected = state.activeAquariumId == aquarium.id,
                        onSelect = { onSelectAquarium(aquarium.id) },
                        onEdit = { onEditAquarium(aquarium.id) },
                        onDelete = { onDeleteAquarium(aquarium.id) },
                        onOpenWaterTests = { onOpenWaterTests(aquarium.id) },
                        onOpenMaintenance = { onOpenMaintenance(aquarium.id) },
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveAquariumSelectorBar(
    aquariums: List<AquariumDetail>,
    activeAquariumId: UUID?,
    onSelectAquarium: (UUID) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.active_aquarium_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                aquariums.forEach { aquarium ->
                    val selected = aquarium.id == activeAquariumId
                    val netText = aquarium.netLitres?.stripTrailingZeros()?.toPlainString()
                    val chipTitle = if (netText != null) "${aquarium.name} ($netText L)" else aquarium.name
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectAquarium(aquarium.id) },
                        label = {
                            Text(
                                text = chipTitle,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WaterTestsTab(
    state: AquariumListState.Success,
    onCreateFirstAquarium: () -> Unit,
    onSelectAquarium: (UUID) -> Unit,
    onLogWaterTest: () -> Unit,
    onDeleteMeasurement: (UUID) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelIcon(R.drawable.ic_temperature, 32.dp)
                Text(
                    text = stringResource(R.string.tests_title),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = stringResource(R.string.tests_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val activeAquarium = state.activeAquarium
        if (activeAquarium == null) {
            EmptyAquariumCard(onCreateFirst = onCreateFirstAquarium)
            return
        }

        ActiveAquariumSelectorBar(
            aquariums = state.aquariumDetails,
            activeAquariumId = activeAquarium.id,
            onSelectAquarium = onSelectAquarium,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val chunkedTrends = state.parameterTrends.chunked(2)
            items(chunkedTrends) { rowPair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowPair.forEach { summary ->
                        ParameterTrendCard(
                            summary = summary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowPair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.tests_history_heading),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    OutlinedButton(onClick = onLogWaterTest) {
                        Text(stringResource(R.string.log_water_test))
                    }
                }
            }

            if (state.measurements.isEmpty()) {
                item {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.tests_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.tests_empty_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(state.measurements, key = { it.id.toString() }) { record ->
                    MeasurementHistoryCard(
                        record = record,
                        onDelete = { onDeleteMeasurement(record.id) },
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }
}

@Composable
private fun ParameterTrendCard(
    summary: ParameterTrendSummary,
    modifier: Modifier = Modifier,
) {
    val latestText = summary.latestValue?.stripTrailingZeros()?.toPlainString() ?: "—"
    val unitSuffix = if (summary.parameter.unit.isEmpty() || summary.latestValue == null) {
        ""
    } else {
        " ${summary.parameter.unit}"
    }
    val cardDescription = "${summary.parameter.displayTitle}: $latestText$unitSuffix, ${summary.accessibilityCue}"

    ElevatedCard(
        modifier = modifier.semantics { contentDescription = cardDescription },
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = summary.parameter.displayTitle,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = "$latestText$unitSuffix",
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Surface(
                color = when (summary.direction) {
                    ParameterTrendDirection.RISING,
                    ParameterTrendDirection.FALLING,
                    ParameterTrendDirection.STABLE -> MaterialTheme.colorScheme.primaryContainer
                    ParameterTrendDirection.SINGLE_POINT -> MaterialTheme.colorScheme.secondaryContainer
                    ParameterTrendDirection.NO_DATA -> MaterialTheme.colorScheme.surfaceVariant
                },
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = summary.accessibilityCue,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            ParameterTrendSparkline(
                summary = summary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
            )

            if (summary.missingGapCount > 0) {
                Text(
                    text = "Unmeasured gaps: ${summary.missingGapCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ParameterTrendSparkline(
    summary: ParameterTrendSummary,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gapColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val baselineColor = MaterialTheme.colorScheme.surfaceVariant
    val sparklineDescription = buildString {
        append("${summary.parameter.displayTitle} trend chart: ")
        append("${summary.measuredCount} measured, ${summary.missingGapCount} unmeasured gaps, ")
        append(summary.accessibilityCue)
    }

    Canvas(
        modifier = modifier.semantics { contentDescription = sparklineDescription }
    ) {
        val width = size.width
        val height = size.height
        val padX = 8.dp.toPx()
        val padY = 6.dp.toPx()
        val usableWidth = (width - 2 * padX).coerceAtLeast(1f)
        val usableHeight = (height - 2 * padY).coerceAtLeast(1f)

        // Subtle baseline without fabricating biological safe ranges
        drawLine(
            color = baselineColor,
            start = Offset(padX, height - padY),
            end = Offset(width - padX, height - padY),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
        )

        val points = summary.points
        if (points.isEmpty()) return@Canvas

        val minVal = summary.minValue?.toFloat() ?: 0f
        val maxVal = summary.maxValue?.toFloat() ?: 0f
        val range = (maxVal - minVal).takeIf { it > 0.0001f }

        fun xForIndex(index: Int): Float =
            if (points.size == 1) width / 2f
            else padX + (index.toFloat() / (points.size - 1).toFloat()) * usableWidth

        fun yForValue(v: BigDecimal): Float {
            val f = v.toFloat()
            return if (range == null) {
                height / 2f
            } else {
                val normalized = ((f - minVal) / range).coerceIn(0f, 1f)
                (height - padY) - normalized * usableHeight
            }
        }

        // Draw segments only between adjacent measured points; leave explicit breaks at null gaps
        for (i in 0 until points.size - 1) {
            val currentVal = points[i].value
            val nextVal = points[i + 1].value
            if (currentVal != null && nextVal != null) {
                drawLine(
                    color = lineColor,
                    start = Offset(xForIndex(i), yForValue(currentVal)),
                    end = Offset(xForIndex(i + 1), yForValue(nextVal)),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        // Draw solid dots for measured values and hollow open markers for explicit null gaps
        points.forEachIndexed { index, point ->
            val x = xForIndex(index)
            val valDecimal = point.value
            if (valDecimal != null) {
                drawCircle(
                    color = lineColor,
                    radius = 3.5.dp.toPx(),
                    center = Offset(x, yForValue(valDecimal)),
                )
            } else {
                drawCircle(
                    color = gapColor,
                    radius = 3.5.dp.toPx(),
                    center = Offset(x, height / 2f),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeasurementHistoryCard(
    record: MeasurementRecord,
    onDelete: () -> Unit,
) {
    val measuredPairs = WaterParameter.entries.mapNotNull { param ->
        record.valueFor(param)?.let { value ->
            val valueText = value.stripTrailingZeros().toPlainString()
            val suffix = if (param.unit.isEmpty()) "" else " ${param.unit}"
            "${param.label}: $valueText$suffix"
        }
    }
    val unmeasuredLabels = WaterParameter.entries
        .filter { record.valueFor(it) == null }
        .map { it.label }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatTimestamp(record.recordedAt),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.delete_aquarium))
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                measuredPairs.forEach { itemText ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = itemText,
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            if (unmeasuredLabels.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.unmeasured_prefix,
                        unmeasuredLabels.joinToString(", "),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MaintenanceTab(
    state: AquariumListState.Success,
    onCreateFirstAquarium: () -> Unit,
    onSelectAquarium: (UUID) -> Unit,
    onLogMaintenance: () -> Unit,
    onEditMaintenance: (UUID) -> Unit,
    onDeleteMaintenance: (UUID) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelIcon(R.drawable.ic_water_change, 32.dp)
                Text(
                    text = stringResource(R.string.maintenance_title),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = stringResource(R.string.maintenance_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val activeAquarium = state.activeAquarium
        if (activeAquarium == null) {
            EmptyAquariumCard(onCreateFirst = onCreateFirstAquarium)
            return
        }

        ActiveAquariumSelectorBar(
            aquariums = state.aquariumDetails,
            activeAquariumId = activeAquarium.id,
            onSelectAquarium = onSelectAquarium,
        )

        val labelByCode = state.maintenanceTypeCatalog.associate { it.code to it.label }

        if (state.maintenanceEvents.isEmpty()) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.maintenance_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.maintenance_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onLogMaintenance) {
                        Text(stringResource(R.string.log_maintenance))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.maintenanceEvents, key = { it.id.toString() }) { event ->
                    MaintenanceHistoryCard(
                        event = event,
                        typeLabel = labelByCode[event.type] ?: event.type,
                        onEdit = { onEditMaintenance(event.id) },
                        onDelete = { onDeleteMaintenance(event.id) },
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }
}

@Composable
private fun MaintenanceHistoryCard(
    event: MaintenanceRecord,
    typeLabel: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = formatTimestamp(event.occurredAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                event.changedLitres?.let { litres ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = "${litres.stripTrailingZeros().toPlainString()} L changed",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            event.notes?.let { notes ->
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onEdit) {
                    Text(stringResource(R.string.edit_aquarium))
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.delete_aquarium))
                }
            }
        }
    }
}

@Composable
private fun EmptyAquariumCard(onCreateFirst: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.aquariums_empty),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.aquariums_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onCreateFirst) {
                Text(stringResource(R.string.create_first_aquarium))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AquariumDetailCard(
    aquarium: AquariumDetail,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenWaterTests: () -> Unit,
    onOpenMaintenance: () -> Unit,
) {
    ElevatedCard(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = aquarium.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = formatVolumesBadge(
                            net = aquarium.netLitres,
                            gross = aquarium.grossLitres,
                            unknownLabel = stringResource(R.string.volume_unknown),
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            if (aquarium.profiles.isEmpty()) {
                Text(
                    text = stringResource(R.string.profiles_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    aquarium.profiles.forEach { profile ->
                        AssistChip(
                            onClick = onEdit,
                            label = { Text(profile.name) },
                        )
                    }
                }
            }

            aquarium.notes?.let { notes ->
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onOpenWaterTests) {
                        Text(stringResource(R.string.nav_tests))
                    }
                    TextButton(onClick = onOpenMaintenance) {
                        Text(stringResource(R.string.nav_maintenance))
                    }
                }
                Row {
                    TextButton(onClick = onEdit) {
                        Text(stringResource(R.string.edit_aquarium))
                    }
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(stringResource(R.string.delete_aquarium))
                    }
                }
            }
        }
    }
}

@Composable
private fun MeasurementEditorDialog(
    editor: MeasurementEditorState,
    aquariumName: String,
    onDismiss: () -> Unit,
    onPhChange: (String) -> Unit,
    onKhChange: (String) -> Unit,
    onGhChange: (String) -> Unit,
    onNo2Change: (String) -> Unit,
    onNo3Change: (String) -> Unit,
    onTdsChange: (String) -> Unit,
    onTemperatureChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.measurement_dialog_title),
                    fontWeight = FontWeight.Bold,
                )
                if (aquariumName.isNotEmpty()) {
                    Text(
                        text = aquariumName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.measurement_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                editor.validationError?.let { errorText ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = errorText,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = editor.phInput,
                        onValueChange = onPhChange,
                        label = { Text(WaterParameter.PH.displayTitle) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = editor.temperatureCInput,
                        onValueChange = onTemperatureChange,
                        label = { Text(WaterParameter.TEMPERATURE_C.displayTitle) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = editor.khDkhInput,
                        onValueChange = onKhChange,
                        label = { Text(WaterParameter.KH_DKH.displayTitle) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = editor.ghDghInput,
                        onValueChange = onGhChange,
                        label = { Text(WaterParameter.GH_DGH.displayTitle) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = editor.no2MgLInput,
                        onValueChange = onNo2Change,
                        label = { Text(WaterParameter.NO2_MGL.displayTitle) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = editor.no3MgLInput,
                        onValueChange = onNo3Change,
                        label = { Text(WaterParameter.NO3_MGL.displayTitle) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }

                OutlinedTextField(
                    value = editor.tdsPpmInput,
                    onValueChange = onTdsChange,
                    label = { Text(WaterParameter.TDS_PPM.displayTitle) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !editor.isSaving) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MaintenanceEditorDialog(
    editor: MaintenanceEditorState,
    aquariumName: String,
    catalog: List<MaintenanceTypeCatalogItem>,
    onDismiss: () -> Unit,
    onSelectType: (String) -> Unit,
    onChangedLitresChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (editor.editingMaintenanceId == null) {
                        stringResource(R.string.maintenance_create_title)
                    } else {
                        stringResource(R.string.maintenance_edit_title)
                    },
                    fontWeight = FontWeight.Bold,
                )
                if (aquariumName.isNotEmpty()) {
                    Text(
                        text = aquariumName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                editor.validationError?.let { errorText ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = errorText,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.maintenance_type_heading),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    catalog.forEach { item ->
                        val selected = item.code == editor.selectedTypeCode
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectType(item.code) },
                            label = { Text(item.label) },
                        )
                    }
                }

                OutlinedTextField(
                    value = editor.changedLitresInput,
                    onValueChange = onChangedLitresChange,
                    label = { Text(stringResource(R.string.field_changed_litres)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                editor.volumeWarning?.let { warningText ->
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = warningText,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                OutlinedTextField(
                    value = editor.notesInput,
                    onValueChange = onNotesChange,
                    label = { Text(stringResource(R.string.field_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !editor.isSaving) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AquariumEditorDialog(
    editor: AquariumEditorState,
    availableProfiles: List<AquariumProfile>,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onNetLitresChange: (String) -> Unit,
    onGrossLitresChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onToggleProfile: (UUID) -> Unit,
    onNewProfileNameChange: (String) -> Unit,
    onAddCustomProfile: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (editor.editingAquariumId == null) {
                    stringResource(R.string.editor_create_title)
                } else {
                    stringResource(R.string.editor_edit_title)
                },
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                editor.validationError?.let { errorText ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = errorText,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                OutlinedTextField(
                    value = editor.nameInput,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.field_aquarium_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = editor.netLitresInput,
                        onValueChange = onNetLitresChange,
                        label = { Text(stringResource(R.string.field_net_litres)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = editor.grossLitresInput,
                        onValueChange = onGrossLitresChange,
                        label = { Text(stringResource(R.string.field_gross_litres)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }

                OutlinedTextField(
                    value = editor.notesInput,
                    onValueChange = onNotesChange,
                    label = { Text(stringResource(R.string.field_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = stringResource(R.string.section_overlapping_profiles),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    availableProfiles.forEach { profile ->
                        val selected = profile.id in editor.selectedProfileIds
                        FilterChip(
                            selected = selected,
                            onClick = { onToggleProfile(profile.id) },
                            label = { Text(profile.name) },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = editor.newProfileNameInput,
                        onValueChange = onNewProfileNameChange,
                        label = { Text(stringResource(R.string.field_new_profile)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = onAddCustomProfile) {
                        Text(stringResource(R.string.add_profile_tag))
                    }
                }

                editor.profileValidationError?.let { profileError ->
                    Text(
                        text = profileError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !editor.isSaving) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun DeleteAquariumConfirmationDialog(
    aquarium: AquariumDetail,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.delete_confirm_title),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(stringResource(R.string.delete_confirm_message, aquarium.name))
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.delete_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private fun formatVolumesBadge(
    net: BigDecimal?,
    gross: BigDecimal?,
    unknownLabel: String,
): String {
    val netText = net?.stripTrailingZeros()?.toPlainString()
    val grossText = gross?.stripTrailingZeros()?.toPlainString()
    return when {
        netText != null && grossText != null -> "$netText L net / $grossText L gross"
        netText != null -> "$netText L net"
        grossText != null -> "$grossText L gross"
        else -> unknownLabel
    }
}

private fun formatTimestamp(instant: Instant): String =
    UtcTimestampFormatter.format(instant)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LivestockAndDosingTab(
    state: AquariumListState.Success,
    onCreateFirstAquarium: () -> Unit,
    onSelectAquarium: (UUID) -> Unit,
    onSelectSection: (LivestockSection) -> Unit,
    onSelectKindFilter: (SpeciesKind?) -> Unit,
    onAddStockEntry: () -> Unit,
    onEditStockEntry: (UUID) -> Unit,
    onMarkStockExited: (UUID) -> Unit,
    onDeleteStockEntry: (UUID) -> Unit,
    onAddSpecies: () -> Unit,
    onEditSpecies: (UUID) -> Unit,
    onDeleteSpecies: (UUID) -> Unit,
    onAddProduct: () -> Unit,
    onEditProduct: (UUID) -> Unit,
    onDeleteProduct: (UUID) -> Unit,
    onAddDosingPlan: () -> Unit,
    onEditDosingPlan: (UUID) -> Unit,
    onDeleteDosingPlan: (UUID) -> Unit,
    onLogDoseFromPlanNow: (UUID) -> Unit,
    onLogAdHocDose: () -> Unit,
    onDeleteDosingEvent: (UUID) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelIcon(R.drawable.ic_livestock, 32.dp)
                Text(
                    text = stringResource(R.string.livestock_title),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = stringResource(R.string.livestock_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val activeAquarium = state.activeAquarium
        if (activeAquarium == null) {
            EmptyAquariumCard(onCreateFirst = onCreateFirstAquarium)
            return
        }

        ActiveAquariumSelectorBar(
            aquariums = state.aquariumDetails,
            activeAquariumId = activeAquarium.id,
            onSelectAquarium = onSelectAquarium,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = state.livestockSection == LivestockSection.STOCK,
                onClick = { onSelectSection(LivestockSection.STOCK) },
                label = { Text(stringResource(R.string.section_stock)) },
            )
            FilterChip(
                selected = state.livestockSection == LivestockSection.DOSING,
                onClick = { onSelectSection(LivestockSection.DOSING) },
                label = { Text(stringResource(R.string.section_dosing)) },
                leadingIcon = { PixelIcon(R.drawable.ic_dose, 18.dp) },
            )
            FilterChip(
                selected = state.livestockSection == LivestockSection.SPECIES,
                onClick = { onSelectSection(LivestockSection.SPECIES) },
                label = { Text(stringResource(R.string.section_species_catalog)) },
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state.livestockSection) {
                LivestockSection.STOCK -> {
                    item(key = "stock-kind-filter") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SpeciesKindFilterRow(
                                selectedFilter = state.selectedSpeciesKindFilter,
                                onSelectFilter = onSelectKindFilter,
                            )
                            OutlinedButton(onClick = onAddStockEntry) {
                                Text(stringResource(R.string.add_stock_entry))
                            }
                        }
                    }

                    if (state.filteredStockEntries.isEmpty()) {
                        item(key = "empty-stock-card") {
                            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.stock_empty_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = stringResource(R.string.stock_empty_hint),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else {
                        items(state.filteredStockEntries, key = { it.id.toString() }) { entry ->
                            StockEntryCard(
                                entry = entry,
                                onEdit = { onEditStockEntry(entry.id) },
                                onMarkExited = { onMarkStockExited(entry.id) },
                                onDelete = { onDeleteStockEntry(entry.id) },
                            )
                        }
                    }
                }

                LivestockSection.DOSING -> {
                    item(key = "products-header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.products_heading),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            OutlinedButton(onClick = onAddProduct) {
                                Text(stringResource(R.string.add_product))
                            }
                        }
                    }

                    items(state.products, key = { "product-${it.id}" }) { product ->
                        ProductCard(
                            product = product,
                            onEdit = { onEditProduct(product.id) },
                            onDelete = { onDeleteProduct(product.id) },
                        )
                    }

                    item(key = "dosing-plans-header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.dosing_plans_heading),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            OutlinedButton(onClick = onAddDosingPlan) {
                                Text(stringResource(R.string.add_dosing_plan))
                            }
                        }
                    }

                    items(state.dosingPlans, key = { "plan-${it.id}" }) { plan ->
                        DosingPlanCard(
                            plan = plan,
                            onLogDoseNow = { onLogDoseFromPlanNow(plan.id) },
                            onEdit = { onEditDosingPlan(plan.id) },
                            onDelete = { onDeleteDosingPlan(plan.id) },
                        )
                    }

                    item(key = "dosing-events-header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.dosing_events_heading),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            OutlinedButton(onClick = onLogAdHocDose) {
                                Text(stringResource(R.string.log_adhoc_dose))
                            }
                        }
                    }

                    items(state.dosingEvents, key = { "event-${it.id}" }) { event ->
                        DosingEventCard(
                            event = event,
                            onDelete = { onDeleteDosingEvent(event.id) },
                        )
                    }
                }

                LivestockSection.SPECIES -> {
                    item(key = "species-kind-filter") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SpeciesKindFilterRow(
                                selectedFilter = state.selectedSpeciesKindFilter,
                                onSelectFilter = onSelectKindFilter,
                            )
                            OutlinedButton(onClick = onAddSpecies) {
                                Text(stringResource(R.string.add_species))
                            }
                        }
                    }

                    items(state.filteredSpeciesCatalog, key = { "species-${it.id}" }) { species ->
                        SpeciesCatalogCard(
                            species = species,
                            onEdit = { onEditSpecies(species.id) },
                            onDelete = { onDeleteSpecies(species.id) },
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeciesKindFilterRow(
    selectedFilter: SpeciesKind?,
    onSelectFilter: (SpeciesKind?) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(
            selected = selectedFilter == null,
            onClick = { onSelectFilter(null) },
            label = { Text(stringResource(R.string.filter_all_species)) },
        )
        FilterChip(
            selected = selectedFilter == SpeciesKind.FAUNA,
            onClick = { onSelectFilter(SpeciesKind.FAUNA) },
            label = { Text(stringResource(R.string.filter_fauna)) },
        )
        FilterChip(
            selected = selectedFilter == SpeciesKind.FLORA,
            onClick = { onSelectFilter(SpeciesKind.FLORA) },
            label = { Text(stringResource(R.string.filter_flora)) },
            leadingIcon = { PixelIcon(R.drawable.ic_plant, 18.dp) },
        )
    }
}

@Composable
private fun StockEntryCard(
    entry: StockEntryRecord,
    onEdit: () -> Unit,
    onMarkExited: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.species.commonName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    entry.species.scientificName?.let { scientific ->
                        Text(
                            text = scientific,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = entry.species.kind.name,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = if (entry.isActive) {
                                    stringResource(R.string.status_active)
                                } else {
                                    stringResource(R.string.status_exited)
                                },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }

            val quantityLabel = entry.quantity?.stripTrailingZeros()?.toPlainString()
                ?: stringResource(R.string.quantity_unknown)
            Text(
                text = "Qty: $quantityLabel • Introduced: ${formatTimestamp(entry.introducedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            entry.exitedAt?.let { exitedAt ->
                Text(
                    text = "Exited: ${formatTimestamp(exitedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            entry.notes?.let { notes ->
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (entry.isActive) {
                    TextButton(onClick = onMarkExited) {
                        Text(stringResource(R.string.mark_exited))
                    }
                }
                TextButton(onClick = onEdit) {
                    Text(stringResource(R.string.edit_aquarium))
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.delete_aquarium))
                }
            }
        }
    }
}

@Composable
private fun SpeciesCatalogCard(
    species: SpeciesCatalogItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = species.commonName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                species.scientificName?.let { scientific ->
                    Text(
                        text = scientific,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = species.kind.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row {
                TextButton(onClick = onEdit) {
                    Text(stringResource(R.string.edit_aquarium))
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.delete_aquarium))
                }
            }
        }
    }
}

@Composable
private fun ProductCard(
    product: ProductRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Row {
                TextButton(onClick = onEdit) {
                    Text(stringResource(R.string.edit_aquarium))
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.delete_aquarium))
                }
            }
        }
    }
}

@Composable
private fun DosingPlanCard(
    plan: DosingPlanRecord,
    onLogDoseNow: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plan.product.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${plan.dose.stripTrailingZeros().toPlainString()} ${plan.unit}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Button(onClick = onLogDoseNow) {
                    Text(stringResource(R.string.log_dose_now))
                }
            }

            val scheduleDescription = when (plan.frequency) {
                DosingFrequency.DAILY -> "${plan.frequency.name} • ${plan.timeZone}"
                DosingFrequency.WEEKLY -> {
                    val days = plan.weekdays.joinToString(", ") { it.name.take(3) }
                    "${plan.frequency.name} ($days) • ${plan.timeZone}"
                }
            }
            Text(
                text = scheduleDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val windowText = if (plan.endDate != null) {
                "${plan.startDate} → ${plan.endDate}"
            } else {
                "From ${plan.startDate}"
            }
            Text(
                text = windowText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onEdit) {
                    Text(stringResource(R.string.edit_aquarium))
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.delete_aquarium))
                }
            }
        }
    }
}

@Composable
private fun DosingEventCard(
    event: DosingEventRecord,
    onDelete: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${event.productNameSnapshot} • ${event.dose.stripTrailingZeros().toPlainString()} ${event.unit}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = formatTimestamp(event.occurredAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (event.planId != null) "Plan snapshot" else "Ad-hoc",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(R.string.delete_aquarium))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeciesEditorDialog(
    editor: SpeciesEditorState,
    onDismiss: () -> Unit,
    onSelectKind: (SpeciesKind) -> Unit,
    onCommonNameChange: (String) -> Unit,
    onScientificNameChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (editor.editingSpeciesId != null) {
                    stringResource(R.string.species_edit_title)
                } else {
                    stringResource(R.string.species_create_title)
                },
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                editor.validationError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpeciesKind.entries.forEach { kind ->
                        FilterChip(
                            selected = editor.selectedKind == kind,
                            onClick = { onSelectKind(kind) },
                            label = { Text(kind.name) },
                        )
                    }
                }

                OutlinedTextField(
                    value = editor.commonNameInput,
                    onValueChange = onCommonNameChange,
                    label = { Text(stringResource(R.string.field_common_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = editor.scientificNameInput,
                    onValueChange = onScientificNameChange,
                    label = { Text(stringResource(R.string.field_scientific_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !editor.isSaving) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StockEditorDialog(
    editor: StockEditorState,
    aquariumName: String,
    speciesCatalog: List<SpeciesCatalogItem>,
    onDismiss: () -> Unit,
    onSelectSpecies: (UUID) -> Unit,
    onQuantityChange: (String) -> Unit,
    onIntroducedDateChange: (String) -> Unit,
    onExitedDateChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = if (editor.editingStockId != null) {
                        stringResource(R.string.stock_edit_title)
                    } else {
                        stringResource(R.string.stock_create_title)
                    },
                    fontWeight = FontWeight.Bold,
                )
                if (aquariumName.isNotBlank()) {
                    Text(
                        text = aquariumName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                editor.validationError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Text(
                    text = stringResource(R.string.section_species_catalog),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    speciesCatalog.forEach { species ->
                        FilterChip(
                            selected = editor.selectedSpeciesId == species.id,
                            onClick = { onSelectSpecies(species.id) },
                            label = { Text("${species.commonName} (${species.kind.name})") },
                        )
                    }
                }

                OutlinedTextField(
                    value = editor.quantityInput,
                    onValueChange = onQuantityChange,
                    label = { Text(stringResource(R.string.field_stock_quantity)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = editor.introducedDateInput,
                    onValueChange = onIntroducedDateChange,
                    label = { Text(stringResource(R.string.field_introduced_date)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = editor.exitedDateInput,
                    onValueChange = onExitedDateChange,
                    label = { Text(stringResource(R.string.field_exited_date)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = editor.notesInput,
                    onValueChange = onNotesChange,
                    label = { Text(stringResource(R.string.field_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !editor.isSaving) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ProductEditorDialog(
    editor: ProductEditorState,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (editor.editingProductId != null) {
                    stringResource(R.string.product_edit_title)
                } else {
                    stringResource(R.string.product_create_title)
                },
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                editor.validationError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                OutlinedTextField(
                    value = editor.nameInput,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.field_product_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !editor.isSaving) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DosingPlanEditorDialog(
    editor: DosingPlanEditorState,
    aquariumName: String,
    products: List<ProductRecord>,
    doseUnits: List<DoseUnitCatalogItem>,
    onDismiss: () -> Unit,
    onSelectProduct: (UUID) -> Unit,
    onDoseChange: (String) -> Unit,
    onSelectUnit: (String) -> Unit,
    onSelectFrequency: (DosingFrequency) -> Unit,
    onToggleWeekday: (DayOfWeek) -> Unit,
    onStartDateChange: (String) -> Unit,
    onEndDateChange: (String) -> Unit,
    onTimeZoneChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = if (editor.editingPlanId != null) {
                        stringResource(R.string.dosing_plan_edit_title)
                    } else {
                        stringResource(R.string.dosing_plan_create_title)
                    },
                    fontWeight = FontWeight.Bold,
                )
                if (aquariumName.isNotBlank()) {
                    Text(
                        text = aquariumName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                editor.validationError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Text(
                    text = stringResource(R.string.products_heading),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    products.forEach { product ->
                        FilterChip(
                            selected = editor.selectedProductId == product.id,
                            onClick = { onSelectProduct(product.id) },
                            label = { Text(product.name) },
                        )
                    }
                }

                OutlinedTextField(
                    value = editor.doseInput,
                    onValueChange = onDoseChange,
                    label = { Text(stringResource(R.string.field_dose_amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    doseUnits.forEach { item ->
                        FilterChip(
                            selected = editor.selectedUnitCode == item.code,
                            onClick = { onSelectUnit(item.code) },
                            label = { Text("${item.code} (${item.dimension.name})") },
                        )
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DosingFrequency.entries.forEach { freq ->
                        FilterChip(
                            selected = editor.frequency == freq,
                            onClick = { onSelectFrequency(freq) },
                            label = { Text(freq.name) },
                        )
                    }
                }

                if (editor.frequency == DosingFrequency.WEEKLY) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(
                                selected = day in editor.selectedWeekdays,
                                onClick = { onToggleWeekday(day) },
                                label = { Text(day.name.take(3)) },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = editor.startDateInput,
                    onValueChange = onStartDateChange,
                    label = { Text(stringResource(R.string.field_start_date)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = editor.endDateInput,
                    onValueChange = onEndDateChange,
                    label = { Text(stringResource(R.string.field_end_date)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = editor.timeZoneInput,
                    onValueChange = onTimeZoneChange,
                    label = { Text(stringResource(R.string.field_timezone)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !editor.isSaving) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdHocDosingEditorDialog(
    editor: AdHocDosingEditorState,
    aquariumName: String,
    doseUnits: List<DoseUnitCatalogItem>,
    onDismiss: () -> Unit,
    onProductNameChange: (String) -> Unit,
    onDoseChange: (String) -> Unit,
    onSelectUnit: (String) -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = stringResource(R.string.adhoc_dosing_title),
                    fontWeight = FontWeight.Bold,
                )
                if (aquariumName.isNotBlank()) {
                    Text(
                        text = aquariumName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                editor.validationError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                OutlinedTextField(
                    value = editor.productNameInput,
                    onValueChange = onProductNameChange,
                    label = { Text(stringResource(R.string.field_product_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = editor.doseInput,
                    onValueChange = onDoseChange,
                    label = { Text(stringResource(R.string.field_dose_amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    doseUnits.forEach { item ->
                        FilterChip(
                            selected = editor.selectedUnitCode == item.code,
                            onClick = { onSelectUnit(item.code) },
                            label = { Text("${item.code} (${item.dimension.name})") },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !editor.isSaving) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun SettingsAndDisclosuresTab(
    state: AquariumListState.Success,
    onExportBackupClick: () -> Unit,
    onInspectRestoreClick: () -> Unit,
    onToggleAutoBackup: (Boolean) -> Unit,
    onSelectBackupFolderClick: () -> Unit,
    onRunAutoBackupNowClick: () -> Unit,
    onOpenLicenseDialog: () -> Unit,
) {
    val schedule = state.backupScheduleSettings
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PixelIcon(R.drawable.ic_settings, 32.dp)
                    Text(
                        text = stringResource(R.string.settings_title),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = stringResource(R.string.settings_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.backup_manual_heading),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = UNENCRYPTED_BACKUP_DISCLOSURE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onExportBackupClick,
                            enabled = !state.isBackupOperationInProgress,
                        ) {
                            Text(stringResource(R.string.backup_export_action))
                        }
                        OutlinedButton(
                            onClick = onInspectRestoreClick,
                            enabled = !state.isBackupOperationInProgress,
                        ) {
                            Text(stringResource(R.string.backup_inspect_restore_action))
                        }
                    }
                    if (state.isBackupOperationInProgress) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.height(18.dp))
                            Text(
                                text = "Verifying backup archive…",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.backup_auto_heading),
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Switch(
                            checked = schedule.autoBackupEnabled,
                            onCheckedChange = onToggleAutoBackup,
                            enabled = !state.isBackupOperationInProgress,
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.backup_auto_retention_note,
                            schedule.retentionCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Folder: ${schedule.treeUri ?: "Not configured"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    schedule.lastBackupAt?.let { lastOk ->
                        Text(
                            text = "Last verified backup: $lastOk",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    schedule.lastBackupStatus?.let { statusText ->
                        Text(
                            text = "Status: $statusText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onSelectBackupFolderClick,
                            enabled = !state.isBackupOperationInProgress,
                        ) {
                            Text(stringResource(R.string.backup_select_folder_action))
                        }
                        if (schedule.treeUri != null) {
                            Button(
                                onClick = onRunAutoBackupNowClick,
                                enabled = !state.isBackupOperationInProgress,
                            ) {
                                Text(stringResource(R.string.backup_run_auto_now_action))
                            }
                        }
                    }
                }
            }
        }

        item {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.disclaimer_biological_heading),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = BIOLOGICAL_AND_DOSING_DISCLAIMER,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.privacy_licenses_heading),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = OFFLINE_PRIVACY_SUMMARY,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onOpenLicenseDialog) {
                        Text(stringResource(R.string.view_license_notices_action))
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RestoreBackupPreviewDialog(
    preview: BackupRestorePreview,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirmReplace: () -> Unit,
) {
    val counts = preview.counts
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_preview_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.restore_replace_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = "Exported UTC: ${preview.createdAt}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "App version: ${preview.appVersion} • Format v${preview.formatVersion}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "SHA-256: ${preview.librarySha256.take(16)}…",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = buildString {
                        appendLine("Aquariums: ${counts.aquariums}")
                        appendLine("Target profiles: ${counts.aquariumProfiles} (links: ${counts.aquariumProfileLinks})")
                        appendLine("Measurements: ${counts.measurements}")
                        appendLine("Maintenance events: ${counts.maintenanceEvents}")
                        appendLine("Species catalog: ${counts.species}")
                        appendLine("Stock entries: ${counts.stockEntries}")
                        appendLine("Products: ${counts.products}")
                        appendLine("Dosing plans: ${counts.dosingPlans}")
                        appendLine("Dosing events: ${counts.dosingEvents}")
                        append("Total records: ${preview.totalRows}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmReplace,
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(R.string.restore_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun LicenseAndNoticesDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.license_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = APACHE_LICENSE_AND_NOTICES_TEXT,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dismiss))
            }
        },
    )
}
