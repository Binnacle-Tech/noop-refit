package com.noop.stepmerge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * JVM unit tests for the pure step-arbiter core (mirrors the HealthExportPlanTest style).
 *
 * Covers the spec matrix (disjoint / overlap / full cover / zero cover / 3-source ranking /
 * hour proration) PLUS the two multi-HC-source cases that pin the one thing that can still go
 * wrong: flattening distinct writers into one tier double-counts, and the authoritative-zero
 * rule lets a higher-ranked source's zero block a lower one. The adapter contract must respect
 * both — these tests document the core's behaviour so the adapter is written against fact.
 *
 * Target path: android/app/src/test/java/com/noop/stepmerge/StepArbiterTest.kt
 */
class StepArbiterTest {

    private fun iv(startMs: Long, endMs: Long, count: Double) = StepInterval(startMs, endMs, count)
    private fun counts(list: List<StepInterval>) = list.map { Triple(it.startMs, it.endMs, it.count) }
    private fun total(list: List<StepInterval>) = list.sumOf { it.count }

    // ---- StepInterval invariant ----

    @Test fun stepInterval_rejectsInvertedBounds() {
        assertThrows(IllegalArgumentException::class.java) { StepInterval(100, 50, 1.0) }
    }

    @Test fun stepInterval_allowsZeroLength() {
        // Zero-length is legal to construct (arbitrate/bucket skip it); only inverted throws.
        assertEquals(0L, StepInterval(100, 100, 0.0).durationMs)
    }

    // ---- arbitrate: the spec matrix ----

    @Test fun fullPhoneCoverage_whoopContributesNothing() {
        val phone = listOf(iv(0, 60_000, 600.0))
        val whoop = listOf(iv(0, 60_000, 999.0))
        val out = StepArbiter.arbitrate(listOf(phone, whoop))
        assertEquals(listOf(Triple(0L, 60_000L, 600.0)), counts(out))
    }

    @Test fun zeroPhoneCoverage_whoopPassesThrough() {
        val out = StepArbiter.arbitrate(listOf(emptyList(), listOf(iv(0, 60_000, 500.0))))
        assertEquals(listOf(Triple(0L, 60_000L, 500.0)), counts(out))
    }

    @Test fun gapFill_proratesWhoopOverTheUncoveredMiddle() {
        // Phone covers [0,20k) and [40k,60k); Whoop (600 over 60k = 0.01/ms) fills [20k,40k) → 200.
        val phone = listOf(iv(0, 20_000, 200.0), iv(40_000, 60_000, 200.0))
        val whoop = listOf(iv(0, 60_000, 600.0))
        val out = StepArbiter.arbitrate(listOf(phone, whoop))
        assertEquals(
            listOf(
                Triple(0L, 20_000L, 200.0),
                Triple(20_000L, 40_000L, 200.0),
                Triple(40_000L, 60_000L, 200.0),
            ),
            counts(out),
        )
        assertEquals(600.0, total(out), 1e-9)
    }

    @Test fun partialOverlap_keepsUncoveredSubportionsProrated() {
        // Phone owns the middle [20k,40k)=250; Whoop (0.01/ms) fills the two ends at 200 each.
        val phone = listOf(iv(20_000, 40_000, 250.0))
        val whoop = listOf(iv(0, 60_000, 600.0))
        val out = StepArbiter.arbitrate(listOf(phone, whoop))
        assertEquals(
            listOf(
                Triple(0L, 20_000L, 200.0),
                Triple(20_000L, 40_000L, 250.0),
                Triple(40_000L, 60_000L, 200.0),
            ),
            counts(out),
        )
    }

    @Test fun threeSourceRanking_eachFillsWhatHigherRanksLeave() {
        // A>B>C. A owns [0,10k)=100; B (10/ms) fills [10k,30k)=200; C (10/ms) fills [30k,60k)=300.
        val a = listOf(iv(0, 10_000, 100.0))
        val b = listOf(iv(0, 30_000, 300.0))
        val c = listOf(iv(0, 60_000, 600.0))
        val out = StepArbiter.arbitrate(listOf(a, b, c))
        assertEquals(
            listOf(
                Triple(0L, 10_000L, 100.0),
                Triple(10_000L, 30_000L, 200.0),
                Triple(30_000L, 60_000L, 300.0),
            ),
            counts(out),
        )
        assertEquals(600.0, total(out), 1e-9)
    }

