# Binnacle port — NOOP Android (full purist)

Plan only. No code changed. Target: `Noop-Binnacle`; leave `noop-main` vanilla upstream.

## The core tension (read first)

NOOP's visual identity **encodes data in hue**: recovery scored red→green, a strain ramp,
five HR-zone colours, sleep-stage colours, and four per-domain "colour worlds"
(Charge/Effort/Rest/Stress). Binnacle's first visual rule is the opposite —
**colour means *state*, never decoration; distinguish data by lightness, position and glyph;
spend novelty on exactly one signature and keep everything else quiet.**

So a *full purist* port is not a recolour — it's removing NOOP's central design device
(rainbow data encoding) and rebuilding legibility on lightness + glyphs. That's the bulk of
the work and the real decision. Everything else is mechanical.

Scale: `Palette.*` is read ~1,740× and its API is **frozen**, so retuning token *values*
reskins the whole app with zero call-site edits. But the decorative-colour system is used at
**487 call sites across 34 files** — those are what purism forces us to revisit.

## Phasing

Phases 1–2 are safe and reversible (values only). Phase 3 is the purist heart and the risk.
4–6 are additive. 7 is optional.

### Phase 1 — Colour foundation (value-only, whole-app reskin)
Rewrite `DarkTokens` to Binnacle's surface/text/accent values; retune `LightTokens` to
Binnacle Light (the re-tune, not an inversion). No call sites change.

| NOOP token | today (dark) | → Binnacle | note |
|---|---|---|---|
| `surfaceBase` | `#121518` | `ink #0E1419` | |
| `surfaceRaised` | `#25292C` | `panel #151E27` | |
| `surfaceOverlay` | `#1C1F26` | `panelAlt #1B2731` | |
| `surfaceInset` | `#1F2229` | `well #101922` | recessed → well |
| `hairline` / `hairlineStrong` | `#21304A` / `#2E3C57` | `line #26333E` / `lineStrong #334353` | |
| `textPrimary/Secondary/Tertiary` | `#F4F6F8` / `#C8CFD8` / `#8A94A4` | `text #E7EEF4` / `muted #93A4B2` / `caption #67798A` | |
| `accent` (+hover/focus) | `#60A0E0` **blue** | `now #E8A33D` **amber** | brand shift back to amber |
| `statusPositive` | `#03E095` green | `good #4FD1C5` cyan | |
| `statusCritical` | `#E0662F` | `stop #E06C5A` | |
| `statusWarning` | `#F0A020` | — | **no clean Binnacle slot — see Decisions** |

### Phase 2 — Type
NOOP bundles no fonts (`FontFamily.SansSerif` everywhere). Add the three faces to
`res/font/` and repoint `NoopType`:
- Space Grotesk → display (hero numbers, headers)
- Inter → UI/body
- IBM Plex Mono → data (times, paths, hex, logs — NOOP already has `NoopType.mono`)

Purist call: machine data reads mono. NOOP currently renders live metric values as tabular
sans. Decide per-style whether live values move to mono or stay sans (see Decisions).

### Phase 3 — Purist colour (the hard part: 487 sites / 34 files)
Collapse decorative hue to the state contract + lightness.

1. **Domain worlds → semantic or single-hue.** `chargeColor/effortColor/restColor/stressColor`
   and their deep/bright/glow ramps stop being four hues. Either point them all at the same
   neutral surface accents, or give each a **lightness** step of one hue. They can no longer
   *mean* by colour.
2. **Data ramps → single-hue lightness ramps.** `recoveryStops`, `strainStops`, HR `zone1–5`,
   sleep stages: replace rainbow with a dark→light ramp of one hue (amber `now`), so a value
   still reads by lightness, and pair categorical series (zones, stages) with **glyphs/labels/
   position**, not colour. This is what keeps charts legible without decorative hue.
3. **The one signature.** Binnacle §06 — pick NOOP's single loud element (recommend the
   **recovery/charge ring**, the thing the app exists to show) and let *it* carry amber boldly;
   everything else goes quiet.
4. **Retire the Classic/Titanium data-viz toggle** (`ChartStylePrefs`) or repurpose it, since
   both are rainbow encodings the purist rule rejects.

Risk: the recovery ring, gauges, hypnogram and HR-zone charts currently communicate almost
entirely through colour. Rebuilding them on lightness+glyph is genuine design work per surface,
not a find-replace. This phase should be done surface-by-surface with screenshots.

### Phase 4 — State contract + glyphs
Port `binnacle-glyphs.svg` → `res/drawable/gl_*.xml` vectors (running/done/held/failed, using
`currentColor` via tint). Adopt the `UiState` enum from `BinnacleTheme.kt` so every state
returns colour **and** icon together. Refactor NOOP's status pills / source badges to always
pair the two. Ship-gate: the **greyscale test** (states must survive colour removal).

