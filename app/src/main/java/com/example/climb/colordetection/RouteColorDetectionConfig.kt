package com.example.climb.colordetection

/**
 * Centralized, documented tuning knobs for route-color detection — deliberately not scattered
 * across the detection classes as inline magic numbers. Phase 2 only introduces the color-model
 * layer's own thresholds; later phases (object detection, boundary refinement) will add their own
 * sections here rather than hardcoding new constants elsewhere.
 */
object RouteColorDetectionConfig {
    /** CIE76/CIEDE2000 distance below this = a confident, "definitely this color" match.
     *
     * Originally 12.0, chosen from CIEDE2000's commonly-cited just-noticeable-difference guidance
     * (~2.3 = a JND under ideal viewing conditions) with no real-footage validation. Raised to 20.0
     * after an initial real-footage diagnostic against an actual user-recorded gym video measured
     * the single closest real pixel in the frame at CIE76 16.04 from the theoretical hex-derived
     * Lab center. That first fix was later found to be based on an INCOMPLETE reading of the data:
     * that "closest-anywhere" pixel's hue distance was actually 9.4 degrees — just outside PINK's
     * own 8-degree tight tolerance — so it was never a viable seeding candidate regardless of this
     * threshold. A follow-up, more precise measurement found the number that actually matters: the
     * closest CIE76 distance among pixels that ALREADY pass both the saturation range and the
     * tight hue gate together is 47.75 — over 2x this constant's current 20.0 value. Real, correctly
     * pink/magenta-hued pixels in this photo simply sit that far from `RouteColor.PINK.hex`'s
     * theoretical Lab center under this gym's actual lighting/camera conditions.
     *
     * This constant was deliberately NOT raised further to cover that 47.75 gap, because doing so
     * is empirically incompatible with this file's own mandatory cross-color discrimination
     * guarantees (see [ColorDistanceTest]'s "ciede2000 and hue distance clearly separate red from
     * orange" and "blue and purple are clearly separated by hue distance" tests, which assert
     * `realGap > STRICT_DELTA_E_THRESHOLD` using CIEDE2000, not CIE76): the measured real
     * CIEDE2000 gaps between actual route-color pairs are RED-vs-ORANGE = 28.41 and
     * BLUE-vs-PURPLE = 38.18 (the largest gap measured across any tested pair). Since even the
     * largest real cross-color gap (38.18) is smaller than the ~55-58 this constant would need to
     * reach to seed the real pink footage, there is no single value that satisfies both "detect
     * this real photo's pink holds" and "keep route colors apart" at once — this isn't a tuning
     * problem, it's proof a single global CIE76/CIEDE2000 constant cannot cover both real-world
     * per-photo lighting deviation and cross-color safety simultaneously. Raising it far enough to
     * fix one real video would silently defeat the discrimination the whole project exists to
     * provide for every other color pair. Stays at 20.0 — still gives real margin over the
     * original 12.0 for moderate lighting deviation, and over the (correct) real CIE76 cross-color
     * gaps (redToOrange CIE76 = 46.60, blueToPurple CIE76 = 59.20, grayToRed CIE76 = 77.89) — while
     * leaving the specific real video that surfaced the 47.75 gap undetected until a proper
     * tap-to-calibrate flow (already built as [ColorCalibrator], not yet wired to any UI) lets
     * detection calibrate against THIS gym's actual captured colors instead of one generic
     * theoretical hex value for every gym's lighting. This is the concrete, measured evidence that
     * per-photo calibration — not a bigger global constant — is the correct fix for real-world
     * lighting variance. */
    const val STRICT_DELTA_E_THRESHOLD = 20.0

