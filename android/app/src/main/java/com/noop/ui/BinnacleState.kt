package com.noop.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.noop.R

/*
 * Binnacle fork — the state contract, structurally enforced (Binnacle §02/§12).
 *
 * The rule: state is NEVER signalled by colour alone. Asking for a [UiState] returns its colour
 * AND its glyph together, so it is impossible to use one without the other. The glyphs are the
 * ported binnacle-glyphs.svg set (24×24, ~2px stroke, silhouette-distinct in greyscale):
 * check = good, triangle = running/now, bars = held, cross = failed, bang-triangle = warning.
 *
 * Colours read from the LIVE Palette (snapshot state), so both skins and both schemes apply:
 * on the stock skin the states use the stock status colours; on Binnacle they use the contract
 * (amber/cyan/neutral/clay) automatically.
 */
enum class UiState { Running, Done, Held, Queued, Warning, Failed }

/** The state's colour — from the active Palette, so skin/scheme/vision all apply.
 *  Queued shares held's colour (both are "real, but not moving"); only the glyph differs. */
val UiState.color: Color
    get() = when (this) {
        UiState.Running -> Palette.accent
        UiState.Done -> Palette.statusPositive
        UiState.Held, UiState.Queued -> Palette.textTertiary
        UiState.Warning -> Palette.statusWarning
        UiState.Failed -> Palette.statusCritical
    }

/** The state's glyph — the non-colour channel; carries meaning under greyscale/CVD. */
val UiState.iconRes: Int
    @DrawableRes get() = when (this) {
        UiState.Running -> R.drawable.gl_running
        UiState.Done -> R.drawable.gl_done
        UiState.Held -> R.drawable.gl_held
        UiState.Queued -> R.drawable.gl_queued
        UiState.Warning -> R.drawable.gl_warning
        UiState.Failed -> R.drawable.gl_failed
    }

/*
 * Domain + metric glyphs (NOOP extension of the Binnacle set, same geometry rules: 24×24 grid,
 * ~2px stroke, round caps/joins, silhouette-distinct at 16px in greyscale). Under the purist
 * ramps the four domains no longer differ by loud hue, so the glyph becomes the second channel
 * that tells Charge from Effort from Rest from Stress — same role the state glyphs play.
 */

/** Glyph for a daily-score domain world (bolt / chevrons / crescent / spike). */
val DomainTheme.iconRes: Int
    @DrawableRes get() = when (this) {
        DomainTheme.Charge -> R.drawable.gl_charge
        DomainTheme.Effort -> R.drawable.gl_effort
        DomainTheme.Rest -> R.drawable.gl_rest
        DomainTheme.Stress -> R.drawable.gl_stress
    }

/** Glyph for a metric key (the Explore/Today series keys); null when none is defined yet. */
@DrawableRes
fun metricGlyph(key: String): Int? = when (key) {
    "hr", "avg_hr", "max_hr", "resting_hr", "rhr" -> R.drawable.gl_hr
    "hrv" -> R.drawable.gl_hrv
    "spo2" -> R.drawable.gl_spo2
    "resp_rate" -> R.drawable.gl_resp
    "steps" -> R.drawable.gl_steps
    "active_kcal", "energy_kcal" -> R.drawable.gl_kcal
    "strain", "effort" -> R.drawable.gl_effort
    "recovery", "charge" -> R.drawable.gl_charge
    "sleep", "in_bed_min", "total_sleep" -> R.drawable.gl_rest
    "workouts", "workout" -> R.drawable.gl_workout
    else -> null
}

/**
 * Glyph + label line: the paired rendering. [text] says what happened (and per the Voice rules,
 * ideally the next move); the glyph carries the state without relying on hue.
 */
@Composable
fun StateLine(
    state: UiState,
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = NoopType.footnote,
    tint: Color = state.color,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(state.iconRes),
            contentDescription = null,   // the adjacent text carries the meaning for TalkBack
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Text(text, style = style, color = tint)
    }
}
