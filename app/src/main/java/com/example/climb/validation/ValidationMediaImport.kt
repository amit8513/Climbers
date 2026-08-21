package com.example.climb.validation

import android.content.Context
import android.graphics.BitmapFactory
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
}