    /** CIE76/CIEDE2000 distance below this (but above [STRICT_DELTA_E_THRESHOLD]) = a possible
     * match — later phases should treat this band as needing corroborating evidence (e.g. hue
     * proximity, how much of a candidate object's surface clears the strict bar) rather than
     * accepting it outright. Above this distance, a pixel/object is not this route color, full
     * stop.
     *
     * Deliberately NOT raised in lockstep with [STRICT_DELTA_E_THRESHOLD]'s real-footage-informed
     * increase (12.0->20.0), even though the two originally sat a coincidental +10 apart — this
     * constant does double duty ([HoldBoundaryRefiner]'s per-ring growth admission test, AND
     * [HoldColorValidator]'s whole-object consistency check that specifically exists to CATCH
     * wall-halo dilution), and a first attempt at raising both together (to 30.0) was empirically
     * shown to be wrong: every wall-halo pixel in `RouteColorDetectorRegressionTest`'s and
     * `HoldColorValidatorTest`'s fixtures fell within the loosened window of the hold's own red
     * median too, so previously-correctly-flagged wall-diluted holds started reading as a perfect
     * 1.0 consistency ratio — silently defeating the exact discrimination this threshold's
     * consistency-check use exists for. [STRICT_DELTA_E_THRESHOLD]'s increase was motivated
     * specifically by Phase 3 candidate SEEDING being too tight for real footage; this constant's
     * wall-leak-detection role in Phases 4/5 was already correctly tuned and validated against its
     * own dedicated fixtures, so it stays at its original 22.0 rather than being disturbed by a fix
     * aimed at a different, unrelated gate. */
    const val LOOSE_DELTA_E_THRESHOLD = 22.0

    /** Default hue tolerance (degrees) for chromatic colors with roomy neighbors on the hue wheel
     * (GREEN/BLUE/PURPLE — see the gap analysis in [RouteColorProfiles]'s doc comment). */
    const val DEFAULT_HUE_TOLERANCE_DEGREES = 15f

    /** Tighter hue tolerance (degrees) for colors sitting close to a real neighbor on the hue
     * wheel (RED/ORANGE/YELLOW/PINK) — this is the actual fix for "red also matches orange": a
     * narrower window plus [STRICT_DELTA_E_THRESHOLD] rather than one generic wide tolerance for
     * every color. */
    const val TIGHT_HUE_TOLERANCE_DEGREES = 8f

    /** [TargetColorModel.hueToleranceDegrees] value used to mean "hue is not a meaningful signal
     * for this color" (BLACK/WHITE) — any value >= 180 covers the whole hue circle, i.e. hue
     * never gates the match; see [TargetColorModel.isAchromatic]. */
    const val ACHROMATIC_HUE_TOLERANCE_DEGREES = 180f

    /** Below this raw HSV saturation, hue readings are unreliable (near-gray pixels) — mirrors the
     * `hsv[1] < 0.2f` achromatic check this project's original full-frame hue-isolation shader
     * used (since replaced by real per-object detection), used the same way here to decide when a
     * [RouteColor] gets achromatic treatment. */
    const val ACHROMATIC_SATURATION_CEILING = 0.2f

    /** Minimum HSV saturation for a chromatic color's own match window — guards against
     * near-gray/washed-out pixels (glare, distant/blurry holds) being accepted just because their
     * hue happens to line up. */
    const val MIN_CHROMATIC_SATURATION = 0.25f

    const val MAX_SATURATION = 1.0f

    /** L* tolerance for chromatic colors — generous on purpose. A single physical hold's shaded
     * side vs. its highlight can easily span 30+ L* units while staying the same hue/chroma, and
     * the design brief is explicit that brightness should influence confidence, not gate the
     * match outright. */
    const val CHROMATIC_LUMINANCE_TOLERANCE = 35f

    /** Minimum L* for a pixel/object to count as [com.example.climb.data.RouteColor.WHITE]. */
    const val WHITE_MIN_LUMINANCE = 75f

    /** Maximum L* for a pixel/object to count as [com.example.climb.data.RouteColor.BLACK]. */
    const val BLACK_MAX_LUMINANCE = 30f

    /** [ColorCalibrator]'s outlier-rejection cutoff, in modified z-score units
     * (`0.6745 * |x - median| / MAD`), applied independently per Lab channel. ~3.5 is the
     * standard robust-statistics rule-of-thumb cutoff (Iglewicz & Hoaglin) for "obvious outlier"
     * — chalk marks, shadows, and highlights sampled inside an ROI should mostly fall inside this,
     * genuine background/wall pixels accidentally included in the tap ROI should mostly fall
     * outside it. */
    const val CALIBRATION_OUTLIER_MODIFIED_Z_THRESHOLD = 3.5

