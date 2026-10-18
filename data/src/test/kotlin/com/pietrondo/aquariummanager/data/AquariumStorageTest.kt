package com.pietrondo.aquariummanager.data

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class AquariumStorageTest {
    private val converters = AquariumConverters()
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
    }
    @Test fun invalidIdentifiersAndNamesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { converters.textToUuid("1-1-1-1-1") }
        assertThrows(IllegalArgumentException::class.java) { entity(name = " ") }
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
        UUID.fromString("00000000-0000-0000-0000-000000000001"), name, net, gross, null, Instant.EPOCH,
    )
}
