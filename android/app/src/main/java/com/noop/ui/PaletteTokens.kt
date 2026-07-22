package com.noop.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// MARK: - PaletteTokens — the per-scheme colour set behind `object Palette`
//
// Compose has no OS-dynamic colour (unlike iOS UIColor(light:dark:)), so the light theme is built
// the same way conceptually: ONE set of colour tokens, swapped wholesale per scheme. `Palette.active`
// is snapshot state, so every `Palette.X` read (in a composable OR a Canvas DrawScope) re-resolves
// automatically when the theme flips — ZERO call-site changes across the ~1,740 references.
//
// Dark values mirror StrandPalette.swift's dark; light values are the approved "Warm Paper" set
// (docs/superpowers/specs/2026-06-16-light-theme-design.md). Names/order match the Swift palette.

data class PaletteTokens(
    val surfaceBase: Color,
    val surfaceRaised: Color,
    val surfaceOverlay: Color,
    val surfaceInset: Color,
    val hairline: Color,
    val hairlineStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val glowAmbient: Color,
    val accent: Color,
    val accentHover: Color,
    val accentMuted: Color,
    val focusRing: Color,
    val recovery000: Color,
    val recovery030: Color,
    val recovery055: Color,
    val recovery078: Color,
    val recovery100: Color,
    val strain000: Color,
    val strain033: Color,
    val strain066: Color,
    val strain100: Color,
    val sleepAwake: Color,
    val sleepLight: Color,
    val sleepDeep: Color,
    val sleepREM: Color,
    val zone1: Color,
    val zone2: Color,
    val zone3: Color,
    val zone4: Color,
    val zone5: Color,
    val statusPositive: Color,
    val statusWarning: Color,
    val statusCritical: Color,
    val metricCyan: Color,
    val metricPurple: Color,
    val metricAmber: Color,
    val metricRose: Color,
    val chargeColor: Color,
    val chargeDeep: Color,
    val chargeBright: Color,
    val chargeGlow: Color,
    val effortColor: Color,
    val effortDeep: Color,
    val effortBright: Color,
    val effortGlow: Color,
    val restColor: Color,
    val restDeep: Color,
    val restBright: Color,
    val restGlow: Color,
    val stressColor: Color,
    val stressDeep: Color,
    val stressBright: Color,
    val stressGlow: Color,
    val scenicCenter: Color,
    val scenicEdge: Color,
    val scenicStar: Color,
    val cardFillTop: Color,
    val cardFillBottom: Color,
    val gold: Color,
    val goldLight: Color,
    val goldDeep: Color,
    val goldDeepText: Color,
    val signalYellow: Color,
    val titaniumTop: Color,
    val titaniumMid: Color,
    val titaniumLow: Color,
    val titaniumDeep: Color,
    // The bright gauge-tip / sparkline-head core: white reads as a highlight on dark; on light it
    // would vanish into the white card, so it flips to a deep ink (crisp centre on the coloured bead).
    val tipCore: Color,
)