    /** [HoldComponentDetector] minimum candidate-component area, as a fraction of total frame
     * pixels rather than an absolute pixel count — a hold-sized region should occupy roughly the
     * same *proportion* of a frame regardless of whether that frame is 720p or 4K, so an absolute
     * pixel cutoff would either reject real holds on a low-res frame or admit noise on a high-res
     * one. 0.0008 (0.08% of the frame) is small enough to admit a distant/small hold, large enough
     * to reject single-digit-pixel chalk/glare/compression-artifact specks. */
    const val MIN_NORMALIZED_HOLD_AREA = 0.0008

    // --- Phase 4: boundary refinement ---
    // Edge-aware region growing over each Phase-3 DetectedHold's mask, to recover chalk/shadow/
    // specular-highlight pixels that failed Phase 3's strict per-pixel color gate but are
    // legitimately part of the same physical hold, without crossing into the wall or a
    // neighboring, differently-colored hold. See HoldBoundaryRefiner.

    /** Sobel L* gradient magnitude (in ΔL*-per-pixel-step units, over the CIE L* channel — see
     * [SobelEdgeDetector]) above which a pixel is treated as sitting on a real luminance boundary
     * (the wall/hold edge, or the seam to a differently-colored neighboring hold) and is never
     * grown into, regardless of how color-plausible it looks. Unlike [STRICT_DELTA_E_THRESHOLD]
     * (grounded in published CIEDE2000 JND literature), there is no equivalent external literature
     * constant for a Sobel-on-L* magnitude threshold — this value (12.0) was chosen empirically
     * against this module's own synthetic hard-edge fixtures (see SobelEdgeDetectorTest): a flat
     * region and its own JPEG-free synthetic noise sit near 0, while a genuine step edge between
     * two fixture colors typically produces a Sobel magnitude in the tens-to-hundreds range at the
     * seam itself, so 12.0 sits well above sensor/quantization-scale noise while well below a real
     * seam. Needs empirical re-tuning against real gym footage once available — flagged honestly,
     * not presented as a derived constant. */
    const val EDGE_GRADIENT_MAGNITUDE_THRESHOLD = 12.0

    /** Fraction of `min(bbox.width, bbox.height)` used to size a hold's own bounded growth radius
     * (see [MIN_GROWTH_RADIUS_PX]/[MAX_GROWTH_RADIUS_PX] for the absolute floor/ceiling) — growth
     * budget scales with the hold's own on-screen size (a distant/small hold gets a small growth
     * budget; a large close-up hold gets more) rather than a fixed pixel count that would either
     * be pointlessly tiny for a large hold or dangerously large for a small one.
     *
     * Originally 0.15, reduced to 0.05 after Phase 8's benchmarking surfaced a real cross-phase
     * bug: at 0.15, the grown "halo" ring into a wall diluted [HoldColorValidator]'s whole-object
     * consistency ratio to ~0.61-0.66 for any isolated hold under ~150x150px against a plain gray
     * wall — below the 0.75 floor, so `detect()` silently rejected the hold entirely (no highlight
     * at all). The dilution ratio for a roughly-square hold of extent `s` growing by radius `r` is
     * geometrically `~= 1 / (1 + 4*(r/s) + 4*(r/s)^2)` (confirmed empirically against this exact
     * formula: predicted vs. measured matched within ~1% across every tested size); solving for a
     * comfortable margin above the 0.75 floor gives a fraction around 0.04-0.05, not 0.15's ~0.077
     * breakeven-adjacent value. Re-measured with 0.05: isolated 30x30/40x40/60x60/100x100/180x180
     * holds against plain gray now land at 0.79-0.83 consistency, all clearing the floor with real
     * margin (see `RouteColorDetectorRegressionTest`). Still bounded by [MIN_GROWTH_RADIUS_PX]'s
     * fixed 2px floor for smaller holds (~20px and under), which this fraction change alone cannot
     * help — see that constant's own doc comment for why that floor isn't lowered further. */
    const val GROWTH_RADIUS_FRACTION_OF_HOLD_EXTENT = 0.05

