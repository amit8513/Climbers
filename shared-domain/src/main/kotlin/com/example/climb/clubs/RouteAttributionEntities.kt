package com.example.climb.clubs

import com.example.climb.colordetection.NormalizedRect

/**
 * Shared domain models/enums for the gym-camera automatic-route-attribution work — usable by both
 * the member app (`:app`) and the future Camera Edge Device module, so neither duplicates these
 * contracts. Pure data + enums only: no route-registration UI, no NFC/camera implementation, no
 * attribution/result-verification algorithm, no official-aggregate writes, no Firestore rules —
 * those all stay app-side (or, later, Edge-Agent-side) and depend on these shared shapes rather
 * than redefining them.
 *
 * All ids here are auto-generated (via `:app`'s `ClubRepository.nextId()` transaction-counter
 * convention for the staff-driven, low-frequency entities below) — never derived from
 * color/grade/name/wall position. High-frequency, potentially-offline-created capture entities
 * live in `CaptureDomainEntities` instead, using client-generatable string ids (UUID/ULID), not
 * `nextId()` — see that file's own doc comment.
 *
 * [routeColorHex] is used instead of `:app`'s `RouteColor` enum specifically because this module
 * cannot depend on `:app` (that dependency only ever goes the other way) — `:app` code converts
 * between `RouteColor` and its `hex: Long` at the boundary when constructing/reading this entity.
 */

/** A physical, camera-monitored climbing wall at one venue/zone — the top-level concept a
 * WallCalibrationEntity/RouteVisionProfileEntity hang off of. One wall maps to at most one
 * CameraEdgeDevice and one NfcReaderDevice for this POC (see `CaptureDomainEntities`). */
data class WallEntity(
    val id: Long,
    val organizationId: Long,
    val venueId: Long,
    val zoneId: Long,
    val name: String,
    val createdAt: Long,
    val retiredAt: Long? = null,
)

/** Where a [WallCalibrationEntity]'s reference frame actually came from. [TEST_FIXTURE] exists
 * specifically for hardware-independent development/testing (Phase 2A's route-registration UI
 * flow, which has no real Edge Capture Agent to talk to yet) — see
 * [WallCalibrationActivationGuard], the one place that enforces a [TEST_FIXTURE] calibration can
 * never be treated as eligible for real activation, no matter how complete its other fields look. */
enum class ReferenceSource { EDGE_AGENT_CAPTURE, TEST_FIXTURE }

/** One captured "clean wall, no climber" reference frame plus the geometric calibration that lets
 * hold geometry (already normalized against it) and pose landmarks (transformed via a
 * `CaptureToReferenceTransform`, resolved in Phase 3) be compared in the same coordinate space.
 * Versioned (not mutated in place) so re-calibrating a wall after a camera bump doesn't
 * retroactively invalidate old profiles silently — see
 * [RouteVisionProfileEntity.needsReconfirmation].
 *
 * The authoritative wall reference cannot merely come from "the same physical camera" — its
 * FOV/crop/orientation must match the future attempt-video path exactly, which is what
 * [cameraGeometryProfileVersion] (see `com.example.climb.edge.CameraGeometryProfile`) exists to
 * pin down; [WallCalibrationActivationGuard] is the one place that checks it against whatever
 * profile version an attempt capture actually used. [referenceSource] and [hardwareValidated] are
 * the other two gates that guard — see [WallCalibrationActivationGuard]'s doc comment for why all
 * three are independent, never-bypassed checks rather than one combined flag. */
data class WallCalibrationEntity(
    val id: Long,
    val organizationId: Long,
    val wallId: Long,
    val referenceImageUrl: String,
    val referenceWidthPx: Int,
    val referenceHeightPx: Int,
    val wallRoiNormalized: NormalizedRect? = null,
    /** A cheap perceptual/luma fingerprint of the reference frame, used by the (Phase 3)
     * alignment check — see `AlignmentCheckResult`'s doc comment for why this is only a fast
     * pre-check/sanity gate, not a full coordinate-mapping solution. */
    val alignmentFingerprint: String,
    val calibratedBy: String,
    val createdAt: Long,
    val supersededAt: Long? = null,
    val configVersion: Int,
    val referenceSource: ReferenceSource,
    /** The `CameraGeometryProfile.version` the reference frame was actually captured under — see
     * this entity's own doc comment and [WallCalibrationActivationGuard]. */
    val cameraGeometryProfileVersion: Int,
    /** Never set true by anything in Phase 2A — real hardware validation (the Phase 1.5A/1.25
     * gates) is a precondition this codebase cannot fabricate its way past. Only a future phase
     * that actually confirms real capture hardware may set this. */
    val hardwareValidated: Boolean = false,
)

/** Which purpose one detected hold serves within a route, tagged at staff-confirmation time
 * (Phase 2) — used by StartHoldMatcher/result-verification in later phases. */
enum class HoldRole { START, BODY, FINISH }

