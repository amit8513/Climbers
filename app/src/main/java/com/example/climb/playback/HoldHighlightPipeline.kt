package com.example.climb.playback

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.media.MediaMetadataRetriever
import androidx.media3.common.util.UnstableApi
import com.example.climb.colordetection.ColorSpace
import com.example.climb.colordetection.HoldMaskRenderer
import com.example.climb.colordetection.PixelBuffer
import com.example.climb.colordetection.RgbColor
import com.example.climb.colordetection.RouteColorDetector
import com.example.climb.colordetection.RouteColorProfiles
import com.example.climb.colordetection.TargetColorModel
import com.example.climb.data.RouteColor

/**
 * Phase 6's shared "detect once on a reference frame, build a mask" pipeline — used identically by
 * the live `ExoPlayer` preview ([com.example.climb.ui.detail.DetailScreen]) and the baked file
 * export ([exportWithHoldHighlight]), so what you see while tuning is exactly what gets saved.
 * Both extraction and detection are real, possibly-non-trivial CPU work (full
 * [RouteColorDetector.detect] pass over a real frame) — callers must run this off the calling
 * thread (e.g. `withContext(Dispatchers.Default)`), it does not do so itself.
 */
@UnstableApi
object HoldHighlightPipeline {

    /** @param holdCount lets a caller distinguish "detection ran and found nothing" from "not run
     * yet" without re-deriving it from [maskBitmap]'s own pixel content. */
    data class Result(val maskBitmap: Bitmap, val holdCount: Int)

    /** Extracts a single reference frame from [videoPath] — this project's "review after
     * recording" delivery mode assumes a fixed/static camera, so one frame's detection result is
     * reused for the whole clip (see [DetectedHoldHighlightEffect]'s own doc comment). */
    fun extractReferenceFrame(videoPath: String): Bitmap {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoPath)
            retriever.getFrameAtTime(0L)
                ?: error("Could not decode a reference frame from $videoPath")
        } finally {
            retriever.release()
        }
    }

    /** Runs real detection on [referenceFrame] and builds the mask [DetectedHoldHighlightEffect]
     * needs. [holdCount] is 0 (an empty, all-zero mask) when detection finds nothing — callers
     * should fall back to displaying the unmodified video rather than treating this as an error. */
    fun buildMask(
        referenceFrame: Bitmap,
        routeColor: RouteColor,
        hueOffsetDegrees: Float,
        hueToleranceDegrees: Float,
    ): Result = buildMask(referenceFrame, targetModelFor(routeColor, hueOffsetDegrees, hueToleranceDegrees))

    /**
     * Same detect+mask-building steps as the [RouteColor]-based [buildMask] overload above, but for
     * a caller that already has a real [TargetColorModel] — e.g. a tap-to-calibrate flow
     * ([com.example.climb.colordetection.ColorCalibrator], [com.example.climb.colordetection.RoiSampler])
     * that calibrated one against the hold's own actual captured color instead of
     * [RouteColorProfiles]'s generic per-color default. Reusing this single implementation means the
     * calibrated flow renders identically to the generic one, just with a different color model.
     */
    fun buildMask(referenceFrame: Bitmap, targetModel: TargetColorModel): Result {
        val buffer = PixelBuffer.fromBitmap(referenceFrame)
        val holds = RouteColorDetector.detect(buffer, targetModel)
        val field = HoldMaskRenderer.renderMaskField(referenceFrame.width, referenceFrame.height, holds)
        val maskBitmap = HoldMaskRenderer.toAlphaBitmap(field, referenceFrame.width, referenceFrame.height)
        return Result(maskBitmap, holds.size)
    }

    /**
     * Builds a [TargetColorModel] for [routeColor], applying `DetailScreen`'s tuning sliders
     * (formerly the old, now-removed hue-isolation shader's hue offset/tolerance knobs, see
     * [DetectedHoldHighlightEffect]'s companion constants for their bounds) as an override on top
     * of [RouteColorProfiles]'s predefined default. This is a coarse but honest mapping given these
     * sliders were originally designed for a single-target-hue shader test, not this model's Lab-
     * space fields: [hueOffsetDegrees] rotates [TargetColorModel.hsvCenter]'s hue (recomputing the
     * paired [TargetColorModel.labCenter] from the rotated hue at the same saturation/value so both
     * stay consistent), and [hueToleranceDegrees] directly replaces
     * [TargetColorModel.hueToleranceDegrees]. Skipped entirely for achromatic colors
     * (BLACK/WHITE) — hue has no meaning there, matching [TargetColorModel.isAchromatic]'s existing
     * contract and the old shader's own achromatic special case.
     *
     * A real tap-to-calibrate flow (this project's
     * [com.example.climb.colordetection.ColorCalibrator], already built in Phase 2) is now wired up
     * via the [TargetColorModel]-based [buildMask] overload above and
     * [com.example.climb.ui.detail.DetailScreen]'s "Calibrate on this hold" flow — this function
     * only ever builds the GENERIC per-color profile path.
     */
    fun targetModelFor(routeColor: RouteColor, hueOffsetDegrees: Float, hueToleranceDegrees: Float): TargetColorModel {
        val default = RouteColorProfiles.defaultFor(routeColor)
        if (default.isAchromatic) return default

        val clampedTolerance = hueToleranceDegrees.coerceIn(
            DetectedHoldHighlightEffect.MIN_HUE_TOLERANCE_DEGREES,
            DetectedHoldHighlightEffect.MAX_HUE_TOLERANCE_DEGREES,
        )
        val clampedOffset = hueOffsetDegrees.coerceIn(
            DetectedHoldHighlightEffect.MIN_HUE_OFFSET_DEGREES,
            DetectedHoldHighlightEffect.MAX_HUE_OFFSET_DEGREES,
        )

        val rotatedHueDeg = ((default.hsvCenter.h + clampedOffset) % 360f + 360f) % 360f
        val rotatedHsv = default.hsvCenter.copy(h = rotatedHueDeg)
        // android.graphics.Color is fine here — this file already depends on the Android
        // framework (MediaMetadataRetriever/Bitmap); only the colordetection package itself stays
        // framework-free.
        val colorInt = AndroidColor.HSVToColor(floatArrayOf(rotatedHsv.h, rotatedHsv.s, rotatedHsv.v))
        val rotatedRgb = RgbColor(
            r = (colorInt shr 16) and 0xFF,
            g = (colorInt shr 8) and 0xFF,
            b = colorInt and 0xFF,
        )
        val rotatedLab = ColorSpace.rgbToLab(rotatedRgb)

        return default.copy(
            hsvCenter = rotatedHsv,
            labCenter = rotatedLab,
            hueToleranceDegrees = clampedTolerance,
        )
    }
}
