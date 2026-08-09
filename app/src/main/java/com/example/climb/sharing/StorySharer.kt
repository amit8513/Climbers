package com.example.climb.sharing

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

private const val FILE_PROVIDER_SUFFIX = ".fileprovider"
private const val INSTAGRAM_PACKAGE = "com.instagram.android"
private const val FACEBOOK_PACKAGE = "com.facebook.katana"

/**
 * Shares a rendered video file to Instagram Stories or Facebook, using each platform's own
 * story-sharing intent where possible — no server-side posting, no OAuth, no Graph API. This just
 * hands the file to whichever app is already installed and signed in on the device (the caller
 * must own that file long enough for the receiving app to actually read it — see the callers in
 * DetailScreen, which don't delete the temp export immediately the way "save to device" does).
 */
object StorySharer {

    private fun contentUriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}$FILE_PROVIDER_SUFFIX", file)

    /**
     * Instagram's documented Stories Sharing intent (`com.instagram.share.ADD_TO_STORY`) — works
     * without any Meta developer app registration. Registering one (Meta for Developers, adding
     * the Android platform with this app's package + signing key hash) would only add a
     * "Return to [App Name]" attribution link on the story, which this skips.
     */
    fun shareToInstagramStory(context: Context, videoFile: File): Result<Unit> = runCatching {
        val uri = contentUriFor(context, videoFile)
        val intent = Intent("com.instagram.share.ADD_TO_STORY").apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage(INSTAGRAM_PACKAGE)
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            throw ActivityNotFoundException("Instagram isn't installed")
        }
        context.startActivity(intent)
    }

    /**
     * Facebook's native Stories intent (`com.facebook.stories.ADD_TO_STORY`) requires a
     * registered Facebook App ID passed as an extra, or Facebook rejects it outright — this app
     * has no such registration, so this opens Facebook's own share sheet instead (a normal
     * standard [Intent.ACTION_SEND] targeted at the Facebook app), where the user picks "Your
     * story" themselves inside Facebook. One extra tap, but works with zero external setup.
     */
    fun shareToFacebook(context: Context, videoFile: File): Result<Unit> = runCatching {
        val uri = contentUriFor(context, videoFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage(FACEBOOK_PACKAGE)
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            throw ActivityNotFoundException("Facebook isn't installed")
        }
        context.startActivity(intent)
    }
}
