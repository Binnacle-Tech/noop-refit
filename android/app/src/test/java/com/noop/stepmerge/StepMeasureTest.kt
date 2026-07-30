package com.noop.stepmerge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the v2 measurement gate. Pins that bias is measured ONLY over the co-covered window
 * (Whoop-only gaps are not comparable), prorates correctly, and reports the ratio that gates v2.
 */
class StepMeasureTest {

    private fun iv(startMs: Long, endMs: Long, count: Double) = StepInterval(startMs, endMs, count)

    @Test fun bias_measuresOnlyCoCoveredWindow() {
        // Phone covers [0,100); Whoop covers [0,200) at 2x pace. Only [0,100) is comparable.
        // Phone in [0,100) = 100. Whoop in [0,100) = half of its 400 = 200. ratio = 2.0.
        val phone = listOf(iv(0, 100, 100.0))
        val whoop = listOf(iv(0, 200, 400.0))
        val b = StepMeasure.crossSourceBias(phone, whoop)
        assertEquals(100L, b.coCoveredMs)
        assertEquals(100.0, b.higherSteps, 1e-9)
        assertEquals(200.0, b.lowerSteps, 1e-9)
        assertEquals(2.0, b.ratio!!, 1e-9)
    }

    @Test fun bias_agreementReadsRatioOne() {
        // Same window, same count → the existing calibration already reconciles them (v2 unneeded).
        val a = listOf(iv(0, 60_000, 500.0))
        val b = listOf(iv(0, 60_000, 500.0))
        assertEquals(1.0, StepMeasure.crossSourceBias(a, b).ratio!!, 1e-9)
    }

    @Test fun bias_noOverlap_isNotComparable() {
        val phone = listOf(iv(0, 100, 50.0))
        val whoop = listOf(iv(200, 300, 90.0))
        val b = StepMeasure.crossSourceBias(phone, whoop)
        assertEquals(0L, b.coCoveredMs)
        assertNull(b.ratio)
    }

    @Test fun bias_partialOverlap_proratesBothSides() {
        // Phone [0,100)=100 (1/ms). Whoop [50,150)=200 (2/ms). Overlap [50,100): phone 50, whoop 100.
        val phone = listOf(iv(0, 100, 100.0))
        val whoop = listOf(iv(50, 150, 200.0))
        val b = StepMeasure.crossSourceBias(phone, whoop)
        assertEquals(50L, b.coCoveredMs)
        assertEquals(50.0, b.higherSteps, 1e-9)
        assertEquals(100.0, b.lowerSteps, 1e-9)
        assertEquals(2.0, b.ratio!!, 1e-9)
    }

    @Test fun bias_fragmentedCoverage_sumsAllOverlaps() {
        // Phone covers two separate windows; Whoop spans both. Co-covered = both phone windows.
        val phone = listOf(iv(0, 100, 100.0), iv(300, 400, 100.0))
        val whoop = listOf(iv(0, 500, 500.0))   // 1/ms
        val b = StepMeasure.crossSourceBias(phone, whoop)
        assertEquals(200L, b.coCoveredMs)
        assertEquals(200.0, b.higherSteps, 1e-9)   // both phone windows
        assertEquals(200.0, b.lowerSteps, 1e-9)    // whoop's 1/ms over the same 200ms
        assertEquals(1.0, b.ratio!!, 1e-9)
    }

    @Test fun bias_higherSilentInWindow_ratioNull() {
        // Phone recorded 0 in the co-covered window → no denominator, not a divide-by-zero.
        val phone = listOf(iv(0, 100, 0.0))
        val whoop = listOf(iv(0, 100, 50.0))
        assertTrue(StepMeasure.crossSourceBias(phone, whoop).ratio == null)
    }

    // ---- calibrationFactor: the opt-in auto-cal gate ----

    private val H = 3_600_000L
    private fun bias(hours: Double, higher: Double, lower: Double) =
        StepMeasure.CrossSourceBias((hours * H).toLong(), higher, lower)

    @Test fun factor_appliesInverseRatio_whenConfident() {
        // 7h co-covered, strap reads 2.5x phone → scale strap by 1/2.5 = 0.4.
        assertEquals(0.4, StepMeasure.calibrationFactor(bias(7.0, 1000.0, 2500.0))!!, 1e-9)
    }

    @Test fun factor_nullBelowMinCoCoveredHours() {
        // Same 2.5x bias but only 1h overlap → too thin, leave the strap raw.
        assertNull(StepMeasure.calibrationFactor(bias(1.0, 1000.0, 2500.0)))
    }

    @Test fun factor_nullWhenRatioOutOfSaneBand() {
        // 50x is beyond the guard band (a freak window) → no correction.
        assertNull(StepMeasure.calibrationFactor(bias(8.0, 100.0, 5000.0)))
    }

    @Test fun factor_nullWhenNoOverlapToMeasure() {
        assertNull(StepMeasure.calibrationFactor(bias(0.0, 0.0, 0.0)))
    }
}
