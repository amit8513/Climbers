package com.example.climb.edgeagent.upload

import com.example.climb.edge.CapturedFrame
import com.example.climb.edge.ReferenceFrameUploader
import com.example.climb.edge.UploadResult
import java.io.File

/**
 * No real backend/Cloud Storage upload exists yet (Phase 1.5A scope — no `ClubVideoAsset`
 * write, no network call). Copies the captured file into a local "uploaded" directory and
 * reports that path as the "remote" reference, so the upload abstraction's contract
 * (success/failure, a reference string) is exercised end-to-end without a network.
 */
class LocalCopyUploader(private val uploadedDirectory: File) : ReferenceFrameUploader {

    override suspend fun upload(frame: CapturedFrame): UploadResult {
        val sourceFile = File(frame.filePath)
        if (!sourceFile.exists()) {
            return UploadResult(success = false, remoteReference = null, errorMessage = "source file missing: ${frame.filePath}")
        }
        return try {
            if (!uploadedDirectory.exists()) uploadedDirectory.mkdirs()
            val destination = File(uploadedDirectory, sourceFile.name)
            sourceFile.copyTo(destination, overwrite = true)
            UploadResult(success = true, remoteReference = destination.absolutePath, errorMessage = null)
        } catch (e: Exception) {
            UploadResult(success = false, remoteReference = null, errorMessage = e.message ?: e::class.java.simpleName)
        }
    }
}
