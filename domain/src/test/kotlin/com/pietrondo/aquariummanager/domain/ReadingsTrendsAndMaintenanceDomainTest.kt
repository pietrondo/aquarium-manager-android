package com.pietrondo.aquariummanager.domain

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingsTrendsAndMaintenanceDomainTest {
    private val tankId = UUID.fromString("00000000-0000-4000-8000-000000000010")
    private val idSmall = UUID.fromString("00000000-0000-4000-8000-000000000001")
    private val idMiddle = UUID.fromString("00000000-0000-4000-8000-000000000002")
    private val idLarge = UUID.fromString("00000000-0000-4000-8000-000000000003")
    private val idFourth = UUID.fromString("00000000-0000-4000-8000-000000000004")

    @Test
    fun measurementDraftAndRecordEnforceMinimumOneReadingAndDistinguishNullFromZero() {
        assertThrows(IllegalArgumentException::class.java) {
            MeasurementDraft()
        }
        assertThrows(IllegalArgumentException::class.java) {
            MeasurementRecord(id = idSmall, aquariumId = tankId, recordedAt = Instant.EPOCH)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MeasurementDraft(no2MgL = BigDecimal("-0.01"))
        }

        val zeroNo2Draft = MeasurementDraft(no2MgL = BigDecimal.ZERO)
        assertEquals(0, BigDecimal.ZERO.compareTo(zeroNo2Draft.no2MgL))
        assertNull(zeroNo2Draft.ph)
        assertNull(zeroNo2Draft.no3MgL)

        val record = MeasurementRecord(
            id = idSmall,
            aquariumId = tankId,
            recordedAt = Instant.parse("2026-10-19T12:00:00Z"),
            ph = BigDecimal("6.8"),
            khDkh = BigDecimal.ZERO,
            ghDgh = BigDecimal("5.5"),
            no2MgL = BigDecimal.ZERO,
            no3MgL = null,
            tdsPpm = BigDecimal("145"),
            temperatureC = BigDecimal("24.5"),
        )
        assertEquals(BigDecimal("6.8"), record.valueFor(WaterParameter.PH))
        assertEquals(BigDecimal.ZERO, record.valueFor(WaterParameter.KH_DKH))
        assertEquals(BigDecimal("5.5"), record.valueFor(WaterParameter.GH_DGH))
        assertEquals(BigDecimal.ZERO, record.valueFor(WaterParameter.NO2_MGL))
        assertNull(record.valueFor(WaterParameter.NO3_MGL))
        assertEquals(BigDecimal("145"), record.valueFor(WaterParameter.TDS_PPM))
        assertEquals(BigDecimal("24.5"), record.valueFor(WaterParameter.TEMPERATURE_C))
    }

    @Test
    fun parameterTrendCalculatorPreservesExplicitNullGapsWithoutFabricatedSafeBands() {
        val t1 = Instant.parse("2026-10-16T10:00:00Z")
        val t2 = Instant.parse("2026-10-17T10:00:00Z")
        val t3 = Instant.parse("2026-10-18T10:00:00Z")
        val t4 = Instant.parse("2026-10-19T10:00:00Z")

        val records = listOf(
            MeasurementRecord(id = idFourth, aquariumId = tankId, recordedAt = t4, no3MgL = BigDecimal("2.5"), ph = BigDecimal("7.0")),
            MeasurementRecord(id = idLarge, aquariumId = tankId, recordedAt = t3, no3MgL = BigDecimal.ZERO, ph = BigDecimal("6.9")),
            MeasurementRecord(id = idMiddle, aquariumId = tankId, recordedAt = t2, no3MgL = null, ph = BigDecimal("6.8")),
            MeasurementRecord(id = idSmall, aquariumId = tankId, recordedAt = t1, no3MgL = BigDecimal("10.0"), ph = BigDecimal("6.7")),
        )

        val no3Trend = calculateParameterTrend(WaterParameter.NO3_MGL, records)
        assertEquals(WaterParameter.NO3_MGL, no3Trend.parameter)
        assertEquals("NO3 (mg/L)", no3Trend.parameter.displayTitle)
        assertEquals(4, no3Trend.points.size)
        // Chronological order: t1, t2, t3, t4
        assertEquals(listOf(t1, t2, t3, t4), no3Trend.points.map { it.recordedAt })
        // Explicit null gap at t2, exact zero at t3
        assertEquals(0, BigDecimal("10").compareTo(no3Trend.points[0].value))
        assertNull(no3Trend.points[1].value)
        assertEquals(0, BigDecimal.ZERO.compareTo(no3Trend.points[2].value))
        assertEquals(0, BigDecimal("2.5").compareTo(no3Trend.points[3].value))

        assertEquals(3, no3Trend.measuredCount)
        assertEquals(1, no3Trend.missingGapCount)
        assertEquals(0, BigDecimal("2.5").compareTo(no3Trend.latestValue))
        assertEquals(t4, no3Trend.latestRecordedAt)
        assertEquals(0, BigDecimal.ZERO.compareTo(no3Trend.previousValue))
        assertEquals(0, BigDecimal("2.5").compareTo(no3Trend.deltaFromPrevious))
        assertEquals(0, BigDecimal.ZERO.compareTo(no3Trend.minValue))
        assertEquals(0, BigDecimal("10").compareTo(no3Trend.maxValue))
        assertEquals(ParameterTrendDirection.RISING, no3Trend.direction)
        assertEquals("↑ Rising (+2.5 mg/L)", no3Trend.accessibilityCue)
    }

    @Test
    fun parameterTrendCalculatorHandlesNoDataSinglePointFallingStableAndDeterministicSameTimestampOrder() {
        val emptyTrends = calculateParameterTrends(emptyList())
        assertEquals(7, emptyTrends.size)
        assertTrue(emptyTrends.all { it.direction == ParameterTrendDirection.NO_DATA })
        assertTrue(emptyTrends.all { it.accessibilityCue == "— No readings" })

        val sameTimestamp = Instant.parse("2026-10-19T15:00:00Z")
        // Per repository ordering (recordedAt DESC, id ASC), idSmall is the newest when recordedAt is equal
        val tieRecords = listOf(
            MeasurementRecord(
                id = idSmall,
                aquariumId = tankId,
                recordedAt = sameTimestamp,
                ph = BigDecimal("7.1"),
                khDkh = BigDecimal("4.00"),
                temperatureC = BigDecimal("24.0"),
            ),
            MeasurementRecord(
                id = idLarge,
                aquariumId = tankId,
                recordedAt = sameTimestamp,
                ph = null,
                khDkh = BigDecimal("4.0"),
                temperatureC = BigDecimal("25.5"),
            ),
        )

        val trendsByParam = calculateParameterTrends(tieRecords).associateBy { it.parameter }

        val phSummary = trendsByParam.getValue(WaterParameter.PH)
        assertEquals(ParameterTrendDirection.SINGLE_POINT, phSummary.direction)
        assertEquals("• Single reading", phSummary.accessibilityCue)
        assertEquals(0, BigDecimal("7.1").compareTo(phSummary.latestValue))

        val khSummary = trendsByParam.getValue(WaterParameter.KH_DKH)
        assertEquals(ParameterTrendDirection.STABLE, khSummary.direction)
        assertEquals("→ Stable (0 dKH)", khSummary.accessibilityCue)

        val tempSummary = trendsByParam.getValue(WaterParameter.TEMPERATURE_C)
        assertEquals(ParameterTrendDirection.FALLING, tempSummary.direction)
        assertEquals("↓ Falling (-1.5 °C)", tempSummary.accessibilityCue)
        assertEquals(0, BigDecimal("24.0").compareTo(tempSummary.latestValue))
        assertEquals(0, BigDecimal("25.5").compareTo(tempSummary.previousValue))
    }

    @Test
    fun maintenanceDraftAndVolumeAdvisoryWarningDoNotInventHardCap() {
        assertThrows(IllegalArgumentException::class.java) {
            MaintenanceDraft(type = "   ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MaintenanceDraft(type = "WATER_CHANGE", changedLitres = BigDecimal.ZERO)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MaintenanceDraft(type = "WATER_CHANGE", changedLitres = BigDecimal("-5"))
        }

        // Large flush > net volume is valid in domain (no hard cap)
        val largeWaterChange = MaintenanceDraft(
            type = "WATER_CHANGE",
            changedLitres = BigDecimal("75"),
            notes = "Major reset water change",
        )
        assertEquals(0, BigDecimal("75").compareTo(largeWaterChange.changedLitres))

        // Advisory warning is returned when changedLitres > netLitres (or grossLitres when net is null)
        val warningExceedsNet = evaluateMaintenanceVolumeWarning(
            changedLitres = BigDecimal("75"),
            netLitres = BigDecimal("60"),
            grossLitres = BigDecimal("70"),
        )
        assertNotNull(warningExceedsNet)
        assertTrue(warningExceedsNet!!.contains("75 L"))
        assertTrue(warningExceedsNet.contains("60 L"))

        val warningExceedsGrossWhenNetUnknown = evaluateMaintenanceVolumeWarning(
            changedLitres = BigDecimal("80"),
            netLitres = null,
            grossLitres = BigDecimal("70"),
        )
        assertNotNull(warningExceedsGrossWhenNetUnknown)
        assertTrue(warningExceedsGrossWhenNetUnknown!!.contains("80 L"))
        assertTrue(warningExceedsGrossWhenNetUnknown.contains("70 L"))

        // Within net volume or unknown volumes -> no warning
        assertNull(evaluateMaintenanceVolumeWarning(BigDecimal("30"), BigDecimal("60"), BigDecimal("70")))
        assertNull(evaluateMaintenanceVolumeWarning(null, BigDecimal("60"), BigDecimal("70")))
        assertNull(evaluateMaintenanceVolumeWarning(BigDecimal("100"), null, null))
    }
}
