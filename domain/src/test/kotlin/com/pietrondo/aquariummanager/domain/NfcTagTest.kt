package com.pietrondo.aquariummanager.domain

import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcTagTest {
    private val aquariumId = UUID.fromString("00000000-0000-4000-8000-000000000301")

    @Test
    fun acceptsLowercaseHexadecimalTagIds() {
        assertTrue(isValidNfcTagId("04a1b2c3d4e5f6"))
        assertTrue(isValidNfcTagId("abcd"))
    }

    @Test
    fun rejectsBlankNonHexadecimalAndOversizedTagIds() {
        assertFalse(isValidNfcTagId(""))
        assertFalse(isValidNfcTagId("   "))
        assertFalse(isValidNfcTagId("xyz"))
        assertFalse(isValidNfcTagId("04A1B2C3D4E5F6"))
        assertFalse(isValidNfcTagId("abc"))
        assertFalse(isValidNfcTagId("a".repeat(65)))
    }

    @Test
    fun normalizesRawChipUid() {
        assertEquals("04a1b2c3d4e5f6", normalizeNfcTagId("  04A1B2C3D4E5F6  "))
    }

    @Test
    fun bindingValidatesItsTagId() {
        val binding = NfcTagBinding(
            tagId = "04a1b2c3d4e5f6",
            aquariumId = aquariumId,
            createdAt = Instant.parse("2026-11-02T10:00:00Z"),
        )
        assertEquals(aquariumId, binding.aquariumId)

        assertThrows(IllegalArgumentException::class.java) {
            NfcTagBinding(
                tagId = "not-hex",
                aquariumId = aquariumId,
                createdAt = Instant.parse("2026-11-02T10:00:00Z"),
            )
        }
    }
}
