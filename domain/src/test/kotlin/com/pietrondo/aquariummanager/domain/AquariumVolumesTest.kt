package com.pietrondo.aquariummanager.domain

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class AquariumVolumesTest {
    @Test fun unknownVolumesAreAllowed() { validateAquariumVolumes(null, null) }
    @Test fun exactValuesAndEqualVolumesAreAllowed() {
        validateAquariumVolumes(BigDecimal("123.456789012345678901"), BigDecimal("123.4567890123456789010"))
    }
    @Test fun suppliedVolumesMustBePositive() {
        for (value in listOf("0", "-0.01")) {
            assertThrows(IllegalArgumentException::class.java) { validateAquariumVolumes(BigDecimal(value), null) }
            assertThrows(IllegalArgumentException::class.java) { validateAquariumVolumes(null, BigDecimal(value)) }
        }
    }
    @Test fun netCannotExceedGross() {
        assertThrows(IllegalArgumentException::class.java) { validateAquariumVolumes(BigDecimal("20.000000000000000001"), BigDecimal("20")) }
    }
}
