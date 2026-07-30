package com.noop.stepmerge

import com.noop.analytics.StepsCounter
import com.noop.data.StepSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the Whoop step adapter's counter-delta derivation. This is the trickiest adapter path
 * and it's pure enough to unit-test: it uses only [StepSample] (a plain data class) and the shared
 * [StepsCounter] kernel. Pins wrap-aware reconstruction, the gap/reset gate, calibration, and the
 * seconds→millis conversion, and — critically — that the interval sum equals the daily-total kernel so
 * the arbiter can't disagree with the number the app already shows.
 */
class StepSourcesTest {

    private fun s(ts: Long, counter: Int) = StepSample(deviceId = "d", ts = ts, counter = counter)

    @Test fun consecutiveDeltas_becomeIntervals_secondsToMillis() {
        val out = StepSources.whoopIntervals(
            listOf(s(100, 0), s(101, 10), s(102, 10), s(103, 25)), ticksPerStep = 1.0,
        )
        // 100→101: +10; 101→102: +0 (still, skipped); 102→103: +15. ts × 1000.
        assertEquals(
            listOf(
                Triple(100_000L, 101_000L, 10.0),
                Triple(102_000L, 103_000L, 15.0),
            ),
            out.map { Triple(it.startMs, it.endMs, it.count) },
        )
    }

    @Test fun calibrationDividesTicksPerStep() {
        val out = StepSources.whoopIntervals(listOf(s(0, 0), s(1, 20)), ticksPerStep = 2.0)
        assertEquals(10.0, out.single().count, 1e-9)   // 20 ticks / 2.0
    }

    @Test fun u16Wrap_isReconstructed_notDropped() {
        // 65530 → 10 across the wrap is a +16 increment, not a negative. Matches the kernel's `and 0xFFFF`.
        val out = StepSources.whoopIntervals(listOf(s(100, 65530), s(101, 10)), ticksPerStep = 1.0)
        assertEquals(16.0, out.single().count, 1e-9)
    }

    @Test fun bigJump_atOrAboveGapGate_isDropped() {
        // A delta >= MAX_STEP_DELTA (512) is a sync-gap/reset boundary, not steps → no interval emitted.
        val out = StepSources.whoopIntervals(
            listOf(s(0, 0), s(1, StepsCounter.MAX_STEP_DELTA)), ticksPerStep = 1.0,
        )
        assertTrue(out.isEmpty())
    }

    @Test fun intervalSumMatchesDailyKernel_parityWithDisplayedTotal() {
        // The whole point: sum of interval counts == StepsCounter.stepsInWindow / calibration, so the
        // arbiter's Whoop contribution never diverges from the app's own displayed daily steps.
        val samples = listOf(s(0, 0), s(1, 30), s(2, 30), s(3, 95), s(4, 110))
        val cal = 1.5
        val intervalsSum = StepSources.whoopIntervals(samples, cal).sumOf { it.count }
        val kernelSteps = StepsCounter.stepsInWindow(samples)!! / maxOf(cal, 0.5)
        assertEquals(kernelSteps, intervalsSum, 1e-9)
    }
}
