package com.example.climb.validation

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File

/** Copies a picked gallery `content://` image/video into a local file this package's other
 * classes can address by plain path, and reads a decoded image's real pixel dimensions — the
 * only two pieces of Android-platform glue the manual-validation flow needs. */
object ValidationMediaImport {

    fun importFile(context: Context, uri: Uri, directory: File, fileName: String): File? {
        if (!directory.exists()) directory.mkdirs()
        val destination = File(directory, fileName)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            destination
        } catch (e: Exception) {
            null
        }
    }

    fun readImageDimensions(path: String): ImageDimensions? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        return ImageDimensions(options.outWidth, options.outHeight)
    }

    /** A cheap container-metadata read of a video's own header - NOT pose extraction, no
     * MediaPipe/ML inference involved. Used by Phase 4C's [PoseArtifactCache] callers that need a
     * video's dimensions before deciding whether a cache hit is even geometrically compatible,
     * without paying for a full MediaPipe pass just to find out. Returns `null` on any failure
     * (missing/corrupt file, unreadable metadata) rather than throwing. */
    fun readVideoDimensions(path: String): ImageDimensions? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            if (width == null || height == null || width <= 0 || height <= 0) {
                null
            } else {
                ImageDimensions(width, height)
            }
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}
