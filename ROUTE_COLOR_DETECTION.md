# Route Color Detection

A redesign of "which pixels are this route's color" — from a naive full-frame hue-isolation video
effect into real per-hold object detection: segment actual climbing-hold objects, validate their
color as a whole object (not just some matching pixels), and highlight only validated hold pixels.
This document explains what's real, what's still broken, and what to do next.

## What's real vs. what's still missing

**Real and unit-tested (97 tests, `app/src/test/java/com/example/climb/colordetection/`):**

- **Color science** (`ColorSpace.kt`, `ColorDistance.kt`, `TargetColorModel.kt`,
  `RouteColorProfiles.kt`) — Lab/HSV conversion, CIE76/CIEDE2000 distance, per-`RouteColor`
  predefined profiles with tighter hue tolerance for close neighbors (RED/ORANGE/YELLOW/PINK).
- **Object detection** (`PixelBuffer.kt`, `ConnectedComponents.kt`, `HoldComponentDetector.kt`) —
  strict candidate seeding (hue + saturation + CIE76 gates) and 4-connected component labeling.
- **Boundary refinement** (`SobelEdgeDetector.kt`, `MooreBoundaryTracer.kt`,
  `HoldBoundaryRefiner.kt`) — bounded, edge-aware region growing per hold, merge of touching grown
  masks (strict 4-connectivity, not 8 — avoids a real contour-corruption bug found and fixed during
  review), and real contour tracing.
- **Object validation** (`HoldColorValidator.kt`, `HoldConfidenceEvaluator.kt`) — whole-object color
  consistency (not just seed-pixel consistency), rejects holds whose boundary growth leaked into a
  wall, real (non-preliminary) confidence scoring.
- **Segmentation metrics & regression** (`SegmentationMetrics.kt`,
  `RouteColorDetectorRegressionTest.kt`) — IoU/Precision/Recall, pinned regression bars on synthetic
  fixtures.
- **The full pipeline is wired into the real app**: `RouteColorDetector.detect()` is the single
  entry point (`app/src/main/java/com/example/climb/colordetection/RouteColorDetector.kt`), called
  from `HoldHighlightPipeline` (`app/src/main/java/com/example/climb/playback/`), which both the
  live `DetailScreen` preview and `VideoEffectExporter.exportWithHoldHighlight` use — so what you
  preview is what gets baked into a saved/shared video.
- **The original naive hue-isolation effect (`ColorIsolationEffect`) is the default, always-on
  experience** — real object detection is an explicit, opt-in "bonus," not the automatic path. This
  was a deliberate reversal: the detection pipeline alone was too strict for real footage (see
  below), so forcing it as the only rendering path made color highlighting silently stop working
  for real recorded climbs. `DetailScreen.kt`'s `DetectionBonusState` (Idle/Loading/Active/NotFound)
  governs this opt-in flow for both ways of reaching a result — the generic "Detect holds (bonus)"
  button (predefined per-color profile) and "Calibrate on this hold" (see below).
