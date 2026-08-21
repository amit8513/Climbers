package com.example.climb.validation

import com.example.climb.analysis.contact.GapState
import com.example.climb.analysis.contact.HoldContactDetector
import com.example.climb.analysis.contact.HoldContactTimeline
import com.example.climb.analysis.contact.HoldShape
import com.example.climb.analysis.contact.Limb
import com.example.climb.analysis.metrics.HoldContactConfig
import com.example.climb.colordetection.AlignmentCheckResult
import com.example.climb.colordetection.CaptureToReferenceTransform
import com.example.climb.colordetection.Point2D
import com.example.climb.pose.PoseAnalysisConfiguration
import com.example.climb.pose.PoseAnalysisProgress
import com.example.climb.pose.PoseAnalysisResult
import com.example.climb.pose.PoseEstimator
import com.example.climb.pose.VideoSource

/** 15fps for this fixed-camera validation path — a distinct [PoseAnalysisConfiguration] instance
 * from the personal pipeline's own default (10fps, [PoseAnalysisConfiguration]'s own default
 * value, never constructed or touched by this file). */
const val MANUAL_VALIDATION_TARGET_FPS: Int = 15

/** One video's worth of per-pose-frame bookkeeping the report needs but the accumulated
 * [HoldContactTimeline] alone can't reconstruct — a short tracking gap never emits an event at
 * all, so "how many short gaps happened" has to be sampled live, frame by frame, while
 * [ManualValidationPipeline] is already iterating. Read from [HoldContactDetector.stateOf] after
 * each `processFrame` call — never re-derives any decision the detector already made. */
data class ManualValidationFrameDiagnostics(
    val timestampMs: Long,
    val isReliable: Boolean,
    val gapStatesByLimb: Map<Limb, GapState>,
    /** `null` for a limb not currently established. Read straight from
     * `HoldContactDetector.stateOf` — never re-derived. */
    val establishedHoldByLimb: Map<Limb, Int?> = emptyMap(),
    val candidateHoldByLimb: Map<Limb, Int?> = emptyMap(),
    /** The limb's resolved `WallReferenceSpace` position as of this frame — carries over
     * unchanged through a tracking gap (the last genuinely-seen position), `null` only if the
     * limb has never been resolved at all yet. For the debug overlay's proxy-position display. */
    val proxyPositionByLimb: Map<Limb, Point2D?> = emptyMap(),
    val establishedConfidenceByLimb: Map<Limb, Float> = emptyMap(),
)

sealed interface ManualValidationOutcome {
    /** Geometry mismatch, or the pose estimator itself failed (bad file, unsupported format,
     * etc). Processing never started, or was abandoned before touching the contact detector. */
    data class Rejected(val reason: String) : ManualValidationOutcome

    data class Processed(
        val frameDiagnostics: List<ManualValidationFrameDiagnostics>,
        val videoDurationMs: Long,
        val timeline: HoldContactTimeline,
    ) : ManualValidationOutcome
}

/**
 * The development-only pipeline: uploaded/local MP4 → existing MediaPipe pose extraction (run
 * exactly once) → [PoseFrame.toContactPoseFrame] → [HoldContactDetector] → [HoldContactTimeline].
 * Never touches [com.example.climb.clubs.ClubRepository], never constructs a
 * `WallCaptureSession`, never sets `AttemptSource.WALL_CAMERA`, never applies anything but the
 * identity transform (see [ManualValidationGeometryGate]).
 */
object ManualValidationPipeline {

    suspend fun run(
        session: ManualValidationSession,
        poseEstimator: PoseEstimator,
        referenceImageDimensions: ImageDimensions,
        expectedGeometryProfileVersion: Int,
        holdContactConfig: HoldContactConfig = HoldContactConfig(),
        onProgress: (PoseAnalysisProgress) -> Unit = {},
    ): ManualValidationOutcome {
        // Exactly one call, exactly one video - MediaPipe never runs a second time for this
        // session, and no other code path in this file (or anywhere in :shared-domain) performs
        // pose extraction of its own.
        val poseResult = poseEstimator.analyzeVideo(
            source = VideoSource.LocalFile(session.videoPath),
            configuration = PoseAnalysisConfiguration(targetFps = MANUAL_VALIDATION_TARGET_FPS),
            onProgress = onProgress,
        )
        val success = poseResult as? PoseAnalysisResult.Success
            ?: return ManualValidationOutcome.Rejected((poseResult as PoseAnalysisResult.Failure).reason)

        val alignment = ManualValidationGeometryGate.check(
            session = session,
            referenceImageDimensions = referenceImageDimensions,
            videoDimensions = ImageDimensions(success.videoWidth, success.videoHeight),
            expectedGeometryProfileVersion = expectedGeometryProfileVersion,
        )
        val validIdentity = alignment as? AlignmentCheckResult.ValidIdentity
            ?: return ManualValidationOutcome.Rejected(
                (alignment as? AlignmentCheckResult.CalibrationInvalid)?.reason ?: "geometry check failed",
            )

        val holds = session.annotatedHolds.map { HoldShape(it.holdId, it.contourNormalized) }
        val detector = HoldContactDetector(holds, holdContactConfig)
        val identity = CaptureToReferenceTransform.identity(validIdentity.wallCalibrationId)

        val diagnostics = success.frames.map { frame ->
            detector.processFrame(frame.toContactPoseFrame(), identity)
            val statesByLimb = Limb.entries.associateWith { detector.stateOf(it) }
            ManualValidationFrameDiagnostics(
                timestampMs = frame.timestampMs,
                isReliable = frame.isReliable,
                gapStatesByLimb = statesByLimb.mapValues { it.value.gapState },
                establishedHoldByLimb = statesByLimb.mapValues { it.value.establishedHoldId },
                candidateHoldByLimb = statesByLimb.mapValues { it.value.candidateHoldId },
                proxyPositionByLimb = statesByLimb.mapValues { it.value.lastSeenReferencePoint },
                establishedConfidenceByLimb = statesByLimb.mapValues { it.value.establishedConfidence },
            )
        }

        return ManualValidationOutcome.Processed(
            frameDiagnostics = diagnostics,
            videoDurationMs = success.videoDurationMs,
            timeline = detector.timeline,
        )
    }
}
