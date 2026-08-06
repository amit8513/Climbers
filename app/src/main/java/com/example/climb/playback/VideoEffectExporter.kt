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
import com.example.climb.data.RouteColor
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Bakes [ColorIsolationEffect] into an actual output file rather than only applying it live at
 * playback time — this is what makes "save the edited video" mean anything, since [ColorIsolationEffect]
 * on its own only ever affects a local `ExoPlayer`'s GL pipeline. Once baked, the tuning is in the
 * pixels themselves, so it survives being uploaded/synced and played back by someone else who has
 * no idea this app's color-isolation effect even exists.
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
