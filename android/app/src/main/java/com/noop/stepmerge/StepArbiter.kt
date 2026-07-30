package com.noop.stepmerge

/**
 * Pure-core step arbitration. No Android / Health Connect / Noop types — mirrors the
 * `HealthExportPlan` precedent so it is JVM-unit-testable and later-extractable.
 *
 * Responsibilities:
 *   1. arbitrate()   — ranked, N-source gap-fill merge over step intervals.
 *   2. bucketByHour() — re-slice the merged result into fixed clock-hour buckets for a
 *                       stable, orphan-free Health Connect write.
 *
 * The Whoop/phone adapters (which build the input lists) and the StepsRecord publisher
 * (which consumes bucketByHour) live in Android-side files; their contract is at the
 * bottom of this file.
 */

/**
 * A step count attributed to the half-open interval [startMs, endMs).
 *
 * [count] is intentionally a Double: Whoop steps are `counterDelta * calibrationCoefficient`
 * and are therefore fractional, and proration multiplies fractions. Carry Double through the
 * whole core; round to Int exactly once, at the Health Connect write boundary.
 */
data class StepInterval(
    val startMs: Long,
    val endMs: Long,
    val count: Double,
) {
    init { require(endMs >= startMs) { "endMs ($endMs) < startMs ($startMs)" } }
    val durationMs: Long get() = endMs - startMs
}

object StepArbiter {

    private const val HOUR_MS = 3_600_000L

    /** Half-open range used only for coverage bookkeeping. */
    private data class Range(val start: Long, val end: Long)

    /**
     * Ranked, N-source gap-fill.
     *
     * @param sourcesByPriority step intervals per source, HIGHEST priority first
     *        (e.g. listOf(phoneIntervals, whoopIntervals)). Intervals within one source
     *        must be non-overlapping — true for consecutive-counter-delta derivation.
     * @return retained, non-overlapping sub-intervals, sorted by start. A higher-priority
     *         source is authoritative across the FULL span of each of its records, including
     *         authoritative zeros (a phone-recorded 0-step minute blocks Whoop from filling it).
     *         Output may contain zero-count entries; the publisher filters those.
     */
    fun arbitrate(sourcesByPriority: List<List<StepInterval>>): List<StepInterval> {
        val out = ArrayList<StepInterval>()
        var covered = emptyList<Range>()   // sorted, non-overlapping (invariant held by union())

        for (source in sourcesByPriority) {
            for (iv in source) {
                if (iv.durationMs == 0L) continue                 // zero-length can't be split
                val gaps = subtract(Range(iv.startMs, iv.endMs), covered)
                if (gaps.isEmpty()) continue
                val perMs = iv.count / iv.durationMs              // uniform-pace assumption
                for (g in gaps) out += StepInterval(g.start, g.end, perMs * (g.end - g.start))
            }
            // Mark the source's full spans covered so lower-priority sources only fill true gaps.
            covered = union(covered, source.map { Range(it.startMs, it.endMs) })
        }
        out.sortBy { it.startMs }
        return out
    }

    /**
     * Re-slice arbitrated intervals into fixed clock-hour buckets (epoch-hour = floor(ms / 3.6e6)),
     * prorating any interval that straddles an hour boundary.
     *
     * @return count per epoch-hour. The publisher writes one StepsRecord per entry with
     *         start = epochHour * 3_600_000, end = start + 3_600_000,
     *         clientRecordId = "noop-steps-<epochHour>" (STABLE across runs → clean upsert, no
     *         orphans), rounding the Double to Int and skipping any bucket that rounds to < 1.
     */
    fun bucketByHour(intervals: List<StepInterval>): Map<Long, Double> {
        val buckets = LinkedHashMap<Long, Double>()
        for (iv in intervals) {
            if (iv.durationMs == 0L || iv.count == 0.0) continue
            val perMs = iv.count / iv.durationMs
            var cursor = iv.startMs
            while (cursor < iv.endMs) {
                val hour = Math.floorDiv(cursor, HOUR_MS)
                val segEnd = minOf((hour + 1) * HOUR_MS, iv.endMs)
                buckets[hour] = (buckets[hour] ?: 0.0) + perMs * (segEnd - cursor)
                cursor = segEnd
            }
        }
        return buckets
    }

    /** Portions of [r] not covered by any range in [covered] (which must be sorted & disjoint). */
    private fun subtract(r: Range, covered: List<Range>): List<Range> {
        val result = ArrayList<Range>()
        var cursor = r.start
        for (c in covered) {
            if (c.end <= cursor) continue
            if (c.start >= r.end) break
            if (c.start > cursor) result += Range(cursor, minOf(c.start, r.end))
            cursor = maxOf(cursor, c.end)
            if (cursor >= r.end) break
        }
        if (cursor < r.end) result += Range(cursor, r.end)
        return result
    }

    /** Coalesce two range sets into one sorted, non-overlapping set. */
    private fun union(a: List<Range>, b: List<Range>): List<Range> {
        val all = (a + b).filter { it.end > it.start }.sortedBy { it.start }
        if (all.isEmpty()) return emptyList()
        val merged = ArrayList<Range>()
        var cur = all.first()
        for (i in 1 until all.size) {
            val n = all[i]
            cur = if (n.start <= cur.end) Range(cur.start, maxOf(cur.end, n.end))
                  else { merged += cur; n }
        }
        merged += cur
        return merged
    }
}

/*
 * ---- Android-side contract (implement in separate files; NOT part of the pure core) ----
 *
 * Whoop adapter:
 *   For consecutive StepSample rows of the same source id:
 *       StepInterval(ts[i], ts[i+1], calibratedSteps[i+1] - calibratedSteps[i])
 *   Reuse the existing positive-delta derivation (drop the u16-rollover slice; do NOT
 *   reconstruct with +65536) and reset deltas at importedSourceIds boundaries.
 *
 * HC-sources adapter:
 *   Raw StepsRecord read for ALL sources #589 sees (phone, watch, Samsung Health, ...),
 *   each kept as StepInterval(record.startTime, record.endTime, record.count.toDouble()).
 *   Do NOT route through #589's daily max-across-sources collapse.
 *
 * Publisher (opt-in gated):
 *   val hours = StepArbiter.bucketByHour(StepArbiter.arbitrate(listOf(phone, whoop)))
 *   for ((hour, steps) in hours) {
 *       val count = Math.round(steps)
 *       if (count < 1L) continue                       // HC rejects count<1 / start==end
 *       upsertStepsRecord(
 *           start = hour * 3_600_000L,
 *           end   = hour * 3_600_000L + 3_600_000L,
 *           count = count,
 *           clientRecordId = "noop-steps-$hour",       // stable → clean re-publish
 *       )
 *   }
 */
