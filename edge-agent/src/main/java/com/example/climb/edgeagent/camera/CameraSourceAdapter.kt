package com.example.climb.edgeagent.camera

import com.example.climb.edge.CapturedFrame

/**
 * Phase 1.5A scope only: still-reference-frame capture. The plan doc's full future contract
 * (docs/ROUTE_ATTRIBUTION_PLAN.md §2 correction) also has `startRecording`/`stopRecording` for
 * the full video pipeline — those belong to Phase 2.5 and are deliberately not declared here yet,
 * since nothing in this phase implements them.
 */
interface CameraSourceAdapter {
    suspend fun captureStillReferenceFrame(): CapturedFrame
}