// WHOOP-reset dark palette (gold killed 2026-06-22). Values match StrandPalette.swift's DARK
// Titanium column byte-for-byte: blue-grey canvas, WHOOP red→yellow→green recovery, green Charge,
// blue Effort, slate Rest, amber Stress. NO gold anywhere — accent/gold tokens point to WHOOP blue.
val DarkTokens = PaletteTokens(
    surfaceBase = Color(0xFF121518), surfaceRaised = Color(0xFF25292C), surfaceOverlay = Color(0xFF1C1F26),
    surfaceInset = Color(0xFF1F2229), hairline = Color(0xFF21304A), hairlineStrong = Color(0xFF2E3C57),
    textPrimary = Color(0xFFF4F6F8), textSecondary = Color(0xFFC8CFD8), textTertiary = Color(0xFF8A94A4),
    glowAmbient = Color(0xFF3A2D0A),
    accent = Color(0xFF60A0E0), accentHover = Color(0xFF8FBEEC), accentMuted = Color(0xFF16233A), focusRing = Color(0xFF60A0E0),
    recovery000 = Color(0xFFE0463C), recovery030 = Color(0xFFE8743C), recovery055 = Color(0xFFF9DF4A),
    recovery078 = Color(0xFF8FD86A), recovery100 = Color(0xFF03E095),
    strain000 = Color(0xFF9C5A14), strain033 = Color(0xFFC2762A), strain066 = Color(0xFFD98A3D), strain100 = Color(0xFFF0A85A),
    sleepAwake = Color(0xFFC2CCDA), sleepLight = Color(0xFF4A90E2), sleepDeep = Color(0xFF2F6FCB), sleepREM = Color(0xFF6FA8E8),
    zone1 = Color(0xFF4A90E2), zone2 = Color(0xFF3FA9C9), zone3 = Color(0xFFE8B84B), zone4 = Color(0xFFD98A3D), zone5 = Color(0xFFE0662F),
    statusPositive = Color(0xFF03E095), statusWarning = Color(0xFFF0A020), statusCritical = Color(0xFFE0662F),
    metricCyan = Color(0xFF3FA9C9), metricPurple = Color(0xFF4A90E2), metricAmber = Color(0xFFD98A3D), metricRose = Color(0xFFE0662F),
    chargeColor = Color(0xFF03E095), chargeDeep = Color(0xFF0B9D62), chargeBright = Color(0xFF6BF0B4), chargeGlow = Color(0xFF03E095),
    effortColor = Color(0xFF4090E0), effortDeep = Color(0xFF2A6FB0), effortBright = Color(0xFF74B6F0), effortGlow = Color(0xFF4090E0),
    restColor = Color(0xFF83A0B8), restDeep = Color(0xFF2F6FCB), restBright = Color(0xFF6FA8E8), restGlow = Color(0xFF4A90E2),
    stressColor = Color(0xFFF0A020), stressDeep = Color(0xFF4A90E2), stressBright = Color(0xFFE0662F), stressGlow = Color(0xFFF0A020),
    scenicCenter = Color(0xFF1C2128), scenicEdge = Color(0xFF121518), scenicStar = Color(0xFFC8CFD8),
    cardFillTop = Color(0xFF15243C), cardFillBottom = Color(0xFF0B1424),
    gold = Color(0xFF60A0E0), goldLight = Color(0xFF9FC8F0), goldDeep = Color(0xFF3A78C8),
    goldDeepText = Color(0xFFFFFFFF), signalYellow = Color(0xFFFFD63D),
    titaniumTop = Color(0xFFF1F3F5), titaniumMid = Color(0xFFC9CFD4), titaniumLow = Color(0xFF969DA4), titaniumDeep = Color(0xFF6B737B),
    tipCore = Color(0xFFFFFFFF),
)

val LightTokens = PaletteTokens(
    surfaceBase = Color(0xFFEAE3D4), surfaceRaised = Color(0xFFFFFFFF), surfaceOverlay = Color(0xFFFFFFFF),
    surfaceInset = Color(0xFFDFD8C8), hairline = Color(0xFFD8D0BD), hairlineStrong = Color(0xFFC7BCA4),
    textPrimary = Color(0xFF1A2230), textSecondary = Color(0xFF4C5564), textTertiary = Color(0xFF7C8696),
    glowAmbient = Color(0xFFF0E4C0),
    // Light chrome accent shifts to the deep brand blue (gold reserved for the recovery world + FAB).
    accent = Color(0xFF234F9E), accentHover = Color(0xFF1C3F80), accentMuted = Color(0xFFE4ECF6), focusRing = Color(0xFF2F6FCB),
    recovery000 = Color(0xFF8F6212), recovery030 = Color(0xFFA87718), recovery055 = Color(0xFFC28E26),
    recovery078 = Color(0xFFD2A23A), recovery100 = Color(0xFFE0B44C),
    strain000 = Color(0xFF7E460E), strain033 = Color(0xFFA4621B), strain066 = Color(0xFFC2792E), strain100 = Color(0xFFD89240),
    sleepAwake = Color(0xFF97A2B2), sleepLight = Color(0xFF3A80D6), sleepDeep = Color(0xFF234F9E), sleepREM = Color(0xFF5790DA),
    zone1 = Color(0xFF3A80D6), zone2 = Color(0xFF2E92B4), zone3 = Color(0xFFC28E26), zone4 = Color(0xFFC2792E), zone5 = Color(0xFFC84E1E),
    statusPositive = Color(0xFFB07D17), statusWarning = Color(0xFFC2792E), statusCritical = Color(0xFFC84E1E),
    metricCyan = Color(0xFF2E92B4), metricPurple = Color(0xFF3A80D6), metricAmber = Color(0xFFC2792E), metricRose = Color(0xFFC84E1E),
    chargeColor = Color(0xFFB88421), chargeDeep = Color(0xFF8F6212), chargeBright = Color(0xFFE0B44C), chargeGlow = Color(0xFFC8902F),
    effortColor = Color(0xFFB26A1C), effortDeep = Color(0xFF7E460E), effortBright = Color(0xFFD89240), effortGlow = Color(0xFFB26A1C),
    restColor = Color(0xFF3A80D6), restDeep = Color(0xFF234F9E), restBright = Color(0xFF5790DA), restGlow = Color(0xFF3A80D6),
    stressColor = Color(0xFFB88421), stressDeep = Color(0xFF3A80D6), stressBright = Color(0xFFC84E1E), stressGlow = Color(0xFFB88421),
    scenicCenter = Color(0xFFFBF6EA), scenicEdge = Color(0xFFEDE6D6), scenicStar = Color(0xFFD8CDB6),
    cardFillTop = Color(0xFFFFFFFF), cardFillBottom = Color(0xFFFAF7F0),
    gold = Color(0xFFDBA52A), goldLight = Color(0xFFECC766), goldDeep = Color(0xFF9A6B12),
    goldDeepText = Color(0xFF3A2708), signalYellow = Color(0xFFE8A800),
    titaniumTop = Color(0xFFDDE1E6), titaniumMid = Color(0xFFBBC2C9), titaniumLow = Color(0xFF98A0A8), titaniumDeep = Color(0xFF6B737B),
    tipCore = Color(0xFF241B06),
)

