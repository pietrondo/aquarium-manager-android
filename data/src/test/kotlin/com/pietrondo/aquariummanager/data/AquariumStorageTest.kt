package com.pietrondo.aquariummanager.data

import com.pietrondo.aquariummanager.domain.DosingFrequency
import com.pietrondo.aquariummanager.domain.SpeciesKind
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class AquariumStorageTest {
    private val converters = AquariumConverters()
    private val id1 = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val id2 = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @Test fun decimalTextIsExactAndCanonical() {
        for (input in listOf("123.456789012345678901234567890", "100.000", "1E+3", "0.0000000000000000001")) {
            val value = BigDecimal(input)
            val text = converters.decimalToText(value)!!
            assertEquals(0, value.compareTo(converters.textToDecimal(text)))
            assertEquals(value.stripTrailingZeros().toPlainString(), text)
        }
        assertEquals(null, converters.decimalToText(null))
        assertEquals(null, converters.textToDecimal(null))
    }

    @Test fun timestampsRoundtripInUtcWithNanoseconds() {
        val instant = Instant.parse("2026-10-19T12:34:56.123456789Z")
        assertEquals(instant.toString(), converters.instantToText(instant))
        assertEquals(instant, converters.textToInstant(converters.instantToText(instant)))
        assertNull(converters.instantToText(null))
        assertNull(converters.textToInstant(null))
    }

    @Test fun localDateZoneIdEnumsAndWeekdaysRoundtripLosslessly() {
        val date = LocalDate.of(2026, 10, 19)
        assertEquals("2026-10-19", converters.localDateToText(date))
        assertEquals(date, converters.textToLocalDate("2026-10-19"))
        assertNull(converters.localDateToText(null))
        assertNull(converters.textToLocalDate(null))

        val zone = ZoneId.of("Europe/Rome")
        assertEquals("Europe/Rome", converters.zoneIdToText(zone))
        assertEquals(zone, converters.textToZoneId("Europe/Rome"))
        assertNull(converters.zoneIdToText(null))
        assertNull(converters.textToZoneId(null))

        assertEquals("FAUNA", converters.speciesKindToText(SpeciesKind.FAUNA))
        assertEquals(SpeciesKind.FLORA, converters.textToSpeciesKind("FLORA"))
        assertEquals("WEEKLY", converters.dosingFrequencyToText(DosingFrequency.WEEKLY))
        assertEquals(DosingFrequency.DAILY, converters.textToDosingFrequency("DAILY"))

        val days = setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        assertEquals("1,3,5", converters.weekdaysToText(days))
        assertEquals(days, converters.textToWeekdays("1,3,5"))
        assertEquals("", converters.weekdaysToText(emptySet()))
        assertEquals(emptySet<DayOfWeek>(), converters.textToWeekdays(""))
        assertThrows(IllegalArgumentException::class.java) { converters.textToWeekdays("5,1") }
        assertThrows(IllegalArgumentException::class.java) { converters.textToWeekdays("1,1") }
        assertThrows(IllegalArgumentException::class.java) { converters.textToWeekdays("8") }
    }

    @Test fun invalidIdentifiersAndNamesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { converters.textToUuid("1-1-1-1-1") }
        assertThrows(IllegalArgumentException::class.java) { converters.textToUuid("00000000-0000-0000-0000-00000000000A") }
        assertThrows(IllegalArgumentException::class.java) { entity(name = " ") }
        assertThrows(IllegalArgumentException::class.java) { AquariumProfileEntity(id1, " ") }
        assertThrows(IllegalArgumentException::class.java) { ProductEntity(id1, "") }
        assertThrows(IllegalArgumentException::class.java) { SpeciesEntity(id1, SpeciesKind.FAUNA, null, " ") }
        assertThrows(IllegalArgumentException::class.java) { SpeciesEntity(id1, SpeciesKind.FLORA, "   ", "Java Fern") }
    }

    @Test fun entitiesEnforceDomainValidationRulesAtConstruction() {
        assertThrows(IllegalArgumentException::class.java) {
            MeasurementEntity(id1, id2, Instant.EPOCH)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MeasurementEntity(id1, id2, Instant.EPOCH, no3MgL = BigDecimal("-0.1"))
        }
        val validMeasurement = MeasurementEntity(id1, id2, Instant.EPOCH, ph = BigDecimal("7.2"))
        assertNull(validMeasurement.khDkh)
        assertNull(validMeasurement.no3MgL)

        assertThrows(IllegalArgumentException::class.java) {
            MaintenanceEventEntity(id1, id2, " ", Instant.EPOCH)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MaintenanceEventEntity(id1, id2, "WATER_CHANGE", Instant.EPOCH, changedLitres = BigDecimal.ZERO)
        }

        assertThrows(IllegalArgumentException::class.java) {
            DosingPlanEntity(
                id1, id2, id1, BigDecimal("1.0"), "mL", DosingFrequency.WEEKLY, emptySet(),
                LocalDate.of(2026, 10, 19), null, ZoneId.of("UTC"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            DosingEventEntity(id1, null, id2, Instant.EPOCH, "Fert", BigDecimal.ZERO, "mL")
        }
        assertThrows(IllegalArgumentException::class.java) {
            StockEntryEntity(id1, id2, id1, BigDecimal.ZERO, Instant.EPOCH, null, null)
        }
        val unknownStock = StockEntryEntity(id1, id2, id1, null, Instant.EPOCH, null, null)
        assertNull(unknownStock.quantity)
    }

    @Test fun entityAppliesDomainVolumeRules() {
        assertThrows(IllegalArgumentException::class.java) { entity(net = BigDecimal.ZERO) }
        assertThrows(IllegalArgumentException::class.java) { entity(net = BigDecimal("101"), gross = BigDecimal("100")) }
    }

    @Test fun summaryMappingIsExplicitAndDoesNotExposeStorageFields() {
        val row = entity()
        assertEquals(row.id, row.toSummary().id)
        assertEquals(row.name, row.toSummary().name)
    }

    private fun entity(name: String = "Reef", net: BigDecimal? = null, gross: BigDecimal? = null) = AquariumEntity(
        id1, name, net, gross, null, Instant.EPOCH,
    )
}
