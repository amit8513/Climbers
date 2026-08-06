package com.example.climb.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException

/**
 * Copies [sourceFile] into the device's public Movies collection (visible in Gallery/Photos apps,
 * shareable to other apps) rather than the app's own private storage. Scoped-storage `MediaStore`
 * insertion needs API 29+ — this app's minSdk is 24, so older devices get a clear error instead
 * of a silent failure or a legacy permission-request flow this app doesn't otherwise need.
 */
fun saveVideoToGallery(context: Context, sourceFile: File, displayName: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        throw IOException("Saving to your device's gallery needs Android 10 or newer")
    }

    val resolver = context.contentResolver
    val contentValues = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Climb")
        put(MediaStore.Video.Media.IS_PENDING, 1)
    }

    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
        ?: throw IOException("Couldn't create a gallery entry for this video")

    try {
        resolver.openOutputStream(uri)?.use { output ->
            sourceFile.inputStream().use { input -> input.copyTo(output) }
        } ?: throw IOException("Couldn't open the gallery entry for writing")
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        throw e
    }

    contentValues.clear()
    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
    resolver.update(uri, contentValues, null, null)
}