/** Per-route, per-wall persistent vision data: the calibrated color model plus normalized hold
 * geometry (contours, each tagged with a [HoldRole]) and an optional corridor. Replaces the old
 * one-nullable-JSON-string-per-CLIMB pattern with a real per-ROUTE, per-WALL-CALIBRATION entity,
 * keyed by RouteVersion (not Route) since the same physical route slot can later hold a different
 * route. Hold ids inside [holdGeometryJson] are assigned once, at the moment staff confirms this
 * profile (a simple sequential index over the final, staff-corrected hold list) — never reordered
 * or regenerated afterward, so every later contact event/debug overlay references a stable
 * identity. */
data class RouteVisionProfileEntity(
    val id: Long,
    val organizationId: Long,
    val wallId: Long,
    val wallCalibrationId: Long,
    val routeId: Long,
    val routeVersionId: Long,
    /** See this file's doc comment for why this is a raw hex value rather than `:app`'s
     * `RouteColor` enum.
     *
     * Exact bit representation: a packed `0xAARRGGBB` 32-bit color value stored in a `Long` —
     * alpha in the top byte, then red, then green, then blue in the low byte — IDENTICAL to the
     * convention already used by `:app`'s `com.example.climb.data.RouteColor.hex: Long` values
     * (e.g. `RouteColor.RED = 0xFFE53935` decodes as alpha=0xFF, red=0xE5, green=0x39, blue=0x35).
     *
     * Alpha is expected to always be `0xFF` (fully opaque) for a route's display color — a route
     * color has no meaningful transparency — but this field does not itself enforce that at
     * construction time; it's a plain `Long`. Use [RouteColorHex] to extract channels and check
     * opacity. */
    val routeColorHex: Long,
    /** Serialized `TargetColorModel` (an `:app`-side type), calibrated per-route against this
     * wall's own reference frame, not a generic per-color default. Real serialization/population
     * is Phase 2 work; this is just the storage slot. */
    val calibratedColorModelJson: String,
    /** Each hold's polygon contour/centroid/bounding-box, normalized to this profile's
     * WallCalibrationEntity's own reference-frame dimensions, plus its stable [HoldRole]. Real
     * serialization is Phase 2 work; this is the storage slot for it. */
    val holdGeometryJson: String,
    val holdCount: Int,
    val corridorNormalized: NormalizedRect? = null,
    val visionProfileFormatVersion: Int,
    val staffConfirmed: Boolean = false,
    val createdAt: Long,
    val createdBy: String,
    val supersededAt: Long? = null,
    /** Set true whenever this wall gets a *newer* WallCalibrationEntity than the one this profile
     * was built against — never silently left "active" while pointing at stale geometry. Staff
     * must explicitly re-confirm (or re-register) before it can be used for attribution again
     * (Phase 2 concern; nothing sets or clears this flag yet). */
    val needsReconfirmation: Boolean = false,
)

/** Pure channel-extraction utilities for a [RouteVisionProfileEntity.routeColorHex]-shaped packed
 * `0xAARRGGBB` `Long` — see that field's doc comment for the exact bit layout this decodes. */
object RouteColorHex {
    fun alpha(argb: Long): Int = ((argb shr 24) and 0xFF).toInt()
    fun red(argb: Long): Int = ((argb shr 16) and 0xFF).toInt()
    fun green(argb: Long): Int = ((argb shr 8) and 0xFF).toInt()
    fun blue(argb: Long): Int = (argb and 0xFF).toInt()

    /** True when [alpha] is `0xFF` (fully opaque) — the expected, but not enforced, state for a
     * route's display color. See [RouteVisionProfileEntity.routeColorHex]'s doc comment. */
    fun isFullyOpaque(argb: Long): Boolean = alpha(argb) == 0xFF
}

/** How a route's start must be established for an attempt's start to count as observed — used by
 * StartHoldMatcher/result-verification in later phases. Null on `:app`'s `RouteVersionEntity`
 * until the route's vision profile actually defines it (Phase 2). */
enum class StartPolicy { SINGLE_HOLD_ANY_HAND, TWO_HOLDS_ONE_PER_HAND, TWO_HANDS_SAME_HOLD }

/** What counts as touching this route's finish hold for a send — used by result-verification in
 * later phases. Null on `:app`'s `RouteVersionEntity` until the route's vision profile defines it
 * (Phase 2). */
enum class FinishPolicy { ONE_HAND_ON_FINISH, TWO_HANDS_ON_FINISH, TOP_OUT_ZONE }