// MARK: - BINNACLE skin (Noop-Binnacle fork) — the house design language as a toggleable skin
//
// Binnacle (house design language): dark instrument surface, amber = now, cyan = good,
// neutral = held, danger = stop — and PURIST data encoding: value reads by LIGHTNESS of one
// hue, not by rainbow. Mapped into the frozen PaletteTokens API so all ~1,740 Palette.* reads
// reskin with zero call-site changes. Toggled via [SkinPrefs] (Settings → Appearance), exactly
// the ChartStylePrefs pattern. Stock tokens above are untouched.
//
// Ramp philosophy (purist): recovery/strain/zones = dark→bright AMBER lightness ramps ("more =
// brighter"); sleep = CYAN lightness (deep = darkest) with AWAKE on neutral (different family =
// "not asleep"); domains: Charge is the amber signature, Effort rides neutral, Rest rides cyan,
// Stress is semantic calm(cyan)→now(amber)→stop(red-clay). statusWarning maps to amber (attention).

val BinnacleDarkTokens = PaletteTokens(
    surfaceBase = Color(0xFF0E1419), surfaceRaised = Color(0xFF151E27), surfaceOverlay = Color(0xFF1B2731),
    surfaceInset = Color(0xFF101922), hairline = Color(0xFF26333E), hairlineStrong = Color(0xFF334353),
    textPrimary = Color(0xFFE7EEF4), textSecondary = Color(0xFF93A4B2), textTertiary = Color(0xFF67798A),
    glowAmbient = Color(0xFF3A2D0A),
    accent = Color(0xFFE8A33D), accentHover = Color(0xFFF0C079), accentMuted = Color(0xFF2B2110), focusRing = Color(0xFFE8A33D),
    recovery000 = Color(0xFF5C3E0A), recovery030 = Color(0xFF8A5D10), recovery055 = Color(0xFFB87F26),
    recovery078 = Color(0xFFE8A33D), recovery100 = Color(0xFFF0C079),
    strain000 = Color(0xFF6E4A0C), strain033 = Color(0xFF8A5D10), strain066 = Color(0xFFC2871F), strain100 = Color(0xFFF0C079),
    sleepAwake = Color(0xFF8FA0AE), sleepLight = Color(0xFF5AD8CC), sleepDeep = Color(0xFF1F5751), sleepREM = Color(0xFF3AA69B),
    zone1 = Color(0xFF6E4A0C), zone2 = Color(0xFF8A5D10), zone3 = Color(0xFFB87F26), zone4 = Color(0xFFE8A33D), zone5 = Color(0xFFF0C079),
    statusPositive = Color(0xFF4FD1C5), statusWarning = Color(0xFFE8A33D), statusCritical = Color(0xFFE06C5A),
    metricCyan = Color(0xFF4FD1C5), metricPurple = Color(0xFF8FA0AE), metricAmber = Color(0xFFE8A33D), metricRose = Color(0xFFE06C5A),
    chargeColor = Color(0xFFE8A33D), chargeDeep = Color(0xFF8A5D10), chargeBright = Color(0xFFF0C079), chargeGlow = Color(0xFFE8A33D),
    effortColor = Color(0xFF8FA0AE), effortDeep = Color(0xFF4A5A68), effortBright = Color(0xFFB8C7D3), effortGlow = Color(0xFF8FA0AE),
    restColor = Color(0xFF2E7E77), restDeep = Color(0xFF1F5751), restBright = Color(0xFF4FD1C5), restGlow = Color(0xFF4FD1C5),
    stressColor = Color(0xFFE8A33D), stressDeep = Color(0xFF4FD1C5), stressBright = Color(0xFFE06C5A), stressGlow = Color(0xFFE8A33D),
    scenicCenter = Color(0xFF151E27), scenicEdge = Color(0xFF0E1419), scenicStar = Color(0xFF93A4B2),
    cardFillTop = Color(0xFF151E27), cardFillBottom = Color(0xFF101922),
    gold = Color(0xFFE8A33D), goldLight = Color(0xFFF0C079), goldDeep = Color(0xFF8A5D10),
    goldDeepText = Color(0xFF1A1206), signalYellow = Color(0xFFF0C079),
    titaniumTop = Color(0xFFE7EEF4), titaniumMid = Color(0xFF93A4B2), titaniumLow = Color(0xFF67798A), titaniumDeep = Color(0xFF4A5A68),
    tipCore = Color(0xFFFFFFFF),
)

