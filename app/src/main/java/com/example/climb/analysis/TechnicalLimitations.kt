package com.example.climb.analysis

import com.example.climb.analysis.metrics.ClimbMetrics
import com.example.climb.analysis.scoring.CategoryScore

/** [evidence] is null for the evergreen, always-true limitations of this analysis technique
 * itself (single monocular camera, no hold/route data, ...); non-null for a warning specific to
 * this attempt's own tracking quality or duration. */
data class TechnicalLimitation(val text: String, val evidence: String? = null)

/**
 * The evergreen limitations of pose-only monocular video analysis — true of every analysis this
 * pipeline produces, regardless of how well this particular video tracked. Stated once, directly,
 * rather than only implied piecemeal through each category's own [CategoryScore.unavailableFactors]
 * footnote, so there's one place a reader can see the full, honest boundary of what this report
 * can and can't know.
 */
private val PIPELINE_LIMITATIONS = listOf(
    "No hold positions, hold types, grip style, or friction are known to this analysis — it never claims a specific hold was over-gripped, under-gripped, or caused a slip.",
    "A single 2D camera with no depth sensing means true wall-contact distance and 3D body position can't be measured — balance and positioning scores are a hip-center stability proxy, not a true 3D measurement.",
    "No route or beta context exists — whether a sequence of moves was objectively optimal, or a specific move was the one intended, isn't knowable from pose alone.",
    "If more than one person is in frame, tracking follows whichever body the pose model locks onto — there's no guarantee it stays on the same climber throughout.",
    "Cardiovascular or muscular fatigue, and actual force or strength output, aren't measurable from pose alone — Endurance and Power reflect movement and pacing patterns, not physiology.",
)

/**
 * Session-specific warnings (this attempt's own tracking quality/duration) come first, since
 * they're the ones worth actually reading before trusting this specific report; the evergreen
 * pipeline limitations follow as background context that applies to every analysis equally.
 */
fun buildTechnicalLimitations(metrics: ClimbMetrics, categoryScores: List<CategoryScore>): List<TechnicalLimitation> {
    val sessionSpecific = mutableListOf<TechnicalLimitation>()

    if (metrics.reliableFramePercentage < 70f) {
        sessionSpecific += TechnicalLimitation(
            text = "Pose tracking was reliable for only ${metrics.reliableFramePercentage.toInt()}% of this video — scores and events drawn from the unreliable stretches carry extra uncertainty.",
            evidence = "reliableFramePercentage=${metrics.reliableFramePercentage.toInt()}%",
        )
    }
    if (metrics.totalDurationMs < 15_000L) {
        sessionSpecific += TechnicalLimitation(
            text = "This attempt was under 15 seconds — too short for the pacing/endurance comparison between the first and second half to be reliable.",
            evidence = "totalDurationMs=${metrics.totalDurationMs}",
        )
    }
    val lowConfidenceCategories = categoryScores.filter { it.confidence < 0.4f }
    if (lowConfidenceCategories.isNotEmpty()) {
        val names = lowConfidenceCategories.joinToString(", ") { it.category.displayName }
        val verb = if (lowConfidenceCategories.size == 1) "is" else "are"
        val pronoun = if (lowConfidenceCategories.size == 1) "it" else "them"
        sessionSpecific += TechnicalLimitation(
            text = "$names $verb based on limited evidence for this specific attempt — treat $pronoun as a rough estimate rather than a precise score.",
            evidence = lowConfidenceCategories.joinToString(", ") { "${it.category.name}=${"%.2f".format(it.confidence)}" },
        )
    }

    return sessionSpecific + PIPELINE_LIMITATIONS.map { TechnicalLimitation(it) }
}