/** Where an attempt's video/route-link actually came from — the dimension the confirmed trust bug
 * (a manual video with a manual route pick indistinguishable from a verified capture) was missing
 * entirely. Kept strictly independent of [AttributionStatus]/[ResultAuthority] — never folded into
 * one enum.
 *
 * Exact semantics, one per value:
 * - [PHONE_CAMERA]: a fresh live recording made with the phone's camera.
 * - [IMPORTED_VIDEO]: a fresh video imported from the phone's gallery (not recorded live).
 * - [MANUAL_LOG]: an attempt entered manually WITHOUT any video at all — a bare log entry. This is
 *   NOT "re-analyzing an existing video"; that usage was wrong and must not recur.
 * - [LEGACY_UNKNOWN]: a video whose original recording provenance (phone camera vs. gallery
 *   import) cannot be determined — e.g. a climb logged before source tracking existed. This is
 *   the correct, honest fallback for "we don't know"; code must never mislabel an
 *   unknown-provenance video as [MANUAL_LOG].
 * - [WALL_CAMERA]: future gym-camera capture (Phase 2+) — not used by any code yet.
 *
 * Re-analyzing an existing video must preserve its ORIGINAL source ([PHONE_CAMERA]/
 * [IMPORTED_VIDEO]) if known, or [LEGACY_UNKNOWN] if the original provenance was never recorded —
 * never [MANUAL_LOG] for this case, since [MANUAL_LOG] means no video exists at all.
 *
 * A legacy `null` on an old attempt/climb row (from before this field existed at all) also means
 * "unverified/unknown provenance," in the same spirit as [LEGACY_UNKNOWN] but for the
 * null-vs-enum-value distinction — null and [LEGACY_UNKNOWN] are both valid, honest
 * representations of "we don't know" and are not collapsed into one required choice; callers must
 * treat them the same way (never trusted/club-eligible). Every *newly created* personal attempt
 * must explicitly set [PHONE_CAMERA], [IMPORTED_VIDEO], or [MANUAL_LOG] (see `:app`'s
 * `RecordScreen`/`ClimbDetailsInputScreen` wiring) — null is only ever a historical value now, not
 * something new code produces. */
enum class AttemptSource { PHONE_CAMERA, IMPORTED_VIDEO, MANUAL_LOG, LEGACY_UNKNOWN, WALL_CAMERA }

/** The one unified status model for route attribution — replaces any earlier ad-hoc split between
 * a "confidence" enum and a separately-implied verified/rejected set. Detailed reasons are a
 * separate field ([AttributionReasonCode]), never encoded into the status itself. */
enum class AttributionStatus { PENDING, VERIFIED, REVIEW_REQUIRED, UNRESOLVED, REJECTED, CALIBRATION_INVALID }

enum class AttributionReasonCode {
    START_NOT_OBSERVED, START_MISMATCH, MARGIN_TOO_SMALL, TRACKING_UNRELIABLE,
    CAMERA_MISALIGNED, NO_CANDIDATES, STAFF_OVERRIDE,
}

/** Whether a start hold was actually observed, and for which candidate — a hard precondition for
 * `AttributionStatus.VERIFIED` in the (Phase 4) attribution engine, never something that gets
 * renormalized away when other signals score well. */
enum class StartEvidenceStatus { START_NOT_OBSERVED, START_OBSERVED_MATCH, START_OBSERVED_MISMATCH }

enum class AttemptResult { SEND, FALL, ABANDONED, UNKNOWN }

/** Independent second gate alongside [AttributionStatus] — official Send/Fall/completion counters
 * (Phase 7) require BOTH `AttributionStatus.VERIFIED` AND `ResultVerificationStatus.VERIFIED`.
 * `REJECTED` at either level must never increment an official aggregate. */
enum class ResultVerificationStatus { PENDING, VERIFIED, REVIEW_REQUIRED, REJECTED }

enum class ResultAuthority { USER_REPORTED, AUTOMATICALLY_DETECTED, STAFF_CONFIRMED }

/** Skeleton persistence shape for one attempt's attribution + result-verification outcome. Real
 * population (the actual scoring/decision algorithm) is Phase 4/5 work — this only defines the
 * shape so it doesn't need to change again once that algorithm exists. High-frequency/
 * potentially-offline id — see `CaptureDomainEntities`'s doc comment on why this uses a
 * client-generatable string id rather than `nextId()`. Heavy per-sub-score/contact-trace debug
 * data lives in Storage, referenced by [debugArtifactStoragePath] — never inlined here as
 * unbounded JSON. */
data class RouteAttributionResultEntity(
    val id: String,
    val organizationId: Long,
    val wallId: Long,
    val wallCalibrationId: Long,
    val captureSessionId: String,
    val candidateRouteVersionIds: List<Long> = emptyList(),
    val winningRouteVersionId: Long? = null,
    val attributionStatus: AttributionStatus = AttributionStatus.PENDING,
    val attributionReasonCode: AttributionReasonCode? = null,
    val competitiveMarginScore: Double? = null,
    val attributionAlgorithmVersion: Int,
    val resultValue: AttemptResult? = null,
    val resultVerificationStatus: ResultVerificationStatus = ResultVerificationStatus.PENDING,
    val resultAuthority: ResultAuthority? = null,
    val resultConfidence: Float? = null,
    val debugArtifactStoragePath: String? = null,
    val createdAt: Long,
)