// Binnacle Light is a RE-TUNE, not an inversion: the dark accents fail contrast on white, so
// amber/cyan darken (#8A5D10 / #186059) and ramps run pale→deep so "more = more ink".
val BinnacleLightTokens = PaletteTokens(
    surfaceBase = Color(0xFFEEF2F6), surfaceRaised = Color(0xFFFFFFFF), surfaceOverlay = Color(0xFFF3F6F9),
    surfaceInset = Color(0xFFE9EEF3), hairline = Color(0xFFD4DDE5), hairlineStrong = Color(0xFFB9C6D1),
    textPrimary = Color(0xFF0E1419), textSecondary = Color(0xFF4A5A68), textTertiary = Color(0xFF65788A),
    glowAmbient = Color(0xFFF0E4C0),
    accent = Color(0xFF8A5D10), accentHover = Color(0xFF6E4A0C), accentMuted = Color(0xFFF3E7D2), focusRing = Color(0xFF8A5D10),
    recovery000 = Color(0xFFD9B25C), recovery030 = Color(0xFFC89A3A), recovery055 = Color(0xFFA87718),
    recovery078 = Color(0xFF8A5D10), recovery100 = Color(0xFF6E4A0C),
    strain000 = Color(0xFFD9B25C), strain033 = Color(0xFFB88421), strain066 = Color(0xFF8A5D10), strain100 = Color(0xFF6E4A0C),
    sleepAwake = Color(0xFF8496A5), sleepLight = Color(0xFF2E8C83), sleepDeep = Color(0xFF10403B), sleepREM = Color(0xFF186059),
    zone1 = Color(0xFFD9B25C), zone2 = Color(0xFFC89A3A), zone3 = Color(0xFFA87718), zone4 = Color(0xFF8A5D10), zone5 = Color(0xFF6E4A0C),
    statusPositive = Color(0xFF186059), statusWarning = Color(0xFF8A5D10), statusCritical = Color(0xFFA32C1A),
    metricCyan = Color(0xFF186059), metricPurple = Color(0xFF5A6B79), metricAmber = Color(0xFF8A5D10), metricRose = Color(0xFFA32C1A),
    chargeColor = Color(0xFF8A5D10), chargeDeep = Color(0xFF6E4A0C), chargeBright = Color(0xFFC89A3A), chargeGlow = Color(0xFF8A5D10),
    effortColor = Color(0xFF5A6B79), effortDeep = Color(0xFF3E4E5C), effortBright = Color(0xFF8FA0AE), effortGlow = Color(0xFF5A6B79),
    restColor = Color(0xFF186059), restDeep = Color(0xFF10403B), restBright = Color(0xFF2E8C83), restGlow = Color(0xFF186059),
    stressColor = Color(0xFF8A5D10), stressDeep = Color(0xFF186059), stressBright = Color(0xFFA32C1A), stressGlow = Color(0xFF8A5D10),
    scenicCenter = Color(0xFFF7FAFC), scenicEdge = Color(0xFFEEF2F6), scenicStar = Color(0xFFB9C6D1),
    cardFillTop = Color(0xFFFFFFFF), cardFillBottom = Color(0xFFF3F6F9),
    gold = Color(0xFF8A5D10), goldLight = Color(0xFFC89A3A), goldDeep = Color(0xFF6E4A0C),
    goldDeepText = Color(0xFFFFFFFF), signalYellow = Color(0xFFB88421),
    titaniumTop = Color(0xFFDDE1E6), titaniumMid = Color(0xFFB9C6D1), titaniumLow = Color(0xFF8FA0AE), titaniumDeep = Color(0xFF5A6B79),
    tipCore = Color(0xFF0E1419),
)

