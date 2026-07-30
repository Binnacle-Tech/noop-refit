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
        val tiers = hcTiers + listOf(whoop)
        android.util.Log.i(TAG, "sources: ${hcTiers.size} HC tier(s) sized ${hcTiers.map { it.size }}, whoop=${whoop.size} intervals")
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
}
