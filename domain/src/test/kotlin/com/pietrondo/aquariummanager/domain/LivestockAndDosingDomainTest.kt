package com.pietrondo.aquariummanager.domain

import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LivestockAndDosingDomainTest {
    private val aquariumId = UUID.fromString("00000000-0000-4000-8000-000000000010")
    private val amano = SpeciesCatalogItem(
        id = UUID.fromString("00000000-0000-4000-8000-000000000201"),
        kind = SpeciesKind.FAUNA,
        commonName = "Amano Shrimp",
        scientificName = "Caridina multidentata",
    )
    private val javaFern = SpeciesCatalogItem(
        id = UUID.fromString("00000000-0000-4000-8000-000000000204"),
        kind = SpeciesKind.FLORA,
        commonName = "Java Fern",
        scientificName = "Microsorum pteropus",
    )
    private val productPrime = ProductRecord(
        id = UUID.fromString("00000000-0000-4000-8000-000000000301"),
        name = "Seachem Prime",
    )

    @Test
    fun speciesDraftFilterAndDuplicateValidationEnforceKindAndNames() {
        val catalog = listOf(amano, javaFern)
        assertEquals(listOf(amano, javaFern), catalog.filterByKind(null))
        assertEquals(listOf(amano), catalog.filterByKind(SpeciesKind.FAUNA))
        assertEquals(listOf(javaFern), catalog.filterByKind(SpeciesKind.FLORA))

        // Blank commonName or blank non-null scientificName rejected in SpeciesDraft
        assertThrows(IllegalArgumentException::class.java) {
            SpeciesDraft(kind = SpeciesKind.FAUNA, commonName = "   ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SpeciesDraft(kind = SpeciesKind.FLORA, commonName = "Rotala", scientificName = "   ")
        }

        // Duplicate (kind, commonName) case-insensitive is rejected
        assertThrows(IllegalArgumentException::class.java) {
            validateUniqueSpeciesCatalog(
                existing = catalog,
                kind = SpeciesKind.FAUNA,
                commonName = "  amano shrimp ",
                scientificName = null,
            )
        }

        // Duplicate scientificName case-insensitive is rejected even across kinds
        assertThrows(IllegalArgumentException::class.java) {
            validateUniqueSpeciesCatalog(
                existing = catalog,
                kind = SpeciesKind.FLORA,
                commonName = "Narrow Leaf Fern",
                scientificName = " MICROSORUM PTEROPUS ",
            )
        }

        // Editing the same species record (excludingId = amano.id) is allowed
        validateUniqueSpeciesCatalog(
            existing = catalog,
            kind = SpeciesKind.FAUNA,
            commonName = "Amano Algae Shrimp",
            scientificName = "Caridina multidentata",
            excludingId = amano.id,
        )
    }

    @Test
    fun stockEntryAndDraftSupportUnknownNullQuantityFractionalFloraAndRejectExitBeforeIntro() {
        val intro = Instant.parse("2026-10-01T10:00:00Z")
        val exit = Instant.parse("2026-10-15T18:30:00Z")

        // Unknown quantity stays null and active when exitedAt is null
        val unknownQtyEntry = StockEntryRecord(
            id = UUID.randomUUID(),
            aquariumId = aquariumId,
            species = amano,
            quantity = null,
            introducedAt = intro,
            exitedAt = null,
            notes = "Colony count unknown",
        )
        assertNull(unknownQtyEntry.quantity)
        assertTrue(unknownQtyEntry.isActive)

        // Fractional flora quantity and exited status
        val exitedFloraEntry = StockEntryRecord(
            id = UUID.randomUUID(),
            aquariumId = aquariumId,
            species = javaFern,
            quantity = BigDecimal("2.5"),
            introducedAt = intro,
            exitedAt = exit,
            notes = "Moved rhizome to second tank",
        )
        assertEquals(0, BigDecimal("2.5").compareTo(exitedFloraEntry.quantity))
        assertFalse(exitedFloraEntry.isActive)

        // Zero or negative quantity is rejected on both Record and Draft
        assertThrows(IllegalArgumentException::class.java) {
            StockEntryRecord(
                id = UUID.randomUUID(),
                aquariumId = aquariumId,
                species = amano,
                quantity = BigDecimal.ZERO,
                introducedAt = intro,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            StockEntryDraft(
                speciesId = amano.id,
                quantity = BigDecimal("-1"),
                introducedAt = intro,
            )
        }

        // Exit before introduction is rejected on both Record and Draft
        val beforeIntro = Instant.parse("2026-09-30T23:59:59Z")
        assertThrows(IllegalArgumentException::class.java) {
            StockEntryRecord(
                id = UUID.randomUUID(),
                aquariumId = aquariumId,
                species = amano,
                quantity = BigDecimal("5"),
                introducedAt = intro,
                exitedAt = beforeIntro,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            StockEntryDraft(
                speciesId = amano.id,
                quantity = BigDecimal("5"),
                introducedAt = intro,
                exitedAt = beforeIntro,
            )
        }
    }

    @Test
    fun productAndDosingEventModelsRejectBlankNamesAndNonPositiveDoses() {
        assertThrows(IllegalArgumentException::class.java) {
            ProductRecord(id = UUID.randomUUID(), name = "   ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProductDraft(name = "")
        }

        val adHocDraft = DosingEventDraft(
            planId = null,
            occurredAt = Instant.parse("2026-10-19T12:00:00Z"),
            productNameSnapshot = "Seachem Flourish",
            dose = BigDecimal("3.5"),
            unit = "mL",
        )
        assertNull(adHocDraft.planId)
        assertEquals("Seachem Flourish", adHocDraft.productNameSnapshot)

        assertThrows(IllegalArgumentException::class.java) {
            DosingEventDraft(
                productNameSnapshot = "   ",
                dose = BigDecimal("1"),
                unit = "mL",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            DosingEventRecord(
                id = UUID.randomUUID(),
                aquariumId = aquariumId,
                planId = null,
                occurredAt = Instant.EPOCH,
                productNameSnapshot = "Flourish",
                dose = BigDecimal.ZERO,
                unit = "mL",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            DosingEventRecord(
                id = UUID.randomUUID(),
                aquariumId = aquariumId,
                planId = null,
                occurredAt = Instant.EPOCH,
                productNameSnapshot = "Flourish",
                dose = BigDecimal("2"),
                unit = "  ",
            )
        }
    }

    @Test
    fun dosingPlanRecurrenceHandlesDailyWeeklyWindowsLeapDatesAndEndBeforeStart() {
        val romeZone = ZoneId.of("Europe/Rome")

        // End date before start date is rejected
        assertThrows(IllegalArgumentException::class.java) {
            DosingPlanDraft(
                productId = productPrime.id,
                dose = BigDecimal("2"),
                unit = "mL",
                frequency = DosingFrequency.DAILY,
                startDate = LocalDate.of(2026, 10, 20),
                endDate = LocalDate.of(2026, 10, 19),
                timeZone = romeZone,
            )
        }

        // Weekly plan without weekdays is rejected
        assertThrows(IllegalArgumentException::class.java) {
            DosingPlanDraft(
                productId = productPrime.id,
                dose = BigDecimal("2"),
                unit = "mL",
                frequency = DosingFrequency.WEEKLY,
                weekdays = emptySet(),
                startDate = LocalDate.of(2026, 10, 19),
                timeZone = romeZone,
            )
        }

        // Leap year daily plan across Feb 28 - Mar 1, 2024 (includes 2024-02-29)
        val leapDailyPlan = DosingPlanRecord(
            id = UUID.randomUUID(),
            aquariumId = aquariumId,
            product = productPrime,
            dose = BigDecimal("1.5"),
            unit = "mL",
            frequency = DosingFrequency.DAILY,
            weekdays = emptySet(),
            startDate = LocalDate.of(2024, 2, 28),
            endDate = LocalDate.of(2024, 3, 1),
            timeZone = romeZone,
        )
        assertTrue(leapDailyPlan.isScheduledOnLocalDate(LocalDate.of(2024, 2, 29)))
        assertFalse(leapDailyPlan.isScheduledOnLocalDate(LocalDate.of(2024, 2, 27)))
        assertFalse(leapDailyPlan.isScheduledOnLocalDate(LocalDate.of(2024, 3, 2)))
        assertEquals(
            listOf(
                LocalDate.of(2024, 2, 28),
                LocalDate.of(2024, 2, 29),
                LocalDate.of(2024, 3, 1),
            ),
            leapDailyPlan.scheduledDatesInWindow(
                fromDate = LocalDate.of(2024, 2, 25),
                toDate = LocalDate.of(2024, 3, 5),
            ),
        )

        // Weekly plan on MONDAY, WEDNESDAY, FRIDAY
        val weeklyPlan = DosingPlanRecord(
            id = UUID.randomUUID(),
            aquariumId = aquariumId,
            product = productPrime,
            dose = BigDecimal("5"),
            unit = "drops",
            frequency = DosingFrequency.WEEKLY,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            startDate = LocalDate.of(2026, 10, 19), // Monday
            endDate = LocalDate.of(2026, 10, 25),   // Sunday
            timeZone = romeZone,
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 10, 19), // Mon
                LocalDate.of(2026, 10, 21), // Wed
                LocalDate.of(2026, 10, 23), // Fri
            ),
            weeklyPlan.scheduledDatesInWindow(
                fromDate = LocalDate.of(2026, 10, 19),
                toDate = LocalDate.of(2026, 10, 25),
            ),
        )
    }

    @Test
    fun dosingPlanRecurrenceHandlesDstGapDstOverlapAndDeviceTimezoneTravel() {
        val nyZone = ZoneId.of("America/New_York")
        val tokyoZone = ZoneId.of("Asia/Tokyo")

        // Plan in America/New_York scheduled only on Sundays (2026-03-08 is DST spring-forward Sunday;
        // 2026-11-01 is DST fall-back Sunday).
        val sundayNyPlan = DosingPlanRecord(
            id = UUID.randomUUID(),
            aquariumId = aquariumId,
            product = productPrime,
            dose = BigDecimal("2"),
            unit = "mL",
            frequency = DosingFrequency.WEEKLY,
            weekdays = setOf(DayOfWeek.SUNDAY),
            startDate = LocalDate.of(2026, 3, 1),
            endDate = LocalDate.of(2026, 11, 30),
            timeZone = nyZone,
        )

        // 1. DST Spring-Forward Gap in America/New_York on 2026-03-08 (02:00 -> 03:00 local jump at 07:00Z):
        // Instants just before (06:59:59Z = 01:59:59 EST) and after (07:00:01Z = 03:00:01 EDT)
        val beforeSpringGap = Instant.parse("2026-03-08T06:59:59Z")
        val afterSpringGap = Instant.parse("2026-03-08T07:00:01Z")
        assertEquals(LocalDate.of(2026, 3, 8), sundayNyPlan.localDateAt(beforeSpringGap))
        assertEquals(LocalDate.of(2026, 3, 8), sundayNyPlan.localDateAt(afterSpringGap))
        assertTrue(sundayNyPlan.isScheduledAtInstant(beforeSpringGap))
        assertTrue(sundayNyPlan.isScheduledAtInstant(afterSpringGap))

        // 2. DST Fall-Back Overlap in America/New_York on 2026-11-01 (01:30 occurs twice: 05:30Z EDT and 06:30Z EST):
        val first0130 = Instant.parse("2026-11-01T05:30:00Z")
        val second0130 = Instant.parse("2026-11-01T06:30:00Z")
        assertEquals(LocalDate.of(2026, 11, 1), sundayNyPlan.localDateAt(first0130))
        assertEquals(LocalDate.of(2026, 11, 1), sundayNyPlan.localDateAt(second0130))
        assertTrue(sundayNyPlan.isScheduledAtInstant(first0130))
        assertTrue(sundayNyPlan.isScheduledAtInstant(second0130))

        // 3. Timezone travel: plan anchored in Asia/Tokyo (UTC+9), scheduled only on Tuesday 2026-10-20.
        // At 2026-10-19T18:00:00Z, it is already 2026-10-20 03:00 in Tokyo, even if the user's phone
        // travels to Europe/Rome (2026-10-19 20:00) or America/New_York (2026-10-19 14:00).
        val tokyoTuesdayPlan = DosingPlanRecord(
            id = UUID.randomUUID(),
            aquariumId = aquariumId,
            product = productPrime,
            dose = BigDecimal("1"),
            unit = "tabs",
            frequency = DosingFrequency.WEEKLY,
            weekdays = setOf(DayOfWeek.TUESDAY),
            startDate = LocalDate.of(2026, 10, 20),
            endDate = LocalDate.of(2026, 10, 20),
            timeZone = tokyoZone,
        )
        val travelInstant = Instant.parse("2026-10-19T18:00:00Z")
        val originalDefault = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals(LocalDate.of(2026, 10, 20), tokyoTuesdayPlan.localDateAt(travelInstant))
            assertTrue(tokyoTuesdayPlan.isScheduledAtInstant(travelInstant))

            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Rome"))
            assertEquals(LocalDate.of(2026, 10, 20), tokyoTuesdayPlan.localDateAt(travelInstant))
            assertTrue(tokyoTuesdayPlan.isScheduledAtInstant(travelInstant))
        } finally {
            TimeZone.setDefault(originalDefault)
        }
    }
}