// Binnacle §11 — the two extra panel modes, mapped from BinnacleTheme.kt's Medium/Contrast columns.
// Medium: mid-tone softened panel. Contrast: true-black low-vision panel (AAA), accents brightened.
val BinnacleMediumTokens = BinnacleDarkTokens.copy(
    surfaceBase = Color(0xFF232F3A), surfaceRaised = Color(0xFF2C3A47), surfaceOverlay = Color(0xFF354553),
    surfaceInset = Color(0xFF1C2833), hairline = Color(0xFF3E4E5C), hairlineStrong = Color(0xFF4E6070),
    textPrimary = Color(0xFFEDF3F8), textSecondary = Color(0xFFB6C6D2), textTertiary = Color(0xFF9EB0BF),
    accentHover = Color(0xFFF3C68A),
    statusPositive = Color(0xFF5AD8CC), statusCritical = Color(0xFFF2907E),
    metricCyan = Color(0xFF5AD8CC), metricPurple = Color(0xFFA6B6C3), metricRose = Color(0xFFF2907E),
    effortColor = Color(0xFFA6B6C3), effortDeep = Color(0xFF5E7080), effortBright = Color(0xFFB6C6D2), effortGlow = Color(0xFFA6B6C3),
    restColor = Color(0xFF3E9A92), restBright = Color(0xFF5AD8CC), restGlow = Color(0xFF5AD8CC),
    sleepAwake = Color(0xFFA6B6C3), sleepLight = Color(0xFF5AD8CC), sleepREM = Color(0xFF3E9A92),
    stressDeep = Color(0xFF5AD8CC), stressBright = Color(0xFFF2907E),
    scenicCenter = Color(0xFF2C3A47), scenicEdge = Color(0xFF232F3A), scenicStar = Color(0xFFB6C6D2),
    cardFillTop = Color(0xFF2C3A47), cardFillBottom = Color(0xFF1C2833),
    titaniumTop = Color(0xFFEDF3F8), titaniumMid = Color(0xFFB6C6D2), titaniumLow = Color(0xFF9EB0BF), titaniumDeep = Color(0xFF5E7080),
)

val BinnacleContrastTokens = BinnacleDarkTokens.copy(
    surfaceBase = Color(0xFF000000), surfaceRaised = Color(0xFF0A0F14), surfaceOverlay = Color(0xFF141C24),
    surfaceInset = Color(0xFF05080B), hairline = Color(0xFF5A6E7E), hairlineStrong = Color(0xFF8098AC),
    textPrimary = Color(0xFFFFFFFF), textSecondary = Color(0xFFDCE7EF), textTertiary = Color(0xFFBCCCD8),
    accent = Color(0xFFFFC061), accentHover = Color(0xFFFFD79A), focusRing = Color(0xFFFFC061),
    recovery000 = Color(0xFF8A5D10), recovery030 = Color(0xFFB87F26), recovery055 = Color(0xFFE8A33D),
    recovery078 = Color(0xFFFFC061), recovery100 = Color(0xFFFFD79A),
    strain000 = Color(0xFF8A5D10), strain033 = Color(0xFFB87F26), strain066 = Color(0xFFE8A33D), strain100 = Color(0xFFFFD79A),
    zone1 = Color(0xFF8A5D10), zone2 = Color(0xFFB87F26), zone3 = Color(0xFFE8A33D), zone4 = Color(0xFFFFC061), zone5 = Color(0xFFFFD79A),
    sleepAwake = Color(0xFFB8C7D3), sleepLight = Color(0xFF7BEFE3), sleepDeep = Color(0xFF2E7E77), sleepREM = Color(0xFF4FB3A8),
    statusPositive = Color(0xFF7BEFE3), statusWarning = Color(0xFFFFC061), statusCritical = Color(0xFFFF8E7A),
    metricCyan = Color(0xFF7BEFE3), metricPurple = Color(0xFFB8C7D3), metricAmber = Color(0xFFFFC061), metricRose = Color(0xFFFF8E7A),
    chargeColor = Color(0xFFFFC061), chargeDeep = Color(0xFFB87F26), chargeBright = Color(0xFFFFD79A), chargeGlow = Color(0xFFFFC061),
    effortColor = Color(0xFFB8C7D3), effortDeep = Color(0xFF7A8B99), effortBright = Color(0xFFDCE7EF), effortGlow = Color(0xFFB8C7D3),
    restColor = Color(0xFF4FB3A8), restDeep = Color(0xFF2E7E77), restBright = Color(0xFF7BEFE3), restGlow = Color(0xFF7BEFE3),
    stressColor = Color(0xFFFFC061), stressDeep = Color(0xFF7BEFE3), stressBright = Color(0xFFFF8E7A), stressGlow = Color(0xFFFFC061),
    scenicCenter = Color(0xFF0A0F14), scenicEdge = Color(0xFF000000), scenicStar = Color(0xFFDCE7EF),
    cardFillTop = Color(0xFF0A0F14), cardFillBottom = Color(0xFF05080B),
    gold = Color(0xFFFFC061), goldLight = Color(0xFFFFD79A), goldDeep = Color(0xFFB87F26),
    titaniumTop = Color(0xFFFFFFFF), titaniumMid = Color(0xFFDCE7EF), titaniumLow = Color(0xFFBCCCD8), titaniumDeep = Color(0xFF8098AC),
)