    /** Absolute floor (pixels) on a hold's growth radius, so a tiny/distant hold (where
     * [GROWTH_RADIUS_FRACTION_OF_HOLD_EXTENT] alone would round to 0 or 1) still gets some growth
     * room to recover boundary pixels — and the exact minimum needed to bridge
     * `RouteColorDetectorRegressionTest`'s 4px-tall cutting-band fixture (each fragment must grow
     * 2px to close the gap). Measured (Phase 8 benchmarking, re-verified after the
     * [GROWTH_RADIUS_FRACTION_OF_HOLD_EXTENT] fix below): isolated holds of ~20px extent and under
     * still fail Phase 5's consistency floor even after that fix, because at that size this fixed
     * floor — not the fraction — dominates the growth radius, and a fixed 2px ring is inherently a
     * large fraction of a ~20px hold's own edge. Lowering this floor further would fix that but
     * break the cutting-band bridge (a 1px floor can only close a 2px gap, not this fixture's 4px
     * one) — a real, accepted tradeoff, not something to chase further without also reconsidering
     * the whole approach for very small holds. */
    const val MIN_GROWTH_RADIUS_PX = 2

    /** Absolute ceiling (pixels) on a hold's growth radius, regardless of hold size — bounds
     * worst-case per-hold growth cost, and is the real mechanism preventing bounded growth from
     * bridging genuine inter-hold gaps (see [HoldBoundaryRefiner]'s merge-step doc comment): two
     * distinct holds placed farther apart than this can never touch after growth, no matter how
     * large either one is on screen. */
    const val MAX_GROWTH_RADIUS_PX = 12

    // --- Phase 5: object validation ---
    // Whole-object color validation over Phase 4's refined holds: does a hold's own pixel
    // population actually look like one coherent, correctly-colored object, or did bounded
    // boundary refinement leak into a similarly-lit, low-saturation wall (Phase 4's own
    // documented, accepted "halo" limitation)? See HoldColorValidator/HoldConfidenceEvaluator.

    /** Minimum fraction of a hold's own pixels that must be within [LOOSE_DELTA_E_THRESHOLD] of
     * its own median Lab color. Below this, treat the hold as dominated by wall-bleed rather than
     * a single coherent surface, and reject it outright. Chosen empirically against this module's
     * own wall-adjacent fixture (see HoldColorValidatorTest) — needs real-footage re-tuning, same
     * honesty standard as every other threshold in this file. */
    const val MIN_COLOR_CONSISTENCY_RATIO = 0.75

    /** Same floor as [MIN_COLOR_CONSISTENCY_RATIO], applied against the generic
     * [TargetColorModel.labCenter] instead of a hold's own median — catches the case where enough
     * wall pixels got absorbed to drag the hold's own median away from the true target color,
     * which would otherwise make [MIN_COLOR_CONSISTENCY_RATIO] alone look artificially healthy
     * again (see [HoldColorValidator]'s class doc on why these two ratios can diverge). */
    const val MIN_TARGET_CONSISTENCY_RATIO = 0.75

    /** Ceiling on final (post-[HoldBoundaryRefiner]) area versus the pre-refinement Phase-3
     * candidate area(s) that fed into a hold. Loosely derived from [HoldBoundaryRefiner]'s own
     * bounded-growth geometry: at [GROWTH_RADIUS_FRACTION_OF_HOLD_EXTENT]'s current 0.05 fraction,
     * a square hold growing outward on all open sides has a geometric ceiling area ratio around
     * `(1 + 2*0.05)^2 ~= 1.21` (was `~= 1.69` at the fraction's original 0.15 value); 2.5 gives real
     * headroom above ordinary expected growth (actual 4-connected ring growth is diamond-cornered,
     * not a perfect square dilation, so it undershoots this geometric bound in practice) while still
     * catching a hold whose area ballooned far beyond what bounded per-hold growth alone should
     * produce (e.g. a cascading multi-candidate merge, each contributing its own halo). Left at 2.5
     * rather than tightened alongside the fraction change — measured `growthAreaRatio` values after
     * that fix stay in the 1.2-1.55 range even for the small holds it was meant to help, so 2.5
     * still comfortably reserves this as a distinct signal from the consistency-ratio floor above,
     * not a redundant one. Needs real-footage tuning like every other threshold in this file. */
    const val MAX_GROWTH_AREA_RATIO = 2.5
}