    @Test fun disjointSources_bothRetainedInFull() {
        val a = listOf(iv(0, 10_000, 100.0))
        val b = listOf(iv(20_000, 30_000, 90.0))
        val out = StepArbiter.arbitrate(listOf(a, b))
        assertEquals(listOf(Triple(0L, 10_000L, 100.0), Triple(20_000L, 30_000L, 90.0)), counts(out))
    }

    // ---- arbitrate: the two multi-HC-source cases (the real risk) ----

    @Test fun distinctWriters_asSeparateTiers_dedupNotDoubleCount() {
        // Phone and watch logged the SAME window. As separate ranked tiers, tier 1 wins, tier 2 blocked.
        val out = StepArbiter.arbitrate(listOf(listOf(iv(0, 60_000, 100.0)), listOf(iv(0, 60_000, 95.0))))
        assertEquals(listOf(Triple(0L, 60_000L, 100.0)), counts(out))
    }

    @Test fun flatteningTwoWritersIntoOneTier_doubleCounts_preconditionGuard() {
        // DOCUMENTS the hazard: two overlapping writers flattened into ONE source list violate the
        // "non-overlapping within a source" precondition and BOTH emit → the #589 double-count returns.
        // The adapter MUST NOT do this; keep each dataOrigin its own ranked tier.
        val flattened = listOf(listOf(iv(0, 60_000, 100.0), iv(0, 60_000, 95.0)))
        val out = StepArbiter.arbitrate(flattened)
        assertEquals(195.0, total(out), 1e-9)   // <-- the wrong answer, pinned so the contract is explicit
    }

    @Test fun authoritativeZero_higherRankBlocksLower() {
        // A phone-recorded 0-step window blocks a lower-ranked source from filling it → 0.
        // Correct for phone > Whoop (trust the phone). CAUTION for phone > watch: a pocketed phone's 0
        // would suppress the watch's real steps — rank two real pedometers by day-total, not fixed order.
        val out = StepArbiter.arbitrate(listOf(listOf(iv(0, 60_000, 0.0)), listOf(iv(0, 60_000, 95.0))))
        assertEquals(listOf(Triple(0L, 60_000L, 0.0)), counts(out))
    }

    // ---- bucketByHour: stable clock-hour re-slice ----

    private val H = 3_600_000L

    @Test fun bucket_singleHour() {
        val b = StepArbiter.bucketByHour(listOf(iv(0, H / 2, 100.0)))
        assertEquals(mapOf(0L to 100.0), b.mapValues { it.value })
    }

    @Test fun bucket_straddlesHourBoundary_proratesByDuration() {
        // [3.0e6, 5.0e6): 0.6e6 in hour 0, 1.4e6 in hour 1, of 2.0e6 total → 30 / 70 of 100.
        val b = StepArbiter.bucketByHour(listOf(iv(3_000_000, 5_000_000, 100.0)))
        assertEquals(30.0, b[0]!!, 1e-9)
        assertEquals(70.0, b[1]!!, 1e-9)
    }

    @Test fun bucket_conservesTotalAcrossHours() {
        val ivs = listOf(iv(0, 5_000_000, 250.0), iv(7_000_000, 7_200_000, 40.0))
        val b = StepArbiter.bucketByHour(ivs)
        assertEquals(290.0, b.values.sum(), 1e-9)
    }

    @Test fun bucket_skipsZeroCountAndZeroLength() {
        val b = StepArbiter.bucketByHour(listOf(iv(0, H, 0.0), iv(H, H, 5.0)))
        assertTrue(b.isEmpty())
    }

    @Test fun bucket_hourKeyIsGloballyUniqueEpochHour() {
        // Same clock-hour on two different days must land in DIFFERENT buckets (stable, collision-free id).
        val day0 = StepArbiter.bucketByHour(listOf(iv(0, 60_000, 10.0)))
        val day1 = StepArbiter.bucketByHour(listOf(iv(24 * H, 24 * H + 60_000, 10.0)))
        assertEquals(setOf(0L), day0.keys)
        assertEquals(setOf(24L), day1.keys)
    }
}