// Binnacle §11 vision profiles: hue can't be recovered under CVD, so separation is carried by
// LIGHTNESS — cyan lightens rather than shifts, and danger moves OFF red so it can't collide with
// amber. Applied AFTER the panel-mode pick (dark/medium/contrast bases; the light re-tune is skipped —
// its darkened accents need their own audit before a profile can responsibly retune them).
fun PaletteTokens.withBinnacleVision(v: BinnacleVision): PaletteTokens = when (v) {
    BinnacleVision.OFF -> this
    BinnacleVision.DEUTAN, BinnacleVision.PROTAN -> copy(
        accent = Color(0xFFEA9F3E), accentHover = Color(0xFFF5C489), focusRing = Color(0xFFEA9F3E),
        statusPositive = Color(0xFFADEBE5), metricCyan = Color(0xFFADEBE5),
        statusCritical = Color(0xFFDD5F7F), metricRose = Color(0xFFDD5F7F),
        restColor = Color(0xFF5F9C96), restBright = Color(0xFFADEBE5), restGlow = Color(0xFFADEBE5),
        sleepLight = Color(0xFFADEBE5), sleepREM = Color(0xFF5F9C96),
        stressDeep = Color(0xFFADEBE5), stressBright = Color(0xFFDD5F7F),
        gold = Color(0xFFEA9F3E), goldLight = Color(0xFFF5C489),
    )
    BinnacleVision.TRITAN -> copy(
        accent = Color(0xFFED9E5A), accentHover = Color(0xFFF6C39A), focusRing = Color(0xFFED9E5A),
        statusPositive = Color(0xFFADEBE5), metricCyan = Color(0xFFADEBE5),
        statusCritical = Color(0xFFDD5F63), metricRose = Color(0xFFDD5F63),
        restColor = Color(0xFF5F9C96), restBright = Color(0xFFADEBE5), restGlow = Color(0xFFADEBE5),
        sleepLight = Color(0xFFADEBE5), sleepREM = Color(0xFF5F9C96),
        stressDeep = Color(0xFFADEBE5), stressBright = Color(0xFFDD5F63),
        gold = Color(0xFFED9E5A), goldLight = Color(0xFFF6C39A),
    )
}

enum class BinnaclePanel(val storageValue: String, val label: String) {
    AUTO("auto", "Auto"),
    MEDIUM("medium", "Medium"),
    CONTRAST("contrast", "Contrast");

    companion object {
        fun fromStorage(raw: String?): BinnaclePanel = entries.firstOrNull { it.storageValue == raw } ?: AUTO
    }
}

enum class BinnacleVision(val storageValue: String, val label: String) {
    OFF("off", "Off"),
    DEUTAN("deutan", "Deutan"),
    PROTAN("protan", "Protan"),
    TRITAN("tritan", "Tritan");

    companion object {
        fun fromStorage(raw: String?): BinnacleVision = entries.firstOrNull { it.storageValue == raw } ?: OFF
    }
}

/** Binnacle-skin panel mode (Auto follows the Theme scheme) + vision profile. Snapshot-mirrored. */
object BinnacleModePrefs {
    private const val FILE = "noop_prefs"
    private const val KEY_PANEL = "theme.binnaclePanel"
    private const val KEY_VISION = "theme.binnacleVision"
    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var panel by mutableStateOf(BinnaclePanel.AUTO)
        private set
    var vision by mutableStateOf(BinnacleVision.OFF)
        private set

