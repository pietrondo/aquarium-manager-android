package com.pietrondo.aquariummanager.domain

import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupDomainTest {
    private val profilePlanted = AquariumProfile(
        id = UUID.fromString("10000000-0000-0000-0000-000000000001"),
        name = "Planted",
    )
    private val catalog = CatalogBundle(
        format = CATALOG_FORMAT_ID,
        version = 1,
        profileLabels = listOf(profilePlanted),
        maintenanceTypes = listOf(
            MaintenanceTypeCatalogItem(code = "WATER_CHANGE", label = "Water change"),
            MaintenanceTypeCatalogItem(code = "FILTER_CLEAN", label = "Filter cleaning"),
        ),
        doseUnits = listOf(
            DoseUnitCatalogItem(code = "mL", label = "mL", dimension = DoseUnitDimension.VOLUME),
            DoseUnitCatalogItem(code = "g", label = "g", dimension = DoseUnitDimension.MASS),
            DoseUnitCatalogItem(code = "drops", label = "drops", dimension = DoseUnitDimension.COUNT),
        ),
        species = listOf(
            SpeciesCatalogItem(
                id = UUID.fromString("20000000-0000-0000-0000-000000000001"),
                kind = SpeciesKind.FAUNA,
                commonName = "Crystal Red Shrimp",
                scientificName = "Caridina cf. cantonensis",
            ),
        ),
    )

    private fun validSnapshot(): LibrarySnapshot {
        val aqId = UUID.fromString("30000000-0000-0000-0000-000000000001")
        val profileId = profilePlanted.id
        val productId = UUID.fromString("40000000-0000-0000-0000-000000000001")
        val planId = UUID.fromString("50000000-0000-0000-0000-000000000001")
        val speciesId = UUID.fromString("20000000-0000-0000-0000-000000000001")
        return LibrarySnapshot(
            aquariums = listOf(
                AquariumSnapshotRecord(
                    id = aqId,
                    name = "Acquario di Pietro \uD83C\uDF3F\uD83D\uDC1F",
                    netLitres = BigDecimal("42.5"),
                    grossLitres = BigDecimal("50"),
                    notes = "Caridina nano cube",
                    createdAt = Instant.parse("2026-10-18T10:00:00Z"),
                )
            ),
            aquariumProfiles = listOf(
                AquariumProfileSnapshotRecord(id = profileId, name = "Planted")
            ),
            aquariumProfileLinks = listOf(
                AquariumProfileLinkSnapshotRecord(aquariumId = aqId, profileId = profileId)
            ),
            measurements = listOf(
                MeasurementSnapshotRecord(
                    id = UUID.fromString("60000000-0000-0000-0000-000000000001"),
                    aquariumId = aqId,
                    recordedAt = Instant.parse("2026-10-18T11:00:00Z"),
                    ph = BigDecimal("6.4"),
                    khDkh = BigDecimal("0.5"),
                    ghDgh = BigDecimal("5.0"),
                    no2MgL = null,
                    no3MgL = BigDecimal("5"),
                    tdsPpm = BigDecimal("145"),
                    temperatureC = BigDecimal("22.3"),
                )
            ),
            maintenanceEvents = listOf(
                MaintenanceEventSnapshotRecord(
                    id = UUID.fromString("70000000-0000-0000-0000-000000000001"),
                    aquariumId = aqId,
                    type = "WATER_CHANGE",
                    occurredAt = Instant.parse("2026-10-18T12:00:00Z"),
                    changedLitres = BigDecimal("10"),
                    notes = "RO remineralized",
                )
            ),
            products = listOf(
                ProductSnapshotRecord(id = productId, name = "SaltyShrimp GH+")
            ),
            dosingPlans = listOf(
                DosingPlanSnapshotRecord(
                    id = planId,
                    aquariumId = aqId,
                    productId = productId,
                    dose = BigDecimal("1.5"),
                    unit = "g",
                    frequency = DosingFrequency.WEEKLY,
                    weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
                    startDate = LocalDate.parse("2026-10-01"),
                    endDate = null,
                    timeZone = ZoneId.of("Europe/Rome"),
                )
            ),
            dosingEvents = listOf(
                DosingEventSnapshotRecord(
                    id = UUID.fromString("80000000-0000-0000-0000-000000000001"),
                    planId = planId,
                    aquariumId = aqId,
                    occurredAt = Instant.parse("2026-10-18T12:05:00Z"),
                    productNameSnapshot = "SaltyShrimp GH+",
                    dose = BigDecimal("1.5"),
                    unit = "g",
                )
            ),
            species = listOf(
                SpeciesSnapshotRecord(
                    id = speciesId,
                    kind = SpeciesKind.FAUNA,
                    scientificName = "Caridina cf. cantonensis",
                    commonName = "Crystal Red Shrimp",
                )
            ),
            stockEntries = listOf(
                StockEntrySnapshotRecord(
                    id = UUID.fromString("90000000-0000-0000-0000-000000000001"),
                    aquariumId = aqId,
                    speciesId = speciesId,
                    quantity = BigDecimal("15"),
                    introducedAt = Instant.parse("2026-10-10T09:00:00Z"),
                    exitedAt = null,
                    notes = "CRS grade SS",
                )
            ),
        )
    }

    @Test
    fun validManifestAndLibrarySnapshotPassValidation() {
        val snapshot = validSnapshot()
        validateLibrarySnapshot(snapshot, catalog)
        val manifest = BackupManifest(
            format = BACKUP_FORMAT_ID,
            formatVersion = 1,
            appVersion = "0.1.0-custom-diagnostic",
            createdAt = Instant.parse("2026-10-19T12:00:00Z"),
            counts = snapshot.counts(),
            librarySha256 = "a".repeat(64),
        )
        validateBackupManifest(manifest)
        assertEquals(10L, snapshot.counts().totalRows)
    }

    @Test
    fun manifestRejectsUnsupportedFormatOrFutureVersionOrBadSha256() {
        val base = BackupManifest(
            format = BACKUP_FORMAT_ID,
            formatVersion = 1,
            appVersion = "0.1.0",
            createdAt = Instant.parse("2026-10-19T12:00:00Z"),
            counts = BackupTableCounts(),
            librarySha256 = "0123456789abcdef".repeat(4),
        )
        // Legacy formatVersion 0 is supported via migrator
        validateBackupManifest(base.copy(formatVersion = 0))

        // Reject future formatVersion = 2
        assertThrowsIllegalArgument {
            validateBackupManifest(base.copy(formatVersion = 2))
        }
        // Reject negative formatVersion = -1
        assertThrowsIllegalArgument {
            validateBackupManifest(base.copy(formatVersion = -1))
        }
        // Reject wrong format ID
        assertThrowsIllegalArgument {
            validateBackupManifest(base.copy(format = "other-app"))
        }
        // Reject uppercase or short SHA-256
        assertThrowsIllegalArgument {
            validateBackupManifest(base.copy(librarySha256 = "A".repeat(64)))
        }
        assertThrowsIllegalArgument {
            validateBackupManifest(base.copy(librarySha256 = "abcd"))
        }
        // Reject negative counts or exceeding MAX_TOTAL_ROWS
        assertThrowsIllegalArgument {
            validateBackupManifest(base.copy(counts = BackupTableCounts(aquariums = -1)))
        }
        assertThrowsIllegalArgument {
            validateBackupManifest(base.copy(counts = BackupTableCounts(measurements = MAX_TOTAL_ROWS + 1)))
        }
    }

    @Test
    fun manifestCountsUseWideSumAndRejectOverflowingAggregate() {
        val base = BackupManifest(
            appVersion = "0.1.0",
            createdAt = Instant.parse("2026-10-19T12:00:00Z"),
            counts = BackupTableCounts(),
            librarySha256 = "a".repeat(64),
        )
        assertEquals(4_294_967_294L, BackupTableCounts(aquariums = Int.MAX_VALUE, products = Int.MAX_VALUE).totalRows)
        assertThrowsIllegalArgument {
            validateBackupManifest(base.copy(counts = BackupTableCounts(aquariums = Int.MAX_VALUE, products = Int.MAX_VALUE)))
        }
        assertThrowsIllegalArgument {
            validateBackupManifest(base.copy(counts = BackupTableCounts(aquariums = -1)))
        }
    }

    @Test
    fun snapshotRejectsDuplicatePrimaryKeysAndDuplicateProfileLinks() {
        val base = validSnapshot()
        val dupAquariums = base.copy(aquariums = base.aquariums + base.aquariums.first())
        assertThrowsIllegalArgument {
            validateLibrarySnapshot(dupAquariums, catalog)
        }

        val dupLinks = base.copy(
            aquariumProfileLinks = base.aquariumProfileLinks + base.aquariumProfileLinks.first()
        )
        assertThrowsIllegalArgument {
            validateLibrarySnapshot(dupLinks, catalog)
        }
    }

    @Test
    fun snapshotRejectsOrphanForeignKeysAcrossAllChildTables() {
        val base = validSnapshot()
        val missingId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")

        // Orphan aquariumId in profile link
        assertThrowsIllegalArgument {
            validateLibrarySnapshot(
                base.copy(
                    aquariumProfileLinks = listOf(
                        AquariumProfileLinkSnapshotRecord(
                            aquariumId = missingId,
                            profileId = base.aquariumProfiles.first().id,
                        )
                    )
                ),
                catalog,
            )
        }

        // Orphan profileId in profile link
        assertThrowsIllegalArgument {
            validateLibrarySnapshot(
                base.copy(
                    aquariumProfileLinks = listOf(
                        AquariumProfileLinkSnapshotRecord(
                            aquariumId = base.aquariums.first().id,
                            profileId = missingId,
                        )
                    )
                ),
                catalog,
            )
        }

        // Orphan aquariumId in measurement
        assertThrowsIllegalArgument {
            validateLibrarySnapshot(
                base.copy(
                    measurements = listOf(base.measurements.first().copy(aquariumId = missingId))
                ),
                catalog,
            )
        }

        // Orphan productId in dosing plan
        assertThrowsIllegalArgument {
            validateLibrarySnapshot(
                base.copy(
                    dosingPlans = listOf(base.dosingPlans.first().copy(productId = missingId))
                ),
                catalog,
            )
        }

        // Orphan planId in dosing event
        assertThrowsIllegalArgument {
            validateLibrarySnapshot(
                base.copy(
                    dosingEvents = listOf(base.dosingEvents.first().copy(planId = missingId))
                ),
                catalog,
            )
        }

        // Orphan speciesId in stock entry
        assertThrowsIllegalArgument {
            validateLibrarySnapshot(
                base.copy(
                    stockEntries = listOf(base.stockEntries.first().copy(speciesId = missingId))
                ),
                catalog,
            )
        }
    }

    @Test
    fun snapshotRejectsUnknownCatalogCodesAndExcessiveTextLength() {
        val base = validSnapshot()

        // Unknown maintenance type
        assertThrowsIllegalArgument {
            validateLibrarySnapshot(
                base.copy(
                    maintenanceEvents = listOf(
                        base.maintenanceEvents.first().copy(type = "UNKNOWN_MAINT_CODE")
                    )
                ),
                catalog,
            )
        }

        // Unknown dose unit in dosing plan
        assertThrowsIllegalArgument {
            validateLibrarySnapshot(
                base.copy(
                    dosingPlans = listOf(
                        base.dosingPlans.first().copy(unit = "gallons")
                    )
                ),
                catalog,
            )
        }

        // Unknown dose unit in dosing event
        assertThrowsIllegalArgument {
            validateLibrarySnapshot(
                base.copy(
                    dosingEvents = listOf(
                        base.dosingEvents.first().copy(unit = "tablespoons")
                    )
                ),
                catalog,
            )
        }

        // Text field exceeding MAX_TEXT_FIELD_LENGTH
        assertThrowsIllegalArgument {
            validateLibrarySnapshot(
                base.copy(
                    aquariums = listOf(
                        base.aquariums.first().copy(notes = "x".repeat(MAX_TEXT_FIELD_LENGTH + 1))
                    )
                ),
                catalog,
            )
        }
    }

    @Test
    fun legalAndDisclosureTextsCoverUnencryptedBackupBiologicalDisclaimerOfflinePrivacyAndApache2() {
        assertTrue(UNENCRYPTED_BACKUP_DISCLOSURE.contains("unencrypted", ignoreCase = true))
        assertTrue(UNENCRYPTED_BACKUP_DISCLOSURE.contains("manifest.json"))
        assertTrue(UNENCRYPTED_BACKUP_DISCLOSURE.contains("library.json"))

        assertTrue(BIOLOGICAL_AND_DOSING_DISCLAIMER.contains("informational", ignoreCase = true))
        assertTrue(BIOLOGICAL_AND_DOSING_DISCLAIMER.contains("veterinary", ignoreCase = true))

        assertTrue(OFFLINE_PRIVACY_SUMMARY.contains("100% offline", ignoreCase = true))
        assertTrue(OFFLINE_PRIVACY_SUMMARY.contains("no internet permission", ignoreCase = true))

        assertTrue(APACHE_LICENSE_AND_NOTICES_TEXT.contains("Apache License, Version 2.0"))
        assertTrue(APACHE_LICENSE_AND_NOTICES_TEXT.contains("Disclaimer of Warranty"))
        assertTrue(APACHE_LICENSE_AND_NOTICES_TEXT.contains("Limitation of Liability"))
        assertTrue(APACHE_LICENSE_AND_NOTICES_TEXT.contains("Accepting Warranty or Additional Liability"))
        assertTrue(APACHE_LICENSE_AND_NOTICES_TEXT.contains("AndroidX Room"))
        assertTrue(APACHE_LICENSE_AND_NOTICES_TEXT.contains("Jetpack Compose"))
    }

    private fun assertThrowsIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException to be thrown")
        } catch (_: IllegalArgumentException) {
            // Expected
        }
    }
}
