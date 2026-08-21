package com.example.climb.analysis.contact

import com.example.climb.colordetection.Point2D

/** A limb's resolved contact-proxy point for one frame, still in capture-frame (pre-transform)
 * coordinates — see `HoldContactDetector.processFrame`. [usedFallback] records which landmark set
 * produced [point], for [EvidenceQuality] classification downstream. */
data class ResolvedLimbProxy(val point: Point2D, val confidence: Float, val usedFallback: Boolean)

/**
 * Resolves one [Limb]'s contact-proxy point from a [ContactPoseFrame], per a fixed, documented
 * fallback chain — never invents an ad-hoc "just use the wrist" rule per call site:
 *
 * - **Hands**: primary = the mean of whichever of INDEX/PINKY/THUMB are present this frame (the
 *   fingertip cluster — the best proxy for an actual grip). If none of those three are present,
 *   fall back to WRIST alone (the most stable/central point when fingers aren't trackable at all).
 * - **Feet**: primary = the mean of whichever of ANKLE/HEEL are present (the "foot body" — the
 *   best proxy for standing weight on a foothold). If neither is present, fall back to FOOT_INDEX
 *   alone (the toe/ball point — the best single indicator for a toe-hooked/edging placement when
 *   the rest of the foot isn't trackable).
 *
 * A landmark is "present" if it appears in the frame at all with positive confidence — the finer
 * question of whether its confidence is *good enough* for contact-quality purposes is
 * [HoldContactConfig.contactMinFrameConfidence]'s job downstream, not this resolver's.
 */
object LimbProxyResolver {

    private data class LimbLandmarkSet(val primary: List<ContactLandmarkType>, val fallback: ContactLandmarkType)

    private val LANDMARK_SETS: Map<Limb, LimbLandmarkSet> = mapOf(
        Limb.LEFT_HAND to LimbLandmarkSet(
            primary = listOf(ContactLandmarkType.LEFT_INDEX, ContactLandmarkType.LEFT_PINKY, ContactLandmarkType.LEFT_THUMB),
            fallback = ContactLandmarkType.LEFT_WRIST,
        ),
        Limb.RIGHT_HAND to LimbLandmarkSet(
            primary = listOf(ContactLandmarkType.RIGHT_INDEX, ContactLandmarkType.RIGHT_PINKY, ContactLandmarkType.RIGHT_THUMB),
            fallback = ContactLandmarkType.RIGHT_WRIST,
        ),
        Limb.LEFT_FOOT to LimbLandmarkSet(
            primary = listOf(ContactLandmarkType.LEFT_ANKLE, ContactLandmarkType.LEFT_HEEL),
            fallback = ContactLandmarkType.LEFT_FOOT_INDEX,
        ),
        Limb.RIGHT_FOOT to LimbLandmarkSet(
            primary = listOf(ContactLandmarkType.RIGHT_ANKLE, ContactLandmarkType.RIGHT_HEEL),
            fallback = ContactLandmarkType.RIGHT_FOOT_INDEX,
        ),
    )

    /** `null` means this limb is entirely untracked this frame (a tracking gap) — neither the
     * primary set nor the fallback landmark was present at all. */
    fun resolve(limb: Limb, frame: ContactPoseFrame): ResolvedLimbProxy? {
        val landmarkSet = LANDMARK_SETS.getValue(limb)
        val presentPrimary = landmarkSet.primary.mapNotNull { frame.landmark(it) }.filter { it.confidence > 0f }
        if (presentPrimary.isNotEmpty()) {
            val avgX = presentPrimary.map { it.position.x }.average().toFloat()
            val avgY = presentPrimary.map { it.position.y }.average().toFloat()
            val avgConfidence = presentPrimary.map { it.confidence }.average().toFloat()
            return ResolvedLimbProxy(Point2D(avgX, avgY), avgConfidence, usedFallback = false)
        }

        val fallbackLandmark = frame.landmark(landmarkSet.fallback)?.takeIf { it.confidence > 0f }
        if (fallbackLandmark != null) {
            return ResolvedLimbProxy(fallbackLandmark.position, fallbackLandmark.confidence, usedFallback = true)
        }

        return null
    }
}
