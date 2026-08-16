package com.example.climb.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.example.climb.colordetection.TargetColorModel
import com.example.climb.data.RouteColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Bakes [ColorIsolationEffect] into an actual output file rather than only applying it live at
 * playback time — this is what makes "save the edited video" mean anything by default, since
 * [ColorIsolationEffect] on its own only ever affects a local `ExoPlayer`'s GL pipeline. This is
 * the original, always-working hue-isolation export path, restored as the default after the
 * detection-pipeline-only [exportWithHoldHighlight] below turned out to reject too many real holds
 * (see that function's own doc comment) — [exportWithHoldHighlight] remains available as the
 * opt-in "bonus" detection path, chosen explicitly by the caller, not assumed.
 */
@UnstableApi
suspend fun exportWithColorIsolation(
    context: Context,
    inputPath: String,
    outputPath: String,
    routeColor: RouteColor,
    hueOffsetDegrees: Float,
    hueToleranceDegrees: Float,
): Unit = suspendCancellableCoroutine { continuation ->
    val outputFile = File(outputPath)
    outputFile.parentFile?.mkdirs()
    if (outputFile.exists()) outputFile.delete()

    val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(File(inputPath))))
        .setEffects(
            Effects(
                emptyList(),
                listOf(ColorIsolationEffect(routeColor, hueToleranceDegrees, hueOffsetDegrees)),
            ),
        )
        .build()

    val transformer = Transformer.Builder(context)
        .addListener(
            object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    if (continuation.isActive) continuation.resumeWithException(exportException)
                }
            },
        )
        .build()

    transformer.start(editedMediaItem, outputPath)

    continuation.invokeOnCancellation { transformer.cancel() }
}

/**
 * Bakes [DetectedHoldHighlightEffect] into an actual output file — the opt-in "bonus" detection
 * path (see [HoldHighlightPipeline]), used only when the caller has already run detection (e.g. via
 * a "Detect holds" button) and wants to save that specific result, not the default hue-isolation
 * effect. Requires a real detection pass over a reference frame first; a caller with zero detected
 * holds should not call this — fall back to [exportWithColorIsolation] instead.
 *
 * Delegates to the [TargetColorModel]-based overload below using the GENERIC per-color profile
 * (plus slider overrides) — a caller that reached its on-screen result via tap-to-calibrate
 * ([com.example.climb.colordetection.ColorCalibrator]) must call the [TargetColorModel] overload
 * directly with that same calibrated model, not this one, or the exported file would silently
 * re-detect with the generic profile instead of matching what was actually previewed and saved.
 */
@UnstableApi
suspend fun exportWithHoldHighlight(
    context: Context,
    inputPath: String,
    outputPath: String,
    routeColor: RouteColor,
    hueOffsetDegrees: Float,
    hueToleranceDegrees: Float,
) = exportWithHoldHighlight(
    context = context,
    inputPath = inputPath,
    outputPath = outputPath,
    targetModel = HoldHighlightPipeline.targetModelFor(routeColor, hueOffsetDegrees, hueToleranceDegrees),
)

/**
 * Same bake-to-file steps as the [RouteColor]-based [exportWithHoldHighlight] overload above, but
 * for a caller that already has a real [TargetColorModel] — e.g. a tap-to-calibrate result — so the
 * exported file matches exactly what was previewed, not a re-detection against the generic profile.
 */
@UnstableApi
suspend fun exportWithHoldHighlight(
    context: Context,
    inputPath: String,
    outputPath: String,
    targetModel: TargetColorModel,
) {
    // Reference-frame extraction + full detection is real CPU work — done off the calling thread,
    // then the Transformer setup below (fast, callback-driven) happens back on the caller's own
    // dispatcher.
    val maskResult = withContext(Dispatchers.Default) {
        val referenceFrame = HoldHighlightPipeline.extractReferenceFrame(inputPath)
        HoldHighlightPipeline.buildMask(referenceFrame, targetModel)
    }

    suspendCancellableCoroutine { continuation ->
        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(File(inputPath))))
            .setEffects(
                Effects(
                    emptyList(),
                    listOf(DetectedHoldHighlightEffect(maskResult.maskBitmap)),
                ),
            )
            .build()

        val transformer = Transformer.Builder(context)
            .addListener(
                object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        if (continuation.isActive) continuation.resumeWithException(exportException)
                    }
                },
            )
            .build()

        transformer.start(editedMediaItem, outputPath)

        continuation.invokeOnCancellation { transformer.cancel() }
    }
}