    fun load(ctx: Context) {
        panel = BinnaclePanel.fromStorage(prefs(ctx).getString(KEY_PANEL, BinnaclePanel.AUTO.storageValue))
        vision = BinnacleVision.fromStorage(prefs(ctx).getString(KEY_VISION, BinnacleVision.OFF.storageValue))
    }

    fun setPanel(ctx: Context, value: BinnaclePanel) {
        panel = value
        prefs(ctx).edit().putString(KEY_PANEL, value.storageValue).apply()
    }

    fun setVision(ctx: Context, value: BinnacleVision) {
        vision = value
        prefs(ctx).edit().putString(KEY_VISION, value.storageValue).apply()
    }
}

// MARK: - UI skin (Noop stock vs Binnacle) — persisted, snapshot-mirrored like ChartStylePrefs

enum class UiSkin(val storageValue: String, val label: String) {
    STOCK("stock", "Noop"),
    BINNACLE("binnacle", "Binnacle");

    companion object {
        fun fromStorage(raw: String?): UiSkin = entries.firstOrNull { it.storageValue == raw } ?: STOCK
    }
}

/** Skin preference. NoopTheme reads [skin] (snapshot state), so a flip re-themes live. */
object SkinPrefs {
    private const val FILE = "noop_prefs"
    private const val KEY = "theme.skin"
    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var skin by mutableStateOf(UiSkin.STOCK)
        private set

    fun load(ctx: Context) {
        skin = UiSkin.fromStorage(prefs(ctx).getString(KEY, UiSkin.STOCK.storageValue))
    }

    fun set(ctx: Context, value: UiSkin) {
        skin = value
        prefs(ctx).edit().putString(KEY, value.storageValue).apply()
    }
}

// MARK: - Chart style (data-viz colour mode) + the Classic throwback ramps

enum class ChartStyle(val storageValue: String, val label: String) {
    TITANIUM("titanium", "Titanium"),
    CLASSIC("classic", "Classic");

    companion object {
        fun fromStorage(raw: String?): ChartStyle = entries.firstOrNull { it.storageValue == raw } ?: TITANIUM
    }
}

/** Chart-colour preference, persisted in `noop_prefs` and mirrored in snapshot state so a flip
 *  re-colours every gauge/chart live (the Palette ramp accessors read [ChartStylePrefs.style]). */
object ChartStylePrefs {
    private const val FILE = "noop_prefs"
    private const val KEY = "chart.style"
    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var style by mutableStateOf(ChartStyle.TITANIUM)
        private set

    fun load(ctx: Context) {
        style = ChartStyle.fromStorage(prefs(ctx).getString(KEY, ChartStyle.TITANIUM.storageValue))
    }

    fun set(ctx: Context, value: ChartStyle) {
        style = value
        prefs(ctx).edit().putString(KEY, value.storageValue).apply()
    }
}

/** The Classic (throwback) data ramps — light/dark tuned. Picked by the Palette accessors when
 *  ChartStylePrefs.style == CLASSIC. Surfaces/text/accent are never classic — only data encodings. */
data class ClassicRamp(
    val recovery: List<Pair<Float, Color>>,
    val strain: List<Pair<Float, Color>>,
    val stress: List<Pair<Float, Color>>,
    val sleepAwake: Color, val sleepLight: Color, val sleepDeep: Color, val sleepREM: Color,
    val zone1: Color, val zone2: Color, val zone3: Color, val zone4: Color, val zone5: Color,
    val statusPositive: Color, val statusWarning: Color, val statusCritical: Color,
    val metricCyan: Color, val metricPurple: Color, val metricAmber: Color, val metricRose: Color,
    val chargeColor: Color, val chargeDeep: Color, val chargeBright: Color,
    val effortColor: Color, val effortDeep: Color, val effortBright: Color,
    val restColor: Color, val restDeep: Color, val restBright: Color,
    val stressColor: Color, val stressDeep: Color, val stressBright: Color,
)

