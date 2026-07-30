package com.noop.stepmerge

/**
 * v2 measurement gate (pure, JVM-testable — no Android types).
 *
 * The v2 question is: does the strap, AFTER the intra-device StepsCalibration that already converts its
 * motion ticks to steps, still read systematically higher or lower than a real phone pedometer? The only
 * honest way to know is to compare the two sources **over the time both actually covered** — Whoop-only
 * gap stretches can't be measured (there's no phone truth there).
 *
 * [crossSourceBias] returns each source's prorated step total within the co-covered window plus that
 * window's size. Log it per run; watch [CrossSourceBias.ratio] across days:
 *   - ratio ≈ 1.0 (weighted by co-covered hours) → the existing calibration already reconciles them;
 *     calibrated gap-fill (v2) buys nothing. v1 is the finish line.
 *   - a persistent offset (e.g. ~1.25 = strap over-reads 25% vs phone) → v2 is worth building, and the
 *     offset IS the correction factor to apply to Whoop steps in the gaps.
 */
object StepMeasure {

    private data class R(val start: Long, val end: Long)

    data class CrossSourceBias(
        val coCoveredMs: Long,
        val higherSteps: Double,   // higher-ranked source (phone) within the co-covered window
        val lowerSteps: Double,    // lower-ranked source (Whoop) within the same window
    ) {
        /** lower ÷ higher over the co-covered window; null when there's nothing to compare against. */
        val ratio: Double? get() = if (higherSteps > 0.0) lowerSteps / higherSteps else null
        val coCoveredHours: Double get() = coCoveredMs / 3_600_000.0
    }

    /**
     * The multiplier to apply to the LOWER source (Whoop) so it matches the HIGHER (phone) over the
     * co-covered window — i.e. `phone / whoop = 1 / ratio`. Opt-in auto-calibration uses this to scale the
     * strap's gap-fill toward phone truth without any manual "walk 1000 steps" tuning.
     *
     * Returns null (→ leave the strap raw) when there isn't enough overlap to trust ([minCoCoveredHours])
     * or the ratio is outside a sane band — a freak window (phone glanced at for two minutes on a huge
     * strap day) must never drive a wild over-correction. The band spans a matched strap (~1) up to the
     * documented ~24–30× 5/MG overcount, with headroom.
     */
    fun calibrationFactor(bias: CrossSourceBias, minCoCoveredHours: Double = 6.0): Double? {
        val ratio = bias.ratio ?: return null
        if (bias.coCoveredHours < minCoCoveredHours) return null
        if (ratio !in 0.5..40.0) return null
        return 1.0 / ratio
    }

    /** Bias of [lower] (Whoop) relative to [higher] (phone) over the intervals BOTH covered. */
    fun crossSourceBias(higher: List<StepInterval>, lower: List<StepInterval>): CrossSourceBias {
        val inter = intersect(coverage(higher), coverage(lower))
        val coMs = inter.sumOf { it.end - it.start }
        return CrossSourceBias(coMs, proratedWithin(higher, inter), proratedWithin(lower, inter))
    }

    /** Merge a source's (possibly touching) intervals into sorted, disjoint coverage ranges. */
    private fun coverage(ivs: List<StepInterval>): List<R> {
        val sorted = ivs.filter { it.endMs > it.startMs }.map { R(it.startMs, it.endMs) }.sortedBy { it.start }
        if (sorted.isEmpty()) return emptyList()
        val out = ArrayList<R>()
        var cur = sorted.first()
        for (i in 1 until sorted.size) {
            val n = sorted[i]
            cur = if (n.start <= cur.end) R(cur.start, maxOf(cur.end, n.end)) else { out += cur; n }
        }
        out += cur
        return out
    }

    /** Intersection of two sorted, disjoint range sets (two-pointer sweep). */
    private fun intersect(a: List<R>, b: List<R>): List<R> {
        val out = ArrayList<R>()
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            val s = maxOf(a[i].start, b[j].start)
            val e = minOf(a[i].end, b[j].end)
            if (e > s) out += R(s, e)
            if (a[i].end < b[j].end) i++ else j++
        }
        return out
    }

    /** Sum of each interval's count prorated by how much of it lands inside [ranges] (sorted, disjoint). */
    private fun proratedWithin(ivs: List<StepInterval>, ranges: List<R>): Double {
        var total = 0.0
        for (iv in ivs) {
            if (iv.durationMs == 0L) continue
            var inside = 0L
            for (r in ranges) {
                if (r.end <= iv.startMs) continue
                if (r.start >= iv.endMs) break
                inside += minOf(r.end, iv.endMs) - maxOf(r.start, iv.startMs)
            }
            if (inside > 0) total += iv.count * (inside.toDouble() / iv.durationMs)
        }
        return total
    }
}
