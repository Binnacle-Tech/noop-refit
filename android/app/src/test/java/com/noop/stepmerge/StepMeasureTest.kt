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

    // ---- per-day accrual ----

    private fun day(d: String, hours: Double, phone: Double, whoop: Double) =
        StepMeasure.DayStat(d, (hours * H).toLong(), phone, whoop)

    @Test fun clip_trimsAndProratesToWindow() {
        assertEquals(
            listOf(Triple(25L, 75L, 50.0)),
            StepMeasure.clip(listOf(iv(0, 100, 100.0)), 25, 75).map { Triple(it.startMs, it.endMs, it.count) },
        )
        assertTrue(StepMeasure.clip(listOf(iv(0, 100, 100.0)), 200, 300).isEmpty())
    }

    @Test fun accrue_overwritesSameDay_newestFirst() {
        val prior = listOf(day("2026-07-01", 2.0, 100.0, 250.0), day("2026-07-02", 2.0, 999.0, 999.0))
        val out = StepMeasure.accrue(prior, day("2026-07-02", 3.0, 200.0, 500.0))
        assertEquals(listOf("2026-07-02", "2026-07-01"), out.map { it.day })   // sorted newest-first
        assertEquals(200.0, out.first().phone, 1e-9)                            // today overwrote, not summed
    }

    @Test fun accrue_prunesToKeepDays() {
        val prior = listOf(day("2026-07-01", 1.0, 1.0, 1.0), day("2026-07-02", 1.0, 1.0, 1.0))
        val out = StepMeasure.accrue(prior, day("2026-07-03", 1.0, 1.0, 1.0), keepDays = 2)
        assertEquals(listOf("2026-07-03", "2026-07-02"), out.map { it.day })   // oldest dropped
    }

    @Test fun accruedFactor_averagesOverDays_thenGates() {
        // 3 days summing to 7h / phone 1000 / whoop 2500 → ratio 2.5 → factor 0.4.
        val stats = listOf(
            day("2026-07-01", 3.0, 400.0, 1000.0),
            day("2026-07-02", 2.0, 300.0, 750.0),
            day("2026-07-03", 2.0, 300.0, 750.0),
        )
        assertEquals(0.4, StepMeasure.accruedFactor(stats)!!, 1e-9)
        // Same ratio but only 4h total accrued → still waiting.
        assertNull(StepMeasure.accruedFactor(listOf(day("2026-07-01", 4.0, 1000.0, 2500.0))))
    }

    @Test fun encodeDecode_roundTrips() {
        val stats = listOf(day("2026-07-01", 1.0, 1000.0, 2500.0), day("2026-07-02", 2.5, 333.0, 812.0))
        val back = StepMeasure.decode(StepMeasure.encode(stats))
        assertEquals(stats.map { it.day }, back.map { it.day })
        assertEquals(2500.0, back.first().whoop, 1e-9)
        assertEquals(2.5 * H, back[1].coMs.toDouble(), 0.0)
    }

    @Test fun decode_toleratesEmptyAndGarbage() {
        assertTrue(StepMeasure.decode(null).isEmpty())
        assertTrue(StepMeasure.decode("").isEmpty())
        assertEquals(1, StepMeasure.decode("bad;2026-07-01,3600000,10.0,20.0;also,bad").size)
    }
}
