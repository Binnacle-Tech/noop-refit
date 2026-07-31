package com.noop.stepmerge

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import com.noop.data.WhoopRepository
import com.noop.ui.NoopPrefs
import com.noop.ui.ProfileStore
import java.time.Instant
import java.time.ZoneId

/**
 * Opt-in multi-source step arbiter publish. Collates phone/watch (Health Connect) + Whoop (Noop's
 * internal counter) into one gap-filled step stream and writes it back to Health Connect under Noop's own
 * origin, hour-bucketed for a stable, orphan-free idempotent upsert. Ledger (and any consumer we control)
 * reads only this origin.
 *
 * Integration footprint is deliberately tiny: this whole feature is new files in a new package; the only
 * upstream touches are `WRITE_STEPS` in the manifest and a single cold call to [publish] from the writeback
 * trigger. Everything below is gated behind the [NoopPrefs.hcWriteSteps] opt-in, so a user who never turns
 * it on pays nothing and no collated total ever enters their Health Connect.
 */
object StepPublisher {

    private const val WINDOW_DAYS = 3L
    private const val HOUR_MS = 3_600_000L
    private const val TAG = "NoopHC"

    /** Read phone steps + write the collated total; both scopes requested when the toggle is enabled. */
    val PERMISSIONS: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
    )

    /**
     * Arbitrate + publish the last [WINDOW_DAYS] of steps. Returns the number of hour-bucket records
     * written (0 when off, unavailable, or nothing to publish). Idempotent: re-running overwrites the same
     * `noop-steps-<epochHour>` ids, so the 15-min re-publish is safe. Assumes [PERMISSIONS] granted
     * (HC throws SecurityException otherwise — caller wraps in runCatching, same as the other writebacks).
     */
    suspend fun publish(context: Context, repo: WhoopRepository, deviceId: String): Int {
        if (!NoopPrefs.hcWriteSteps(context)) { android.util.Log.i(TAG, "skip: toggle off"); return 0 }
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            android.util.Log.i(TAG, "skip: HC unavailable"); return 0
        }
        val client = HealthConnectClient.getOrCreate(context)

        val nowMs = System.currentTimeMillis()
        val fromMs = nowMs - WINDOW_DAYS * 86_400_000L
        val fromSec = fromMs / 1000
        val toSec = nowMs / 1000

        // Whoop fallback tier: calibrated counter-delta intervals, derived per imported source id because
        // the counter is device-scoped (active strap + canonical "my-whoop", #814).
        val ticksPerStep = ProfileStore.from(context).stepTicksPerStep
        val whoop = ArrayList<StepInterval>()
        for (id in repo.importedSourceIds(deviceId)) {
            whoop += StepSources.whoopIntervals(repo.stepSamples(id, fromSec, toSec), ticksPerStep)
        }

        // Ranked tiers: HC pedometers (dominant first) then Whoop last, and arbitrate.
        val hcTiers = StepSources.hcIntervalTiers(client, context, fromMs, nowMs)
        android.util.Log.i(TAG, "sources: ${hcTiers.size} HC tier(s) sized ${hcTiers.map { it.size }}, whoop=${whoop.size} intervals")
        // v2 measurement gate: residual strap-vs-phone bias over the co-covered window. Accrues per run;
        // a ratio persistently off 1.0 (weighted by co-covered hours) is the only thing that justifies
        // building calibrated gap-fill. Pure measurement — changes nothing this run.
        // Attribute THIS run's phone↔strap comparison to TODAY only (clip to local-midnight→now), fold it
        // into the rolling per-day history (overwrite today, idempotent), and persist. Accrued every run
        // regardless of the toggle, so the factor steadies over days and is ready when auto-cal turns on.
        val phoneTier = hcTiers.firstOrNull().orEmpty()
        val zoneNow = ZoneId.systemDefault()
        val todayStartMs = java.time.LocalDate.now(zoneNow).atStartOfDay(zoneNow).toInstant().toEpochMilli()
        val todayBias = StepMeasure.crossSourceBias(
            StepMeasure.clip(phoneTier, todayStartMs, nowMs), StepMeasure.clip(whoop, todayStartMs, nowMs),
        )
        // Backfill the calibration history from ~SEED_DAYS of EXISTING WHOOP + phone data so the factor is
        // trustworthy immediately instead of learned forward from zero. Keyed by the seed WIDTH: it runs
        // once per window size, so widening SEED_DAYS later triggers exactly one re-seed (never per-run).
        var priorStats = StepMeasure.decode(NoopPrefs.hcStepCalStats(context))
        if (NoopPrefs.hcStepCalSeededDays(context) < SEED_DAYS) {
            val seed = seedFromHistory(client, context, repo, deviceId, zoneNow, nowMs)
            if (seed.isNotEmpty()) priorStats = seed
            NoopPrefs.setHcStepCalSeededDays(context, SEED_DAYS)
            android.util.Log.i(TAG, "seed: backfilled ${seed.size} day(s) with overlap from ${SEED_DAYS}d history")
        }
        val accrued = StepMeasure.accrue(
            priorStats,
            StepMeasure.DayStat(java.time.LocalDate.now(zoneNow).toString(), todayBias.coCoveredMs, todayBias.higherSteps, todayBias.lowerSteps),
        )
        NoopPrefs.setHcStepCalStats(context, StepMeasure.encode(accrued))
        val accruedHours = accrued.sumOf { it.coMs } / 3_600_000.0
        val accruedRatio = accrued.sumOf { it.whoop }.takeIf { it > 0 }?.let { w -> accrued.sumOf { it.phone }.takeIf { it > 0 }?.let { w / it } }
        android.util.Log.i(TAG, "measure: accrued %d day(s), co-covered=%.1fh ratio(whoop/phone)=%s".format(
            accrued.size, accruedHours, accruedRatio?.let { "%.3f".format(it) } ?: "n/a"))

        // Opt-in auto-calibration: scale the strap tier toward phone truth by the AVERAGED factor, once
        // enough overlap has accrued. Arbiter-only — the app's own displayed steps are untouched.
        val factor = if (NoopPrefs.hcStepAutoCalibrate(context)) StepMeasure.accruedFactor(accrued) else null
        val whoopTier = if (factor != null) whoop.map { it.copy(count = it.count * factor) } else whoop
        if (NoopPrefs.hcStepAutoCalibrate(context)) {
            android.util.Log.i(TAG, factor?.let { "auto-cal: applied x%.3f (accrued %.1fh over %d day(s))".format(it, accruedHours, accrued.size) }
                ?: "auto-cal: waiting (need >=1h accrued co-covered, have %.1fh)".format(accruedHours))
        }
        val tiers = hcTiers + listOf(whoopTier)
        if (tiers.all { it.isEmpty() }) { android.util.Log.i(TAG, "skip: no step data in window"); return 0 }
        val hours = StepArbiter.bucketByHour(StepArbiter.arbitrate(tiers))
        if (hours.isEmpty()) { android.util.Log.i(TAG, "skip: arbitration produced no hours"); return 0 }

        val zone = ZoneId.systemDefault()
        val version = nowMs / 1000
        val records = ArrayList<StepsRecord>(hours.size)
        for ((hour, steps) in hours) {
            val count = Math.round(steps)
            if (count < 1L) continue                                  // HC rejects count < 1 / start == end
            val start = Instant.ofEpochMilli(hour * HOUR_MS)
            val end = Instant.ofEpochMilli(hour * HOUR_MS + HOUR_MS)
            records += StepsRecord(
                startTime = start, startZoneOffset = zone.rules.getOffset(start),
                endTime = end, endZoneOffset = zone.rules.getOffset(end),
                count = count,
                metadata = Metadata(clientRecordId = "noop-steps-$hour", clientRecordVersion = version),
            )
        }
        if (records.isEmpty()) { android.util.Log.i(TAG, "skip: every hour rounded to <1 step"); return 0 }
        runCatching { records.chunked(1000).forEach { client.insertRecords(it) } }
            .onSuccess { android.util.Log.i(TAG, "published ${records.size} hour-bucket StepsRecord(s)") }
            .onFailure { android.util.Log.w(TAG, "step publish FAILED", it); throw it }
        return records.size
    }

    /** One-time calibration backfill: bucket ~30 days of existing phone (HC) + strap (internal) steps
     *  into per-day co-covered stats, so auto-cal has a confident factor immediately. Reads wider than
     *  the publish window, but only once (guarded by the seeded flag). */
    private const val SEED_DAYS = 60

    private suspend fun seedFromHistory(
        client: HealthConnectClient,
        context: Context,
        repo: WhoopRepository,
        deviceId: String,
        zone: ZoneId,
        nowMs: Long,
    ): List<StepMeasure.DayStat> {
        val fromMs = nowMs - SEED_DAYS * 86_400_000L
        val phone = StepSources.hcIntervalTiers(client, context, fromMs, nowMs).firstOrNull().orEmpty()
        val ticksPerStep = ProfileStore.from(context).stepTicksPerStep
        val whoop = ArrayList<StepInterval>()
        for (id in repo.importedSourceIds(deviceId)) {
            whoop += StepSources.whoopIntervals(repo.stepSamples(id, fromMs / 1000, nowMs / 1000), ticksPerStep)
        }
        val windows = (0 until SEED_DAYS).map { back ->
            val d = java.time.LocalDate.now(zone).minusDays(back.toLong())
            val s = d.atStartOfDay(zone).toInstant().toEpochMilli()
            val e = minOf(d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), nowMs)
            StepMeasure.DayWindow(d.toString(), s, e)
        }
        // Keep the freshest days with real overlap — aligns with the rolling accrual cap.
        return StepMeasure.perDayStats(phone, whoop, windows).sortedByDescending { it.day }.take(SEED_DAYS)
    }
}
