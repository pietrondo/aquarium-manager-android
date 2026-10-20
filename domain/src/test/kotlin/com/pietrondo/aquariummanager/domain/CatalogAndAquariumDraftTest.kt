package com.pietrondo.aquariummanager.domain

import java.math.BigDecimal
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CatalogAndAquariumDraftTest {
    @Test
    fun aquariumDraftRejectsTextLongerThanBackupLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            AquariumDraft(name = "x".repeat(MAX_TEXT_FIELD_LENGTH + 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AquariumDraft(name = "Tank", notes = "x".repeat(MAX_TEXT_FIELD_LENGTH + 1))
        }
    }
    private val profilePlanted = AquariumProfile(UUID.fromString("00000000-0000-4000-8000-000000000101"), "Planted")
    private val profileShrimp = AquariumProfile(UUID.fromString("00000000-0000-4000-8000-000000000103"), "Shrimp")
    private val maintWaterChange = MaintenanceTypeCatalogItem("WATER_CHANGE", "Water change")
    private val unitMl = DoseUnitCatalogItem("mL", "Millilitres (mL)", DoseUnitDimension.VOLUME)
    private val unitGrams = DoseUnitCatalogItem("g", "Grams (g)", DoseUnitDimension.MASS)
    private val unitDrops = DoseUnitCatalogItem("drops", "Drops", DoseUnitDimension.COUNT)
    private val unitTabs = DoseUnitCatalogItem("tabs", "Tablets / Capsules", DoseUnitDimension.COUNT)
    private val speciesAmano = SpeciesCatalogItem(
        id = UUID.fromString("00000000-0000-4000-8000-000000000201"),
        kind = SpeciesKind.FAUNA,
        commonName = "Amano Shrimp",
        scientificName = "Caridina multidentata",
    )

    private fun validCatalog() = CatalogBundle(
        format = CATALOG_FORMAT_ID,
        version = 1,
        profileLabels = listOf(profilePlanted, profileShrimp),
        maintenanceTypes = listOf(maintWaterChange),
        doseUnits = listOf(unitMl, unitGrams, unitDrops, unitTabs),
        species = listOf(speciesAmano),
    )

    @Test
    fun catalogBundleValidatesFormatVersionAndUniqueIdentifiers() {
        val bundle = validCatalog()
        assertEquals(1, bundle.version)
        assertEquals(2, bundle.profileLabels.size)

        assertThrows(IllegalArgumentException::class.java) {
            validCatalog().copy(format = "wrong-format")
        }
        assertThrows(IllegalArgumentException::class.java) {
            validCatalog().copy(version = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validCatalog().copy(version = 99)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validCatalog().copy(
                profileLabels = listOf(
                    profilePlanted,
                    AquariumProfile(profilePlanted.id, "Duplicate UUID"),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            validCatalog().copy(
                profileLabels = listOf(
                    profilePlanted,
                    AquariumProfile(UUID.fromString("00000000-0000-4000-8000-000000000199"), " planted "),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            validCatalog().copy(
                maintenanceTypes = listOf(
                    maintWaterChange,
                    MaintenanceTypeCatalogItem("WATER_CHANGE", "Duplicate code"),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            validCatalog().copy(
                doseUnits = listOf(
                    unitMl,
                    DoseUnitCatalogItem("mL", "Duplicate unit", DoseUnitDimension.VOLUME),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            validCatalog().copy(
                species = listOf(
                    speciesAmano,
                    speciesAmano.copy(commonName = "Duplicate species ID"),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AquariumProfile(profilePlanted.id, "   ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MaintenanceTypeCatalogItem(" ", "Water change")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DoseUnitCatalogItem("mL", " ", DoseUnitDimension.VOLUME)
        }
    }

    @Test
    fun catalogValidationRejectsUnknownUnitCodesAndCrossDimensionComparisons() {
        val bundle = validCatalog()
        bundle.validateMaintenanceTypeCode("WATER_CHANGE")
        bundle.validateDoseUnitCode("mL")
        bundle.validateDoseUnitCode("g")
        bundle.validateDoseUnitCode("drops")
        bundle.requireCompatibleDoseDimension("drops", "tabs")

        assertThrows(IllegalArgumentException::class.java) {
            bundle.validateMaintenanceTypeCode("UNKNOWN_MAINT")
        }
        assertThrows(IllegalArgumentException::class.java) {
            bundle.validateDoseUnitCode("spoons")
        }
        assertThrows(IllegalArgumentException::class.java) {
            bundle.requireCompatibleDoseDimension("mL", "g")
        }
        assertThrows(IllegalArgumentException::class.java) {
            bundle.requireCompatibleDoseDimension("drops", "mL")
        }
        assertThrows(IllegalArgumentException::class.java) {
            bundle.requireCompatibleDoseDimension("unknown", "mL")
        }
    }

    @Test
    fun aquariumDraftValidatesNameOptionalUnknownVolumesAndProfileSet() {
        val unknownVolumesDraft = AquariumDraft(
            name = " Quarantine Cube ",
            netLitres = null,
            grossLitres = null,
            notes = null,
            profileIds = setOf(profilePlanted.id, profileShrimp.id),
        )
        assertNull(unknownVolumesDraft.netLitres)
        assertNull(unknownVolumesDraft.grossLitres)
        assertEquals(2, unknownVolumesDraft.profileIds.size)

        assertThrows(IllegalArgumentException::class.java) {
            AquariumDraft(name = "   ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AquariumDraft(name = "Tank", netLitres = BigDecimal.ZERO)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AquariumDraft(name = "Tank", netLitres = BigDecimal("50"), grossLitres = BigDecimal("40"))
        }
    }
}
