package com.example.climb.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Bakes a `[startPositionMs, endPositionMs)` trim into an actual output file, using the same
 * `MediaItem.ClippingConfiguration` used for live preview (see [com.example.climb.ui.home.HomeVideoBackground]'s
 * `loadClip`) but run through a real [Transformer] export pass (mirroring
 * [exportWithColorIsolation]'s pattern) so the trim is actually baked into [outputPath] rather than
 * only affecting a player's live position range — the saved file genuinely only contains the
 * trimmed range, not the full original with a start/end marker that gets ignored later.
 *
 * No effects are applied here — this is a pure trim. A caller that also wants an effect baked in
 * (e.g. [exportWithColorIsolation]) should trim first and run that export against the trimmed
 * output, rather than trying to combine both into one pass.
 */
@UnstableApi
suspend fun exportTrimmedVideo(
    context: Context,
    inputPath: String,
    outputPath: String,
    startPositionMs: Long,
    endPositionMs: Long,
): Unit = suspendCancellableCoroutine { continuation ->
    val outputFile = File(outputPath)
    outputFile.parentFile?.mkdirs()
    if (outputFile.exists()) outputFile.delete()

    val clippedMediaItem = MediaItem.Builder()
        .setUri(Uri.fromFile(File(inputPath)))
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startPositionMs)
                .setEndPositionMs(endPositionMs)
                .build(),
        )
        .build()

    val editedMediaItem = EditedMediaItem.Builder(clippedMediaItem).build()

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