val ClassicDark = ClassicRamp(
    recovery = listOf(0.0f to Color(0xFFE5483B), 0.30f to Color(0xFFEE8B3C), 0.55f to Color(0xFFF2C53D), 0.78f to Color(0xFFA6D04E), 1.0f to Color(0xFF46B45A)),
    strain = listOf(0.0f to Color(0xFF7FB2E8), 0.33f to Color(0xFF4A90E2), 0.66f to Color(0xFF2F6FCB), 1.0f to Color(0xFF1E4FA0)),
    stress = listOf(0.0f to Color(0xFF46B45A), 0.5f to Color(0xFFF2C53D), 1.0f to Color(0xFFE5483B)),
    sleepAwake = Color(0xFFC9CCD6), sleepLight = Color(0xFF6FA8E8), sleepDeep = Color(0xFF2A4C8F), sleepREM = Color(0xFF8E6FD6),
    zone1 = Color(0xFF9AA7B5), zone2 = Color(0xFF46B45A), zone3 = Color(0xFFF2C53D), zone4 = Color(0xFFEE8B3C), zone5 = Color(0xFFE5483B),
    statusPositive = Color(0xFF46B45A), statusWarning = Color(0xFFF2C53D), statusCritical = Color(0xFFE5483B),
    metricCyan = Color(0xFF3FA9C9), metricPurple = Color(0xFF8E6FD6), metricAmber = Color(0xFFF2C53D), metricRose = Color(0xFFE5483B),
    chargeColor = Color(0xFF46B45A), chargeDeep = Color(0xFF2E9E4F), chargeBright = Color(0xFF86D98E),
    effortColor = Color(0xFF4A90E2), effortDeep = Color(0xFF2F6FCB), effortBright = Color(0xFF7FB2E8),
    restColor = Color(0xFF6FA8E8), restDeep = Color(0xFF2A4C8F), restBright = Color(0xFF8E6FD6),
    stressColor = Color(0xFFF2C53D), stressDeep = Color(0xFF46B45A), stressBright = Color(0xFFE5483B),
)

val ClassicLight = ClassicRamp(
    recovery = listOf(0.0f to Color(0xFFCB3A2F), 0.30f to Color(0xFFD87328), 0.55f to Color(0xFFCFA528), 0.78f to Color(0xFF74A53A), 1.0f to Color(0xFF2E9E4F)),
    strain = listOf(0.0f to Color(0xFF5E92D6), 0.33f to Color(0xFF3A74C4), 0.66f to Color(0xFF284F9C), 1.0f to Color(0xFF1C3E80)),
    stress = listOf(0.0f to Color(0xFF2E9E4F), 0.5f to Color(0xFFCFA528), 1.0f to Color(0xFFCB3A2F)),
    sleepAwake = Color(0xFF8C95A3), sleepLight = Color(0xFF3A80D6), sleepDeep = Color(0xFF203E73), sleepREM = Color(0xFF6A4FC0),
    zone1 = Color(0xFF828D9B), zone2 = Color(0xFF2E9E4F), zone3 = Color(0xFFCFA528), zone4 = Color(0xFFD87328), zone5 = Color(0xFFCB3A2F),
    statusPositive = Color(0xFF2E9E4F), statusWarning = Color(0xFFCFA528), statusCritical = Color(0xFFCB3A2F),
    metricCyan = Color(0xFF2E92B4), metricPurple = Color(0xFF6A4FC0), metricAmber = Color(0xFFCFA528), metricRose = Color(0xFFCB3A2F),
    chargeColor = Color(0xFF2E9E4F), chargeDeep = Color(0xFF207A3C), chargeBright = Color(0xFF5FBE6E),
    effortColor = Color(0xFF3A74C4), effortDeep = Color(0xFF284F9C), effortBright = Color(0xFF5E92D6),
    restColor = Color(0xFF3A80D6), restDeep = Color(0xFF203E73), restBright = Color(0xFF6A4FC0),
    stressColor = Color(0xFFCFA528), stressDeep = Color(0xFF2E9E4F), stressBright = Color(0xFFCB3A2F),
)

// MARK: - Appearance preference (System / Light / Dark)

enum class AppearanceMode(val storageValue: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromStorage(raw: String?): AppearanceMode =
            entries.firstOrNull { it.storageValue == raw } ?: SYSTEM
    }
}

/** Theme preference, persisted in `noop_prefs` and mirrored in snapshot state so the toggle is live.
 *  [load] is called once from MainActivity before first composition (no flash); [set] writes + flips. */
object AppearancePrefs {
    private const val FILE = "noop_prefs"
    private const val KEY = "theme.appearance"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Live appearance mode read by NoopTheme; defaults to System until [load] runs. */
    var mode by mutableStateOf(AppearanceMode.SYSTEM)
        private set

    fun load(ctx: Context) {
        mode = AppearanceMode.fromStorage(prefs(ctx).getString(KEY, AppearanceMode.SYSTEM.storageValue))
    }

    fun set(ctx: Context, value: AppearanceMode) {
        mode = value
        prefs(ctx).edit().putString(KEY, value.storageValue).apply()
    }
}
