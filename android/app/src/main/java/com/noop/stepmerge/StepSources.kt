package com.noop.stepmerge

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.noop.analytics.StepsCounter
import com.noop.data.StepSample
import java.time.Instant
import kotlin.math.max

/**
 * Adapters that turn Noop/Health-Connect step data into the pure core's [StepInterval] lists.
 * Android / Health Connect / Noop types live HERE; the merge math stays type-free in [StepArbiter].
 * New file in a new package — no upstream lines touched, so nothing to conflict on at merge time.
 */
object StepSources {

    /**
     * Whoop step intervals from consecutive [StepSample] `step_motion_counter@57` deltas, calibrated.
     *
     * Mirrors the shared [StepsCounter] kernel EXACTLY — wrap-aware `(cur - prev) and 0xFFFF`, and the
     * `< MAX_STEP_DELTA` (512) gate that treats a big jump as a sync-gap/reset boundary rather than steps.
     * Because the per-pair math is identical, the SUM of these intervals equals the app's displayed daily
     * step total (`StepsCounter.stepsInWindow / stepTicksPerStep`); the arbiter can never disagree with the
     * number the user already sees. (Note: the kernel RECONSTRUCTS a u16 wrap, it does not drop it — the
     * earlier "drop the rollover slice" note in the design was wrong; matching the kernel keeps parity.)
     *
     * [samples] must be ONE source id's series (the counter is device-scoped; a strap swap starts a fresh
     * series). `StepSample.ts` is epoch SECONDS → converted to ms for the core.
     */
    fun whoopIntervals(samples: List<StepSample>, ticksPerStep: Double): List<StepInterval> {
        val sorted = samples.sortedBy { it.ts }
        val cal = max(ticksPerStep, 0.5)   // floor mirrors AnalyticsEngine: a bad pref can at most double
        val out = ArrayList<StepInterval>(maxOf(0, sorted.size - 1))
        for (i in 1 until sorted.size) {
            val delta = (sorted[i].counter - sorted[i - 1].counter) and 0xFFFF   // wrap-aware u16 increment
            if (delta in 1 until StepsCounter.MAX_STEP_DELTA) {
                out += StepInterval(sorted[i - 1].ts * 1000L, sorted[i].ts * 1000L, delta / cal)
            }
            // delta 0 (still) or >= 512 (gap/reset): leave the span uncovered — Whoop is the fallback
            // tier, so an uncovered gap simply contributes nothing, exactly as the daily total does.
        }
        return out
    }

    /**
     * Phone / watch / any other Health-Connect step writer, as RANKED tiers — one tier per `dataOrigin`
     * package, ordered by that window's total steps DESC so the dominant real pedometer outranks the rest
     * and none is dropped (this ports #589's "max per source" as a RANKING signal, not a collapsed total).
     *
     * Excludes Noop's own package so a prior collated write is never re-ingested. Keeps real start/end
     * times as intervals — deliberately NOT routed through #589's daily max-across-sources collapse, which
     * would hand the core an already-flattened number.
     */
    suspend fun hcIntervalTiers(client: HealthConnectClient, context: Context, fromMs: Long, toMs: Long): List<List<StepInterval>> {
        val filter = TimeRangeFilter.between(Instant.ofEpochMilli(fromMs), Instant.ofEpochMilli(toMs))
        val self = context.packageName
        val byOrigin = LinkedHashMap<String, MutableList<StepInterval>>()
        var pageToken: String? = null
        do {
            val resp = client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = filter,
                    pageSize = 1000,
                    pageToken = pageToken,
                ),
            )
            for (r in resp.records) {
                val origin = r.metadata.dataOrigin.packageName
                if (origin == self) continue                     // never re-read our own collated origin
                val s = r.startTime.toEpochMilli()
                val e = r.endTime.toEpochMilli()
                if (e > s) byOrigin.getOrPut(origin) { ArrayList() }.add(StepInterval(s, e, r.count.toDouble()))
            }
            pageToken = resp.pageToken
        } while (pageToken != null)
        // Dominant pedometer first. Each origin's own records are assumed non-overlapping (HC convention),
        // which is the core's within-source precondition; the RANK across origins is what dedups the
        // phone+watch double-count — each origin is its own tier, never flattened together.
        return byOrigin.values.sortedByDescending { tier -> tier.sumOf { it.count } }
    }
}
