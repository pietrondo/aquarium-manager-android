package com.pietrondo.aquariummanager.domain

import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class AquariumVolumesTest {
    @Test fun unknownVolumesAreAllowed() { validateAquariumVolumes(null, null) }
    @Test fun exactValuesAndEqualVolumesAreAllowed() {
        validateAquariumVolumes(BigDecimal("123.456789012345678901"), BigDecimal("123.4567890123456789010"))
    }
    @Test fun decimalExpansionIsBoundedBeforePersistenceButNormalDecimalsRemainValid() {
        validateAquariumVolumes(BigDecimal("1.234567890123456789"), null)
        assertThrows(IllegalArgumentException::class.java) {
            validateAquariumVolumes(BigDecimal("1E+100000000"), null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateMeasurementReadings(BigDecimal("1E-100000000"), null, null, null, null, null, null)
        }
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

    @Test fun nonBlankValidationRejectsEmptyAndWhitespace() {
        validateNonBlank("Reef", "Aquarium name")
        assertThrows(IllegalArgumentException::class.java) { validateNonBlank("", "Aquarium name") }
        assertThrows(IllegalArgumentException::class.java) { validateNonBlank("   ", "Aquarium name") }
    }

    @Test fun measurementRequiresAtLeastOneReadingAndDistinguishesNullFromZero() {
        assertThrows(IllegalArgumentException::class.java) {
            validateMeasurementReadings(null, null, null, null, null, null, null)
        }
        validateMeasurementReadings(null, BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null)
    }

    @Test fun measurementRejectsNegativeConcentrationsHardnessAndTds() {
        assertThrows(IllegalArgumentException::class.java) {
            validateMeasurementReadings(null, BigDecimal("-0.1"), null, null, null, null, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateMeasurementReadings(null, null, BigDecimal("-1"), null, null, null, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateMeasurementReadings(null, null, null, BigDecimal("-0.001"), null, null, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateMeasurementReadings(null, null, null, null, BigDecimal("-5"), null, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateMeasurementReadings(null, null, null, null, null, BigDecimal("-10"), null)
        }
    }

    @Test fun measurementAllowsAnyFinitePhAndTemperatureWithoutBiologicalRangeHardcoding() {
        validateMeasurementReadings(BigDecimal("-1.5"), null, null, null, null, null, BigDecimal("-5.0"))
        validateMeasurementReadings(BigDecimal("15.2"), null, null, null, null, null, BigDecimal("65.0"))
    }

    @Test fun maintenanceEventRequiresNonBlankTypeAndPositiveChangedLitresWhenPresent() {
        validateMaintenanceEvent("WATER_CHANGE", null)
        validateMaintenanceEvent("WATER_CHANGE", BigDecimal("12.5"))
        assertThrows(IllegalArgumentException::class.java) { validateMaintenanceEvent(" ", BigDecimal("10")) }
        assertThrows(IllegalArgumentException::class.java) { validateMaintenanceEvent("WATER_CHANGE", BigDecimal.ZERO) }
        assertThrows(IllegalArgumentException::class.java) { validateMaintenanceEvent("WATER_CHANGE", BigDecimal("-1")) }
    }

    @Test fun dosingPlanAndEventValidateDoseUnitWeekdaysAndDateOrder() {
        val start = LocalDate.of(2026, 10, 19)
        validateDosingPlan(BigDecimal("2.5"), "mL", DosingFrequency.DAILY, emptySet(), start, null)
        validateDosingPlan(BigDecimal("1"), "drops", DosingFrequency.WEEKLY, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), start, start)
        assertThrows(IllegalArgumentException::class.java) {
            validateDosingPlan(BigDecimal.ZERO, "mL", DosingFrequency.DAILY, emptySet(), start, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateDosingPlan(BigDecimal("1"), " ", DosingFrequency.DAILY, emptySet(), start, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateDosingPlan(BigDecimal("1"), "mL", DosingFrequency.WEEKLY, emptySet(), start, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateDosingPlan(BigDecimal("1"), "mL", DosingFrequency.DAILY, emptySet(), start, start.minusDays(1))
        }

        validateDosingEvent("All-For-Reef", BigDecimal("5.0"), "mL")
        assertThrows(IllegalArgumentException::class.java) { validateDosingEvent(" ", BigDecimal("5.0"), "mL") }
        assertThrows(IllegalArgumentException::class.java) { validateDosingEvent("All-For-Reef", BigDecimal.ZERO, "mL") }
        assertThrows(IllegalArgumentException::class.java) { validateDosingEvent("All-For-Reef", BigDecimal("5.0"), " ") }
    }

    @Test fun speciesAndStockEntriesValidateNamesFractionalOrUnknownQuantityAndExitOrder() {
        validateSpecies("Amano Shrimp", null)
        validateSpecies("Amano Shrimp", "Caridina multidentata")
        assertThrows(IllegalArgumentException::class.java) { validateSpecies(" ", "Caridina multidentata") }
        assertThrows(IllegalArgumentException::class.java) { validateSpecies("Amano Shrimp", "   ") }

        val intro = Instant.parse("2026-10-19T10:00:00Z")
        validateStockEntry(null, intro, null)
        validateStockEntry(BigDecimal("0.5"), intro, intro)
        validateStockEntry(BigDecimal("10"), intro, intro.plusSeconds(60))
        assertThrows(IllegalArgumentException::class.java) { validateStockEntry(BigDecimal.ZERO, intro, null) }
        assertThrows(IllegalArgumentException::class.java) { validateStockEntry(BigDecimal("-2"), intro, null) }
        assertThrows(IllegalArgumentException::class.java) { validateStockEntry(BigDecimal("1"), intro, intro.minusSeconds(1)) }
    }
}