### Phase 5 — Modes & vision profiles
Extend `AppearanceMode` (add `MEDIUM`, `CONTRAST`) and add a `VisionProfile` pref
(None/Deutan/Protan/Tritan). Wire both into the `NoopTheme` token swap (Binnacle already
provides `MediumColors`, `ContrastColors`, `.deutan()`, `.tritan()`). Additive; no call sites.

### Phase 6 — Shape, spacing, motion
Adopt Binnacle radii (card 10 / control 8 / pill 20dp) and the 4dp spacing rhythm in
`Metrics`/`NoopShapes`. Audit `Motion`: cut decorative pulse/breathe, keep the reduced-motion
guard.

### Phase 7 — Helm & Voice (optional, non-visual)
Drop the four Helm tenets into a `PRINCIPLES.md`; pass NOOP's copy through the Voice rules
(active voice, name-by-what-the-user-controls, errors say the fix). Behavioural, not code.

## Decisions needed before Phase 3

1. **`statusWarning`** has no Binnacle state. Fold into `stop` (treat as problem) or `held`
   (treat as "attention, not moving")?
2. **Live metric values** — mono (strict Binnacle "data") or keep tabular sans (readability)?
3. **Categorical data colour** (HR zones, sleep stages): single-hue lightness ramp, or a small
   greyscale-safe set distinguished primarily by glyph/label?
4. **The one signature element** — confirm the recovery/charge ring (my recommendation) vs
   another surface.
5. **Classic/Titanium toggle** — remove, or keep as an explicit "non-Binnacle" escape hatch?

## Effort / risk snapshot

| Phase | Effort | Risk | Reversible |
|---|---|---|---|
| 1 Colour values | S | low | yes (values) |
| 2 Type | S | low | yes |
| 3 Purist colour | **L** | **high** | partial |
| 4 Glyphs + states | M | med | yes |
| 5 Modes/vision | S–M | low | yes |
| 6 Shape/motion | S | low | yes |
| 7 Helm/Voice | S | none | yes |

## Suggested order

Ship Phase 1+2 first (immediate Binnacle look, near-zero risk), then 4+5+6 (contract + modes +
shape), then tackle Phase 3 surface-by-surface last, once the decisions above are settled.
Resolve the 5 decisions and I'll turn any phase into concrete edits.

---

# Compliance audit — 2026-07-21 (against Binnacle-DesignLanguage)

## Compliant
- **§02 Colour** — accent contract mapped into both schemes; purist lightness ramps for
  recovery/strain/zones (amber), sleep (cyan, awake on neutral); Classic/Titanium rainbow
  encodings yield while the skin is active; light mode is the re-tune, not an inversion.
- **§03 Typography (faces)** — Space Grotesk / Inter / IBM Plex Mono bundled, role-mapped,
  skin-reactive. (Latin subsets; accented locales fall back per-glyph.)
- **§12 Glyphs** — core set ported (24×24, ~2px, round, currentColor-equivalent tinting);
  NOOP extension sheet (domains + metrics) designed under the same contract and documented
  in the house doc; UiState pairs colour+glyph structurally (StateLine).
- **§16 Ports** — mapped Binnacle values into the host's frozen token API behind its own
  theme seam; stock byte-identical; pattern recorded in the house doc.
- **Helm 2 (attribute)** — font OFL attribution added to ATTRIBUTION.md.
- **Helm 3 (fail loud)** — writeback outcomes are semantic states with the fix in reach
  (permission line = warning glyph + tap-to-regrant); raw causes logged locally (NoopHC).

## Partial
- **Never colour alone** — enforced on retrofitted surfaces (writeback status, hero domains,
  Key Metrics tiles). Many legacy surfaces still signal by colour only (readiness flags,
  source badges, zone charts' in-chart encoding). Rollout continues surface-by-surface.
- **§03 (machine data reads mono)** — DECIDED: mono for small data (timestamps, log-ish
  lines) on the Binnacle skin; big tile/gauge numbers stay Inter tnum for readability.
  First conversion: the writeback "Last shared" timestamp. Rollout continues.
- **Part III Voice** — new copy follows the rules; existing copy unaudited.

## Not yet compliant
- **§06 Signature rule** — the biggest open violation. Day-cycle sky, card washes, glows,
  and the liquid gauges are all loud at once. Candidate signature: the liquid gauge trio;
  everything else quiets around it. (Planned "signature purge".)
- **§11 Modes & vision profiles** — DONE: Medium + Contrast panel modes and deutan/protan/
  tritan profiles shipped (Settings → Appearance, Binnacle skin only). Known gap: vision
  retune skips the Light re-tune pending its own contrast audit.
- **§07 Motion restraint / reduced-motion** — unaudited; NOOP has ambient liquid/breathe
  animation. Doc requires reduced-motion respected.
- **§13 Form controls** — RESOLVED: the active-calories opt-in converted to a switch
  ("switch means immediate"); it applies on flip like the sharing toggle above it.
- **§04/§05/§14** — layout grammar, component kit, loading/empty/error idioms: NOOP's own,
  unaudited against the doc.