- **Developer debug tooling** (`HoldDetectionDebugScreen.kt`, debug-build-only, reached via
  `DetailScreen`'s "Debug: view hold detection stages" link) — visualizes Phase-3 candidates,
  final validated holds, and refined-but-rejected holds with real per-hold numbers (confidence,
  consistency ratios, growth ratio). Confirmed on-device to draw correct green contour outlines
  around real detected holds when detection succeeds.

**Confirmed on real device + real footage:**

- The default hue-isolation effect correctly isolates a route's color against a grayscale
  background on real recorded video.
- The generic "Detect holds (bonus)" button found and correctly contoured real holds in a real gym
  photo (6 holds detected on one real YELLOW-route video, verified both in the live preview and in
  the debug screen's contour overlay).

## The core known limitation: one global color-distance threshold cannot cover real-world lighting

Real-footage testing (a real PINK-route video) proved the strict candidate-seeding gate
(`RouteColorDetectionConfig.STRICT_DELTA_E_THRESHOLD`) has a hard, measured limit, not just a
tuning gap:

- The real photographed pink hold's closest pixel (among pixels that already pass the tight hue
  gate) sits **47.75 CIE76 units** from the generic, theoretical `RouteColor.PINK.hex`-derived Lab
  center — real gym lighting/camera JPEG compression shifts captured color substantially even while
  preserving hue almost perfectly (best hue match measured: 0.23° off).
- Raising the global threshold enough to cover that would require ~55-58 — but the largest real
  measured **cross-color** gap (RED vs. ORANGE, the closest color-wheel neighbors) is only 28.41
  CIEDE2000 / 46.60 CIE76. There is no single global constant that both detects this real photo
  and keeps different route colors discriminated — raising it far enough to fix one video would
  silently defeat cross-color discrimination for every other color pair.
- Full reasoning and exact numbers are in `RouteColorDetectionConfig.kt`'s own doc comment on
  `STRICT_DELTA_E_THRESHOLD` — read it before touching that constant again.

**This is why "Calibrate on this hold" exists**: instead of comparing a photo against one generic
theoretical color, the user taps the actual hold, `RoiSampler` samples real pixels around the tap,
and `ColorCalibrator` (median/MAD-robust) builds a `TargetColorModel` centered on that hold's actual
captured color in this specific lighting. `HoldHighlightPipeline.buildMask(frame, targetModel)` and
`exportWithHoldHighlight(context, inputPath, outputPath, targetModel)` both accept a calibrated
model directly, so a calibrated result exports exactly what was previewed (this was a real bug,
found and fixed during review — export used to silently re-detect with the generic profile,
discarding the calibration).

## What's NOT confirmed working — the actual next step

**The "Calibrate on this hold" tap gesture has not been confirmed to work reliably on-device.**
Manual on-device testing hit repeated, hard-to-diagnose interaction issues:

- The picker dialog (`CalibrationPickerDialog` in `DetailScreen.kt`) sometimes rendered a
  double-exposed/stale-looking frame instead of a clean static image.
- Taps inside the displayed image frequently did not trigger `onTap` at all — the dialog should
  close immediately and synchronously on any registered tap (`calibrationPickerState` flips to
  `Hidden` before any async work starts), but it was observed staying open through several tap
  attempts at coordinates that should have been well within the image bounds (cross-checked via
  `uiautomator dump`, not guessed).
- One tap appears to have fallen through to whatever was behind the dialog (the app navigated back
  to the Home screen), suggesting the dialog's tap/dismiss handling may not be fully isolating touch
  events from the screen behind it.

A static read of `CalibrationPickerDialog`'s code (`Dialog` + `Image` with
`Modifier.pointerInput(Unit) { detectTapGestures { ... } }`, gated on `onSizeChanged` reporting a
non-zero size) didn't reveal an obvious logic bug — the `offset`/`displayedSize` plumbing into
`DebugCoordinateMapper.unmapPoint` looks correct. So either this is a real, subtler bug (touch
consumption/z-order, a race between `onSizeChanged` and the first tap, or something specific to
`Dialog`'s default window behavior) or it's a device/interaction artifact from scripted `adb input
tap` testing rather than a real finger. **This needs to be tested with an actual finger on the
device**, not just adb-scripted taps, before trusting or further debugging the reported symptoms.

### Next steps, in priority order

1. **Manually test "Calibrate on this hold" with real touch input** on the device. If it reproduces
   there too, instrument `CalibrationPickerDialog` (e.g. temporary logging in the `detectTapGestures`
   block) to confirm whether `onTap` is even being invoked, then work backward from there — possible
   real culprits worth checking first: whether `Dialog`'s default `usePlatformDefaultWidth` is
   clipping/offsetting the actual touchable region relative to what's rendered, or whether
   `onSizeChanged` fires late enough that early taps land before `displayedSize` is set (the current
   code silently no-ops in that case rather than surfacing it — that's a plausible dead-air cause).
2. **Get more real calibration data points** — this project has exactly one real calibrated
   measurement (the PINK video). More real footage across different gyms/lighting would validate
   whether `ColorCalibrator`'s median/MAD approach is actually robust in practice, and whether the
   ROI size (`RoiSampler.DEFAULT_RADIUS_PX = 10`) is well-chosen.
3. **Persist a successful calibration** — right now a calibrated `TargetColorModel` lives only in
   `DetailScreen`'s in-memory `DetectionBonusState`, lost on navigating away. If tap-to-calibrate
   works reliably, consider persisting the calibrated Lab/HSV center per climb (would need new
   `ClimbEntity` fields) so a user doesn't have to recalibrate every time they reopen a climb.
4. **Real-photo test dataset** — every fixture in every phase's tests is synthetic. Phase 8's own
   `SegmentationMetrics`/regression tests document a reserved (currently-empty) location for real
   annotated photos: `app/src/test/resources/colordetection/realFrames/`. Populating this would let
   segmentation-quality regressions be caught against real footage, not just synthetic squares.
5. **Small-hold-under-~20px limitation** — even after the growth-radius fix
   (`RouteColorDetectionConfig.GROWTH_RADIUS_FRACTION_OF_HOLD_EXTENT`, 0.15 → 0.05), an isolated
   hold smaller than ~20px against a plain wall still fails the consistency floor. Documented as an
   accepted limit in `RouteColorDetectionConfig.kt`; revisit only if real footage shows this
   matters in practice.
6. **Explicitly deferred, not started**: live/tracking detection during recording (this pipeline
   only ever analyzes one static reference frame — see `HoldHighlightPipeline`'s own doc comments
   for why), person/clothing exclusion masking, and GrabCut-based segmentation (evaluated and
   declined in favor of the current pure-Kotlin edge-aware growing — see `HoldBoundaryRefiner.kt`'s
   doc comment for the reasoning, revisit only if bounded growing proves insufficient against real
   footage).
